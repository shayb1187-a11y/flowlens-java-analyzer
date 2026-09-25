package dev.sjavainspector.semantic;

import dev.sjavainspector.api.SourceRange;

/**
 * A declaration within one analysis. Symbols use identity equality: two declarations with the
 * same name and type are still different symbols. Identities belong to a single source snapshot.
 */
public sealed interface Symbol permits VariableSymbol, MethodSymbol {
    String name();
    /** Range of the declared name token. */
    SourceRange nameRange();
    /** The scope that owns the declaration; methods belong to the global scope. */
    Scope scope();
    /** False for a rejected duplicate declaration, which never becomes visible to name lookup. */
    boolean isAccepted();
}
