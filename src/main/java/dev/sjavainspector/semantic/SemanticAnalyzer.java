package dev.sjavainspector.semantic;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import dev.sjavainspector.api.SourceRange;
import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-analysis visitor: collects signatures, then checks globals and isolated method bodies.
 * While resolving names it records the declarations, references, scopes, and expression types
 * that make up the {@link SemanticModel}; flow state uses the same symbol identities.
 */
public final class SemanticAnalyzer implements Ast.Visitor<Type> {
    private final List<Diagnostic> diagnostics;
    private final TypeRules types = new TypeRules();
    private final Map<String, MethodSymbol> methods = new LinkedHashMap<>();
    private final Map<Ast.Method, MethodSymbol> methodSymbols = new IdentityHashMap<>();
    private final List<Symbol> symbols = new ArrayList<>();
    private final List<Reference> references = new ArrayList<>();
    private final IdentityHashMap<Ast.Expression, Type> expressionTypes = new IdentityHashMap<>();
    private Scope scope;
    private FlowState flow = new FlowState();
    private boolean used;

    public SemanticAnalyzer(List<Diagnostic> diagnostics) { this.diagnostics = diagnostics; }

    /** Validates {@code program}, adding errors to the diagnostics list, and returns its frozen model. */
    public SemanticModel analyze(SourceUnit source, Ast.Program program) {
        program.accept(this);
        scope.freeze();
        return new SemanticModel(source, program, scope, symbols, references, expressionTypes);
    }

    @Override public Type visitProgram(Ast.Program n) {
        if (used) throw new IllegalStateException("A SemanticAnalyzer analyzes one program");
        used = true;
        scope = new Scope(Scope.Kind.GLOBAL, null, n);
        for (Ast.Method method : n.methods()) {
            boolean accepted = !methods.containsKey(method.name());
            MethodSymbol symbol = new MethodSymbol(method, scope, accepted);
            symbols.add(symbol);
            methodSymbols.put(method, symbol);
            if (accepted) methods.put(method.name(), symbol);
            else error("E101", "Duplicate method '" + method.name() + "'.", method.position());
        }
        n.globals().forEach(s -> s.accept(this));
        // Each method begins with the same completed global environment. Calls do not
        // establish initialization facts: this is deliberately intraprocedural analysis.
        Scope globals = scope;
        FlowState globalFlow = flow.copy();
        for (Ast.Method method : n.methods()) {
            scope = new Scope(Scope.Kind.METHOD, globals, method);
            flow = globalFlow.copy();
            method.accept(this);
        }
        scope = globals;
        flow = globalFlow;
        return Type.ERROR;
    }

    @Override public Type visitMethod(Ast.Method n) {
        for (Ast.Parameter parameter : n.parameters()) {
            VariableSymbol symbol = declare(parameter.name(), parameter.type(), parameter.isFinal(),
                    VariableSymbol.Kind.PARAMETER, parameter.position());
            if (!symbol.isAccepted()) error("E101", "Duplicate parameter '" + parameter.name() + "'.", parameter.position());
            else flow.initialize(symbol);
        }
        // Parameters and the method's top-level locals share a scope.
        n.body().statements().forEach(s -> s.accept(this));
        if (flow.reachable()) error("E109", "Method '" + n.name() + "' can finish without an explicit return.", n.position());
        return Type.ERROR;
    }

    @Override public Type visitBlock(Ast.Block n) {
        Scope parent = scope;
        scope = new Scope(Scope.Kind.BLOCK, parent, n);
        try { n.statements().forEach(s -> s.accept(this)); }
        finally { scope.forgetLocals(flow); scope = parent; }
        return Type.ERROR;
    }

