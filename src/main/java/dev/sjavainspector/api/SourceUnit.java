package dev.sjavainspector.api;

import java.util.Objects;

/** The core accepts text; reading files and rendering editors belong to adapters. */
public record SourceUnit(String name, String text) {
    public SourceUnit {
        Objects.requireNonNull(name);
        Objects.requireNonNull(text);
    }
}
