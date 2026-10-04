package org.buaa.parser;

import org.buaa.ast.Ast;
import org.buaa.diagnostic.DiagnosticEngine;
import org.buaa.diagnostic.ErrorType;
import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;
import org.buaa.source.SourceSpan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.buaa.lexer.TokenType.*;

/**
 * 递归下降分析器：Java 方法调用栈保存尚未完成的文法规则，相当于下推栈。
 * 左递归表达式改为循环，并逐次构造左结合 AST、输出对应的归约标记。
 * AST 与文法输出分开保存，使语义分析不必依赖输出用的文法包装节点。
 */
public final class Parser {
    private final List<Token> tokens;
    private final DiagnosticEngine diagnostics;
    private final StringBuilder output = new StringBuilder();
    private int position;
    private Ast.CompUnit root;

    public Parser(List<Token> tokens, DiagnosticEngine diagnostics) {
        this.tokens = List.copyOf(tokens);
        this.diagnostics = Objects.requireNonNull(diagnostics);
        if (tokens.isEmpty() || tokens.get(tokens.size() - 1).type() != EOF) {
            throw new IllegalArgumentException("Token stream must end with EOF");
        }
    }

    /** 即使 diagnostics 已有词法错误，也完整构建 AST；重复调用返回同一个根节点。 */
    public Ast.CompUnit parse() {
        if (root != null) {
            return root;
        }
        Token start = current();
        List<Ast.Declaration> declarations = new ArrayList<>();
        while (isDeclarationStart()) {
            declarations.add(parseDeclaration());
        }
        List<Ast.Function> functions = new ArrayList<>();
        while (at(VOIDTK) || (isBaseType(current().type()) && peek(1).type() == IDENFR)) {
            functions.add(parseFunction());
        }
        Ast.Function mainFunction = parseMainFunction();
        expect(EOF);
        finish("CompUnit");
        root = new Ast.CompUnit(declarations, functions, mainFunction, spanFrom(start));
        return root;
    }

    /** 不含 EOF、虚拟补入的标点以及 Decl/BType/BlockItem 标记。 */
    public String output() {
        parse();
        return output.toString();
    }

    public void writeTo(Path path) throws IOException {
        Files.writeString(path, output());
    }

    private Ast.Declaration parseDeclaration() {
        Token start = current();
        boolean constant = match(CONSTTK);
        boolean isStatic = !constant && match(STATICTK);
        Token baseType = parseBaseType();
        List<Ast.Definition> definitions = new ArrayList<>();
        definitions.add(parseDefinition(constant));
        while (match(COMMA)) {
            definitions.add(parseDefinition(constant));
        }
        expect(SEMICN);
        finish(constant ? "ConstDecl" : "VarDecl");
        return new Ast.Declaration(constant, isStatic, baseType, definitions, spanFrom(start));
    }

    private Token parseBaseType() {
        if (!isBaseType(current().type())) {
            throw unexpected("int or char");
        }
        return advance();
    }

    private Ast.Definition parseDefinition(boolean constant) {
        Token name = expect(IDENFR);
        Ast.Expr length = null;
        if (match(LBRACK)) {
            length = parseConstExp();
            expect(RBRACK);
        }
        Ast.Initializer initializer = null;
        if (constant) {
            expect(ASSIGN);
            initializer = parseInitializer(true);
        } else if (match(ASSIGN)) {
            initializer = parseInitializer(false);
        }
        finish(constant ? "ConstDef" : "VarDef");
        return new Ast.Definition(name, length, initializer, spanFrom(name));
    }

    private Ast.Initializer parseInitializer(boolean constant) {
        Token start = current();
        Ast.Initializer initializer;
        if (at(STRCON)) {
            initializer = new Ast.StringInitializer(advance(), spanFrom(start));
        } else if (match(LBRACE)) {
            List<Ast.Expr> elements = new ArrayList<>();
            if (!at(RBRACE)) {
                elements.add(constant ? parseConstExp() : parseExp());
                while (match(COMMA)) {
                    elements.add(constant ? parseConstExp() : parseExp());
                }
            }
            expect(RBRACE);
            initializer = new Ast.ListInitializer(elements, spanFrom(start));
        } else {
            Ast.Expr expression = constant ? parseConstExp() : parseExp();
            initializer = new Ast.ExprInitializer(expression, spanFrom(start));
        }
        finish(constant ? "ConstInitVal" : "InitVal");
        return initializer;
    }

