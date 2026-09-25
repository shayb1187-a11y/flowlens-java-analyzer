package dev.sjavainspector.api;

import java.util.Objects;

public record Diagnostic(Severity severity, String code, String message, SourcePosition position) {
    public enum Severity { ERROR, WARNING }
    public Diagnostic {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
        Objects.requireNonNull(position);
    }
    public static Diagnostic error(String code, String message, SourcePosition position) {
        return new Diagnostic(Severity.ERROR, code, message, position);
    }
    public static Diagnostic warning(String code, String message, SourcePosition position) {
        return new Diagnostic(Severity.WARNING, code, message, position);
    }
}
