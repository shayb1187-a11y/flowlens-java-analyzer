package dev.sjavainspector.syntax;

import static dev.sjavainspector.testing.SourceAssertions.analyze;
import static dev.sjavainspector.testing.SourceAssertions.error;
import static dev.sjavainspector.testing.SourceAssertions.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrontendTest {
    @Test @DisplayName("commas and equals inside quoted values")
    void commasAndEqualsInsideQuotes() {
        valid("void f(){ String a = \"a,b=c\", b = \"d=e,f\"; use(a,b); return; } void use(String a,String b){return;}");
    }

    @Test @DisplayName("comment markers inside quoted literals")
    void commentMarkersInsideLiterals() { valid("String text = \"/* text */ // more\"; char c = ',';"); }

    @Test @DisplayName("inline, indented, and multiline comments")
    void comments() { valid("  // heading\nint x=1; // inline\n/* block\ncomment */ void f(){return;}"); }

    @Test @DisplayName("escaped quote and backslash")
    void escapes() { valid("String a = \"quoted \\\"yes\\\" \\\\ path\"; char b = '\\n'; char c = '\\'';"); }

    @Test @DisplayName("unterminated string")
    void unterminatedString() { error("E001", "String x = \"oops;"); }

    @Test @DisplayName("unterminated comment")
    void unterminatedComment() { error("E001", "/* never closed"); }

    @Test @DisplayName("empty char rejected")
    void emptyChar() { error("E001", "char x = '';"); }

    @Test @DisplayName("multi-character char rejected")
    void multiCharacterChar() { error("E001", "char x = 'ab';"); }

    @Test @DisplayName("unknown escape rejected")
    void unknownEscape() { error("E001", "String x = \"\\q\";"); }

    @Test @DisplayName("numeric literal forms")
    void numericLiteralForms() { valid("double a=.5, b=-.5, c=2., d=+2.0; int n=-2;"); }

    @Test @DisplayName("malformed numeric literal")
    void malformedNumber() { error("E001", "double x=1.2.3;"); }

    @Test @DisplayName("arithmetic is outside the language")
    void arithmeticRejected() { error("E002", "int x=1+2;"); }

    @Test @DisplayName("single logical operator rejected")
    void singleLogicalOperator() { error("E001", "void f(){if(true & false){return;} return;}"); }

    @Test @DisplayName("keyword cannot be variable name")
    void keywordAsName() { error("E002", "int while=1;"); }

    @Test @DisplayName("single underscore rejected")
    void singleUnderscore() { error("E002", "int _=1;"); }

    @Test @DisplayName("double underscore prefix rejected")
    void doubleUnderscorePrefix() { error("E002", "int __x=1;"); }

    @Test @DisplayName("underscore plus digit accepted for variables")
    void underscoreDigit() { valid("int _1=2;"); }

    @Test @DisplayName("method must start with a letter")
    void methodStartsWithLetter() { error("E002", "void _f(){return;}"); }

    @Test @DisplayName("multiple statements per line")
    void multipleStatementsPerLine() { valid("int x=1; void f(){int y=x; y=2; return;}"); }

    @Test @DisplayName("multiline declarations and calls")
    void multilineDeclarationsAndCalls() {
        valid("void f(\n int a,\n String b\n){return;} void g(){f(\n1,\n\"hello\"\n);return;}");
    }

    @Test @DisplayName("CRLF source positions")
    void crlfPositions() {
        var position = error("E102", "void f(){\r\n  x = 1;\r\n  return;\r\n}").diagnostics().get(0).position();
        assertEquals(2, position.line());
        assertEquals(3, position.column());
        assertEquals(13, position.offset());
    }

    @Test @DisplayName("bare CR source positions")
    void bareCrPositions() {
        var result = error("E102", "void f(){\r x=1;\r return;\r}");
        assertEquals(2, result.diagnostics().get(0).position().line());
    }

    @Test @DisplayName("logical precedence and parentheses")
    void logicalPrecedence() {
        List<Diagnostic> errors = new ArrayList<>();
        Ast.Program program = new Parser(new Lexer("boolean x=true || false && !true;", errors).scan(), errors).parse();
        var declaration = (Ast.Declaration) program.globals().get(0);
        var expression = (Ast.Logical) declaration.bindings().get(0).initializer().orElseThrow();
        assertEquals(Ast.LogicalOperator.OR, expression.operator());
        assertEquals(Ast.LogicalOperator.AND, ((Ast.Logical) expression.right()).operator());
        assertEquals(List.of(), errors);
        valid("boolean x=(true || false) && !(1 || 0);");
    }

    @Test @DisplayName("trailing comma rejected")
    void trailingComma() { error("E002", "int x=1,;"); }

    @Test @DisplayName("missing closing brace")
    void missingClosingBrace() { error("E002", "void f(){return;"); }

    @Test @DisplayName("extra closing brace")
    void extraClosingBrace() { error("E002", "void f(){return;}}"); }

    @Test @DisplayName("nested method rejected")
    void nestedMethod() { error("E002", "void f(){void g(){return;} return;}"); }

    @Test @DisplayName("global call rejected")
    void globalCall() { error("E002", "f(); void f(){return;}"); }

    @Test @DisplayName("global return rejected")
    void globalReturn() { error("E002", "return;"); }

    @Test @DisplayName("syntax errors recover at statement boundaries")
    void recoveryAtStatementBoundaries() {
        var result = error("E002", "void f(){int a=; int b=; return;}");
        assertTrue(result.diagnostics().size() >= 2, "Expected multiple parser diagnostics");
        assertEquals(List.of(), result.methods());
    }

    @Test @DisplayName("expression recursion guard")
    void expressionRecursionGuard() { error("E002", "boolean x=" + "!".repeat(200) + "true;"); }

    @Test @DisplayName("block recursion guard")
    void blockRecursionGuard() {
        error("E002", "void f(){" + "if(true){".repeat(70) + "return;" + "}".repeat(70) + "return;}");
    }

    @Test @DisplayName("source length guard")
    void sourceLengthGuard() { error("E900", " ".repeat(1_000_001)); }

    @Test @DisplayName("AST collections are immutable")
    void astCollectionsImmutable() {
        List<Diagnostic> errors = new ArrayList<>();
        var program = new Parser(new Lexer("void f(){return;}", errors).scan(), errors).parse();
        assertThrows(UnsupportedOperationException.class, () -> program.methods().clear());
        assertThrows(UnsupportedOperationException.class, () -> program.methods().get(0).body().statements().clear());
    }

    @Test @DisplayName("500 seeded malformed token streams never crash")
    void seededMalformedStreams() {
        Random random = new Random(260925);
        String[] atoms = {"void", "f", "int", "x", "=", "1", ";", "(", ")", "{", "}", "if", "else", "return", ",", "true", "&&", "!"};
        for (int i = 0; i < 500; i++) {
            StringBuilder source = new StringBuilder();
            for (int j = 0; j < 50; j++) source.append(atoms[random.nextInt(atoms.length)]).append(' ');
            analyze(source.toString());
        }
    }
}