    private Ast.Function parseFunction() {
        Token type = current();
        if (!at(VOIDTK) && !isBaseType(type.type())) {
            throw unexpected("function type");
        }
        advance();
        finish("FuncType");
        Token name = expect(IDENFR);
        expect(LPARENT);
        List<Ast.Parameter> parameters = new ArrayList<>();
        if (isBaseType(current().type())) {
            parameters.add(parseParameter());
            while (match(COMMA)) {
                parameters.add(parseParameter());
            }
            finish("FuncFParams");
        }
        expect(RPARENT);
        Ast.Block body = parseBlock();
        finish("FuncDef");
        return new Ast.Function(type, name, parameters, body, spanFrom(type));
    }

    private Ast.Function parseMainFunction() {
        Token type = expect(INTTK);
        Token name = expect(MAINTK);
        expect(LPARENT);
        expect(RPARENT);
        Ast.Block body = parseBlock();
        finish("MainFuncDef");
        return new Ast.Function(type, name, List.of(), body, spanFrom(type));
    }

    private Ast.Parameter parseParameter() {
        Token type = parseBaseType();
        Token name = expect(IDENFR);
        boolean array = match(LBRACK);
        if (array) {
            expect(RBRACK);
        }
        finish("FuncFParam");
        return new Ast.Parameter(type, name, array, spanFrom(type));
    }

    private Ast.Block parseBlock() {
        Token start = expect(LBRACE);
        List<Ast.BlockItem> items = new ArrayList<>();
        while (!at(RBRACE) && !at(EOF)) {
            if (at(CONSTTK) || at(STATICTK) || isBaseType(current().type())) {
                items.add(parseDeclaration());
            } else {
                items.add(parseStmt());
            }
        }
        expect(RBRACE);
        finish("Block");
        return new Ast.Block(items, spanFrom(start));
    }

    private Ast.Stmt parseStmt() {
        Token start = current();
        Ast.Stmt statement;
        switch (start.type()) {
            case LBRACE -> statement = parseBlock();
            case IFTK -> {
                advance();
                expect(LPARENT);
                Ast.Expr condition = parseCond();
                expect(RPARENT);
                Ast.Stmt thenBranch = parseStmt();
                // 递归先完成内层 if，因此 else 自然匹配最近的未配对 if。
                Ast.Stmt elseBranch = match(ELSETK) ? parseStmt() : null;
                statement = new Ast.IfStmt(condition, thenBranch, elseBranch, spanFrom(start));
            }
            case WHILETK -> {
                advance();
                expect(LPARENT);
                Ast.Expr condition = parseCond();
                expect(RPARENT);
                Ast.Stmt body = parseStmt();
                statement = new Ast.WhileStmt(condition, body, spanFrom(start));
            }
            case SWITCHTK -> statement = parseSwitch();
            case BREAKTK -> {
                advance();
                expect(SEMICN);
                statement = new Ast.BreakStmt(spanFrom(start));
            }
            case CONTINUETK -> {
                advance();
                expect(SEMICN);
                statement = new Ast.ContinueStmt(spanFrom(start));
            }
            case RETURNTK -> {
                advance();
                Ast.Expr value = isExpressionStart() ? parseExp() : null;
                expect(SEMICN);
                statement = new Ast.ReturnStmt(value, spanFrom(start));
            }
            case PRINTFTK -> {
                advance();
                expect(LPARENT);
                Token format = expect(STRCON);
                List<Ast.Expr> arguments = new ArrayList<>();
                while (match(COMMA)) {
                    arguments.add(parseExp());
                }
                expect(RPARENT);
                expect(SEMICN);
                statement = new Ast.PrintfStmt(format, arguments, spanFrom(start));
            }
            case SEMICN -> {
                advance();
                statement = new Ast.ExprStmt(null, spanFrom(start));
            }
            default -> statement = parseAssignmentOrExpStmt();
        }
        finish("Stmt");
        return statement;
    }

