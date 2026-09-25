package dev.sjavainspector.lint;

import dev.sjavainspector.api.AnalysisResult.MethodMetrics;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.TreeWalker;
import java.util.ArrayList;
import java.util.List;

/** A separate Visitor adds measurements without changing parser or semantic behavior. */
public final class MetricsCollector extends TreeWalker {
    private final List<MethodMetrics> metrics = new ArrayList<>();
    private int statements;
    private int complexity;
    private int depth;
    private int maxDepth;

    public List<MethodMetrics> collect(Ast.Program program) {
        metrics.clear();
        program.methods().forEach(m -> m.accept(this));
        return List.copyOf(metrics);
    }
    @Override public Void visitMethod(Ast.Method n) {
        statements = 0; complexity = 1; depth = 0; maxDepth = 0;
        super.visitMethod(n);
        metrics.add(new MethodMetrics(n.name(), n.position().line(), statements, complexity, maxDepth));
        return null;
    }
    @Override public Void visitDeclaration(Ast.Declaration n) { statements++; return super.visitDeclaration(n); }
    @Override public Void visitAssignment(Ast.Assignment n) { statements++; return super.visitAssignment(n); }
    @Override public Void visitCall(Ast.Call n) { statements++; return super.visitCall(n); }
    @Override public Void visitReturn(Ast.Return n) { statements++; return null; }
    @Override public Void visitIf(Ast.If n) {
        statements++; complexity++; enter();
        super.visitIf(n); depth--; return null;
    }
    @Override public Void visitWhile(Ast.While n) {
        statements++; complexity++; enter();
        super.visitWhile(n); depth--; return null;
    }
    @Override public Void visitLogical(Ast.Logical n) { complexity++; return super.visitLogical(n); }
    private void enter() { depth++; maxDepth = Math.max(maxDepth, depth); }
}
