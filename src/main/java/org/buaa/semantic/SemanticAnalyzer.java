package org.buaa.semantic;

import org.buaa.ast.Ast;
import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.diagnostic.ErrorType;
import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.buaa.diagnostic.ErrorType.*;
import static org.buaa.semantic.ValueType.*;

/**
 * 按源码顺序遍历 AST，不预声明后续函数；函数本身先入表，以支持递归调用。
 * 作用域栈、活动记录栈和循环/switch 上下文栈彼此独立，错误不会中断遍历。
 */
public final class SemanticAnalyzer {
    private enum Control { LOOP, SWITCH }
    private record Visit(Ast.Expr expression, boolean finish) { }

    private final DiagnosticEngine diagnostics;
    private final SymbolTable symbols = new SymbolTable();
    private final Deque<ActivationRecord> activeFrames = new ArrayDeque<>();
    private final Deque<Control> controls = new ArrayDeque<>();
    private final List<ActivationRecord> frames = new ArrayList<>();
    private final List<StorageSlot> staticStorage = new ArrayList<>();
    private final Map<Ast.Node, Symbol> bindings = new IdentityHashMap<>();
    private final Map<Ast.Expr, ExpressionInfo> expressions = new IdentityHashMap<>();
    private final Map<Symbol, StorageSlot> storage = new IdentityHashMap<>();
    private long staticSlotCount;
    private Ast.CompUnit root;
    private SemanticResult result;

