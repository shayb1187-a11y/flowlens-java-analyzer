package dev.sjavainspector.semantic;

import dev.sjavainspector.api.SourceRange;
import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable result of name resolution and typing for one source snapshot. Symbols, scopes, and
 * AST nodes are compared by identity, so a model must be rebuilt after the source is edited.
 */
public final class SemanticModel {
    private final SourceUnit source;
    private final Ast.Program program;
    private final Scope globalScope;
    private final List<Symbol> symbols;
    private final List<Reference> references;
    private final Map<Symbol, List<Reference>> referencesBySymbol;
    private final Map<Ast.Expression, Type> expressionTypes;
    /** Declaration names and references, sorted by start offset; token ranges never overlap. */
    private final List<Occurrence> occurrences;

    private record Occurrence(SourceRange range, Symbol symbol, Reference reference) { }

    SemanticModel(SourceUnit source, Ast.Program program, Scope globalScope, List<Symbol> symbols,
                  List<Reference> references, IdentityHashMap<Ast.Expression, Type> expressionTypes) {
        this.source = source;
        this.program = program;
        this.globalScope = globalScope;
        this.symbols = List.copyOf(symbols);
        this.references = references.stream()
                .sorted(Comparator.comparingInt((Reference r) -> r.range().start().offset())).toList();
        Map<Symbol, List<Reference>> bySymbol = new IdentityHashMap<>();
        for (Reference reference : this.references) {
            reference.target().ifPresent(s -> bySymbol.computeIfAbsent(s, k -> new ArrayList<>()).add(reference));
        }
        bySymbol.replaceAll((symbol, list) -> List.copyOf(list));
        this.referencesBySymbol = Collections.unmodifiableMap(bySymbol);
        this.expressionTypes = Collections.unmodifiableMap(new IdentityHashMap<>(expressionTypes));
        List<Occurrence> index = new ArrayList<>();
        for (Symbol symbol : this.symbols) index.add(new Occurrence(symbol.nameRange(), symbol, null));
        for (Reference reference : this.references) {
            index.add(new Occurrence(reference.range(), reference.target().orElse(null), reference));
        }
        index.sort(Comparator.comparingInt(o -> o.range().start().offset()));
        this.occurrences = List.copyOf(index);
    }

    public SourceUnit source() { return source; }
    public Ast.Program program() { return program; }
    public Scope globalScope() { return globalScope; }
    /** Every declaration in source order of analysis, including rejected duplicates. */
    public List<Symbol> symbols() { return symbols; }
    /** Every identifier use, sorted by source offset. */
    public List<Reference> references() { return references; }
    /** Resolved uses of {@code symbol}, sorted by source offset. */
    public List<Reference> references(Symbol symbol) { return referencesBySymbol.getOrDefault(symbol, List.of()); }

    /** The declaration named at {@code offset}, either at its declaration or at a resolved use. */
    public Optional<Symbol> symbolAt(int offset) {
        return occurrenceAt(offset).map(Occurrence::symbol);
    }
    /** The identifier use covering {@code offset}, resolved or not. */
    public Optional<Reference> referenceAt(int offset) {
        return occurrenceAt(offset).map(Occurrence::reference);
    }
    /**
     * The type the analyzer computed for this exact expression node, {@code ERROR} included.
     * Lookup is by node identity: a structurally equal copy of a node is not found.
     */
    public Optional<Type> typeOf(Ast.Expression expression) {
        return Optional.ofNullable(expressionTypes.get(expression));
    }

    private Optional<Occurrence> occurrenceAt(int offset) {
        int low = 0, high = occurrences.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Occurrence candidate = occurrences.get(middle);
            if (candidate.range().start().offset() > offset) high = middle - 1;
            else if (candidate.range().end() <= offset) low = middle + 1;
            else return Optional.of(candidate);
        }
        return Optional.empty();
    }
    @Override public String toString() {
        return "SemanticModel[" + source.name() + ", " + symbols.size() + " symbols, " + references.size() + " references]";
    }
}
