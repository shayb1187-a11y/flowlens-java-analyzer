package dev.sjavainspector.semantic;

import dev.sjavainspector.api.SourceRange;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Type;
import java.util.List;

/** A void method declaration. Methods have their own namespace, separate from variables. */
public final class MethodSymbol implements Symbol {
    private final Ast.Method node;
    private final SourceRange nameRange;
    private final Scope scope;
    private final boolean accepted;
    private final List<Type> parameterTypes;

    MethodSymbol(Ast.Method node, Scope scope, boolean accepted) {
        this.node = node;
        this.nameRange = SourceRange.of(node.namePosition(), node.name().length());
        this.scope = scope;
        this.accepted = accepted;
        this.parameterTypes = node.parameters().stream().map(Ast.Parameter::type).toList();
    }
    @Override public String name() { return node.name(); }
    @Override public SourceRange nameRange() { return nameRange; }
    @Override public Scope scope() { return scope; }
    @Override public boolean isAccepted() { return accepted; }
    public Ast.Method node() { return node; }
    public List<Type> parameterTypes() { return parameterTypes; }
    @Override public String toString() {
        return "method " + node.name() + parameterTypes.stream().map(Type::display).toList()
                + " @" + nameRange.start().line() + ":" + nameRange.start().column();
    }
}
