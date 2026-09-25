package dev.sjavainspector.report;

import dev.sjavainspector.api.AnalysisResult;
import java.util.List;

/** Strategy: output format varies independently of analysis. */
@FunctionalInterface
public interface ReportFormatter {
    String format(List<AnalysisResult> results);
}
