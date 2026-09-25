package dev.sjavainspector.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourceUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticLintTest {
    private static AnalysisResult semantic(String source) {
        return new Analyzer(List.of(), Analyzer.semanticLintRules()).analyze(new SourceUnit("lint.sjava", source));
    }
    private static List<String> codes(AnalysisResult result) {
        return result.diagnostics().stream().map(Diagnostic::code).toList();
    }
    private static Diagnostic only(String code, AnalysisResult result) {
        List<Diagnostic> matches = result.diagnostics().stream().filter(d -> d.code().equals(code)).toList();
        assertEquals(1, matches.size(), () -> "Expected one " + code + ": " + result.diagnostics());
        return matches.get(0);
    }

    @Test @DisplayName("W004 reports unused local and global variables at the name")
    void unusedVariables() {
        String source = "int unusedGlobal=1; void f(){int unusedLocal=2; return;}";
        var result = semantic(source);
        assertEquals(List.of("W004", "W004"), codes(result));
        assertEquals(source.indexOf("unusedGlobal"), result.diagnostics().get(0).position().offset());
        assertEquals("Variable 'unusedLocal' is never read.", result.diagnostics().get(1).message());
        assertTrue(result.isValid());
    }

    @Test @DisplayName("W004 is silent when variables are read, including globals read in methods")
    void usedVariables() {
        assertEquals(List.of(), codes(semantic("int g=1; void f(){int a=g; use(a); return;} void use(int n){int m=n; if(m){return;} return;}")));
    }

    @Test @DisplayName("write-only variables are unused")
    void writeOnlyVariables() {
        var result = semantic("int g; void f(){int x; x=1; g=2; return;}");
        assertEquals(List.of("W004", "W004"), codes(result));
    }

    @Test @DisplayName("reads in unreachable code count as use")
    void unreachableReadsCount() {
        assertEquals(List.of(), codes(semantic("void f(){int x=1; return; int y=x; use(y);} void use(int n){if(n){return;} return;}")));
        assertEquals(List.of("W003"), codes(Analyzer.withSemanticLint().analyze(new SourceUnit("u",
                "void f(){int x=1; return; use(x);} void use(int n){if(n){return;} return;}"))));
    }

    @Test @DisplayName("W005 reports parameters that are never read")
    void unusedParameters() {
        String source = "void f(int used, final boolean ignored){if(used){return;} return;}";
        Diagnostic warning = only("W005", semantic(source));
        assertEquals(source.indexOf("ignored"), warning.position().offset());
        assertEquals("Parameter 'ignored' is never read.", warning.message());
        assertEquals(List.of("W005"), codes(semantic("void f(int p){p=2; return;}")));
    }

    @Test @DisplayName("W006 reports a block local hiding a method local and names the hidden location")
    void shadowedLocal() {
        String source = "void f(){int x=1;\n if(x){int x=2; use(x);} return;} void use(int n){if(n){return;} return;}";
        Diagnostic warning = only("W006", semantic(source));
        assertEquals(source.lastIndexOf("int x") + 4, warning.position().offset());
        assertEquals("Local variable 'x' shadows the local variable declared at line 1, column 14.", warning.message());
    }

    @Test @DisplayName("W006 reports parameters and locals hiding globals declared anywhere in the file")
    void shadowedGlobals() {
        var parameter = only("W006", semantic("int g=1; int k=g; void f(int g){if(g){return;} return;} void h(){int u=k; if(u){return;} return;}"));
        assertEquals("Parameter 'g' shadows the global variable declared at line 1, column 5.", parameter.message());
        var laterGlobal = semantic("void f(){int x=1; if(x){return;} return;} int x=2; int y=x;");
        assertEquals(List.of("W006", "W004"), codes(laterGlobal));
    }

    @Test @DisplayName("W006 is silent for siblings, later outer declarations, and method names")
    void notShadowing() {
        assertEquals(List.of(), codes(semantic(
                "void f(boolean c){if(c){int x=1; use(x);}else{int x=2; use(x);} return;} void use(int n){if(n){return;} return;}")));
        assertEquals(List.of(), codes(semantic(
                "void f(){if(true){int x=1; use(x);} int x=2; use(x); return;} void use(int n){if(n){return;} return;}")));
        assertEquals(List.of(), codes(semantic("int use=1; void use(int n){int m=use; if(m && n){return;} return;}")));
    }

    @Test @DisplayName("semantic lint is suppressed when the program has semantic errors")
    void suppressedOnErrors() {
        var result = semantic("void f(int unused){int x=1; if(true){int x=2;} missing(); return;}");
        assertEquals(List.of("E107"), codes(result));
        assertTrue(result.semanticModel().isPresent());
    }

    @Test @DisplayName("default analyzer keeps semantic lint off; the factory adds it to default rules")
    void configuration() {
        String source = "void Bad_Name(int unused){return;}";
        assertEquals(List.of("W001"), codes(new Analyzer().analyze(new SourceUnit("a", source))));
        assertEquals(List.of("W001", "W005"), codes(Analyzer.withSemanticLint().analyze(new SourceUnit("a", source))));
        assertEquals(List.of(), codes(new Analyzer(List.of()).analyze(new SourceUnit("a", source))));
        assertEquals(3, Analyzer.defaultRules().size());
        assertEquals(3, Analyzer.semanticLintRules().size());
    }

    @Test @DisplayName("custom semantic rule reads the model and configuration is copied")
    void customSemanticRule() {
        SemanticRule rule = model -> List.of(Diagnostic.warning("TEAM002",
                model.references().size() + " references", model.program().position()));
        List<SemanticRule> configured = new ArrayList<>(List.of(rule));
        Analyzer analyzer = new Analyzer(List.of(), configured);
        configured.clear();
        var result = analyzer.analyze(new SourceUnit("x.sjava", "int x=1; int y=x;"));
        assertEquals("1 references", result.diagnostics().get(0).message());
    }
}
