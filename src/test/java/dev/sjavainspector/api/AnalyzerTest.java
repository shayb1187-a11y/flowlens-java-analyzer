package dev.sjavainspector.api;

import static dev.sjavainspector.testing.SourceAssertions.analyze;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyzerTest {
    @Test @DisplayName("result collections are immutable")
    void resultCollectionsImmutable() {
        var result = analyze("void f(){return;}");
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.methods().clear());
    }

    @Test @DisplayName("same analyzer has no state leakage between runs")
    void noStateLeakage() {
        Analyzer analyzer = new Analyzer();
        assertTrue(analyzer.analyze(new SourceUnit("a", "int x=1;")).isValid(), "first run");
        assertFalse(analyzer.analyze(new SourceUnit("b", "int y=x;")).isValid(), "globals leaked");
        assertTrue(analyzer.analyze(new SourceUnit("c", "int x=2;")).isValid(), "diagnostics leaked");
    }

    @Test @DisplayName("100 concurrent analyses share one facade safely")
    void concurrentAnalyses() throws Exception {
        Analyzer analyzer = new Analyzer();
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                boolean valid = i % 2 == 0;
                futures.add(pool.submit(() -> analyzer.analyze(new SourceUnit("parallel.sjava",
                        valid ? "void f(){int x=1;return;}" : "void f(){int x=x;return;}")).isValid() == valid));
            }
            for (Future<Boolean> future : futures) assertTrue(future.get(), "Concurrent result corrupted");
        } finally { pool.shutdownNow(); }
    }

    @Test @DisplayName("core never writes to console")
    void coreNeverWritesToConsole() {
        PrintStream oldOut = System.out, oldErr = System.err;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            try {
                System.setOut(capture); System.setErr(capture);
                analyze("void f(){missing();return;}");
            } finally { System.setOut(oldOut); System.setErr(oldErr); }
        }
        assertEquals(0, bytes.size());
    }
}
