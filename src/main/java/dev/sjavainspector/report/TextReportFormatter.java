package dev.sjavainspector.report;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Diagnostic;
import java.util.List;
import java.util.Locale;

public final class TextReportFormatter implements ReportFormatter {
    @Override public String format(List<AnalysisResult> results) {
        StringBuilder out = new StringBuilder();
        for (AnalysisResult result : results) {
            out.append(result.isValid() ? "PASS " : "FAIL ").append(result.sourceName()).append('\n');
            for (Diagnostic d : result.diagnostics()) {
                out.append("  ").append(result.sourceName()).append(':').append(d.position().line()).append(':')
                        .append(d.position().column()).append("  ").append(d.severity().name().toLowerCase(Locale.ROOT))
                        .append(' ').append(d.code()).append("  ").append(d.message()).append('\n');
            }
            for (AnalysisResult.MethodMetrics m : result.methods()) {
                out.append("  ").append(m.name()).append(": ").append(m.statements()).append(" statements, complexity ")
                        .append(m.cyclomaticComplexity()).append(", nesting ").append(m.maxNesting()).append('\n');
            }
        }
        return out.toString();
    }
}