    public SemanticAnalyzer(DiagnosticEngine diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public SemanticResult analyze(Ast.CompUnit unit) {
        if (root != null) {
            if (root != unit) {
                throw new IllegalStateException("Use a new analyzer for each compilation unit");
            }
            return result;
        }
        root = Objects.requireNonNull(unit);
        for (Ast.Declaration declaration : unit.declarations()) {
            declaration(declaration);
        }
        for (Ast.Function function : unit.functions()) {
            function(function);
        }
        function(unit.mainFunction());
        result = new SemanticResult(symbols.scopes(), frames, staticStorage, bindings, expressions, storage);
        return result;
    }

    private void declaration(Ast.Declaration declaration) {
        ValueType base = ValueType.from(declaration.baseType().type());
        Symbol.Kind kind = declaration.constant() ? Symbol.Kind.CONSTANT
                : declaration.isStatic() ? Symbol.Kind.STATIC_VARIABLE : Symbol.Kind.VARIABLE;
        for (Ast.Definition definition : declaration.definitions()) {
            boolean duplicate = symbols.current().lookupLocal(definition.name().text()) != null;
            if (duplicate) {
                report(definition.name(), REDEFINED_NAME);
            }
            boolean array = definition.arrayLength() != null;
            Integer length = 1;
            if (array) {
                ExpressionInfo info = expression(definition.arrayLength());
                requireType(info, INT, definition.arrayLength());
                length = info.type() == INT ? info.constantValue() : null;
            }
            Symbol symbol = new Symbol(symbols.current().id(), definition.name(), kind,
                    array ? base.arrayType() : base, List.of(), length);
            if (!duplicate) {
                symbols.define(symbol);
                bindings.put(definition, symbol);
            }
            // 当前声明在自己的初值中可见，但此时还没有可用于常量折叠的初值。
            symbol.setInitialValue(initializer(symbol, definition.initializer()));
            if (!duplicate) {
                allocate(symbol);
            }
        }
    }

    private InitialValue initializer(Symbol symbol, Ast.Initializer initializer) {
        int length = symbol.arrayLength() == null ? 0 : Math.max(0, symbol.arrayLength());
        boolean zeroFill = initializer != null || symbol.scopeId() == 1
                || symbol.kind() == Symbol.Kind.STATIC_VARIABLE;
        List<Integer> values = new ArrayList<>();
        ValueType elementType = symbol.type().elementType();
        if (initializer instanceof Ast.ExprInitializer scalar) {
            ExpressionInfo info = expression(scalar.expression());
            if (symbol.type().isArray()) {
                report(initializer, TYPE_MISMATCH);
            } else {
                requireType(info, elementType, scalar.expression());
            }
            values.add(info.constantValue());
        } else if (initializer instanceof Ast.ListInitializer list) {
            if (!symbol.type().isArray()) {
                report(initializer, TYPE_MISMATCH);
            }
            for (Ast.Expr element : list.elements()) {
                ExpressionInfo info = expression(element);
                requireType(info, elementType, element);
                values.add(info.constantValue());
            }
        } else if (initializer instanceof Ast.StringInitializer string) {
            if (elementType != CHAR) {
                report(string.literal(), TYPE_MISMATCH);
            }
            String text = string.literal().text();
            for (int i = 1; i < text.length() - 1; i++) {
                char value = text.charAt(i);
                if (value == '\\') {
                    value = (char) escape(text.charAt(++i));
                }
                values.add((int) value);
            }
        }
        // 不额外附加字符串终止符；依对象长度截断或零填充。
        if (values.size() > length) {
            values = values.subList(0, length);
        }
        return new InitialValue(length, values, zeroFill);
    }

    private void function(Ast.Function function) {
        List<ValueType> parameters = function.parameters().stream().map(parameter -> {
            ValueType type = ValueType.from(parameter.baseType().type());
            return parameter.array() ? type.arrayType() : type;
        }).toList();
        Symbol symbol = new Symbol(1, function.name(), Symbol.Kind.FUNCTION,
                ValueType.from(function.returnType().type()), parameters, null);
        // main 为关键字，只创建活动记录的函数描述，不插入全局符号表。
        if (!function.isMain() && !symbols.define(symbol)) {
            report(function.name(), REDEFINED_NAME);
        }
        bindings.put(function, symbol);
        Scope scope = symbols.enterScope();
        ActivationRecord frame = new ActivationRecord(symbol, scope.id());
        frames.add(frame);
        activeFrames.push(frame);
        try {
            for (int i = 0; i < function.parameters().size(); i++) {
                Ast.Parameter parameter = function.parameters().get(i);
                Symbol formal = new Symbol(scope.id(), parameter.name(), Symbol.Kind.PARAMETER,
                        parameters.get(i), List.of(), parameter.array() ? null : Integer.valueOf(1));
                if (symbols.define(formal)) {
                    bindings.put(parameter, formal);
                    allocate(formal);
                } else {
                    report(parameter.name(), REDEFINED_NAME);
                }
            }
            // 形参与函数体最外层声明共用同一作用域，不能再为函数体入栈。
            blockItems(function.body());
            List<Ast.BlockItem> items = function.body().items();
            if (symbol.type() != VOID && (items.isEmpty()
                    || !(items.get(items.size() - 1) instanceof Ast.ReturnStmt))) {
                diagnostics.report(function.body().span().end().line(), MISSING_RETURN);
            }
        } finally {
            activeFrames.pop();
            symbols.exitScope();
        }
    }

    private void allocate(Symbol symbol) {
        StorageSlot slot;
        if (symbol.scopeId() == 1 || symbol.kind() == Symbol.Kind.STATIC_VARIABLE) {
            int size = symbol.arrayLength() == null ? 1 : Math.max(1, symbol.arrayLength());
            slot = new StorageSlot(symbol, symbol.scopeId() == 1 ? StorageSlot.Area.GLOBAL
                    : StorageSlot.Area.STATIC_LOCAL, staticSlotCount, size, false);
            staticSlotCount += size;
            staticStorage.add(slot);
        } else {
            slot = activeFrames.getFirst().allocate(symbol);
        }
        storage.put(symbol, slot);
    }

    private void blockItems(Ast.Block block) {
        for (Ast.BlockItem item : block.items()) {
            if (item instanceof Ast.Declaration declaration) {
                declaration(declaration);
            } else {
                statement((Ast.Stmt) item);
            }
        }
    }

    private void statement(Ast.Stmt statement) {
        if (statement instanceof Ast.Block block) {
            symbols.enterScope();
            try {
                blockItems(block);
            } finally {
                symbols.exitScope();
            }
        } else if (statement instanceof Ast.Assignment assignment) {
            Symbol target = symbols.lookup(assignment.target().name().text());
            if (target != null && target.isConstant()) {
                report(assignment.target().name(), MODIFY_CONST);
            }
            ExpressionInfo left = expression(assignment.target());
            ExpressionInfo right = expression(assignment.value());
            if (left.type() != ERROR && right.type() != ERROR
                    && (!left.type().isScalar() || left.type() != right.type())) {
                report(assignment, TYPE_MISMATCH);
            }
        } else if (statement instanceof Ast.ExprStmt expr) {
            if (expr.expression() != null) {
                expression(expr.expression());
            }
        } else if (statement instanceof Ast.IfStmt branch) {
            requireScalar(expression(branch.condition()), branch.condition());
            statement(branch.thenBranch());
            if (branch.elseBranch() != null) {
                statement(branch.elseBranch());
            }
        } else if (statement instanceof Ast.WhileStmt loop) {
            requireScalar(expression(loop.condition()), loop.condition());
            controls.push(Control.LOOP);
            try {
                statement(loop.body());
            } finally {
                controls.pop();
            }
        } else if (statement instanceof Ast.SwitchStmt selection) {
            switchStatement(selection);
        } else if (statement instanceof Ast.BreakStmt) {
            if (controls.isEmpty()) {
                report(statement, INVALID_BREAK_CONTINUE);
            }
        } else if (statement instanceof Ast.ContinueStmt) {
            if (!controls.contains(Control.LOOP)) {
                report(statement, INVALID_BREAK_CONTINUE);
            }
        } else if (statement instanceof Ast.ReturnStmt returned) {
            ValueType expected = activeFrames.getFirst().function().type();
            ExpressionInfo actual = returned.value() == null
                    ? new ExpressionInfo(VOID, null) : expression(returned.value());
            if (expected == VOID && returned.value() != null) {
                report(returned, VOID_RETURN_VALUE);
            } else {
                requireType(actual, expected, returned);
            }
        } else if (statement instanceof Ast.PrintfStmt printf) {
            printf(printf);
        } else {
            throw new IllegalStateException("Unknown statement: " + statement.getClass());
        }
    }

    private void switchStatement(Ast.SwitchStmt selection) {
        ExpressionInfo selector = expression(selection.expression());
        requireScalar(selector, selection.expression());
        Set<Integer> labels = new HashSet<>();
        boolean seenDefault = false;
        controls.push(Control.SWITCH);
        try {
            // switch 的花括号不是文法 Block；只有 case 内显式 Block 才创建作用域。
            for (Ast.CaseClause clause : selection.cases()) {
                if (clause.value() == null) {
                    if (seenDefault) {
                        report(clause.keyword(), DUPLICATE_CASE_DEFAULT);
                    }
                    seenDefault = true;
                } else {
                    ExpressionInfo value = expression(clause.value());
                    if (selector.type() != ERROR) {
                        requireType(value, selector.type(), clause.value());
                    }
                    if (!labels.add(value.constantValue())) {
                        report(clause.keyword(), DUPLICATE_CASE_DEFAULT);
                    }
                }
                for (Ast.Stmt statement : clause.statements()) {
                    statement(statement);
                }
            }
        } finally {
            controls.pop();
        }
    }

    private void printf(Ast.PrintfStmt printf) {
        List<ValueType> expected = new ArrayList<>();
        String format = printf.format().text();
        for (int i = 1; i < format.length() - 1; i++) {
            if (format.charAt(i) == '%' && i + 1 < format.length() - 1) {
                ValueType type = switch (format.charAt(i + 1)) {
                    case 'd' -> INT;
                    case 'c' -> CHAR;
                    case 's' -> CHAR_ARRAY;
                    default -> null;
                };
                if (type != null) {
                    expected.add(type);
                    i++;
                }
            }
        }
        boolean countMatches = expected.size() == printf.arguments().size();
        if (!countMatches) {
            report(printf, PRINTF_MISMATCH);
        }
        for (int i = 0; i < printf.arguments().size(); i++) {
            ExpressionInfo actual = expression(printf.arguments().get(i));
            if (countMatches) {
                requireType(actual, expected.get(i), printf);
            }
        }
    }

    /** 显式后序栈处理表达式，保留语法阶段对长左结合表达式的支持。 */
    private ExpressionInfo expression(Ast.Expr root) {
        Deque<Visit> pending = new ArrayDeque<>();
        pending.push(new Visit(root, false));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            Ast.Expr expr = visit.expression();
            if (expressions.containsKey(expr)) {
                continue;
            }
            if (visit.finish()) {
                expressions.put(expr, finishExpression(expr));
                continue;
            }
            pending.push(new Visit(expr, true));
            if (expr instanceof Ast.BinaryExpr binary) {
                pending.push(new Visit(binary.right(), false));
                pending.push(new Visit(binary.left(), false));
            } else if (expr instanceof Ast.UnaryExpr unary) {
                pending.push(new Visit(unary.operand(), false));
            } else if (expr instanceof Ast.CastExpr cast) {
                pending.push(new Visit(cast.operand(), false));
            } else if (expr instanceof Ast.GroupExpr group) {
                pending.push(new Visit(group.expression(), false));
            } else if (expr instanceof Ast.LValue value && value.index() != null) {
                pending.push(new Visit(value.index(), false));
            } else if (expr instanceof Ast.CallExpr call) {
                for (int i = call.arguments().size() - 1; i >= 0; i--) {
                    pending.push(new Visit(call.arguments().get(i), false));
                }
            }
        }
        return expressions.get(root);
    }

