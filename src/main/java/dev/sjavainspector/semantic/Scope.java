package dev.sjavainspector.semantic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Owns declarations only; control-flow facts live separately in FlowState. */
final class Scope {
    private final Scope parent;
    private final Map<String, VariableSymbol> symbols = new LinkedHashMap<>();
    Scope(Scope parent) { this.parent = parent; }
    Scope parent() { return parent; }
    boolean declare(VariableSymbol symbol) { return symbols.putIfAbsent(symbol.name(), symbol) == null; }
    Optional<VariableSymbol> resolve(String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            VariableSymbol symbol = scope.symbols.get(name);
            if (symbol != null) return Optional.of(symbol);
        }
        return Optional.empty();
    }
    void forgetLocals(FlowState flow) { symbols.values().forEach(flow::forget); }
}
