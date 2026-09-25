package dev.sjavainspector.lint;

import dev.sjavainspector.api.AnalysisResult.MethodMetrics;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.syntax.Ast;
import java.util.ArrayList;
import java.util.List;

/** Immutable configuration makes this rule safely reusable by concurrent analyses. */
public final class ComplexityRule implements AnalysisRule {
    private final int maxComplexity;
    private final int maxNesting;
    public ComplexityRule(int maxComplexity, int maxNesting) {
        if (maxComplexity < 1 || maxNesting < 0) throw new IllegalArgumentException("Invalid complexity thresholds");
        this.maxComplexity = maxComplexity; this.maxNesting = maxNesting;
    }
    @Override public List<Diagnostic> inspect(Ast.Program program, List<MethodMetrics> metrics) {
        List<Diagnostic> result = new ArrayList<>();
        for (int i = 0; i < program.methods().size(); i++) {
            MethodMetrics metric = metrics.get(i);
            if (metric.cyclomaticComplexity() > maxComplexity || metric.maxNesting() > maxNesting) {
                result.add(Diagnostic.warning("W002", "Method '" + metric.name() + "' has complexity "
                        + metric.cyclomaticComplexity() + " and nesting " + metric.maxNesting()
                        + "; configured limits are " + maxComplexity + " and " + maxNesting + ".",
                        program.methods().get(i).position()));
            }
        }
        return List.copyOf(result);
    }
}
