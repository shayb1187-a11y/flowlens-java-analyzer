package dev.sjavainspector.semantic;

import dev.sjavainspector.api.SourceRange;
import dev.sjavainspector.syntax.Type;
import java.util.Optional;

/** Identity represents a declaration, so shadowed variables never share assignment state. */
public final class VariableSymbol implements Symbol {
    public enum Kind { GLOBAL, LOCAL, PARAMETER }
    private final String name;
    private final Type type;
    private final boolean isFinal;
    private final Kind kind;
    private final SourceRange nameRange;
    private final Scope scope;
    private final boolean accepted;
    private final VariableSymbol shadowed;

    VariableSymbol(String name, Type type, boolean isFinal, Kind kind, SourceRange nameRange,
                   Scope scope, boolean accepted, VariableSymbol shadowed) {
        this.name = name; this.type = type; this.isFinal = isFinal; this.kind = kind;
        this.nameRange = nameRange; this.scope = scope; this.accepted = accepted; this.shadowed = shadowed;
    }
    @Override public String name() { return name; }
    public Type type() { return type; }
    public boolean isFinal() { return isFinal; }
    public Kind kind() { return kind; }
    @Override public SourceRange nameRange() { return nameRange; }
    @Override public Scope scope() { return scope; }
    @Override public boolean isAccepted() { return accepted; }
    /** The variable or parameter in an ancestor scope that this accepted declaration hides. */
    public Optional<VariableSymbol> shadowed() { return Optional.ofNullable(shadowed); }
    @Override public String toString() {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + type.display() + " " + name
                + " @" + nameRange.start().line() + ":" + nameRange.start().column();
    }
}
