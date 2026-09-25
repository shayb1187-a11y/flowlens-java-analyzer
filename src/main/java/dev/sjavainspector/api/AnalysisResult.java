package dev.sjavainspector.api;

import dev.sjavainspector.semantic.SemanticModel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of one analysis. The semantic model is present whenever parsing succeeded, including
 * when semantic validation reported errors; it is absent for input-limit, lexical, parse, and I/O failures.
 */
public record AnalysisResult(String sourceName, List<Diagnostic> diagnostics,
                             List<MethodMetrics> methods, Optional<SemanticModel> semanticModel) {
    public AnalysisResult {
        diagnostics = List.copyOf(diagnostics);
        methods = List.copyOf(methods);
        Objects.requireNonNull(semanticModel);
    }
    public AnalysisResult(String sourceName, List<Diagnostic> diagnostics, List<MethodMetrics> methods) {
        this(sourceName, diagnostics, methods, Optional.empty());
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
