package dev.sjavainspector.semantic;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-analysis visitor: collects signatures, then checks globals and isolated method bodies. */
public final class SemanticAnalyzer implements Ast.Visitor<Type> {
    private final List<Diagnostic> diagnostics;
    private final TypeRules types = new TypeRules();
    private final Map<String, Ast.Method> methods = new LinkedHashMap<>();
    private Scope scope = new Scope(null);
    private FlowState flow = new FlowState();

    public SemanticAnalyzer(List<Diagnostic> diagnostics) { this.diagnostics = diagnostics; }

    @Override public Type visitProgram(Ast.Program n) {
        for (Ast.Method method : n.methods()) {
            if (methods.putIfAbsent(method.name(), method) != null) {
                error("E101", "Duplicate method '" + method.name() + "'.", method.position());
            }
        }
        n.globals().forEach(s -> s.accept(this));
        // Each method begins with the same completed global environment. Calls do not
        // establish initialization facts: this is deliberately intraprocedural analysis.
        Scope globals = scope;
        FlowState globalFlow = flow.copy();
        for (Ast.Method method : n.methods()) {
            scope = new Scope(globals);
            flow = globalFlow.copy();
            method.accept(this);
        }
        scope = globals;
        flow = globalFlow;
        return Type.ERROR;
    }

    @Override public Type visitMethod(Ast.Method n) {
        for (Ast.Parameter parameter : n.parameters()) {
            VariableSymbol symbol = new VariableSymbol(parameter.name(), parameter.type(), parameter.isFinal());
            if (!scope.declare(symbol)) error("E101", "Duplicate parameter '" + parameter.name() + "'.", parameter.position());
            else flow.initialize(symbol);
        }
        // Parameters and the method's top-level locals share a scope.
        n.body().statements().forEach(s -> s.accept(this));
        if (flow.reachable()) error("E109", "Method '" + n.name() + "' can finish without an explicit return.", n.position());
        return Type.ERROR;
    }

    @Override public Type visitBlock(Ast.Block n) {
        Scope parent = scope;
        scope = new Scope(parent);
        try { n.statements().forEach(s -> s.accept(this)); }
        finally { scope.forgetLocals(flow); scope = parent; }
        return Type.ERROR;
    }

    @Override public Type visitDeclaration(Ast.Declaration n) {
        for (Ast.Binding binding : n.bindings()) {
            VariableSymbol symbol = new VariableSymbol(binding.name(), n.type(), n.isFinal());
            boolean declared = scope.declare(symbol);
            if (!declared) error("E101", "Duplicate variable '" + binding.name() + "' in this scope.", binding.position());
            if (binding.initializer().isPresent()) {
                Ast.Expression initializer = binding.initializer().get();
                int before = errorCount();
                Type actual = initializer.accept(this);
                checkAssignable(n.type(), actual, initializer.position());
                if (declared && errorCount() == before && actual != Type.ERROR) flow.initialize(symbol);
            } else if (n.isFinal()) {
                error("E105", "Final variable '" + binding.name() + "' requires an initializer.", binding.position());
            }
        }
        return Type.ERROR;
    }

    @Override public Type visitAssignment(Ast.Assignment n) {
        for (Ast.Write write : n.writes()) {
            int before = errorCount();
            VariableSymbol target = resolve(write.name(), write.position());
            Type actual = write.value().accept(this);
            if (target == null) continue;
            if (target.isFinal()) error("E106", "Cannot reassign final variable '" + target.name() + "'.", write.position());
            checkAssignable(target.type(), actual, write.value().position());
            if (errorCount() == before && actual != Type.ERROR) flow.initialize(target);
        }
        return Type.ERROR;
    }

    @Override public Type visitCall(Ast.Call n) {
        Ast.Method method = methods.get(n.name());
        List<Type> actual = n.arguments().stream().map(a -> a.accept(this)).toList();
        if (method == null) error("E107", "Unknown method '" + n.name() + "'.", n.position());
        else if (actual.size() != method.parameters().size()) {
            error("E108", "Method '" + n.name() + "' expects " + method.parameters().size()
                    + " arguments; received " + actual.size() + ".", n.position());
        } else {
            for (int i = 0; i < actual.size(); i++) {
                checkAssignable(method.parameters().get(i).type(), actual.get(i), n.arguments().get(i).position());
            }
        }
        return Type.ERROR;
    }

    @Override public Type visitIf(Ast.If n) {
        checkCondition(n.condition());
        FlowState before = flow.copy();
        n.thenBranch().accept(this);
        FlowState afterThen = flow;
        flow = before.copy();
        n.elseBranch().ifPresent(b -> b.accept(this));
        flow = FlowState.merge(afterThen, flow);
        return Type.ERROR;
    }

    @Override public Type visitWhile(Ast.While n) {
        checkCondition(n.condition());
        FlowState before = flow.copy();
        n.body().accept(this);
        // A loop may execute zero times, even if a literal condition looks constant.
        flow = before;
        return Type.ERROR;
    }
    @Override public Type visitReturn(Ast.Return n) { flow.terminate(); return Type.ERROR; }
    @Override public Type visitLiteral(Ast.Literal n) { return n.type(); }
    @Override public Type visitVariable(Ast.Variable n) {
        VariableSymbol symbol = resolve(n.name(), n.position());
        if (symbol == null) return Type.ERROR;
        if (!flow.isInitialized(symbol)) {
            error("E103", "Variable '" + n.name() + "' may be uninitialized.", n.position());
            return Type.ERROR;
        }
        return symbol.type();
    }
    @Override public Type visitLogical(Ast.Logical n) {
        boolean left = checkCondition(n.left());
        boolean right = checkCondition(n.right());
        return left && right ? Type.BOOLEAN : Type.ERROR;
    }
    @Override public Type visitNot(Ast.Not n) { return checkCondition(n.operand()) ? Type.BOOLEAN : Type.ERROR; }

    private boolean checkCondition(Ast.Expression expression) {
        Type type = expression.accept(this);
        if (!types.canTest(type)) {
            error("E110", "Condition requires boolean, int, or double; received " + type.display() + ".", expression.position());
            return false;
        }
        return type != Type.ERROR;
    }
    private void checkAssignable(Type target, Type actual, SourcePosition position) {
        if (!types.canAssign(target, actual)) {
            error("E104", "Cannot assign " + actual.display() + " to " + target.display() + ".", position);
        }
    }
    private VariableSymbol resolve(String name, SourcePosition position) {
        VariableSymbol symbol = scope.resolve(name).orElse(null);
        if (symbol == null) error("E102", "Unknown variable '" + name + "'.", position);
        return symbol;
    }
    private int errorCount() { return diagnostics.size(); }
    private void error(String code, String message, SourcePosition position) {
        diagnostics.add(Diagnostic.error(code, message, position));
    }
}
