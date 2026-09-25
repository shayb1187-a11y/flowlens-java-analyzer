package dev.sjavainspector.semantic;

import dev.sjavainspector.syntax.Type;

/** Identity represents a declaration, so shadowed variables never share assignment state. */
final class VariableSymbol {
    private final String name;
    private final Type type;
    private final boolean isFinal;
    VariableSymbol(String name, Type type, boolean isFinal) {
        this.name = name; this.type = type; this.isFinal = isFinal;
    }
    String name() { return name; }
    Type type() { return type; }
    boolean isFinal() { return isFinal; }
}
