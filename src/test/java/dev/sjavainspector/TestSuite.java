package dev.sjavainspector;

/** Small dependency-free runner; assertions execute even without Java's -ea flag. */
public final class TestSuite {
    @FunctionalInterface interface Check { void run() throws Exception; }
    private static int passed;
    private static int failed;
    private TestSuite() { }
    static void test(String name, Check check) {
        try { check.run(); passed++; System.out.println("PASS " + name); }
        catch (Exception | AssertionError failure) {
            failed++; System.err.println("FAIL " + name); failure.printStackTrace(System.err);
        }
    }
    public static void main(String[] args) {
        FrontendTests.run();
        LanguageTests.run();
        ApplicationTests.run();
        System.out.println("\n" + passed + " tests passed; " + failed + " failed.");
        if (failed != 0) System.exit(1);
    }
}
