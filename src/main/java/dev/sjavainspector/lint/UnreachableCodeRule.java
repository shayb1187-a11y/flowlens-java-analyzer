package dev.sjavainspector.lint;

import dev.sjavainspector.api.AnalysisResult.MethodMetrics;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.TreeWalker;
import java.util.ArrayList;
import java.util.List;

public final class UnreachableCodeRule implements AnalysisRule {
    @Override public List<Diagnostic> inspect(Ast.Program program, List<MethodMetrics> metrics) {
        List<Diagnostic> warnings = new ArrayList<>();
        program.accept(new TreeWalker() {
            @Override public Void visitBlock(Ast.Block block) {
                boolean stopped = false;
                for (Ast.Statement statement : block.statements()) {
                    if (stopped) {
                        warnings.add(Diagnostic.warning("W003", "Statement is unreachable after an unconditional return.", statement.position()));
                        // Do not emit another warning for every child in the unreachable subtree.
                    } else {
                        statement.accept(this);
                        stopped = terminates(statement);
                    }
                }
                return null;
            }
        });
        return List.copyOf(warnings);
    }
    private static boolean terminates(Ast.Statement statement) {
        if (statement instanceof Ast.Return) return true;
        if (statement instanceof Ast.If branch && branch.elseBranch().isPresent()) {
            return terminates(branch.thenBranch()) && terminates(branch.elseBranch().get());
        }
        return false;
    }
    private static boolean terminates(Ast.Block block) {
        return block.statements().stream().anyMatch(UnreachableCodeRule::terminates);
    }
}
