package dev.sjavainspector.api;

import java.util.Objects;

/** A half-open span [start.offset, end) of UTF-16 code units; columns also count UTF-16 units. */
public record SourceRange(SourcePosition start, int end) {
    public SourceRange {
        Objects.requireNonNull(start);
        if (end < start.offset()) throw new IllegalArgumentException("Range ends before it starts");
    }
    /** A range on a single line, such as an identifier token. */
    public static SourceRange of(SourcePosition start, int length) {
        return new SourceRange(start, start.offset() + length);
    }
    public int length() { return end - start.offset(); }
    public boolean contains(int offset) { return offset >= start.offset() && offset < end; }
}
