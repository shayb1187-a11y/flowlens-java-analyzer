package dev.sjavainspector.api;

import dev.sjavainspector.lint.AnalysisRule;
import dev.sjavainspector.lint.ComplexityRule;
import dev.sjavainspector.lint.MethodNamingRule;
import dev.sjavainspector.lint.MetricsCollector;
import dev.sjavainspector.lint.UnreachableCodeRule;
import dev.sjavainspector.semantic.SemanticAnalyzer;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Lexer;
import dev.sjavainspector.syntax.Parser;
import dev.sjavainspector.syntax.Token;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Facade for CLI, desktop, and embedded use. No I/O, output, or shared mutable analysis state. */
public final class Analyzer {
    public static final int MAX_SOURCE_CHARACTERS = 1_000_000;
    private final List<AnalysisRule> rules;
    public Analyzer() { this(List.of(new MethodNamingRule(), new ComplexityRule(7, 3), new UnreachableCodeRule())); }
    public Analyzer(List<AnalysisRule> rules) { this.rules = List.copyOf(rules); }

    public AnalysisResult analyze(SourceUnit source) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (source.text().length() > MAX_SOURCE_CHARACTERS) {
            return new AnalysisResult(source.name(), List.of(Diagnostic.error("E900",
                    "Source exceeds the limit of " + MAX_SOURCE_CHARACTERS + " UTF-16 characters.", SourcePosition.start())), List.of());
        }
        List<Token> tokens = new Lexer(source.text(), diagnostics).scan();
        if (!diagnostics.isEmpty()) return result(source, diagnostics, List.of());
        Ast.Program program = new Parser(tokens, diagnostics).parse();
        if (!diagnostics.isEmpty()) return result(source, diagnostics, List.of());
        program.accept(new SemanticAnalyzer(diagnostics));
        List<AnalysisResult.MethodMetrics> metrics = new MetricsCollector().collect(program);
        for (AnalysisRule rule : rules) diagnostics.addAll(rule.inspect(program, metrics));
        return result(source, diagnostics, metrics);
    }
    private AnalysisResult result(SourceUnit source, List<Diagnostic> diagnostics, List<AnalysisResult.MethodMetrics> metrics) {
        diagnostics.sort(Comparator.comparingInt((Diagnostic d) -> d.position().offset())
                .thenComparing(Diagnostic::severity).thenComparing(Diagnostic::code));
        return new AnalysisResult(source.name(), diagnostics, metrics);
    }
}
