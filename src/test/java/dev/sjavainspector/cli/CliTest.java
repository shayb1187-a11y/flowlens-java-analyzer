package dev.sjavainspector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.io.SourceRepository;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CliTest {
    @Test @DisplayName("CLI processes multiple files independently")
    void multipleFilesIndependently() {
        SourceRepository repo = p -> new SourceUnit(p.toString(), p.toString().equals("first.sjava") ? "int x=1;" : "int y=x;");
        var run = cli(repo, "check", "first.sjava", "second.sjava");
        assertEquals(1, run.exit());
        assertTrue(run.out().contains("PASS first.sjava") && run.out().contains("FAIL second.sjava"), "Batch lost results");
    }

    @Test @DisplayName("CLI I/O failure takes precedence over source failure")
    void ioFailurePrecedence() {
        var run = cli(p -> {
            if (p.toString().equals("missing.sjava")) throw new IOException("not found");
            return new SourceUnit(p.toString(), "int x=x;");
        }, "check", "bad.sjava", "missing.sjava");
        assertEquals(2, run.exit());
        assertTrue(run.out().contains("EIO") && run.out().contains("E103"), "Missing batch errors");
    }

    @Test @DisplayName("CLI warnings gate and no-lint switch")
    void warningsGateAndNoLint() {
        SourceRepository repo = p -> new SourceUnit(p.toString(), "void Bad_Name(){return;}");
        assertEquals(0, cli(repo, "check", "a.sjava").exit());
        assertEquals(1, cli(repo, "check", "--warnings-as-errors", "a.sjava").exit());
        assertEquals(0, cli(repo, "check", "--warnings-as-errors", "--no-lint", "a.sjava").exit());
    }

    @Test @DisplayName("CLI rejects malformed invocation")
    void rejectsMalformedInvocation() {
        SourceRepository repo = p -> new SourceUnit(p.toString(), "");
        assertEquals(2, cli(repo).exit());
        assertEquals(2, cli(repo, "check").exit());
        assertEquals(2, cli(repo, "check", "--format=xml", "a.sjava").exit());
        assertEquals(2, cli(repo, "wat").exit());
        assertEquals(0, cli(repo, "--help").exit());
    }

    @Test @DisplayName("CLI end-of-options marker")
    void endOfOptionsMarker() {
        var run = cli(p -> new SourceUnit(p.toString(), ""), "check", "--", "--source.sjava");
        assertEquals(0, run.exit());
        assertTrue(run.out().contains("--source.sjava"), "Path lost");
    }

    @Test @DisplayName("CLI JSON is clean stdout")
    void jsonIsCleanStdout() {
        var run = cli(p -> new SourceUnit(p.toString(), "int x=x;"), "check", "--format=json", "a.sjava");
        assertEquals(1, run.exit());
        assertEquals("", run.err());
        assertTrue(run.out().startsWith("{\"schemaVersion\":1"), "JSON polluted with logging");
    }

    private record Invocation(int exit, String out, String err) { }

    private static Invocation cli(SourceRepository sources, String... args) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int status = new Cli(sources).run(args, new PrintWriter(out), new PrintWriter(err));
        return new Invocation(status, out.toString(), err.toString());
    }
}
