package dev.sjavainspector.semantic;

import java.util.HashSet;
import java.util.Set;

/** Facts known on every path reaching a point. Branches get independent copies. */
final class FlowState {
    private final Set<VariableSymbol> initialized;
    private boolean reachable = true;
    FlowState() { initialized = new HashSet<>(); }
    private FlowState(FlowState other) {
        initialized = new HashSet<>(other.initialized);
        reachable = other.reachable;
    }
    FlowState copy() { return new FlowState(this); }
    boolean isInitialized(VariableSymbol symbol) { return initialized.contains(symbol); }
    void initialize(VariableSymbol symbol) { initialized.add(symbol); }
    void forget(VariableSymbol symbol) { initialized.remove(symbol); }
    boolean reachable() { return reachable; }
    void terminate() { reachable = false; }
    static FlowState merge(FlowState left, FlowState right) {
        if (!left.reachable) return right.copy();
        if (!right.reachable) return left.copy();
        FlowState result = left.copy();
        result.initialized.retainAll(right.initialized);
        return result;
    }
}
