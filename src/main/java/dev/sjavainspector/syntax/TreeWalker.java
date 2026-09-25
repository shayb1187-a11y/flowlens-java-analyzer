package dev.sjavainspector.syntax;

/** Reusable depth-first traversal; subclasses override only the nodes they inspect. */
public class TreeWalker implements Ast.Visitor<Void> {
    @Override public Void visitProgram(Ast.Program n) {
        n.globals().forEach(s -> s.accept(this));
        n.methods().forEach(m -> m.accept(this));
        return null;
    }
    @Override public Void visitMethod(Ast.Method n) { n.body().accept(this); return null; }
    @Override public Void visitBlock(Ast.Block n) { n.statements().forEach(s -> s.accept(this)); return null; }
    @Override public Void visitDeclaration(Ast.Declaration n) {
        n.bindings().forEach(b -> b.initializer().ifPresent(e -> e.accept(this))); return null;
    }
    @Override public Void visitAssignment(Ast.Assignment n) {
        n.writes().forEach(w -> w.value().accept(this)); return null;
    }
    @Override public Void visitCall(Ast.Call n) { n.arguments().forEach(e -> e.accept(this)); return null; }
    @Override public Void visitIf(Ast.If n) {
        n.condition().accept(this); n.thenBranch().accept(this);
        n.elseBranch().ifPresent(b -> b.accept(this)); return null;
    }
    @Override public Void visitWhile(Ast.While n) {
        n.condition().accept(this); n.body().accept(this); return null;
    }
    @Override public Void visitReturn(Ast.Return n) { return null; }
    @Override public Void visitLiteral(Ast.Literal n) { return null; }
    @Override public Void visitVariable(Ast.Variable n) { return null; }
    @Override public Void visitLogical(Ast.Logical n) {
        n.left().accept(this); n.right().accept(this); return null;
    }
    @Override public Void visitNot(Ast.Not n) { n.operand().accept(this); return null; }
}
