package dev.sjavainspector;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.SourceUnit;
import java.util.List;
import java.util.Objects;

final class Assertions {
    private Assertions() { }
    static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + "; received " + actual);
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static AnalysisResult analyze(String source) { return new Analyzer(List.of()).analyze(new SourceUnit("test.sjava", source)); }
    static void valid(String source) {
        AnalysisResult result = analyze(source);
        check(result.isValid(), "Expected valid source: " + result.diagnostics());
    }
    static AnalysisResult error(String code, String source) {
        AnalysisResult result = analyze(source);
        check(result.diagnostics().stream().anyMatch(d -> d.code().equals(code)), "Expected " + code + ": " + result.diagnostics());
        return result;
    }
    static void immutable(Runnable mutation) {
        try { mutation.run(); throw new AssertionError("Mutation unexpectedly succeeded"); }
        catch (UnsupportedOperationException expected) { /* correct */ }
    }
}
