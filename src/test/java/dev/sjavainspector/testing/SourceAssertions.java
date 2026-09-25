package dev.sjavainspector.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.SourceUnit;
import java.util.List;

/** Source-level assertions shared by the analyzer tests; lint rules are disabled so only validation is observed. */
public final class SourceAssertions {
    private SourceAssertions() { }

    public static AnalysisResult analyze(String source) {
        return new Analyzer(List.of()).analyze(new SourceUnit("test.sjava", source));
    }

    public static void valid(String source) {
        AnalysisResult result = analyze(source);
        assertTrue(result.isValid(), () -> "Expected valid source: " + result.diagnostics());
    }

    public static AnalysisResult error(String code, String source) {
        AnalysisResult result = analyze(source);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals(code)),
                () -> "Expected " + code + ": " + result.diagnostics());
        return result;
    }
}