    private ExpressionInfo finishExpression(Ast.Expr expr) {
        if (expr instanceof Ast.NumberLiteral literal) {
            Token token = literal.token();
            if (token.type() == TokenType.INTCON) {
                return new ExpressionInfo(INT, new BigInteger(token.text()).intValue());
            }
            String text = token.text();
            return new ExpressionInfo(CHAR, text.charAt(1) == '\\'
                    ? escape(text.charAt(2)) : (int) text.charAt(1));
        }
        if (expr instanceof Ast.GroupExpr group) {
            return expressions.get(group.expression());
        }
        if (expr instanceof Ast.LValue value) {
            return lValue(value);
        }
        if (expr instanceof Ast.CallExpr call) {
            return call(call);
        }
        if (expr instanceof Ast.CastExpr cast) {
            ExpressionInfo operand = expressions.get(cast.operand());
            if (!requireScalar(operand, cast)) {
                return ExpressionInfo.ERROR;
            }
            ValueType target = ValueType.from(cast.targetType().type());
            return new ExpressionInfo(target, normalize(operand.constantValue(), target));
        }
        if (expr instanceof Ast.UnaryExpr unary) {
            ExpressionInfo operand = expressions.get(unary.operand());
            if (!requireScalar(operand, unary)) {
                return ExpressionInfo.ERROR;
            }
            Integer value = operand.constantValue();
            if (value != null) {
                value = switch (unary.operator().type()) {
                    case PLUS -> value;
                    case MINU -> -value;
                    case NOT -> value == 0 ? 1 : 0;
                    default -> throw new IllegalStateException("Unknown unary operator");
                };
            }
            ValueType resultType = unary.operator().type() == TokenType.NOT ? INT : operand.type();
            return new ExpressionInfo(resultType, normalize(value, resultType));
        }
        if (expr instanceof Ast.BinaryExpr binary) {
            ExpressionInfo left = expressions.get(binary.left());
            ExpressionInfo right = expressions.get(binary.right());
            if (left.type() == ERROR || right.type() == ERROR) {
                return ExpressionInfo.ERROR;
            }
            if (!left.type().isScalar() || left.type() != right.type()) {
                report(binary.operator(), TYPE_MISMATCH);
                return ExpressionInfo.ERROR;
            }
            Integer value = fold(binary.operator().type(), left.constantValue(), right.constantValue());
            // 算术结果保留操作数类型；比较和逻辑结果统一为 int，值为 0 或 1。
            ValueType resultType = switch (binary.operator().type()) {
                case LSS, LEQ, GRE, GEQ, EQL, NEQ, AND, OR -> INT;
                default -> left.type();
            };
            return new ExpressionInfo(resultType, normalize(value, resultType));
        }
        throw new IllegalStateException("Unknown expression: " + expr.getClass());
    }

