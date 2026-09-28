package org.buaa.lexer;

public record Token(TokenType type,
                    String text,
                    int line,
                    int column) {
}
