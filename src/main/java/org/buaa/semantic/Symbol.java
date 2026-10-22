package org.buaa.semantic;

import org.buaa.lexer.Token;

import java.util.List;
import java.util.Objects;

/** 每次成功声明对应唯一对象，绑定关系和存储位置均使用该对象身份。 */
public final class Symbol {
    public enum Kind { CONSTANT, VARIABLE, STATIC_VARIABLE, PARAMETER, FUNCTION }

    private final int scopeId;
    private final Token token;
    private final Kind kind;
    private final ValueType type;
    private final List<ValueType> parameterTypes;
    // 数组形参或错误程序中无法求出的长度为 null；标量为 1。
    private final Integer arrayLength;
    private InitialValue initialValue;

    public Symbol(int scopeId, Token token, Kind kind, ValueType type,
                  List<ValueType> parameterTypes, Integer arrayLength) {
        this.scopeId = scopeId;
        this.token = Objects.requireNonNull(token);
        this.kind = Objects.requireNonNull(kind);
        this.type = Objects.requireNonNull(type);
        this.parameterTypes = List.copyOf(parameterTypes);
        this.arrayLength = arrayLength;
    }

    public int scopeId() { return scopeId; }
    public Token token() { return token; }
    public String name() { return token.text(); }
    public Kind kind() { return kind; }
    public ValueType type() { return type; }
    public List<ValueType> parameterTypes() { return parameterTypes; }
    public Integer arrayLength() { return arrayLength; }
    public InitialValue initialValue() { return initialValue; }
    public boolean isFunction() { return kind == Kind.FUNCTION; }
    public boolean isConstant() { return kind == Kind.CONSTANT; }

    void setInitialValue(InitialValue value) {
        initialValue = value;
    }

    public String typeName() {
        String base = switch (type.elementType()) {
            case INT -> "Int";
            case CHAR -> "Char";
            case VOID -> "Void";
            default -> throw new IllegalStateException("Invalid symbol type: " + type);
        };
        if (isFunction()) {
            return base + "Func";
        }
        String prefix = switch (kind) {
            case CONSTANT -> "Const";
            case STATIC_VARIABLE -> "Static";
            default -> "";
        };
        return prefix + base + (type.isArray() ? "Array" : "");
    }
}
