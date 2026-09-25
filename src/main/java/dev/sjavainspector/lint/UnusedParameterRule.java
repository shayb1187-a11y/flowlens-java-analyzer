package dev.sjavainspector.lint;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.semantic.SemanticModel;
import dev.sjavainspector.semantic.VariableSymbol;
import java.util.List;

/** W005: a method parameter that is never read. */
public final class UnusedParameterRule implements SemanticRule {
    @Override public List<Diagnostic> inspect(SemanticModel model) {
        return Usage.unread(model, VariableSymbol.Kind.PARAMETER).stream()
                .map(p -> Diagnostic.warning("W005", "Parameter '" + p.name() + "' is never read.", p.nameRange().start()))
                .toList();
    }
}
