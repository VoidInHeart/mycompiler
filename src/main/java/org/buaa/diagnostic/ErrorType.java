package org.buaa.diagnostic;

public enum ErrorType {
    ILLEGAL_SYMBOL('a'),
    REDEFINED_NAME('b'),
    UNDEFINED_NAME('c'),
    PARAM_COUNT_MISMATCH('d'),
    TYPE_MISMATCH('e'),
    VOID_RETURN_VALUE('f'),
    MISSING_RETURN('g'),
    MODIFY_CONST('h'),
    MISSING_SEMICOLON('i'),
    MISSING_RPARENT('j'),
    MISSING_RBRACK('k'),
    PRINTF_MISMATCH('l'),
    INVALID_BREAK_CONTINUE('m'),
    DUPLICATE_CASE_DEFAULT('n');

    private final char code;

    ErrorType(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }
}