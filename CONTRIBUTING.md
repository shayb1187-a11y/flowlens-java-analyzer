# Contributing

Use JDK 17 or later. Start by reading `docs/LANGUAGE.md` so an apparent bug is distinguished from an unsupported language feature.

1. Reproduce the behavior with the smallest source example.
2. Add a behavioral case in `FrontendTests`, `LanguageTests`, or `ApplicationTests`.
3. Keep the analysis core free of disk access, Swing objects, output streams, and shared mutable state.
4. For grammar changes, update the language document, AST/visitor contracts, semantic behavior, and relevant tests together.
5. For a warning rule, implement `AnalysisRule`; do not put optional style checks in semantic validation.
6. Run `java Build.java test`. A passing run compiles with warnings treated as errors and creates the executable JAR.

New tests should exercise observable behavior, especially failure paths and counterexamples. Keep generated files in `build/`. Do not update a count or screenshot in documentation without running the checks that produce it.

The custom test runner is intentionally small and dependency-free. Throw an `AssertionError` to fail a check; do not rely on Java `assert`, which is disabled unless the JVM receives `-ea`.
