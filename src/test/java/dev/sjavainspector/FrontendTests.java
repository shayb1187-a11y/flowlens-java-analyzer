package dev.sjavainspector;

import dev.sjavainspector.api.Diagnostic;
import dev.sjavainspector.syntax.Ast;
import dev.sjavainspector.syntax.Lexer;
import dev.sjavainspector.syntax.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static dev.sjavainspector.Assertions.*;
import static dev.sjavainspector.TestSuite.test;

final class FrontendTests {
    private FrontendTests() { }
    static void run() {
        test("commas and equals inside quoted values", () -> valid("void f(){ String a = \"a,b=c\", b = \"d=e,f\"; use(a,b); return; } void use(String a,String b){return;}"));
        test("comment markers inside quoted literals", () -> valid("String text = \"/* text */ // more\"; char c = ',';"));
        test("inline, indented, and multiline comments", () -> valid("  // heading\nint x=1; // inline\n/* block\ncomment */ void f(){return;}"));
        test("escaped quote and backslash", () -> valid("String a = \"quoted \\\"yes\\\" \\\\ path\"; char b = '\\n'; char c = '\\'';"));
        test("unterminated string", () -> error("E001", "String x = \"oops;"));
        test("unterminated comment", () -> error("E001", "/* never closed"));
        test("empty char rejected", () -> error("E001", "char x = '';"));
        test("multi-character char rejected", () -> error("E001", "char x = 'ab';"));
        test("unknown escape rejected", () -> error("E001", "String x = \"\\q\";"));
        test("numeric literal forms", () -> valid("double a=.5, b=-.5, c=2., d=+2.0; int n=-2;"));
        test("malformed numeric literal", () -> error("E001", "double x=1.2.3;"));
        test("arithmetic is outside the language", () -> error("E002", "int x=1+2;"));
        test("single logical operator rejected", () -> error("E001", "void f(){if(true & false){return;} return;}"));
        test("keyword cannot be variable name", () -> error("E002", "int while=1;"));
        test("single underscore rejected", () -> error("E002", "int _=1;"));
        test("double underscore prefix rejected", () -> error("E002", "int __x=1;"));
        test("underscore plus digit accepted for variables", () -> valid("int _1=2;"));
        test("method must start with a letter", () -> error("E002", "void _f(){return;}"));
        test("multiple statements per line", () -> valid("int x=1; void f(){int y=x; y=2; return;}"));
        test("multiline declarations and calls", () -> valid("void f(\n int a,\n String b\n){return;} void g(){f(\n1,\n\"hello\"\n);return;}"));
        test("CRLF source positions", () -> {
            var result = error("E102", "void f(){\r\n  x = 1;\r\n  return;\r\n}");
            equal(2, result.diagnostics().get(0).position().line());
            equal(3, result.diagnostics().get(0).position().column());
            equal(13, result.diagnostics().get(0).position().offset());
        });
        test("bare CR source positions", () -> {
            var result = error("E102", "void f(){\r x=1;\r return;\r}");
            equal(2, result.diagnostics().get(0).position().line());
        });
        test("logical precedence and parentheses", () -> {
            List<Diagnostic> errors = new ArrayList<>();
            Ast.Program program = new Parser(new Lexer("boolean x=true || false && !true;", errors).scan(), errors).parse();
            var declaration = (Ast.Declaration) program.globals().get(0);
            var expression = (Ast.Logical) declaration.bindings().get(0).initializer().orElseThrow();
            equal(Ast.LogicalOperator.OR, expression.operator());
            equal(Ast.LogicalOperator.AND, ((Ast.Logical) expression.right()).operator());
            equal(List.of(), errors);
            valid("boolean x=(true || false) && !(1 || 0);");
        });
        test("trailing comma rejected", () -> error("E002", "int x=1,;"));
        test("missing closing brace", () -> error("E002", "void f(){return;"));
        test("extra closing brace", () -> error("E002", "void f(){return;}}"));
        test("nested method rejected", () -> error("E002", "void f(){void g(){return;} return;}"));
        test("global call rejected", () -> error("E002", "f(); void f(){return;}"));
        test("global return rejected", () -> error("E002", "return;"));
        test("syntax errors recover at statement boundaries", () -> {
            var result = error("E002", "void f(){int a=; int b=; return;}");
            check(result.diagnostics().size() >= 2, "Expected multiple parser diagnostics");
            equal(List.of(), result.methods());
        });
        test("expression recursion guard", () -> error("E002", "boolean x=" + "!".repeat(200) + "true;"));
        test("block recursion guard", () -> error("E002", "void f(){" + "if(true){".repeat(70) + "return;" + "}".repeat(70) + "return;}"));
        test("source length guard", () -> error("E900", " ".repeat(1_000_001)));
        test("AST collections are immutable", () -> {
            List<Diagnostic> errors = new ArrayList<>();
            var program = new Parser(new Lexer("void f(){return;}", errors).scan(), errors).parse();
            immutable(() -> program.methods().clear());
            immutable(() -> program.methods().get(0).body().statements().clear());
        });
        test("500 seeded malformed token streams never crash", () -> {
            Random random = new Random(260925);
            String[] atoms = {"void", "f", "int", "x", "=", "1", ";", "(", ")", "{", "}", "if", "else", "return", ",", "true", "&&", "!"};
            for (int i = 0; i < 500; i++) {
                StringBuilder source = new StringBuilder();
                for (int j = 0; j < 50; j++) source.append(atoms[random.nextInt(atoms.length)]).append(' ');
                analyze(source.toString());
            }
        });
    }
}
