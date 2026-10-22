package org.buaa.semantic;

/**
 * 与目标机无关的逻辑槽位，不表示字节偏移。标量占 1 槽，数组对象占 length 槽，
 * 数组形参占 1 个地址槽。全局/静态对象的偏移属于独立的静态存储区。
 */
public record StorageSlot(Symbol symbol, Area area, long offset, int size, boolean indirect) {
    public enum Area { PARAMETER, LOCAL, GLOBAL, STATIC_LOCAL }
}
