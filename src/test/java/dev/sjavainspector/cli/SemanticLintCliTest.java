package dev.sjavainspector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.io.SourceRepository;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticLintCliTest {
    private static final SourceRepository UNUSED = p -> new SourceUnit(p.toString(), "void f(){int x=1; return;}");

    @Test @DisplayName("CLI default output has no semantic lint")
    void defaultHasNoSemanticLint() {
        var run = cli(UNUSED, "check", "a.sjava");
        assertEquals(0, run.exit());
        assertFalse(run.out().contains("W004"), run.out());
    }

    @Test @DisplayName("CLI --semantic-lint reports W004 and respects the warning gate")
    void semanticLintAndGate() {
        var run = cli(UNUSED, "check", "--semantic-lint", "a.sjava");
        assertEquals(0, run.exit());
        assertTrue(run.out().contains("W004"), run.out());
        assertEquals(1, cli(UNUSED, "check", "--semantic-lint", "--warnings-as-errors", "a.sjava").exit());
    }

    @Test @DisplayName("CLI --no-lint overrides --semantic-lint in either order")
    void noLintWins() {
        for (String[] args : new String[][]{
                {"check", "--semantic-lint", "--no-lint", "--warnings-as-errors", "a.sjava"},
                {"check", "--no-lint", "--semantic-lint", "--warnings-as-errors", "a.sjava"}}) {
            var run = cli(UNUSED, args);
            assertEquals(0, run.exit());
            assertFalse(run.out().contains("W00"), run.out());
        }
    }

    @Test @DisplayName("CLI semantic lint in JSON keeps schema v1")
    void json() {
        var run = cli(UNUSED, "check", "--format=json", "--semantic-lint", "a.sjava");
        assertEquals(0, run.exit());
        assertEquals("", run.err());
        assertTrue(run.out().startsWith("{\"schemaVersion\":1,"), run.out());
        assertTrue(run.out().contains("\"code\":\"W004\""), run.out());
        assertFalse(run.out().contains("semanticModel"), run.out());
    }

    @Test @DisplayName("CLI semantic lint analyzes batch inputs independently")
    void batch() {
        SourceRepository repo = p -> new SourceUnit(p.toString(), p.toString().equals("unused.sjava")
                ? "int x=1;" : p.toString().equals("broken.sjava") ? "int y=x;" : "int z=1; int w=z; int v=w; int u=v; int t=u; void f(){int s=t; if(s){return;} return;}");
        var run = cli(repo, "check", "--semantic-lint", "unused.sjava", "broken.sjava", "clean.sjava");
        assertEquals(1, run.exit());
        assertTrue(run.out().contains("W004"), run.out());
        assertTrue(run.out().contains("E102"), run.out());
        assertEquals(1, run.out().split("W004", -1).length - 1, run.out());
    }

    private record Invocation(int exit, String out, String err) { }
    private static Invocation cli(SourceRepository sources, String... args) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int status = new Cli(sources).run(args, new PrintWriter(out), new PrintWriter(err));
        return new Invocation(status, out.toString(), err.toString());
    }
}
