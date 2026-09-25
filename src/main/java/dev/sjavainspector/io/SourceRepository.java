package dev.sjavainspector.io;

import dev.sjavainspector.api.SourceUnit;
import java.io.IOException;
import java.nio.file.Path;

/** Application port: tests can supply in-memory sources without touching the disk. */
@FunctionalInterface
public interface SourceRepository {
    SourceUnit read(Path path) throws IOException;
}
