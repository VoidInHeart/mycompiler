package org.buaa.semantic;

/** constantValue 为 null 表示不能在编译期求值；char 常量值始终处于 0..255。 */
public record ExpressionInfo(ValueType type, Integer constantValue) {
    public static final ExpressionInfo ERROR = new ExpressionInfo(ValueType.ERROR, null);
}