    private Ast.SwitchStmt parseSwitch() {
        Token start = expect(SWITCHTK);
        expect(LPARENT);
        Ast.Expr expression = parseExp();
        expect(RPARENT);
        expect(LBRACE);
        List<Ast.CaseClause> cases = new ArrayList<>();
        while (at(CASETK) || at(DEFAULTTK)) {
            cases.add(parseCase());
        }
        expect(RBRACE);
        return new Ast.SwitchStmt(expression, cases, spanFrom(start));
    }

    private Ast.CaseClause parseCase() {
        Token keyword = advance();
        Ast.NumberLiteral value = keyword.type() == CASETK ? parseNumber() : null;
        expect(COLON);
        List<Ast.Stmt> statements = new ArrayList<>();
        while (!at(CASETK) && !at(DEFAULTTK) && !at(RBRACE) && !at(EOF)) {
            statements.add(parseStmt());
        }
        finish("CaseStmt");
        return new Ast.CaseClause(keyword, value, statements, spanFrom(keyword));
    }

    private Ast.Stmt parseAssignmentOrExpStmt() {
        Token start = current();
        Ast.Expr expression;
        if (at(IDENFR) && peek(1).type() != LPARENT) {
            // LVal 是赋值与表达式的公共前缀，只解析一次，避免回溯重复输出/报错。
            Ast.LValue lValue = parseLValue();
            if (match(ASSIGN)) {
                Ast.Expr value = parseExp();
                expect(SEMICN);
                return new Ast.Assignment(lValue, value, spanFrom(start));
            }
            finish("PrimaryExp");
            finish("UnaryExp");
            expression = parseAddExpTail(parseMulExpTail(lValue));
            finish("Exp");
        } else {
            expression = parseExp();
        }
        expect(SEMICN);
        return new Ast.ExprStmt(expression, spanFrom(start));
    }

    private Ast.Expr parseExp() {
        Ast.Expr expression = parseAddExp();
        finish("Exp");
        return expression;
    }

    private Ast.Expr parseConstExp() {
        Ast.Expr expression = parseAddExp();
        finish("ConstExp");
        return expression;
    }

    private Ast.Expr parseCond() {
        Ast.Expr expression = parseLOrExp();
        finish("Cond");
        return expression;
    }

    private Ast.LValue parseLValue() {
        Token name = expect(IDENFR);
        Ast.Expr index = null;
        if (match(LBRACK)) {
            index = parseExp();
            expect(RBRACK);
        }
        finish("LVal");
        return new Ast.LValue(name, index, spanFrom(name));
    }

    private Ast.Expr parsePrimaryExp() {
        Token start = current();
        Ast.Expr expression;
        if (match(LPARENT)) {
            Ast.Expr inner = parseExp();
            expect(RPARENT);
            expression = new Ast.GroupExpr(inner, spanFrom(start));
        } else if (at(IDENFR)) {
            expression = parseLValue();
        } else {
            expression = parseNumber();
        }
        finish("PrimaryExp");
        return expression;
    }

    private Ast.NumberLiteral parseNumber() {
        if (!at(INTCON) && !at(CHARCON)) {
            throw unexpected("integer or character constant");
        }
        Token number = advance();
        finish("Number");
        return new Ast.NumberLiteral(number, SourceSpan.of(number));
    }

    private Ast.Expr parseUnaryExp() {
        Token start = current();
        Ast.Expr expression;
        if (at(PLUS) || at(MINU) || at(NOT)) {
            Token operator = advance();
            finish("UnaryOp");
            expression = new Ast.UnaryExpr(operator, parseUnaryExp(), spanFrom(start));
        } else if (at(LPARENT) && isBaseType(peek(1).type())) {
            // 即便缺失 ')'，看到 '(' BType 即可判定是显式类型转换。
            advance();
            Token type = parseBaseType();
            expect(RPARENT);
            expression = new Ast.CastExpr(type, parseUnaryExp(), spanFrom(start));
        } else if (at(IDENFR) && peek(1).type() == LPARENT) {
            Token name = advance();
            expect(LPARENT);
            List<Ast.Expr> arguments = new ArrayList<>();
            if (isExpressionStart()) {
                arguments.add(parseExp());
                while (match(COMMA)) {
                    arguments.add(parseExp());
                }
                finish("FuncRParams");
            }
            expect(RPARENT);
            expression = new Ast.CallExpr(name, arguments, spanFrom(start));
        } else {
            expression = parsePrimaryExp();
        }
        finish("UnaryExp");
        return expression;
    }

