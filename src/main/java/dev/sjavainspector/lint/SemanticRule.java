package dev.sjavainspector.lint;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.semantic.SemanticModel;
import java.util.List;

/**
 * Extension point for read-only rules over resolved names. Runs only when semantic validation
 * found no errors. Implementations must be stateless/thread-safe and must not resolve names
 * themselves: bindings come from the model.
 */
@FunctionalInterface
public interface SemanticRule {
    List<Diagnostic> inspect(SemanticModel model);
}
