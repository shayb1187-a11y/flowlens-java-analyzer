package dev.sjavainspector.semantic;

import dev.sjavainspector.syntax.Ast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns declarations only; control-flow facts live separately in FlowState. Mutable while its
 * analysis runs, then frozen: afterwards every exposed collection is unmodifiable.
 */
public final class Scope {
    public enum Kind { GLOBAL, METHOD, BLOCK }
    private final Kind kind;
    private final Scope parent;
    private final Ast.Node node;
    private final Map<String, VariableSymbol> symbols = new LinkedHashMap<>();
    private List<Scope> children = new ArrayList<>();
    private List<VariableSymbol> declarations;

    Scope(Kind kind, Scope parent, Ast.Node node) {
        this.kind = kind; this.parent = parent; this.node = node;
        if (parent != null) parent.children.add(this);
    }
    public Kind kind() { return kind; }
    public Optional<Scope> parent() { return Optional.ofNullable(parent); }
    /** The program for the global scope, the method for a method scope, or the block. */
    public Ast.Node node() { return node; }
    public List<Scope> children() { return children; }
    /** Accepted variable and parameter declarations, in declaration order. */
    public List<VariableSymbol> declarations() {
        if (declarations == null) throw new IllegalStateException("Scope is still being analyzed");
        return declarations;
    }
    /** True when this scope is {@code other} or one of its ancestors. */
    public boolean encloses(Scope other) {
        for (Scope scope = other; scope != null; scope = scope.parent) if (scope == this) return true;
        return false;
    }

    boolean declaresLocally(String name) { return symbols.containsKey(name); }
    void declare(VariableSymbol symbol) {
        if (declarations != null) throw new IllegalStateException("Scope is frozen");
        symbols.put(symbol.name(), symbol);
    }
    Optional<VariableSymbol> resolve(String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            VariableSymbol symbol = scope.symbols.get(name);
            if (symbol != null) return Optional.of(symbol);
        }
        return Optional.empty();
    }
    void forgetLocals(FlowState flow) { symbols.values().forEach(flow::forget); }
    void freeze() {
        declarations = List.copyOf(symbols.values());
        children = List.copyOf(children);
        children.forEach(Scope::freeze);
    }
    @Override public String toString() { return kind + " scope"; }
}
