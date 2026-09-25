package dev.sjavainspector.semantic;

import dev.sjavainspector.api.SourceRange;
import java.util.Objects;
import java.util.Optional;

/**
 * One use of an identifier. {@code target} is empty when the name did not resolve. A variable
 * read inside its own initializer ({@code int x = x;}) is a READ bound to the new declaration.
 */
public record Reference(Kind kind, String name, SourceRange range, Scope scope, Optional<Symbol> target) {
    public enum Kind { READ, WRITE, CALL }
    public Reference {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(name);
        Objects.requireNonNull(range);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(target);
    }
}
