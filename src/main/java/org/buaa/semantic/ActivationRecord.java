package org.buaa.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * 每个函数的活动记录描述，供后续后端实例化为调用栈帧；分析阶段不会执行函数。
 * 槽 0/1 预留调用者链接/返回地址，有返回值时槽 2 预留返回值。
 * 所有嵌套块的自动局部对象均归属于该函数，暂不复用离开作用域后的槽。
 */
public final class ActivationRecord {
    public static final int CALLER_LINK_SLOT = 0;
    public static final int RETURN_ADDRESS_SLOT = 1;

    private final Symbol function;
    private final int bodyScopeId;
    private final List<StorageSlot> slots = new ArrayList<>();
    private long slotCount;

    public ActivationRecord(Symbol function, int bodyScopeId) {
        if (!function.isFunction()) {
            throw new IllegalArgumentException("An activation record needs a function");
        }
        this.function = function;
        this.bodyScopeId = bodyScopeId;
        slotCount = function.type() == ValueType.VOID ? 2 : 3;
    }

    public Symbol function() { return function; }
    public int bodyScopeId() { return bodyScopeId; }
    public Integer returnValueSlot() { return function.type() == ValueType.VOID ? null : 2; }
    public long slotCount() { return slotCount; }
    public List<StorageSlot> slots() { return List.copyOf(slots); }

    StorageSlot allocate(Symbol symbol) {
        boolean parameter = symbol.kind() == Symbol.Kind.PARAMETER;
        if (symbol.isFunction() || symbol.scopeId() == 1
                || symbol.kind() == Symbol.Kind.STATIC_VARIABLE) {
            throw new IllegalArgumentException("Object does not have automatic storage");
        }
        boolean indirect = parameter && symbol.type().isArray();
        int size = indirect || symbol.arrayLength() == null ? 1 : Math.max(1, symbol.arrayLength());
        StorageSlot slot = new StorageSlot(symbol,
                parameter ? StorageSlot.Area.PARAMETER : StorageSlot.Area.LOCAL,
                slotCount, size, indirect);
        slots.add(slot);
        slotCount += size;
        return slot;
    }
}
