package dev.sjavainspector.lint;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import dev.sjavainspector.semantic.SemanticModel;
import dev.sjavainspector.semantic.VariableSymbol;
import java.util.List;
import java.util.Locale;

/**
 * W006: a variable or parameter hides a visible variable or parameter of an ancestor scope.
 * Globals are visible in every method; methods have a separate namespace and never shadow.
 */
public final class ShadowingRule implements SemanticRule {
    @Override public List<Diagnostic> inspect(SemanticModel model) {
        return model.symbols().stream()
                .filter(s -> s instanceof VariableSymbol && s.isAccepted())
                .map(VariableSymbol.class::cast)
                .filter(v -> v.shadowed().isPresent())
                .map(ShadowingRule::warning)
                .toList();
    }
    private static Diagnostic warning(VariableSymbol symbol) {
        VariableSymbol hidden = symbol.shadowed().orElseThrow();
        SourcePosition at = hidden.nameRange().start();
        return Diagnostic.warning("W006", describe(symbol) + " '" + symbol.name() + "' shadows the "
                + describe(hidden).toLowerCase(Locale.ROOT) + " declared at line " + at.line()
                + ", column " + at.column() + ".", symbol.nameRange().start());
    }
    private static String describe(VariableSymbol symbol) {
        return switch (symbol.kind()) {
            case GLOBAL -> "Global variable";
            case LOCAL -> "Local variable";
            case PARAMETER -> "Parameter";
        };
    }
}