    private ExpressionInfo lValue(Ast.LValue value) {
        Symbol symbol = symbols.lookup(value.name().text());
        if (symbol == null) {
            report(value.name(), UNDEFINED_NAME);
            return ExpressionInfo.ERROR;
        }
        bindings.put(value, symbol);
        if (symbol.isFunction()) {
            report(value.name(), TYPE_MISMATCH);
            return ExpressionInfo.ERROR;
        }
        if (value.index() == null) {
            Integer constant = symbol.isConstant() && symbol.type().isScalar()
                    && symbol.initialValue() != null ? symbol.initialValue().valueAt(0) : null;
            return new ExpressionInfo(symbol.type(), constant);
        }
        ExpressionInfo index = expressions.get(value.index());
        if (!symbol.type().isArray()) {
            report(value.name(), TYPE_MISMATCH);
            return ExpressionInfo.ERROR;
        }
        if (!requireType(index, INT, value.index())) {
            return ExpressionInfo.ERROR;
        }
        Integer constant = symbol.isConstant() && index.constantValue() != null
                && symbol.initialValue() != null ? symbol.initialValue().valueAt(index.constantValue()) : null;
        return new ExpressionInfo(symbol.type().elementType(), constant);
    }

    private ExpressionInfo call(Ast.CallExpr call) {
        Symbol symbol = symbols.lookup(call.callee().text());
        if (symbol == null) {
            report(call.callee(), UNDEFINED_NAME);
            return ExpressionInfo.ERROR;
        }
        bindings.put(call, symbol);
        if (!symbol.isFunction()) {
            report(call.callee(), TYPE_MISMATCH);
            return ExpressionInfo.ERROR;
        }
        if (symbol.parameterTypes().size() != call.arguments().size()) {
            report(call.callee(), PARAM_COUNT_MISMATCH);
            return ExpressionInfo.ERROR;
        }
        boolean valid = true;
        for (int i = 0; i < call.arguments().size(); i++) {
            ExpressionInfo actual = expressions.get(call.arguments().get(i));
            if (!requireType(actual, symbol.parameterTypes().get(i), call)) {
                valid = false;
            }
        }
        return valid ? new ExpressionInfo(symbol.type(), null) : ExpressionInfo.ERROR;
    }

