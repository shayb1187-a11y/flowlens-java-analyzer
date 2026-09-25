package dev.sjavainspector.lint;

import static dev.sjavainspector.testing.SourceAssertions.analyze;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourceUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LintRulesTest {
    @Test @DisplayName("three built-in lint rules")
    void builtInRules() throws Exception {
        String source = Files.readString(Path.of("examples/lint.sjava"));
        var result = new Analyzer().analyze(new SourceUnit("lint.sjava", source));
        assertEquals(List.of("W001", "W002", "W003"), codes(result));
        assertTrue(result.isValid() && result.hasWarnings(), "Warnings must not invalidate source");
    }

    @Test @DisplayName("lint can be disabled without affecting validation")
    void lintCanBeDisabled() {
        assertEquals(List.of(), analyze("void Bad_Name(){return;int x=1;}").diagnostics());
    }

    @Test @DisplayName("custom rule plugs in without changing analyzer")
    void customRule() {
        AnalysisRule rule = (program, metrics) -> List.of(Diagnostic.warning("TEAM001", "Review required", program.position()));
        List<AnalysisRule> configured = new ArrayList<>(List.of(rule));
        Analyzer analyzer = new Analyzer(configured);
        configured.clear();
        assertEquals("TEAM001", analyzer.analyze(new SourceUnit("x.sjava", "")).diagnostics().get(0).code());
    }

    @Test @DisplayName("complexity thresholds are configurable")
    void complexityThresholds() {
        var result = new Analyzer(List.of(new ComplexityRule(1, 0)))
                .analyze(new SourceUnit("x.sjava", "void f(){if(true){return;}return;}"));
        assertEquals("W002", result.diagnostics().get(0).code());
    }

    @Test @DisplayName("metrics count decisions, logical operators, statements and depth")
    void metrics() {
        var result = analyze("void f(boolean a,boolean b){if(a && b){while(a){return;}}return;}");
        assertEquals(new AnalysisResult.MethodMetrics("f", 1, 4, 4, 2), result.methods().get(0));
    }

    @Test @DisplayName("unreachable warning after two returning branches")
    void unreachableAfterReturningBranches() {
        var result = new Analyzer().analyze(new SourceUnit("x.sjava", "void f(boolean b){if(b){return;}else{return;}int x=1;}"));
        assertEquals(List.of("W003"), codes(result));
    }

    private static List<String> codes(AnalysisResult result) {
        return result.diagnostics().stream().map(Diagnostic::code).toList();
    }
}
