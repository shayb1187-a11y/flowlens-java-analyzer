package dev.sjavainspector.api;

/** A one-based line and column, plus a zero-based UTF-16 offset. */
public record SourcePosition(int line, int column, int offset) {
    public SourcePosition {
        if (line < 1 || column < 1 || offset < 0) {
            throw new IllegalArgumentException("Invalid source position");
        }
    }
    public static SourcePosition start() { return new SourcePosition(1, 1, 0); }
}
