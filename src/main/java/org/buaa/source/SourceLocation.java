package org.buaa.source;

/** 源码中的位置，行号和列号均从 1 开始。 */
public record SourceLocation(int line, int column) {
    public SourceLocation {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("Source positions are one-based");
        }
    }
}
