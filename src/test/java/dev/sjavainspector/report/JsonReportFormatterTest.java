package dev.sjavainspector.report;

import static dev.sjavainspector.testing.SourceAssertions.analyze;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonReportFormatterTest {
    @Test @DisplayName("JSON schema empty report golden output")
    void emptyReportGolden() {
        String json = new JsonReportFormatter().format(List.of(analyze("")));
        assertEquals("{\"schemaVersion\":1,\"results\":[{\"source\":\"test.sjava\",\"valid\":true,\"diagnostics\":[],\"methods\":[]}]}\n", json);
    }

    @Test @DisplayName("JSON escapes quotes, slashes, controls and surrogates")
    void escaping() throws Exception {
        var diagnostic = Diagnostic.error("TEST", "\"\\\n\r\t\u0001😀", SourcePosition.start());
        var result = new AnalysisResult("C:\\test.sjava", List.of(diagnostic), List.of());
        String json = new JsonReportFormatter().format(List.of(result));
        assertTrue(json.contains("\\\"\\\\\\n\\r\\t\\u0001\\ud83d\\ude00"), () -> "Bad JSON escaping: " + json);
        // Kept as a build artifact so the output can be checked by an independent JSON parser.
        Files.writeString(Files.createDirectories(Path.of("target")).resolve("json-escaping.json"), json);
    }
}
