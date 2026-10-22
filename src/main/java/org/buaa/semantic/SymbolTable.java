package org.buaa.semantic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** 活跃作用域使用栈查找；历史作用域独立保存，编号从 1 开始且不复用。 */
public final class SymbolTable {
    private final Deque<Scope> active = new ArrayDeque<>();
    private final List<Scope> scopes = new ArrayList<>();
    private final Map<String, Symbol> runtimeFunctions = RuntimeLibrary.functions();

    public SymbolTable() {
        enterScope();
    }

    public Scope enterScope() {
        Scope scope = new Scope(scopes.size() + 1, active.isEmpty() ? null : current().id());
        scopes.add(scope);
        active.push(scope);
        return scope;
    }

    public Scope exitScope() {
        if (active.size() <= 1) {
            throw new IllegalStateException("Cannot exit the global scope");
        }
        return active.pop();
    }

    public Scope current() { return active.getFirst(); }
    public List<Scope> scopes() { return List.copyOf(scopes); }

    public boolean define(Symbol symbol) {
        if (symbol.scopeId() != current().id()) {
            throw new IllegalArgumentException("Symbol belongs to a different scope");
        }
        return current().define(symbol);
    }

    public Symbol lookup(String name) {
        for (Scope scope : active) {
            Symbol symbol = scope.lookupLocal(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return runtimeFunctions.get(name);
    }
}
