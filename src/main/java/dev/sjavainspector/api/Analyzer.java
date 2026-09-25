package dev.sjavainspector.api;

import dev.sjavainspector.lint.AnalysisRule;
import dev.sjavainspector.lint.ComplexityRule;
import dev.sjavainspector.lint.MethodNamingRule;
import dev.sjavainspector.lint.MetricsCollector;
import dev.sjavainspector.lint.SemanticRule;
import dev.sjavainspector.lint.ShadowingRule;
import dev.sjavainspector.lint.UnreachableCodeRule;
import dev.sjavainspector.lint.UnusedParameterRule;
import dev.sjavainspector.lint.UnusedVariableRule;
import dev.sjavainspector.semantic.SemanticAnalyzer;
import dev.sjavainspector.semantic.SemanticModel;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Lexer;
import dev.sjavainspector.syntax.Parser;
import dev.sjavainspector.syntax.Token;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Facade for CLI, desktop, and embedded use. No I/O, output, or shared mutable analysis state. */
public final class Analyzer {
    public static final int MAX_SOURCE_CHARACTERS = 1_000_000;
    private final List<AnalysisRule> rules;
    private final List<SemanticRule> semanticRules;
    /** Default syntax lint (W001–W003); semantic lint is opt-in. */
    public Analyzer() { this(defaultRules(), List.of()); }
    public Analyzer(List<AnalysisRule> rules) { this(rules, List.of()); }
    /** Semantic rules run only when semantic validation reports no errors. */
    public Analyzer(List<AnalysisRule> rules, List<SemanticRule> semanticRules) {
        this.rules = List.copyOf(rules);
        this.semanticRules = List.copyOf(semanticRules);
    }
    /** Default rules plus unused-variable, unused-parameter, and shadowing warnings (W004–W006). */
    public static Analyzer withSemanticLint() { return new Analyzer(defaultRules(), semanticLintRules()); }
    public static List<AnalysisRule> defaultRules() {
        return List.of(new MethodNamingRule(), new ComplexityRule(7, 3), new UnreachableCodeRule());
    }
    public static List<SemanticRule> semanticLintRules() {
        return List.of(new UnusedVariableRule(), new UnusedParameterRule(), new ShadowingRule());
    }

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
        SemanticModel model = new SemanticAnalyzer(diagnostics).analyze(source, program);
        // Only semantic errors can be present here; lexical and parse failures returned above.
        boolean semanticallyValid = diagnostics.isEmpty();
        List<AnalysisResult.MethodMetrics> metrics = new MetricsCollector().collect(program);
        for (AnalysisRule rule : rules) diagnostics.addAll(rule.inspect(program, metrics));
        // Skipped for invalid programs to avoid secondary warnings about broken declarations.
        if (semanticallyValid) for (SemanticRule rule : semanticRules) diagnostics.addAll(rule.inspect(model));
        return result(source, diagnostics, metrics, Optional.of(model));
    }
    private AnalysisResult result(SourceUnit source, List<Diagnostic> diagnostics, List<AnalysisResult.MethodMetrics> metrics) {
        return result(source, diagnostics, metrics, Optional.empty());
    }
    private AnalysisResult result(SourceUnit source, List<Diagnostic> diagnostics,
                                  List<AnalysisResult.MethodMetrics> metrics, Optional<SemanticModel> model) {
        diagnostics.sort(Comparator.comparingInt((Diagnostic d) -> d.position().offset())
                .thenComparing(Diagnostic::severity).thenComparing(Diagnostic::code));
        return new AnalysisResult(source.name(), diagnostics, metrics, model);
    }
}