    private Ast.Expr parseMulExp() {
        return parseMulExpTail(parseUnaryExp());
    }

    private Ast.Expr parseMulExpTail(Ast.Expr left) {
        return parseBinaryTail(left, this::parseUnaryExp, "MulExp", MULT, DIV, MOD);
    }

    private Ast.Expr parseAddExp() {
        return parseAddExpTail(parseMulExp());
    }

    private Ast.Expr parseAddExpTail(Ast.Expr left) {
        return parseBinaryTail(left, this::parseMulExp, "AddExp", PLUS, MINU);
    }

    private Ast.Expr parseRelExp() {
        return parseBinaryTail(parseAddExp(), this::parseAddExp, "RelExp", LSS, GRE, LEQ, GEQ);
    }

    private Ast.Expr parseEqExp() {
        return parseBinaryTail(parseRelExp(), this::parseRelExp, "EqExp", EQL, NEQ);
    }

    private Ast.Expr parseLAndExp() {
        return parseBinaryTail(parseEqExp(), this::parseEqExp, "LAndExp", AND);
    }

    private Ast.Expr parseLOrExp() {
        return parseBinaryTail(parseLAndExp(), this::parseLAndExp, "LOrExp", OR);
    }

    private Ast.Expr parseBinaryTail(Ast.Expr left, Supplier<Ast.Expr> parseRight,
                                     String component, TokenType... operators) {
        finish(component);
        while (atAny(operators)) {
            Token operator = advance();
            Ast.Expr right = parseRight.get();
            left = new Ast.BinaryExpr(left, operator, right,
                    new SourceSpan(left.span().start(), right.span().end()));
            // 对 A -> A op B，每次归约都输出 A，不能只在整个循环结束时输出。
            finish(component);
        }
        return left;
    }

    private boolean isDeclarationStart() {
        return at(CONSTTK) || at(STATICTK)
                || (isBaseType(current().type()) && peek(1).type() != MAINTK
                && peek(2).type() != LPARENT);
    }

    private boolean isBaseType(TokenType type) {
        return type == INTTK || type == CHARTK;
    }

    private boolean isExpressionStart() {
        return atAny(IDENFR, INTCON, CHARCON, LPARENT, PLUS, MINU, NOT);
    }

    private Token current() {
        return peek(0);
    }

    private Token peek(int offset) {
        return tokens.get(Math.min(position + offset, tokens.size() - 1));
    }

    private Token previous() {
        return tokens.get(Math.max(0, position - 1));
    }

    private boolean at(TokenType type) {
        return current().type() == type;
    }

    private boolean atAny(TokenType... types) {
        for (TokenType type : types) {
            if (at(type)) {
                return true;
            }
        }
        return false;
    }

    private Token advance() {
        Token token = current();
        if (token.type() != EOF) {
            position++;
            output.append(token.type()).append(' ').append(token.text()).append('\n');
        }
        return token;
    }

    private boolean match(TokenType type) {
        if (!at(type)) {
            return false;
        }
        advance();
        return true;
    }

    private Token expect(TokenType type) {
        if (at(type)) {
            return advance();
        }
        ErrorType error = switch (type) {
            case SEMICN -> ErrorType.MISSING_SEMICOLON;
            case RPARENT -> ErrorType.MISSING_RPARENT;
            case RBRACK -> ErrorType.MISSING_RBRACK;
            default -> null;
        };
        if (error == null) {
            // 文法说明保证不会出现其它破坏语法树结构的错误；此处防止死循环。
            throw unexpected(type.toString());
        }
        Token last = previous();
        diagnostics.report(last.line(), error);
        // 按插入缺失标点恢复：不消耗下一条语句的 token，也不输出虚拟 token。
        return new Token(type, "", last.line(), last.column() + last.text().length());
    }

    private SourceSpan spanFrom(Token start) {
        return SourceSpan.between(start, previous());
    }

    private void finish(String component) {
        output.append('<').append(component).append(">\n");
    }

    private IllegalStateException unexpected(String expected) {
        Token token = current();
        return new IllegalStateException("Unexpected " + token.type() + " at "
                + token.line() + ":" + token.column() + "; expected " + expected);
    }
}
