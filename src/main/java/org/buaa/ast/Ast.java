package org.buaa.ast;

import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;
import org.buaa.source.SourceSpan;

import java.util.List;

/**
 * 供后续语义分析使用的不可变 AST。
 * 不保留 Exp/AddExp 等单纯的文法包装；原始 token 和源码范围保留定位信息。
 * 可选子节点用 null 表示，列表始终非 null。标识符不在语法阶段进行名字解析。
 */
public final class Ast {
    private Ast() {
    }

    public interface Node {
        SourceSpan span();
    }

    public interface BlockItem extends Node {
    }

    public interface Stmt extends BlockItem {
    }

    public interface Expr extends Node {
    }

    public record CompUnit(List<Declaration> declarations, List<Function> functions,
                           Function mainFunction, SourceSpan span) implements Node {
        public CompUnit {
            declarations = List.copyOf(declarations);
            functions = List.copyOf(functions);
        }
    }

    public record Declaration(boolean constant, boolean isStatic, Token baseType,
                              List<Definition> definitions, SourceSpan span) implements BlockItem {
        public Declaration {
            definitions = List.copyOf(definitions);
        }
    }

    /** arrayLength 为 null 表示标量；initializer 为 null 表示没有显式初值。 */
    public record Definition(Token name, Expr arrayLength, Initializer initializer,
                             SourceSpan span) implements Node {
    }

    public interface Initializer extends Node {
    }

    public record ExprInitializer(Expr expression, SourceSpan span) implements Initializer {
    }

    public record ListInitializer(List<Expr> elements, SourceSpan span) implements Initializer {
        public ListInitializer {
            elements = List.copyOf(elements);
        }
    }

    public record StringInitializer(Token literal, SourceSpan span) implements Initializer {
    }

    public record Function(Token returnType, Token name, List<Parameter> parameters,
                           Block body, SourceSpan span) implements Node {
        public Function {
            parameters = List.copyOf(parameters);
        }

        public boolean isMain() {
            return name.type() == TokenType.MAINTK;
        }
    }

    public record Parameter(Token baseType, Token name, boolean array,
                            SourceSpan span) implements Node {
    }

    public record Block(List<BlockItem> items, SourceSpan span) implements Stmt {
        public Block {
            items = List.copyOf(items);
        }
    }

    public record Assignment(LValue target, Expr value, SourceSpan span) implements Stmt {
    }

    /** expression 为 null 表示空语句。 */
    public record ExprStmt(Expr expression, SourceSpan span) implements Stmt {
    }

    public record IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch,
                         SourceSpan span) implements Stmt {
    }

    public record WhileStmt(Expr condition, Stmt body, SourceSpan span) implements Stmt {
    }

    public record SwitchStmt(Expr expression, List<CaseClause> cases,
                             SourceSpan span) implements Stmt {
        public SwitchStmt {
            cases = List.copyOf(cases);
        }
    }

    /** value 为 null 表示 default，保留 keyword 以定位重复标签错误。 */
    public record CaseClause(Token keyword, NumberLiteral value, List<Stmt> statements,
                             SourceSpan span) implements Node {
        public CaseClause {
            statements = List.copyOf(statements);
        }
    }

    public record BreakStmt(SourceSpan span) implements Stmt {
    }

    public record ContinueStmt(SourceSpan span) implements Stmt {
    }

    /** value 为 null 表示不带返回值的 return。 */
    public record ReturnStmt(Expr value, SourceSpan span) implements Stmt {
    }

    public record PrintfStmt(Token format, List<Expr> arguments, SourceSpan span) implements Stmt {
        public PrintfStmt {
            arguments = List.copyOf(arguments);
        }
    }

    /** 数值保留原文，由语义分析解释整型值和字符转义。 */
    public record NumberLiteral(Token token, SourceSpan span) implements Expr {
    }

    /** index 为 null 表示未使用下标；标量和数组名由语义分析区分。 */
    public record LValue(Token name, Expr index, SourceSpan span) implements Expr {
    }

    public record GroupExpr(Expr expression, SourceSpan span) implements Expr {
    }

    public record CallExpr(Token callee, List<Expr> arguments, SourceSpan span) implements Expr {
        public CallExpr {
            arguments = List.copyOf(arguments);
        }
    }

    public record UnaryExpr(Token operator, Expr operand, SourceSpan span) implements Expr {
    }

    public record BinaryExpr(Expr left, Token operator, Expr right, SourceSpan span) implements Expr {
    }

    public record CastExpr(Token targetType, Expr operand, SourceSpan span) implements Expr {
    }
}
