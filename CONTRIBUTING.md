# Contributing

Use JDK 17 or later. Start by reading `docs/LANGUAGE.md` so an apparent bug is distinguished from an unsupported language feature.

1. Reproduce the behavior with the smallest source example.
2. Add a behavioral JUnit test in the test class that mirrors the package under change, for example `syntax/FrontendTest`, `semantic/SemanticAnalyzerTest`, or `cli/CliTest`. Tests that need the packaged JAR are named `*IT` and run in the integration-test phase.
3. Keep the analysis core free of disk access, Swing objects, output streams, and shared mutable state.
4. For grammar changes, update the language document, AST/visitor contracts, semantic behavior, and relevant tests together.
5. For a warning rule, implement `AnalysisRule` for syntax-only checks, or `SemanticRule` when it needs resolved names. Read bindings from the `SemanticModel`; do not resolve names in a rule, and do not put optional style checks in semantic validation.
6. Run `./mvnw verify` (Windows: `mvnw.cmd verify`). A passing run compiles with warnings treated as errors, runs unit and integration tests, creates `target/flowlens.jar`, and enforces the JaCoCo coverage minimums in `pom.xml`.

New tests should exercise observable behavior, especially failure paths and counterexamples. Keep generated files in `target/`. Do not update a count or screenshot in documentation without running the checks that produce it.

Use JUnit assertions and give each test a `@DisplayName` describing the observed behavior. `testing/SourceAssertions` provides `valid`, `error`, and `analyze` helpers for s-Java snippets. Do not lower the coverage minimums to make a build pass; add tests instead.
