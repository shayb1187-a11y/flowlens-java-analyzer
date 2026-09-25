package dev.sjavainspector.api;

import java.util.List;

public record AnalysisResult(String sourceName, List<Diagnostic> diagnostics,
                             List<MethodMetrics> methods) {
    public AnalysisResult {
        diagnostics = List.copyOf(diagnostics);
        methods = List.copyOf(methods);
    }
    public boolean isValid() {
        return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }
    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING);
    }
    public record MethodMetrics(String name, int line, int statements,
                                int cyclomaticComplexity, int maxNesting) { }
}
