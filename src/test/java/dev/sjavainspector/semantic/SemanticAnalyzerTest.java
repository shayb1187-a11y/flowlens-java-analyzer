package dev.sjavainspector.semantic;

import static dev.sjavainspector.testing.SourceAssertions.analyze;
import static dev.sjavainspector.testing.SourceAssertions.error;
import static dev.sjavainspector.testing.SourceAssertions.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sjavainspector.api.Diagnostic;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticAnalyzerTest {
    @Test @DisplayName("empty input is valid")
    void emptyInput() { valid(""); }

    @Test @DisplayName("uninitialized declarations are allowed")
    void uninitializedDeclarations() { valid("int x; boolean b;"); }

    @Test @DisplayName("boolean assignment establishes initialization")
    void booleanAssignmentInitializes() { valid("void f(){boolean b;b=true;if(b){return;}return;}"); }

    @Test @DisplayName("self initialization rejected")
    void selfInitialization() { error("E103", "void f(){int x=x;return;}"); }

    @Test @DisplayName("redeclare after assignment rejected")
    void redeclareAfterAssignment() { error("E101", "void f(){int x=1;x=2;int x=3;return;}"); }

    @Test @DisplayName("comma assignments processed left to right")
    void commaAssignmentsLeftToRight() { valid("void f(){int x,y;x=1,y=x;use(y);return;}void use(int n){return;}"); }

    @Test @DisplayName("multi declaration sees earlier initializer")
    void multiDeclarationSeesEarlier() { valid("int x=1,y=x;"); }

    @Test @DisplayName("multi declaration cannot read later binding")
    void multiDeclarationCannotReadLater() { error("E102", "int x=y,y=1;"); }

    @Test @DisplayName("assignment to undeclared variable")
    void assignmentToUndeclared() { error("E102", "void f(){x=1;return;}"); }

    @Test @DisplayName("use before initialization")
    void useBeforeInitialization() { error("E103", "void f(){int x;int y=x;return;}"); }

    @Test @DisplayName("final declaration needs initializer")
    void finalNeedsInitializer() { error("E105", "final int x;"); }

    @Test @DisplayName("final assignment forbidden")
    void finalAssignment() { error("E106", "final int x=1;x=2;"); }

    @Test @DisplayName("final assignment forbidden across scope")
    void finalAssignmentAcrossScope() { error("E106", "void f(){final int x=1;if(true){x=2;}return;}"); }

    @Test @DisplayName("final parameter cannot be reassigned")
    void finalParameter() { error("E106", "void f(final int x){x=2;return;}"); }

    @Test @DisplayName("parameters are initialized")
    void parametersInitialized() {
        valid("void f(final String a,boolean b,char c,double d,int e){String x=a;boolean y=b;char z=c;return;}");
    }

    @Test @DisplayName("duplicate parameters rejected")
    void duplicateParameters() { error("E101", "void f(int x,boolean x){return;}"); }

    @Test @DisplayName("parameters share method scope")
    void parametersShareMethodScope() { error("E101", "void f(int x){int x=1;return;}"); }

    @Test @DisplayName("nested scope may shadow")
    void nestedScopeShadows() { valid("int x=1;void f(){String x=\"a\";if(true){char x='b';}return;}"); }

    @Test @DisplayName("shadow initializer resolves to own declaration")
    void shadowInitializer() { error("E103", "int x=1;void f(){int x=x;return;}"); }

    @Test @DisplayName("local cannot escape block")
    void localCannotEscapeBlock() { error("E102", "void f(){if(true){int x=1;}int y=x;return;}"); }

    @Test @DisplayName("shadow assignments do not initialize outer")
    void shadowAssignmentsDoNotInitializeOuter() { error("E103", "void f(){int x;if(true){int x=1;}int y=x;return;}"); }

    @Test @DisplayName("if without else does not definitely assign")
    void ifWithoutElse() { error("E103", "void f(boolean b){int x;if(b){x=1;}int y=x;return;}"); }

    @Test @DisplayName("loop may execute zero times")
    void loopZeroIterations() { error("E103", "void f(){int x;while(true){x=1;}int y=x;return;}"); }

    @Test @DisplayName("assignments visible within a branch")
    void assignmentsVisibleWithinBranch() { valid("void f(){int x;if(true){x=1;int y=x;}return;}"); }

    @Test @DisplayName("both branches establish assignment")
    void bothBranchesAssign() { valid("void f(boolean b){int x;if(b){x=1;}else{x=2;}int y=x;return;}"); }

    @Test @DisplayName("one incomplete branch fails merge")
    void incompleteBranch() { error("E103", "void f(boolean b){int x;if(b){x=1;}else{int y=2;}int z=x;return;}"); }

    @Test @DisplayName("returning branch excluded from merge")
    void returningBranchExcluded() { valid("void f(boolean b){int x;if(b){return;}else{x=2;}int z=x;return;}"); }

    @Test @DisplayName("returning else excluded from merge")
    void returningElseExcluded() { valid("void f(boolean b){int x;if(b){x=2;}else{return;}int z=x;return;}"); }

    @Test @DisplayName("nested complete branches merge")
    void nestedCompleteBranches() {
        valid("void f(boolean a,boolean b){int x;if(a){if(b){x=1;}else{x=2;}}else{x=3;}int z=x;return;}");
    }

    @Test @DisplayName("existing assignment survives branch")
    void existingAssignmentSurvives() { valid("void f(boolean b){int x=1;if(b){x=2;}int y=x;return;}"); }

    @Test @DisplayName("global initialization isolated between methods")
    void globalInitializationIsolated() { error("E103", "int x;void a(){x=1;return;}void b(){int y=x;return;}"); }

    @Test @DisplayName("global assignment in same method is visible")
    void globalAssignmentSameMethod() { valid("int x;void a(){x=1;int y=x;return;}"); }

    @Test @DisplayName("global initialization completes before method analysis")
    void globalsBeforeMethods() { valid("void a(){int y=x;return;}int x=1;"); }

    @Test @DisplayName("globals initialized in source order")
    void globalsInSourceOrder() { error("E102", "int y=x;int x=1;"); }

    @Test @DisplayName("failed initializer does not initialize variable")
    void failedInitializer() {
        var result = error("E104", "void f(){int x=\"bad\";int y=x;return;}");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("E103")),
                "Expected uninitialized read after failed declaration");
    }

    @Test @DisplayName("forward calls and recursion")
    void forwardCallsAndRecursion() { valid("void first(){second();return;}void second(){first();return;}"); }

    @Test @DisplayName("unknown method rejected")
    void unknownMethod() { error("E107", "void f(){missing();return;}"); }

    @Test @DisplayName("wrong argument count rejected")
    void wrongArgumentCount() { error("E108", "void f(int a){return;}void g(){f();return;}"); }

    @Test @DisplayName("wrong argument type rejected")
    void wrongArgumentType() { error("E104", "void f(int a){return;}void g(){f(\"a\");return;}"); }

    @Test @DisplayName("uninitialized call argument")
    void uninitializedCallArgument() { error("E103", "void f(int a){return;}void g(){int x;f(x);return;}"); }

    @Test @DisplayName("duplicate methods rejected")
    void duplicateMethods() { error("E101", "void f(){return;}void f(int a){return;}"); }

    @Test @DisplayName("type widening allowed")
    void typeWidening() { valid("double x=1;boolean a=2,b=3.5;"); }

    @Test @DisplayName("narrowing rejected")
    void narrowing() { error("E104", "int x=2.5;"); }

    @Test @DisplayName("boolean cannot widen to number")
    void booleanToNumber() { error("E104", "double x=true;"); }

    @Test @DisplayName("char is distinct from String")
    void charDistinctFromString() { error("E104", "String x='a';"); }

    @Test @DisplayName("numeric conditions allowed")
    void numericConditions() { valid("void f(){if(1 && 2.5){return;}return;}"); }

    @Test @DisplayName("String condition rejected")
    void stringCondition() { error("E110", "void f(){if(\"yes\"){return;}return;}"); }

    @Test @DisplayName("char condition rejected")
    void charCondition() { error("E110", "void f(){if('a'){return;}return;}"); }

    @Test @DisplayName("logical operators validate both sides")
    void logicalOperatorsValidateBothSides() { error("E110", "boolean b=true || \"bad\";"); }

    @Test @DisplayName("missing return diagnosed")
    void missingReturn() { error("E109", "void f(){int x=1;}"); }

    @Test @DisplayName("conditional return alone insufficient")
    void conditionalReturnInsufficient() { error("E109", "void f(boolean b){if(b){return;}}"); }

    @Test @DisplayName("both returning branches satisfy method")
    void bothReturningBranches() { valid("void f(boolean b){if(b){return;}else{return;}}"); }

    @Test @DisplayName("return in while alone insufficient")
    void returnInWhileInsufficient() { error("E109", "void f(){while(true){return;}}"); }

    @Test @DisplayName("return cannot carry a value")
    void returnWithValue() { error("E002", "void f(){return 1;}"); }

    @Test @DisplayName("multiple independent semantic errors")
    void multipleIndependentErrors() {
        var result = analyze("void f(){unknown=1;missing();int x=\"a\";return;}");
        assertEquals(List.of("E102", "E107", "E104"), result.diagnostics().stream().map(Diagnostic::code).toList());
    }
}
