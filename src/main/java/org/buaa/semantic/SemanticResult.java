package org.buaa.semantic;

import org.buaa.ast.Ast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 保存作用域历史、声明/使用绑定、表达式类型及存储布局，供后续阶段直接查询。 */
public final class SemanticResult {
    private final List<Scope> scopes;
    private final List<ActivationRecord> activationRecords;
    private final List<StorageSlot> staticStorage;
    private final Map<Ast.Node, Symbol> bindings;
    private final Map<Ast.Expr, ExpressionInfo> expressions;
    private final Map<Symbol, StorageSlot> storage;

    SemanticResult(List<Scope> scopes, List<ActivationRecord> activationRecords,
                   List<StorageSlot> staticStorage, Map<Ast.Node, Symbol> bindings,
                   Map<Ast.Expr, ExpressionInfo> expressions, Map<Symbol, StorageSlot> storage) {
        this.scopes = List.copyOf(scopes);
        this.activationRecords = List.copyOf(activationRecords);
        this.staticStorage = List.copyOf(staticStorage);
        // AST 是 record，普通 HashMap 会递归计算整棵子树的 hashCode。
        // 按节点身份索引既保留精确绑定，也避免长表达式导致栈溢出。
        this.bindings = Collections.unmodifiableMap(new IdentityHashMap<>(bindings));
        this.expressions = Collections.unmodifiableMap(new IdentityHashMap<>(expressions));
        this.storage = Collections.unmodifiableMap(new IdentityHashMap<>(storage));
    }

    public List<Scope> scopes() { return scopes; }
    public List<ActivationRecord> activationRecords() { return activationRecords; }
    public List<StorageSlot> staticStorage() { return staticStorage; }
    public Symbol symbolOf(Ast.Node node) { return bindings.get(node); }
    public ExpressionInfo expressionInfo(Ast.Expr expression) { return expressions.get(expression); }
    public StorageSlot storageOf(Symbol symbol) { return storage.get(symbol); }

    public String symbolOutput() {
        StringBuilder output = new StringBuilder();
        for (Scope scope : scopes) {
            for (Symbol symbol : scope.symbols()) {
                output.append(scope.id()).append(' ').append(symbol.name()).append(' ')
                        .append(symbol.typeName()).append('\n');
            }
        }
        return output.toString();
    }

    public void writeSymbolsTo(Path path) throws IOException {
        Files.writeString(path, symbolOutput());
    }
}
