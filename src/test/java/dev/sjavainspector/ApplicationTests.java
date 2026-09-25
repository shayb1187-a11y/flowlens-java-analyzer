package dev.sjavainspector;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.cli.Cli;
import dev.sjavainspector.io.FileSourceRepository;
import dev.sjavainspector.io.SourceRepository;
import dev.sjavainspector.lint.AnalysisRule;
import dev.sjavainspector.lint.ComplexityRule;
import dev.sjavainspector.report.JsonReportFormatter;
import dev.sjavainspector.ui.WorkbenchPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static dev.sjavainspector.Assertions.*;
import static dev.sjavainspector.TestSuite.test;

final class ApplicationTests {
    private ApplicationTests() { }
    static void run() {
        test("three built-in lint rules", () -> {
            String source = Files.readString(Path.of("examples/lint.sjava"));
            var result = new Analyzer().analyze(new SourceUnit("lint.sjava", source));
            equal(List.of("W001", "W002", "W003"), result.diagnostics().stream().map(Diagnostic::code).toList());
            check(result.isValid() && result.hasWarnings(), "Warnings must not invalidate source");
        });
        test("lint can be disabled without affecting validation", () -> {
            var result = analyze("void Bad_Name(){return;int x=1;}");
            equal(List.of(), result.diagnostics());
        });
        test("custom rule plugs in without changing analyzer", () -> {
            AnalysisRule rule = (program, metrics) -> List.of(Diagnostic.warning("TEAM001", "Review required", program.position()));
            List<AnalysisRule> configured = new ArrayList<>(List.of(rule));
            Analyzer analyzer = new Analyzer(configured);
            configured.clear();
            equal("TEAM001", analyzer.analyze(new SourceUnit("x.sjava", "")).diagnostics().get(0).code());
        });
        test("complexity thresholds are configurable", () -> {
            var result = new Analyzer(List.of(new ComplexityRule(1, 0)))
                    .analyze(new SourceUnit("x.sjava", "void f(){if(true){return;}return;}"));
            equal("W002", result.diagnostics().get(0).code());
        });
        test("metrics count decisions, logical operators, statements and depth", () -> {
            var result = analyze("void f(boolean a,boolean b){if(a && b){while(a){return;}}return;}");
            equal(new AnalysisResult.MethodMetrics("f", 1, 4, 4, 2), result.methods().get(0));
        });
        test("unreachable warning after two returning branches", () -> {
            var result = new Analyzer().analyze(new SourceUnit("x.sjava", "void f(boolean b){if(b){return;}else{return;}int x=1;}"));
            equal(List.of("W003"), result.diagnostics().stream().map(Diagnostic::code).toList());
        });
        test("result collections are immutable", () -> {
            var result = analyze("void f(){return;}");
            immutable(() -> result.diagnostics().clear());
            immutable(() -> result.methods().clear());
        });
        test("same analyzer has no state leakage between runs", () -> {
            Analyzer analyzer = new Analyzer();
            check(analyzer.analyze(new SourceUnit("a", "int x=1;")).isValid(), "first run");
            check(!analyzer.analyze(new SourceUnit("b", "int y=x;")).isValid(), "globals leaked");
            check(analyzer.analyze(new SourceUnit("c", "int x=2;")).isValid(), "diagnostics leaked");
        });
        test("100 concurrent analyses share one facade safely", () -> {
            Analyzer analyzer = new Analyzer();
            var pool = Executors.newFixedThreadPool(4);
            try {
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    boolean valid = i % 2 == 0;
                    futures.add(pool.submit(() -> analyzer.analyze(new SourceUnit("parallel.sjava",
                            valid ? "void f(){int x=1;return;}" : "void f(){int x=x;return;}")).isValid() == valid));
                }
                for (Future<Boolean> future : futures) check(future.get(), "Concurrent result corrupted");
            } finally { pool.shutdownNow(); }
        });
        test("core never writes to console", () -> {
            PrintStream oldOut = System.out, oldErr = System.err;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                try {
                    System.setOut(capture); System.setErr(capture);
                    analyze("void f(){missing();return;}");
                } finally { System.setOut(oldOut); System.setErr(oldErr); }
            }
            equal(0, bytes.size());
        });
        test("file adapter reads UTF-8 with optional BOM", () -> withFile("\uFEFFString x=\"שלום\";", path -> {
            var source = new FileSourceRepository().read(path);
            equal("String x=\"שלום\";", source.text());
            valid(source.text());
        }));
        test("file adapter rejects malformed UTF-8", () -> withFile("", path -> {
            Files.write(path, new byte[]{(byte) 0xC3, 0x28});
            try { new FileSourceRepository().read(path); throw new AssertionError("Malformed encoding accepted"); }
            catch (java.nio.charset.CharacterCodingException expected) { /* correct */ }
        }));
        test("file adapter rejects unsupported extension", () -> {
            try { new FileSourceRepository().read(Path.of("source.txt")); throw new AssertionError("Extension accepted"); }
            catch (IOException expected) { check(expected.getMessage().contains(".sjava"), "Wrong failure"); }
        });
        test("CLI processes multiple files independently", () -> {
            SourceRepository repo = p -> new SourceUnit(p.toString(), p.toString().equals("first.sjava") ? "int x=1;" : "int y=x;");
            var run = cli(repo, "check", "first.sjava", "second.sjava");
            equal(1, run.exit()); check(run.out().contains("PASS first.sjava") && run.out().contains("FAIL second.sjava"), "Batch lost results");
        });
        test("CLI I/O failure takes precedence over source failure", () -> {
            var run = cli(p -> {
                if (p.toString().equals("missing.sjava")) throw new IOException("not found");
                return new SourceUnit(p.toString(), "int x=x;");
            }, "check", "bad.sjava", "missing.sjava");
            equal(2, run.exit()); check(run.out().contains("EIO") && run.out().contains("E103"), "Missing batch errors");
        });
        test("CLI warnings gate and no-lint switch", () -> {
            SourceRepository repo = p -> new SourceUnit(p.toString(), "void Bad_Name(){return;}");
            equal(0, cli(repo, "check", "a.sjava").exit());
            equal(1, cli(repo, "check", "--warnings-as-errors", "a.sjava").exit());
            equal(0, cli(repo, "check", "--warnings-as-errors", "--no-lint", "a.sjava").exit());
        });
        test("CLI rejects malformed invocation", () -> {
            SourceRepository repo = p -> new SourceUnit(p.toString(), "");
            equal(2, cli(repo).exit());
            equal(2, cli(repo, "check").exit());
            equal(2, cli(repo, "check", "--format=xml", "a.sjava").exit());
            equal(2, cli(repo, "wat").exit());
            equal(0, cli(repo, "--help").exit());
        });
        test("CLI end-of-options marker", () -> {
            var run = cli(p -> new SourceUnit(p.toString(), ""), "check", "--", "--source.sjava");
            equal(0, run.exit()); check(run.out().contains("--source.sjava"), "Path lost");
        });
        test("JSON schema empty report golden output", () -> {
            String json = new JsonReportFormatter().format(List.of(analyze("")));
            equal("{\"schemaVersion\":1,\"results\":[{\"source\":\"test.sjava\",\"valid\":true,\"diagnostics\":[],\"methods\":[]}]}\n", json);
        });
        test("JSON escapes quotes, slashes, controls and surrogates", () -> {
            var diagnostic = Diagnostic.error("TEST", "\"\\\n\r\t\u0001😀", SourcePosition.start());
            var result = new AnalysisResult("C:\\test.sjava", List.of(diagnostic), List.of());
            String json = new JsonReportFormatter().format(List.of(result));
            check(json.contains("\\\"\\\\\\n\\r\\t\\u0001\\ud83d\\ude00"), "Bad JSON escaping: " + json);
            Files.writeString(Path.of("build/json-escaping.json"), json);
        });
        test("CLI JSON is clean stdout", () -> {
            var run = cli(p -> new SourceUnit(p.toString(), "int x=x;"), "check", "--format=json", "a.sjava");
            equal(1, run.exit()); equal("", run.err());
            check(run.out().startsWith("{\"schemaVersion\":1"), "JSON polluted with logging");
        });
        test("real CLI process exit codes", () -> {
            equal(0, process("check", "examples/valid.sjava"));
            equal(1, process("check", "examples/errors.sjava"));
            equal(2, process("check", "examples/does-not-exist.sjava"));
            equal(2, process("gui")); // Deliberately headless JVM.
        });
        test("Swing analysis, diagnostic navigation, stale-result rejection and rendering", ApplicationTests::desktop);
    }

    private record Invocation(int exit, String out, String err) { }
    private static Invocation cli(SourceRepository sources, String... args) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int status = new Cli(sources).run(args, new PrintWriter(out), new PrintWriter(err));
        return new Invocation(status, out.toString(), err.toString());
    }
    @FunctionalInterface private interface FileCheck { void run(Path path) throws Exception; }
    private static void withFile(String content, FileCheck check) throws Exception {
        Path path = Files.createTempFile("inspector-test-", ".sjava");
        try { Files.writeString(path, content); check.run(path); }
        finally { Files.deleteIfExists(path); }
    }
    private static int process(String... args) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(executable, "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), "dev.sjavainspector.cli.Main"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
    private static void desktop() throws Exception {
        AtomicReference<WorkbenchPanel> reference = new AtomicReference<>();
        String source = Files.readString(Path.of("examples/errors.sjava"));
        SwingUtilities.invokeAndWait(() -> {
            WorkbenchPanel panel = new WorkbenchPanel(); reference.set(panel);
            panel.setSource(new SourceUnit("errors.sjava", source));
            find(panel, JButton.class, b -> b.getText().equals("Analyze source")).doClick();
        });
        WorkbenchPanel panel = reference.get();
        waitUntilAnalyzed(panel);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(panel, JTable.class, t -> t.getColumnCount() == 4);
            equal(5, table.getRowCount());
            table.setRowSelectionInterval(0, 0);
            JTextArea editor = find(panel, JTextArea.class, t -> true);
            equal(source.indexOf("retries;", source.indexOf("double budget")), editor.getCaretPosition());
            check(find(panel, JButton.class, b -> b.getText().equals("Export JSON")).isEnabled(), "No export after analysis");
            panel.setSize(1160, 760); layout(panel);
            BufferedImage image = new BufferedImage(1160, 760, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            panel.printAll(graphics); graphics.dispose();
            try { ImageIO.write(image, "png", Path.of("build/workbench-preview.png").toFile()); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            // Edit immediately after starting another run, before its callback can run on the EDT.
            find(panel, JButton.class, b -> b.getText().equals("Analyze source")).doClick();
            editor.append("\n// edited during analysis");
            equal(0, table.getRowCount());
            check(!find(panel, JButton.class, b -> b.getText().equals("Export JSON")).isEnabled(), "Stale report remains exportable");
        });
        waitUntilAnalyzed(panel);
        SwingUtilities.invokeAndWait(() -> check(find(panel, JLabel.class,
                label -> label.getText().startsWith("Source changed during analysis")) != null, "Stale result was displayed"));
    }
    private static void waitUntilAnalyzed(WorkbenchPanel panel) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> done.set(find(panel, JButton.class,
                    b -> b.getText().equals("Analyze source")).isEnabled()));
            if (done.get()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Swing analysis timed out");
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> match) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && match.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container container) {
                T result = find(container, type, match);
                if (result != null) return result;
            }
        }
        return null;
    }
}
