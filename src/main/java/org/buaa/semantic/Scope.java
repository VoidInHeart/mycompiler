package org.buaa.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一个词法作用域；按声明顺序保存成功定义的符号，退出后仍可用于输出。 */
public final class Scope {
    private final int id;
    private final Integer parentId;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    Scope(int id, Integer parentId) {
        this.id = id;
        this.parentId = parentId;
    }

    public int id() { return id; }
    public Integer parentId() { return parentId; }
    public List<Symbol> symbols() { return List.copyOf(symbols.values()); }
    public Symbol lookupLocal(String name) { return symbols.get(name); }

    boolean define(Symbol symbol) {
        return symbols.putIfAbsent(symbol.name(), symbol) == null;
    }
}
