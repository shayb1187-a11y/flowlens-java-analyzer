package dev.sjavainspector.lint;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.semantic.SemanticModel;
import dev.sjavainspector.semantic.VariableSymbol;
import java.util.List;

/** W004: a global or local variable that is never read. Assignments alone are not a use. */
public final class UnusedVariableRule implements SemanticRule {
    @Override public List<Diagnostic> inspect(SemanticModel model) {
        return Usage.unread(model, VariableSymbol.Kind.GLOBAL, VariableSymbol.Kind.LOCAL).stream()
                .map(v -> Diagnostic.warning("W004", "Variable '" + v.name() + "' is never read.", v.nameRange().start()))
                .toList();
    }
}
