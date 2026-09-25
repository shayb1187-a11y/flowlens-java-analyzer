package dev.sjavainspector.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.AnalysisResult;
import dev.sjavainspector.api.Analyzer;
import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.api.SourcePosition;
import dev.sjavainspector.api.SourceRange;
import dev.sjavainspector.api.SourceUnit;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticModelTest {
    private static AnalysisResult result(String source) {
        return new Analyzer(List.of()).analyze(new SourceUnit("model.sjava", source));
    }
    private static SemanticModel model(String source) { return result(source).semanticModel().orElseThrow(); }
    private static List<VariableSymbol> variables(SemanticModel model, String name) {
        return model.symbols().stream().filter(s -> s instanceof VariableSymbol && s.name().equals(name))
                .map(VariableSymbol.class::cast).toList();
    }
    private static VariableSymbol variable(SemanticModel model, String name, int index) { return variables(model, name).get(index); }
    private static MethodSymbol method(SemanticModel model, String name, int index) {
        return model.symbols().stream().filter(s -> s instanceof MethodSymbol && s.name().equals(name))
                .map(MethodSymbol.class::cast).toList().get(index);
    }
    /** The symbol bound at the last character of the first occurrence of {@code marker}, e.g. "a=x". */
    private static Symbol at(SemanticModel model, String marker) {
        return model.symbolAt(model.source().text().indexOf(marker) + marker.length() - 1).orElseThrow();
    }
    private static List<Reference.Kind> kinds(List<Reference> references) {
        return references.stream().map(Reference::kind).toList();
    }

    @Test @DisplayName("nested shadowing binds each read to the innermost visible declaration")
    void nestedShadowing() {
        var model = model("int x=1; void f(){int x=2; if(true){int x=3; int a=x;} int b=x; return;}");
        VariableSymbol global = variable(model, "x", 0), local = variable(model, "x", 1), inner = variable(model, "x", 2);
        assertEquals(VariableSymbol.Kind.GLOBAL, global.kind());
        assertEquals(VariableSymbol.Kind.LOCAL, local.kind());
        assertSame(inner, at(model, "a=x"));
        assertSame(local, at(model, "b=x"));
        assertSame(global, local.shadowed().orElseThrow());
        assertSame(local, inner.shadowed().orElseThrow());
        assertTrue(global.shadowed().isEmpty());
        assertEquals(List.of(), model.references(global));
    }

    @Test @DisplayName("sibling scopes hold distinct declarations that do not shadow each other")
    void siblingScopes() {
        var model = model("void f(boolean c){if(c){int x=1;int a=x;}else{int x=2;int b=x;}return;}");
        VariableSymbol first = variable(model, "x", 0), second = variable(model, "x", 1);
        assertNotSame(first, second);
        assertTrue(first.shadowed().isEmpty() && second.shadowed().isEmpty());
        assertSame(first, at(model, "a=x"));
        assertSame(second, at(model, "b=x"));
        Scope methodScope = model.globalScope().children().get(0);
        assertEquals(Scope.Kind.METHOD, methodScope.kind());
        assertEquals(2, methodScope.children().size());
        assertFalse(first.scope().encloses(second.scope()));
        assertTrue(methodScope.encloses(first.scope()) && model.globalScope().encloses(second.scope()));
        assertSame(methodScope, first.scope().parent().orElseThrow());
    }

    @Test @DisplayName("parameters share the method scope and record reads and writes")
    void parameters() {
        var model = model("void f(final int p, boolean q){int a=p; q=true; return;}");
        VariableSymbol p = variable(model, "p", 0), q = variable(model, "q", 0), a = variable(model, "a", 0);
        assertEquals(VariableSymbol.Kind.PARAMETER, p.kind());
        assertTrue(p.isFinal() && !q.isFinal());
        assertEquals(Type.INT, p.type());
        assertSame(p.scope(), a.scope());
        assertEquals(Scope.Kind.METHOD, p.scope().kind());
        assertEquals(List.of(p, q, a), p.scope().declarations());
        assertEquals(List.of(Reference.Kind.READ), kinds(model.references(p)));
        assertEquals(List.of(Reference.Kind.WRITE), kinds(model.references(q)));
        assertEquals(List.of(Type.INT, Type.BOOLEAN), method(model, "f", 0).parameterTypes());
    }

    @Test @DisplayName("comma declarations see earlier bindings only")
    void commaDeclarations() {
        var model = model("int x=1,y=x; int a=b,b=1;");
        assertSame(variable(model, "x", 0), at(model, "y=x"));
        Reference unresolved = model.referenceAt(model.source().text().indexOf("a=b") + 2).orElseThrow();
        assertTrue(unresolved.target().isEmpty());
        assertEquals(List.of(), model.references(variable(model, "b", 0)));
    }

    @Test @DisplayName("self-initialization is a read bound to the new declaration")
    void selfInitialization() {
        var result = result("int x=1; void f(){int x=x;return;}");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("E103")));
        var model = result.semanticModel().orElseThrow();
        VariableSymbol inner = variable(model, "x", 1);
        List<Reference> uses = model.references(inner);
        assertEquals(List.of(Reference.Kind.READ), kinds(uses));
        assertEquals(model.source().text().lastIndexOf('x'), uses.get(0).range().start().offset());
        assertEquals(List.of(), model.references(variable(model, "x", 0)));
    }

    @Test @DisplayName("assignments record writes, including comma assignments")
    void writes() {
        var model = model("int x; int y; void f(){x=1,y=x; return;}");
        assertEquals(List.of(Reference.Kind.WRITE, Reference.Kind.READ), kinds(model.references(variable(model, "x", 0))));
        assertEquals(List.of(Reference.Kind.WRITE), kinds(model.references(variable(model, "y", 0))));
    }

    @Test @DisplayName("forward calls and recursion bind to method symbols")
    void callsAndRecursion() {
        var model = model("void a(){b(1);a();return;} void b(int n){return;}");
        MethodSymbol a = method(model, "a", 0), b = method(model, "b", 0);
        assertEquals(List.of(Reference.Kind.CALL), kinds(model.references(b)));
        assertEquals(List.of(Reference.Kind.CALL), kinds(model.references(a)));
        assertSame(model.globalScope(), a.scope());
        assertTrue(a.isAccepted());
        assertSame(model.program().methods().get(0), a.node());
    }

    @Test @DisplayName("variables and methods have separate namespaces")
    void namespaces() {
        var model = model("int f=1; void f(){int a=f; f(); return;}");
        String text = model.source().text();
        assertTrue(at(model, "a=f") instanceof VariableSymbol);
        assertTrue(model.symbolAt(text.indexOf("f();")).orElseThrow() instanceof MethodSymbol);
    }

    @Test @DisplayName("rejected duplicate variables and parameters never replace the accepted declaration")
    void duplicateVariables() {
        var model = model("void f(int p, int p){int x=1;int x=2;int y=x;int z=p;return;}");
        VariableSymbol first = variable(model, "x", 0), duplicate = variable(model, "x", 1);
        assertTrue(first.isAccepted());
        assertFalse(duplicate.isAccepted());
        assertTrue(duplicate.shadowed().isEmpty());
        assertEquals(List.of(Reference.Kind.READ), kinds(model.references(first)));
        assertEquals(List.of(), model.references(duplicate));
        assertFalse(first.scope().declarations().contains(duplicate));
        assertFalse(variable(model, "p", 1).isAccepted());
        assertSame(variable(model, "p", 0), at(model, "z=p"));
    }

    @Test @DisplayName("duplicate method bodies are analyzed and their calls bind to the first definition")
    void duplicateMethods() {
        var model = model("void f(int a){return;} void f(){int z=1; f(z); return;}");
        MethodSymbol first = method(model, "f", 0), duplicate = method(model, "f", 1);
        assertTrue(first.isAccepted());
        assertFalse(duplicate.isAccepted());
        assertEquals(List.of(Reference.Kind.CALL), kinds(model.references(first)));
        VariableSymbol z = variable(model, "z", 0);
        assertSame(duplicate.node(), z.scope().node());
        assertEquals(List.of(Reference.Kind.READ), kinds(model.references(z)));
    }

    @Test @DisplayName("unresolved names are retained without a target")
    void unresolvedNames() {
        var model = model("void f(){x=1;missing();int y=z;return;}");
        List<Reference> unresolved = model.references().stream().filter(r -> r.target().isEmpty()).toList();
        assertEquals(List.of("x", "missing", "z"), unresolved.stream().map(Reference::name).toList());
        assertEquals(List.of(Reference.Kind.WRITE, Reference.Kind.CALL, Reference.Kind.READ), kinds(unresolved));
        assertTrue(model.symbolAt(model.source().text().indexOf("missing")).isEmpty());
        assertTrue(model.referenceAt(model.source().text().indexOf("missing")).isPresent());
    }

    @Test @DisplayName("identifier ranges survive whitespace and comments")
    void rangesWithWhitespaceAndComments() {
        String source = "int   /* note */\n\t spaced = 1; void   go ( ){int u=spaced;return;}";
        var model = model(source);
        SourceRange range = variable(model, "spaced", 0).nameRange();
        assertEquals(source.indexOf("spaced"), range.start().offset());
        assertEquals(source.indexOf("spaced") + 6, range.end());
        assertEquals(6, range.length());
        assertEquals(2, range.start().line());
        assertEquals(3, range.start().column());
        MethodSymbol go = method(model, "go", 0);
        assertEquals(source.indexOf("go"), go.nameRange().start().offset());
        assertEquals(source.indexOf("void"), go.node().position().offset());
        assertEquals(source.lastIndexOf("spaced"), model.references(variable(model, "spaced", 0)).get(0).range().start().offset());
    }

    @Test @DisplayName("identifier ranges after CRLF line breaks")
    void rangesAfterCrlf() {
        String source = "void f(){\r\n  int x=1;\r\n  int y =  x;\r\n  return;\r\n}";
        var model = model(source);
        Reference read = model.references(variable(model, "x", 0)).get(0);
        assertEquals(source.lastIndexOf('x'), read.range().start().offset());
        assertEquals(3, read.range().start().line());
        assertEquals(12, read.range().start().column());
    }

    @Test @DisplayName("offsets and columns count UTF-16 units after supplementary characters")
    void rangesAfterSupplementaryCharacters() {
        String source = "String s=\"😀\"; int after=1;";
        var model = model(source);
        SourceRange range = variable(model, "after", 0).nameRange();
        assertEquals(source.indexOf("after"), range.start().offset());
        assertEquals(source.indexOf("after") + 1, range.start().column());
        assertEquals(2, "😀".length());
    }

    @Test @DisplayName("offset queries find declarations and uses but not gaps")
    void offsetQueries() {
        String source = "int count=1; void f(){int copy=count;return;}";
        var model = model(source);
        VariableSymbol count = variable(model, "count", 0);
        int use = source.lastIndexOf("count");
        assertSame(count, model.symbolAt(source.indexOf("count")).orElseThrow());
        assertSame(count, model.symbolAt(use + 4).orElseThrow());
        assertTrue(model.symbolAt(use + 5).isEmpty());
        assertTrue(model.symbolAt(0).isEmpty());
        assertTrue(model.symbolAt(source.length() + 10).isEmpty());
        assertTrue(model.referenceAt(source.indexOf("count")).isEmpty());
        assertEquals(Reference.Kind.READ, model.referenceAt(use).orElseThrow().kind());
        assertSame(model.symbolAt(source.indexOf("copy")).orElseThrow().scope(), model.referenceAt(use).orElseThrow().scope());
    }

    @Test @DisplayName("model is present after parsing and absent for input, lexical, parse and I/O failures")
    void modelAvailability() {
        assertTrue(result("int x=1;").semanticModel().isPresent());
        var invalid = result("int x=y;");
        assertFalse(invalid.isValid());
        assertTrue(invalid.semanticModel().isPresent());
        assertTrue(result(" ".repeat(Analyzer.MAX_SOURCE_CHARACTERS + 1)).semanticModel().isEmpty());
        assertTrue(result("String s=\"open;").semanticModel().isEmpty());
        assertTrue(result("int x=;").semanticModel().isEmpty());
        var io = new AnalysisResult("x.sjava", List.of(Diagnostic.error("EIO", "Cannot read", SourcePosition.start())), List.of());
        assertTrue(io.semanticModel().isEmpty());
    }

    @Test @DisplayName("model collections are immutable")
    void immutableCollections() {
        var model = model("int x=1; void f(){if(true){int y=x;}return;}");
        VariableSymbol x = variable(model, "x", 0);
        assertThrows(UnsupportedOperationException.class, () -> model.symbols().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.references().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.references(x).clear());
        assertThrows(UnsupportedOperationException.class, () -> model.globalScope().children().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.globalScope().declarations().clear());
        assertThrows(UnsupportedOperationException.class, () -> method(model, "f", 0).parameterTypes().clear());
    }

    @Test @DisplayName("expression types are recorded by node identity")
    void expressionTypes() {
        var model = model("int i=1; boolean b=true && !false; void f(){int u; boolean c=u || true; double d=i; return;}");
        List<Ast.Expression> initializers = new ArrayList<>();
        model.program().globals().forEach(s -> ((Ast.Declaration) s).bindings()
                .forEach(bind -> initializers.add(bind.initializer().orElseThrow())));
        assertEquals(Type.INT, model.typeOf(initializers.get(0)).orElseThrow());
        Ast.Logical logical = (Ast.Logical) initializers.get(1);
        assertEquals(Type.BOOLEAN, model.typeOf(logical).orElseThrow());
        assertEquals(Type.BOOLEAN, model.typeOf(logical.right()).orElseThrow());
        assertEquals(Type.BOOLEAN, model.typeOf(((Ast.Not) logical.right()).operand()).orElseThrow());
        var body = model.program().methods().get(0).body().statements();
        Ast.Logical uninitialized = (Ast.Logical) ((Ast.Declaration) body.get(1)).bindings().get(0).initializer().orElseThrow();
        assertEquals(Type.ERROR, model.typeOf(uninitialized.left()).orElseThrow());
        assertEquals(Type.ERROR, model.typeOf(uninitialized).orElseThrow());
        Ast.Expression widened = ((Ast.Declaration) body.get(2)).bindings().get(0).initializer().orElseThrow();
        assertEquals(Type.INT, model.typeOf(widened).orElseThrow());
        Ast.Literal copy = new Ast.Literal(Type.INT, "1", initializers.get(0).position());
        assertEquals(copy, initializers.get(0));
        assertTrue(model.typeOf(copy).isEmpty());
    }

    @Test @DisplayName("concurrent analyses build independent models")
    void concurrentModels() throws Exception {
        Analyzer analyzer = new Analyzer();
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<SemanticModel>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String name = "v" + i;
                futures.add(pool.submit(() -> analyzer.analyze(new SourceUnit(name,
                        "int " + name + "=1; void f(){int copy=" + name + ";return;}")).semanticModel().orElseThrow()));
            }
            for (int i = 0; i < futures.size(); i++) {
                SemanticModel model = futures.get(i).get();
                VariableSymbol global = variable(model, "v" + i, 0);
                assertEquals(1, model.references(global).size());
                assertEquals(List.of(global), model.globalScope().declarations());
            }
            assertNotSame(futures.get(0).get().globalScope(), futures.get(1).get().globalScope());
        } finally { pool.shutdownNow(); }
    }

    @Test @DisplayName("an analyzer is single-use and the model has a short description")
    void singleUseAnalyzer() {
        var program = new Ast.Program(List.of(), List.of(), SourcePosition.start());
        var analyzer = new SemanticAnalyzer(new ArrayList<>());
        var model = analyzer.analyze(new SourceUnit("empty.sjava", ""), program);
        assertEquals("SemanticModel[empty.sjava, 0 symbols, 0 references]", model.toString());
        assertThrows(IllegalStateException.class, () -> analyzer.analyze(new SourceUnit("again", ""), program));
        assertEquals("GLOBAL scope", model.globalScope().toString());
        assertTrue(model.globalScope().parent().isEmpty());
        assertSame(program, model.globalScope().node());
    }

    @Test @DisplayName("symbol descriptions and source range validation")
    void descriptionsAndRanges() {
        var model = model("int x=1; void go(int a){return;}");
        assertEquals("global int x @1:5", variable(model, "x", 0).toString());
        assertEquals("method go[int] @1:15", method(model, "go", 0).toString());
        SourceRange range = SourceRange.of(new SourcePosition(1, 3, 2), 4);
        assertTrue(range.contains(2) && range.contains(5));
        assertFalse(range.contains(1) || range.contains(6));
        assertThrows(IllegalArgumentException.class, () -> new SourceRange(new SourcePosition(1, 3, 2), 1));
        assertThrows(NullPointerException.class, () -> new AnalysisResult("x", List.of(), List.of(), null));
    }
}
