package dev.sjavainspector.io;

import static dev.sjavainspector.testing.SourceAssertions.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSourceRepositoryTest {
    @TempDir Path directory;

    @Test @DisplayName("file adapter reads UTF-8 with optional BOM")
    void readsUtf8WithBom() throws Exception {
        Path path = Files.writeString(directory.resolve("bom.sjava"), "﻿String x=\"שלום\";");
        var source = new FileSourceRepository().read(path);
        assertEquals("String x=\"שלום\";", source.text());
        valid(source.text());
    }

    @Test @DisplayName("file adapter rejects malformed UTF-8")
    void rejectsMalformedUtf8() throws Exception {
        Path path = Files.write(directory.resolve("malformed.sjava"), new byte[]{(byte) 0xC3, 0x28});
        assertThrows(CharacterCodingException.class, () -> new FileSourceRepository().read(path));
    }

    @Test @DisplayName("file adapter rejects unsupported extension")
    void rejectsUnsupportedExtension() {
        IOException failure = assertThrows(IOException.class, () -> new FileSourceRepository().read(Path.of("source.txt")));
        assertTrue(failure.getMessage().contains(".sjava"), "Wrong failure");
    }
}
