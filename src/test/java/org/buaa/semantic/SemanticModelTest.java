package org.buaa.semantic;

import org.buaa.lexer.Token;
import org.buaa.lexer.TokenType;

import java.util.List;

/** 仅依赖 JDK 的作用域和存储模型回归。 */
public final class SemanticModelTest {
    public static void main(String[] args) {
        SymbolTable table = new SymbolTable();
        Symbol global = symbol(1, "x", Symbol.Kind.VARIABLE, ValueType.INT, 1);
        check(table.define(global), "define global");
        check(!table.define(symbol(1, "x", Symbol.Kind.CONSTANT, ValueType.CHAR, 1)), "duplicate");
        table.enterScope();
        Symbol local = symbol(2, "x", Symbol.Kind.VARIABLE, ValueType.CHAR, 1);
        check(table.define(local) && table.lookup("x") == local, "inner name shadows global");
        table.enterScope();
        check(table.lookup("x") == local, "lookup through active stack");
        table.exitScope();
        table.exitScope();
        check(table.lookup("x") == global, "pop restores outer binding");
        check(table.enterScope().id() == 4, "scope numbers are not reused");
        check(table.scopes().get(1).symbols().get(0) == local, "history survives pop");

        Symbol function = symbol(1, "f", Symbol.Kind.FUNCTION, ValueType.INT, null);
        ActivationRecord frame = new ActivationRecord(function, 4);
        Symbol arrayParameter = symbol(4, "a", Symbol.Kind.PARAMETER, ValueType.INT_ARRAY, null);
        Symbol arrayLocal = symbol(5, "b", Symbol.Kind.VARIABLE, ValueType.CHAR_ARRAY, 6);
        StorageSlot parameter = frame.allocate(arrayParameter);
        StorageSlot array = frame.allocate(arrayLocal);
        check(parameter.offset() == 3 && parameter.size() == 1 && parameter.indirect(), "array address");
        check(array.offset() == 4 && array.size() == 6 && !array.indirect(), "local array storage");
        check(frame.slotCount() == 10 && frame.returnValueSlot() == 2, "frame header and size");
        try {
            frame.allocate(symbol(4, "saved", Symbol.Kind.STATIC_VARIABLE, ValueType.INT, 1));
            throw new AssertionError("static object must not enter stack frame");
        } catch (IllegalArgumentException expected) {
            // Static storage is separate from the activation record.
        }
        InitialValue compact = new InitialValue(1_000_000_000, List.of(7), true);
        check(compact.valueAt(0) == 7 && compact.valueAt(999_999_999) == 0, "implicit zero fill");
        check(compact.valueAt(1_000_000_000) == null, "invalid index remains unknown");
        check(symbol(4, "s", Symbol.Kind.STATIC_VARIABLE, ValueType.CHAR_ARRAY, 3)
                .typeName().equals("StaticCharArray"), "static array type name");
        check(symbol(4, "c", Symbol.Kind.CONSTANT, ValueType.INT_ARRAY, 3)
                .typeName().equals("ConstIntArray"), "constant array type name");
        check(function.typeName().equals("IntFunc"), "function type name");
        System.out.println("Passed semantic model tests.");
    }

    private static Symbol symbol(int scope, String name, Symbol.Kind kind, ValueType type, Integer length) {
        return new Symbol(scope, new Token(TokenType.IDENFR, name, 1, 1), kind, type, List.of(), length);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
