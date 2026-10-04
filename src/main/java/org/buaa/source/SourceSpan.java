package org.buaa.source;

import org.buaa.lexer.Token;

/** 左闭右开的源码范围；缺失的标点不会延长 AST 的源码范围。 */
public record SourceSpan(SourceLocation start, SourceLocation end) {
    public static SourceSpan between(Token first, Token last) {
        return new SourceSpan(
                new SourceLocation(first.line(), first.column()),
                new SourceLocation(last.line(), last.column() + last.text().length())
        );
    }

    public static SourceSpan of(Token token) {
        return between(token, token);
    }
}
