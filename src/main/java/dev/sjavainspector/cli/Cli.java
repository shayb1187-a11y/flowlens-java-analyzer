package dev.sjavainspector.cli;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import dev.sjavainspector.io.SourceRepository;
import dev.sjavainspector.report.JsonReportFormatter;
import dev.sjavainspector.report.ReportFormatter;
import dev.sjavainspector.report.TextReportFormatter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Testable application boundary: dependencies and output streams are supplied by the caller. */
public final class Cli {
    private static final String USAGE = """
            FlowLens 1.0.1
            Usage:
              java -jar flowlens.jar check [options] FILE.sjava [FILE.sjava ...]
              java -jar flowlens.jar gui
            Options:
              --format=text|json    Human-readable output or JSON schema v1 (default: text)
              --no-lint             Disable style and maintainability warnings
              --warnings-as-errors  Return exit code 1 when warnings are present
              --                    Treat remaining arguments as file paths
              --help                Show this help
            Exit codes: 0 = valid, 1 = source errors / warning gate, 2 = arguments or I/O failure.
            Each input is analyzed independently; files do not share declarations.
            """;
    private final SourceRepository sources;
    public Cli(SourceRepository sources) { this.sources = sources; }

    public int run(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length == 1 && args[0].equals("--help")) { out.print(USAGE); out.flush(); return 0; }
        if (args.length == 0 || !args[0].equals("check")) return usageError("Expected the 'check' command.", err);
        boolean noLint = false;
        boolean warningsAsErrors = false;
        boolean positional = false;
        ReportFormatter formatter = new TextReportFormatter();
        List<Path> paths = new ArrayList<>();
        try {
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (positional || !arg.startsWith("--")) { paths.add(Path.of(arg)); continue; }
                switch (arg) {
                    case "--" -> positional = true;
                    case "--no-lint" -> noLint = true;
                    case "--warnings-as-errors" -> warningsAsErrors = true;
                    case "--format=json" -> formatter = new JsonReportFormatter();
                    case "--format=text" -> formatter = new TextReportFormatter();
                    case "--help" -> { out.print(USAGE); out.flush(); return 0; }
                    default -> { return usageError("Unknown option: " + arg, err); }
                }
            }
        } catch (InvalidPathException e) { return usageError("Invalid input path: " + e.getInput(), err); }
        if (paths.isEmpty()) return usageError("Provide at least one .sjava file.", err);

        Analyzer analyzer = noLint ? new Analyzer(List.of()) : new Analyzer();
        List<AnalysisResult> results = new ArrayList<>();
        boolean ioFailure = false;
        for (Path path : paths) {
            try { results.add(analyzer.analyze(sources.read(path))); }
            catch (IOException | SecurityException e) {
                ioFailure = true;
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new AnalysisResult(path.toString(), List.of(Diagnostic.error("EIO",
                        "Cannot read source: " + reason, SourcePosition.start())), List.of()));
            }
        }
        out.print(formatter.format(results)); out.flush();
        if (ioFailure) return 2;
        for (AnalysisResult result : results) {
            if (!result.isValid() || warningsAsErrors && result.hasWarnings()) return 1;
        }
        return 0;
    }
    private int usageError(String message, PrintWriter err) {
        err.println(message); err.print(USAGE); err.flush(); return 2;
    }
}
