package dev.sjavainspector.lint;

import dev.sjavainspector.semantic.Reference;
import dev.sjavainspector.semantic.SemanticModel;
import dev.sjavainspector.semantic.VariableSymbol;
import java.util.List;
import java.util.Set;

/** Syntactic usage: any resolved read counts, including reads in unreachable code. */
final class Usage {
    private Usage() { }
    static List<VariableSymbol> unread(SemanticModel model, VariableSymbol.Kind... kinds) {
        Set<VariableSymbol.Kind> wanted = Set.of(kinds);
        return model.symbols().stream()
                .filter(s -> s instanceof VariableSymbol && s.isAccepted())
                .map(VariableSymbol.class::cast)
                .filter(v -> wanted.contains(v.kind()))
                .filter(v -> model.references(v).stream().noneMatch(r -> r.kind() == Reference.Kind.READ))
                .toList();
    }
}
