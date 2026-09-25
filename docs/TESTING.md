# Verification record

Portfolio verification date: **2026-09-25**.

## Observed local result

**150 named JUnit 5 tests passed, zero failed**, using Temurin OpenJDK **17.0.19** and Apache Maven **3.9.16** (via the Maven Wrapper) on Windows. 149 are unit tests run by Surefire. One integration test, run by Failsafe, launches the packaged `target/flowlens.jar` in a separate JVM. Both production and test sources compile with `--release 17 -Xlint:all -Werror`.

**JaCoCo coverage from the unit tests: 94.1% of lines (935/994) and 85.4% of branches (461/540).** The build fails below 90% line or 80% branch coverage (`coverage.*.minimum` in `pom.xml`). Coverage by package ranges from 76% (`ui`) and 78% (`cli`, whose `Main` entry point runs only in the integration test's child JVM, which is not instrumented) up to 100% (`io`, `lint`, `report`, `semantic`).

[verification.log](verification.log) contains the Maven test summary, every executed test by display name, and the coverage totals. These are explicitly executed tests, not a projected test count. Two named tests additionally exercise 500 seeded malformed token streams and 100 parallel analyses, respectively; those individual iterations are not counted as extra named tests.

```sh
./mvnw verify        # Windows: mvnw.cmd verify
```

The HTML coverage report is written to `target/site/jacoco/index.html`. Test classes mirror the main packages (`syntax/FrontendTest`, `semantic/SemanticAnalyzerTest`, `cli/CliTest`, …). Each test's `@DisplayName` states the behavior it checks.

## Coverage of behavior

| Area | Examples of checks |
| --- | --- |
| Lexing | Quotes, escapes, commas, comment markers in text, inline/block comments, invalid numbers, CRLF and CR positions |
| Parsing | Precedence, grouping, malformed statements, multiple syntax diagnostics, braces, nesting limits, illegal names |
| Semantic model | Exact declaration/reference bindings for shadowing, sibling scopes, parameters, comma declarations, self-initialization, writes, calls, recursion, duplicates, unresolved names; ranges across whitespace, comments, CRLF and supplementary characters; availability on failures; immutability; identity-keyed expression types; concurrent models |
| Semantic lint | W004–W006 positive and negative cases, write-only declarations, reads in unreachable code, suppression on semantic errors, API configuration, and CLI flag combinations including JSON and batches |
| Semantic regressions | Boolean assignment, self-initialization, redeclaration after assignment, local/global final preservation, multiple assignments |
| Scopes and flow | Shadowing, scope exit, conditional assignment, loop zero-iteration path, branch intersection, returning paths, method isolation |
| Method/type checks | Forward calls, recursion, argument types/counts, missing returns, compatible widening and incompatible narrowing |
| Extensibility | Custom rules, configurable complexity thresholds, warning suppression, immutable configuration/results |
| Isolation | Reuse after success/failure, no console output from core, 100 analyses using one facade across four threads |
| I/O and CLI | UTF-8/BOM, malformed encoding, unsupported extension, batch results, I/O precedence, usage errors, real process statuses |
| Reporting | JSON golden schema, control/surrogate escaping, clean stdout, stable diagnostic codes and positions |
| Desktop | Analysis on Swing worker, diagnostic navigation, export availability, edit invalidation, stale-result rejection, headless rendering |

The parser-recovery test initially caught a bug in the new implementation: when an invalid initializer had already consumed its semicolon, recovery skipped the next statement. Recovery now checks whether a boundary was already consumed while guaranteeing progress. The test remains in the suite.

## Additional delivered-artifact checks

The built executable JAR was invoked directly against valid, invalid, missing-file, and warning-gated examples. Exit codes were 0, 1, 2, and 1, respectively. The test-generated escaping fixture is written to `target/json-escaping.json` for independent parsing.

The build gates were checked in the failing direction too. A deliberately broken assertion made `./mvnw verify` fail in the test phase. Raising the line-coverage minimum to 99% made it fail at `jacoco:check`. Both changes were reverted.

The screenshot in the README is rendered from the actual Swing component with the errors example after analysis, not a conceptual mockup. Its headless appearance differs from native desktop look-and-feel themes.

## What has not been verified

- Native top-level windows and file dialogs on Windows/macOS.
- For hosted Windows/Linux and JDK 17/21 results, see [GitHub Actions](https://github.com/shayb1187-a11y/flowlens-java-analyzer/actions/workflows/ci.yml).
- Exact compatibility with an unavailable university specification or hidden grader.
- Full Java language conformance, runtime value evaluation, security-service hardening, or production workloads.
- A performance benchmark. No such number is claimed. The coverage percentage shows which code the tests executed, not that the code is correct.

The malformed-token test is a deterministic regression exercise, not a proof of parser robustness for every possible input.

## Continuous integration

`.github/workflows/ci.yml` runs `./mvnw -B verify` for pull requests, pushes, and manual runs on Ubuntu and Windows with JDK 17 and 21. That single command covers compilation, unit tests, packaging, the JAR integration test, and the coverage gate. It uses read-only repository permissions, does not persist checkout credentials, caches the Maven repository, and uploads the generated JAR and headless workbench image; the artifact step requires the files to exist. On failure it uploads the Surefire/Failsafe reports. The Ubuntu/JDK 21 leg writes line and branch coverage to the job summary and uploads the JaCoCo HTML report as the `coverage-report` artifact.

Action usage was checked against the official repositories: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), and [upload-artifact](https://github.com/actions/upload-artifact). Major tags are explicit, and Dependabot is configured for both Actions and Maven dependency updates. This does not substitute for running the workflow in the target repository.
