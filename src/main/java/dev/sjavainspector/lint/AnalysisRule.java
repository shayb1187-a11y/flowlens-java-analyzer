package dev.sjavainspector.lint;

import dev.sjavainspector.api.AnalysisResult.MethodMetrics;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.syntax.Ast;
import java.util.List;

/** Extension point for read-only rules. Implementations must be stateless/thread-safe. */
@FunctionalInterface
public interface AnalysisRule {
    List<Diagnostic> inspect(Ast.Program program, List<MethodMetrics> metrics);
}