    private boolean requireScalar(ExpressionInfo info, Ast.Node node) {
        if (info.type() == ERROR) {
            return false;
        }
        if (!info.type().isScalar()) {
            report(node, TYPE_MISMATCH);
            return false;
        }
        return true;
    }

    private boolean requireType(ExpressionInfo info, ValueType expected, Ast.Node node) {
        if (info.type() == ERROR) {
            return false;
        }
        if (info.type() != expected) {
            report(node, TYPE_MISMATCH);
            return false;
        }
        return true;
    }

    private static Integer normalize(Integer value, ValueType type) {
        if (value == null) {
            return null;
        }
        return type == CHAR ? value & 255 : value;
    }

    private static Integer fold(TokenType operator, Integer left, Integer right) {
        if (left == null || right == null) {
            return null;
        }
        int a = left;
        int b = right;
        return switch (operator) {
            case PLUS -> a + b;
            case MINU -> a - b;
            case MULT -> a * b;
            // 文法未给除零错误码；保留未知值，避免错误程序导致分析器崩溃。
            case DIV -> b == 0 ? null : Integer.valueOf(a / b);
            case MOD -> b == 0 ? null : Integer.valueOf(a % b);
            case LSS -> a < b ? 1 : 0;
            case LEQ -> a <= b ? 1 : 0;
            case GRE -> a > b ? 1 : 0;
            case GEQ -> a >= b ? 1 : 0;
            case EQL -> a == b ? 1 : 0;
            case NEQ -> a != b ? 1 : 0;
            case AND -> a != 0 && b != 0 ? 1 : 0;
            case OR -> a != 0 || b != 0 ? 1 : 0;
            default -> throw new IllegalStateException("Unknown binary operator: " + operator);
        };
    }

    private static int escape(char escaped) {
        return switch (escaped) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '0' -> 0;
            case '\\' -> '\\';
            case '\'' -> '\'';
            default -> throw new IllegalArgumentException("Unsupported escape: " + escaped);
        };
    }

    private void report(Ast.Node node, ErrorType type) {
        diagnostics.report(node.span().start().line(), type);
    }

    private void report(Token token, ErrorType type) {
        diagnostics.report(token.line(), type);
    }
}
