package dev.sjavainspector;

import java.util.List;
import static dev.sjavainspector.Assertions.*;
import static dev.sjavainspector.TestSuite.test;

final class LanguageTests {
    private LanguageTests() { }
    static void run() {
        test("empty input is valid", () -> valid(""));
        test("uninitialized declarations are allowed", () -> valid("int x; boolean b;"));
        test("boolean assignment establishes initialization", () -> valid("void f(){boolean b;b=true;if(b){return;}return;}"));
        test("self initialization rejected", () -> error("E103", "void f(){int x=x;return;}"));
        test("redeclare after assignment rejected", () -> error("E101", "void f(){int x=1;x=2;int x=3;return;}"));
        test("comma assignments processed left to right", () -> valid("void f(){int x,y;x=1,y=x;use(y);return;}void use(int n){return;}"));
        test("multi declaration sees earlier initializer", () -> valid("int x=1,y=x;"));
        test("multi declaration cannot read later binding", () -> error("E102", "int x=y,y=1;"));
        test("assignment to undeclared variable", () -> error("E102", "void f(){x=1;return;}"));
        test("use before initialization", () -> error("E103", "void f(){int x;int y=x;return;}"));
        test("final declaration needs initializer", () -> error("E105", "final int x;"));
        test("final assignment forbidden", () -> error("E106", "final int x=1;x=2;"));
        test("final assignment forbidden across scope", () -> error("E106", "void f(){final int x=1;if(true){x=2;}return;}"));
        test("final parameter cannot be reassigned", () -> error("E106", "void f(final int x){x=2;return;}"));
        test("parameters are initialized", () -> valid("void f(final String a,boolean b,char c,double d,int e){String x=a;boolean y=b;char z=c;return;}"));
        test("duplicate parameters rejected", () -> error("E101", "void f(int x,boolean x){return;}"));
        test("parameters share method scope", () -> error("E101", "void f(int x){int x=1;return;}"));
        test("nested scope may shadow", () -> valid("int x=1;void f(){String x=\"a\";if(true){char x='b';}return;}"));
        test("shadow initializer resolves to own declaration", () -> error("E103", "int x=1;void f(){int x=x;return;}"));
        test("local cannot escape block", () -> error("E102", "void f(){if(true){int x=1;}int y=x;return;}"));
        test("shadow assignments do not initialize outer", () -> error("E103", "void f(){int x;if(true){int x=1;}int y=x;return;}"));
        test("if without else does not definitely assign", () -> error("E103", "void f(boolean b){int x;if(b){x=1;}int y=x;return;}"));
        test("loop may execute zero times", () -> error("E103", "void f(){int x;while(true){x=1;}int y=x;return;}"));
        test("assignments visible within a branch", () -> valid("void f(){int x;if(true){x=1;int y=x;}return;}"));
        test("both branches establish assignment", () -> valid("void f(boolean b){int x;if(b){x=1;}else{x=2;}int y=x;return;}"));
        test("one incomplete branch fails merge", () -> error("E103", "void f(boolean b){int x;if(b){x=1;}else{int y=2;}int z=x;return;}"));
        test("returning branch excluded from merge", () -> valid("void f(boolean b){int x;if(b){return;}else{x=2;}int z=x;return;}"));
        test("returning else excluded from merge", () -> valid("void f(boolean b){int x;if(b){x=2;}else{return;}int z=x;return;}"));
        test("nested complete branches merge", () -> valid("void f(boolean a,boolean b){int x;if(a){if(b){x=1;}else{x=2;}}else{x=3;}int z=x;return;}"));
        test("existing assignment survives branch", () -> valid("void f(boolean b){int x=1;if(b){x=2;}int y=x;return;}"));
        test("global initialization isolated between methods", () -> error("E103", "int x;void a(){x=1;return;}void b(){int y=x;return;}"));
        test("global assignment in same method is visible", () -> valid("int x;void a(){x=1;int y=x;return;}"));
        test("global initialization completes before method analysis", () -> valid("void a(){int y=x;return;}int x=1;"));
        test("globals initialized in source order", () -> error("E102", "int y=x;int x=1;"));
        test("failed initializer does not initialize variable", () -> {
            var result = error("E104", "void f(){int x=\"bad\";int y=x;return;}");
            check(result.diagnostics().stream().anyMatch(d -> d.code().equals("E103")), "Expected uninitialized read after failed declaration");
        });
        test("forward calls and recursion", () -> valid("void first(){second();return;}void second(){first();return;}"));
        test("unknown method rejected", () -> error("E107", "void f(){missing();return;}"));
        test("wrong argument count rejected", () -> error("E108", "void f(int a){return;}void g(){f();return;}"));
        test("wrong argument type rejected", () -> error("E104", "void f(int a){return;}void g(){f(\"a\");return;}"));
        test("uninitialized call argument", () -> error("E103", "void f(int a){return;}void g(){int x;f(x);return;}"));
        test("duplicate methods rejected", () -> error("E101", "void f(){return;}void f(int a){return;}"));
        test("type widening allowed", () -> valid("double x=1;boolean a=2,b=3.5;"));
        test("narrowing rejected", () -> error("E104", "int x=2.5;"));
        test("boolean cannot widen to number", () -> error("E104", "double x=true;"));
        test("char is distinct from String", () -> error("E104", "String x='a';"));
        test("numeric conditions allowed", () -> valid("void f(){if(1 && 2.5){return;}return;}"));
        test("String condition rejected", () -> error("E110", "void f(){if(\"yes\"){return;}return;}"));
        test("char condition rejected", () -> error("E110", "void f(){if('a'){return;}return;}"));
        test("logical operators validate both sides", () -> error("E110", "boolean b=true || \"bad\";"));
        test("missing return diagnosed", () -> error("E109", "void f(){int x=1;}"));
        test("conditional return alone insufficient", () -> error("E109", "void f(boolean b){if(b){return;}}"));
        test("both returning branches satisfy method", () -> valid("void f(boolean b){if(b){return;}else{return;}}"));
        test("return in while alone insufficient", () -> error("E109", "void f(){while(true){return;}}"));
        test("return cannot carry a value", () -> error("E002", "void f(){return 1;}"));
        test("multiple independent semantic errors", () -> {
            var result = analyze("void f(){unknown=1;missing();int x=\"a\";return;}");
            equal(List.of("E102", "E107", "E104"), result.diagnostics().stream().map(d -> d.code()).toList());
        });
    }
}
