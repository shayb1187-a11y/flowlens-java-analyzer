# Review of the uploaded project

## What was already useful

The original `ex5` code had a clear entry point, explicit error categories, nested symbol lookup, and a two-pass approach for forward method references. Those are sound foundations for a language verifier. The portfolio edition retains the problem domain and the idea of registering signatures before checking bodies.

The uploaded archive contained Java sources, a README, and a UML PDF; no tests, build configuration, or course grading specification were provided. Review findings below are grounded in the source and direct executions of the original Java code, not in assumptions about a hidden grader. The design was reviewed from the source and README; the old UML is not presented as a diagram of this edition.

## Reproduced behavior

The original sources were compiled and run unchanged on OpenJDK 17. Each input used the line-oriented formatting accepted by the original program. Full inputs and captured stdout/stderr are in [original-reproduction.json](original-reproduction.json).

| Case | Original output | Portfolio result | Finding |
| --- | --- | --- | --- |
| Declare a boolean, assign `true`, then test it | `1`: uninitialized variable | Valid | `doAssign` returns early for boolean literals without recording initialization |
| `int x = x;` | `0`: accepted | `E103` | The original stores the initializer spelling as the variable's value before checking its source |
| Declare, assign, then redeclare `x` in one local scope | `0`: accepted | `E101` | Assignment replaces the symbol with one marked as not locally declared |
| Declare local `final int x = 1;`, then assign `x = 2;` | `0`: accepted | `E106` | Initialization replaces the local symbol and drops its final flag |
| `x = 1, y = 2;` | `1`: type mismatch | Valid | Regex accepts the shape, but assignment execution splits it incorrectly |
| String containing a comma | `1`: unrecognized statement | Valid | Regex/string splitting couples literal content to statement delimiters |
| String containing `/* literal */` | `1`: block comments not allowed | Valid | Comment detection examines raw text without recognizing quotes |
| Read a final parameter | `0`: accepted | Valid | Control case: parameter initialization behavior was retained |

The last two rejected inputs also motivate explicit dialect documentation: the original specification is unavailable, so the portfolio language deliberately defines quoted punctuation and comment handling instead of claiming grader equivalence.

The original executable printed `1` for source errors but still returned process status `0`. That can be appropriate for a course output protocol; it is unhelpful for shell automation. The portfolio CLI uses real exit statuses and structured reports.

## Structural issues and changes

| Original structure | Consequence | Portfolio change |
| --- | --- | --- |
| Static mutable `SourceAnalyzer.symbols` | Analysis invocations share state; unsuitable for independent concurrent calls | Fresh semantic state per facade invocation |
| `LineParser` performs syntax checks and symbol mutations | Parsing and semantic behavior cannot evolve or be tested independently | Lexer → parser → immutable AST → semantic visitor |
| `SymbolTable` checks types, stores values, manages methods and prints errors | Multiple responsibilities and output coupled to domain logic | Separate scopes, declaration identities, flow facts, type rules and diagnostics |
| String values represent initialization | A source variable name can be mistaken for an established value | Definite assignment is an explicit set of declaration identities |
| Symbol replacement on assignment | Declaration identity and final modifiers are lost | Symbols are immutable; assignment modifies flow facts only |
| Mutable parameter list exposed by `MethodInfo` | Callers can change method metadata indirectly | AST and report collections are defensively copied |
| Regex repetition for every declaration type | Difficult quoting behavior and repeated grammar rules | One declaration grammar plus an enum and centralized compatibility policy |
| No supplied regression suite | Correctness claims are hard to substantiate | Behavioral cases cover original defects and documented extensions |

## Scale of the change

This is a substantial redesign, not a cosmetic wrapper around the submitted classes. The old regex engine was replaced because adding more UI and patterns around it would preserve the demonstrated state and parsing defects. The new code has one analysis engine shared by two front ends, with immutable contracts and explicit extension points.

The original archive is not embedded in the public-facing project; its university identifier and submission metadata do not belong in a portfolio README. The new documentation explains the academic origin, demonstrated changes, and verification limits.
