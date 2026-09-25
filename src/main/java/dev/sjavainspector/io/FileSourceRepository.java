package dev.sjavainspector.io;

import dev.sjavainspector.api.SourceUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSourceRepository implements SourceRepository {
    private static final int MAX_BYTES = 4_000_000;
    @Override public SourceUnit read(Path path) throws IOException {
        if (!path.toString().endsWith(".sjava")) throw new IOException("Expected a .sjava file");
        // Bounded read also handles files that grow between metadata lookup and reading.
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Source exceeds the 4 MB input limit");
            var decoder = StandardCharsets.UTF_8.newDecoder();
            String text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return new SourceUnit(path.toString(), text);
        }
    }
}
