package org.buaa.semantic;

import org.buaa.lexer.TokenType;

/** 表达式类型不携带 const/static；ERROR 用于阻止一个错误引发后续类型误报。 */
public enum ValueType {
    INT, CHAR, INT_ARRAY, CHAR_ARRAY, VOID, ERROR;

    public static ValueType from(TokenType token) {
        return switch (token) {
            case INTTK -> INT;
            case CHARTK -> CHAR;
            case VOIDTK -> VOID;
            default -> throw new IllegalArgumentException("Not a type token: " + token);
        };
    }

    public boolean isScalar() {
        return this == INT || this == CHAR;
    }

    public boolean isArray() {
        return this == INT_ARRAY || this == CHAR_ARRAY;
    }

    public ValueType arrayType() {
        return switch (this) {
            case INT -> INT_ARRAY;
            case CHAR -> CHAR_ARRAY;
            default -> throw new IllegalStateException("Not an element type: " + this);
        };
    }

    public ValueType elementType() {
        return switch (this) {
            case INT_ARRAY -> INT;
            case CHAR_ARRAY -> CHAR;
            default -> this;
        };
    }
}
