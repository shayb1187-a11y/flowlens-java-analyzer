package dev.sjavainspector.lint;

import dev.sjavainspector.api.AnalysisResult.MethodMetrics;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.syntax.Ast;
import java.util.List;

public final class MethodNamingRule implements AnalysisRule {
    @Override public List<Diagnostic> inspect(Ast.Program program, List<MethodMetrics> metrics) {
        return program.methods().stream().filter(m -> !m.name().matches("[a-z][A-Za-z0-9]*"))
                .map(m -> Diagnostic.warning("W001", "Prefer lowerCamelCase for method '" + m.name() + "'.", m.position()))
                .toList();
    }
}