    @Override public Type visitDeclaration(Ast.Declaration n) {
        VariableSymbol.Kind kind = scope.kind() == Scope.Kind.GLOBAL ? VariableSymbol.Kind.GLOBAL : VariableSymbol.Kind.LOCAL;
        for (Ast.Binding binding : n.bindings()) {
            VariableSymbol symbol = declare(binding.name(), n.type(), n.isFinal(), kind, binding.position());
            boolean declared = symbol.isAccepted();
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
            VariableSymbol target = resolve(Reference.Kind.WRITE, write.name(), write.position());
            Type actual = write.value().accept(this);
            if (target == null) continue;
            if (target.isFinal()) error("E106", "Cannot reassign final variable '" + target.name() + "'.", write.position());
            checkAssignable(target.type(), actual, write.value().position());
            if (errorCount() == before && actual != Type.ERROR) flow.initialize(target);
        }
        return Type.ERROR;
    }

    @Override public Type visitCall(Ast.Call n) {
        MethodSymbol method = methods.get(n.name());
        reference(Reference.Kind.CALL, n.name(), n.position(), method);
        List<Type> actual = n.arguments().stream().map(a -> a.accept(this)).toList();
        if (method == null) error("E107", "Unknown method '" + n.name() + "'.", n.position());
        else if (actual.size() != method.parameterTypes().size()) {
            error("E108", "Method '" + n.name() + "' expects " + method.parameterTypes().size()
                    + " arguments; received " + actual.size() + ".", n.position());
        } else {
            for (int i = 0; i < actual.size(); i++) {
                checkAssignable(method.parameterTypes().get(i), actual.get(i), n.arguments().get(i).position());
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
    @Override public Type visitLiteral(Ast.Literal n) { return typed(n, n.type()); }
    @Override public Type visitVariable(Ast.Variable n) {
        VariableSymbol symbol = resolve(Reference.Kind.READ, n.name(), n.position());
        if (symbol == null) return typed(n, Type.ERROR);
        if (!flow.isInitialized(symbol)) {
            error("E103", "Variable '" + n.name() + "' may be uninitialized.", n.position());
            return typed(n, Type.ERROR);
        }
        return typed(n, symbol.type());
    }
    @Override public Type visitLogical(Ast.Logical n) {
        boolean left = checkCondition(n.left());
        boolean right = checkCondition(n.right());
        return typed(n, left && right ? Type.BOOLEAN : Type.ERROR);
    }
    @Override public Type visitNot(Ast.Not n) { return typed(n, checkCondition(n.operand()) ? Type.BOOLEAN : Type.ERROR); }

    /** Records every declaration; only an accepted one becomes visible, so duplicates never replace it. */
    private VariableSymbol declare(String name, Type type, boolean isFinal, VariableSymbol.Kind kind, SourcePosition position) {
        boolean accepted = !scope.declaresLocally(name);
        VariableSymbol shadowed = accepted ? scope.parent().flatMap(p -> p.resolve(name)).orElse(null) : null;
        VariableSymbol symbol = new VariableSymbol(name, type, isFinal, kind,
                SourceRange.of(position, name.length()), scope, accepted, shadowed);
        symbols.add(symbol);
        if (accepted) scope.declare(symbol);
        return symbol;
    }
    private Type typed(Ast.Expression expression, Type type) {
        expressionTypes.put(expression, type);
        return type;
    }
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
    private VariableSymbol resolve(Reference.Kind kind, String name, SourcePosition position) {
        VariableSymbol symbol = scope.resolve(name).orElse(null);
        reference(kind, name, position, symbol);
        if (symbol == null) error("E102", "Unknown variable '" + name + "'.", position);
        return symbol;
    }
    private void reference(Reference.Kind kind, String name, SourcePosition position, Symbol target) {
        references.add(new Reference(kind, name, SourceRange.of(position, name.length()), scope, Optional.ofNullable(target)));
    }
    private int errorCount() { return diagnostics.size(); }
    private void error(String code, String message, SourcePosition position) {
        diagnostics.add(Diagnostic.error(code, message, position));
    }
}
