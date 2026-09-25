package dev.sjavainspector.report;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Diagnostic;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Versioned machine-readable schema. The formatter never writes to System.out. */
public final class JsonReportFormatter implements ReportFormatter {
    @Override public String format(List<AnalysisResult> results) {
        StringJoiner reports = new StringJoiner(",", "{\"schemaVersion\":1,\"results\":[", "]}\n");
        for (AnalysisResult result : results) {
            StringJoiner diagnostics = new StringJoiner(",", "[", "]");
            for (Diagnostic d : result.diagnostics()) {
                diagnostics.add("{\"severity\":" + quote(d.severity().name().toLowerCase(Locale.ROOT))
                        + ",\"code\":" + quote(d.code()) + ",\"message\":" + quote(d.message())
                        + ",\"line\":" + d.position().line() + ",\"column\":" + d.position().column()
                        + ",\"offset\":" + d.position().offset() + "}");
            }
            StringJoiner methods = new StringJoiner(",", "[", "]");
            for (AnalysisResult.MethodMetrics m : result.methods()) {
                methods.add("{\"name\":" + quote(m.name()) + ",\"line\":" + m.line() + ",\"statements\":"
                        + m.statements() + ",\"cyclomaticComplexity\":" + m.cyclomaticComplexity()
                        + ",\"maxNesting\":" + m.maxNesting() + "}");
            }
            reports.add("{\"source\":" + quote(result.sourceName()) + ",\"valid\":" + result.isValid()
                    + ",\"diagnostics\":" + diagnostics + ",\"methods\":" + methods + "}");
        }
        return reports.toString();
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || Character.isSurrogate(c)) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
