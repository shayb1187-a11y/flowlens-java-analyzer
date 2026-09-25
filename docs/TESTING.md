# Verification record

Portfolio verification date: **2026-09-25**.

## Observed local result

**113 named tests passed, zero failed**, using Temurin OpenJDK **17.0.19** on Windows. Both production and test sources compile with `--release 17 -Xlint:all -Werror`.

The complete runner output is included in [verification.log](verification.log). These are explicitly executed tests, not a projected test count. Two named tests additionally exercise 500 seeded malformed token streams and 100 parallel analyses, respectively; those individual iterations are not counted as extra named tests.

```sh
java Build.java test
```

## Coverage of behavior

| Area | Examples of checks |
| --- | --- |
| Lexing | Quotes, escapes, commas, comment markers in text, inline/block comments, invalid numbers, CRLF and CR positions |
| Parsing | Precedence, grouping, malformed statements, multiple syntax diagnostics, braces, nesting limits, illegal names |
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

The built executable JAR was invoked directly against valid, invalid, missing-file, and warning-gated examples. Exit codes were 0, 1, 2, and 1, respectively. JSON output and the test-generated escaping fixture were parsed independently with PowerShell's JSON parser.

The screenshot in the README is rendered from the actual Swing component with the errors example after analysis, not a conceptual mockup. Its headless appearance differs from native desktop look-and-feel themes.

## What has not been verified

- Native top-level windows and file dialogs on Windows/macOS.
- For hosted Windows/Linux and JDK 17/21 results, see [GitHub Actions](https://github.com/shayb1187-a11y/flowlens-java-analyzer/actions/workflows/ci.yml).
- Exact compatibility with an unavailable university specification or hidden grader.
- Full Java language conformance, runtime value evaluation, security-service hardening, or production workloads.
- A code-coverage percentage or performance benchmark. No such number is claimed.

The malformed-token test is a deterministic regression exercise, not a proof of parser robustness for every possible input.

## Continuous integration

`.github/workflows/ci.yml` compiles and executes this same test suite for pull requests, pushes, and manual runs. It uses read-only repository permissions, does not persist checkout credentials, and uploads the generated JAR and headless workbench image. The artifact step requires files to exist.

Action usage was checked against the official repositories: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), and [upload-artifact](https://github.com/actions/upload-artifact). Major tags are explicit, and Dependabot configuration is included for Actions updates. This does not substitute for running the workflow in the target repository.
