# Presenting and understanding this project

## A concise description

“FlowLens is a static-analysis tool for a small Java-like language. I started from an academic verifier and developed a compiler-style front end, an immutable AST, and separate analysis visitors. Its most interesting feature is tracking whether variables are initialized on every continuing path. The same engine runs behind a CLI and a desktop workbench.”

Use this description after you have read, run, and understood the implementation. Describe the academic baseline and the subsequent assisted refactor honestly; do not imply the original submission already contained every feature.

## Five-minute demonstration

1. **Show the product.** Run `java -jar build/flowlens.jar gui`. Analyze the default valid example. Explain that the program checks source without executing it.
2. **Find a real defect.** Load `errors.sjava`, analyze it, and select the first diagnostic. `retries` is assigned only if the branch executes. Add `else { retries = 1; }`, analyze again, and watch that diagnostic disappear while the others remain.
3. **Show assignment identity.** Explain why `int self = self;` is invalid and why assigning a variable does not allow declaring it again. Open `Scope`, `VariableSymbol`, and `FlowState` if asked.
4. **Show an extension seam.** Open `AnalysisRule`, `MethodNamingRule`, and the custom-rule example in `ARCHITECTURE.md`. A team rule can be added without modifying grammar or semantics.
5. **Show automation.** Run `java Build.java test`, then `java -jar build/flowlens.jar check --format=json examples/errors.sjava`. Explain the actual process exit codes and the CI configuration.

The `errors.sjava` example also demonstrates final-variable reassignment, self-initialization, duplicate declarations, and unresolved calls. `branching.sjava` demonstrates both an intersection merge and exclusion of a returning path. `lint.sjava` demonstrates valid-but-hard-to-maintain source.

## Questions to be ready for

| Question | What a strong answer should cover |
| --- | --- |
| Why replace the regular expressions? | Regex is useful for simple tokens; an explicit grammar handles quoting, nested structure, precedence and source positions more clearly |
| Why Visitor? | Several operations run over a mostly stable set of node types; double dispatch separates syntax from analysis; adding node types is the tradeoff |
| Why are `Scope` and `FlowState` separate? | A declaration has a stable identity, type and final flag; initialization depends on the path through the program |
| How do branches merge? | Intersect facts from continuing paths; discard a returning path; an absent else carries incoming facts |
| Why do loop assignments not initialize variables afterward? | Without proving an iteration occurs, the zero-iteration path must be considered |
| Why does each method get a separate state? | Analyzing one method must not mutate the initial state of another, and the analyzer does not infer callee side effects |
| Why interfaces for reports and sources? | Concrete alternatives exist: text/JSON output and file/in-memory input. They are useful substitution points |
| Is this full Java? | No: name the supported types and control statements, and explicitly exclude expressions/calls/features the dialect does not implement |
| Is it thread-safe? | The facade owns immutable configuration and creates fresh per-run state. Built-in rules are stateless; custom rules must honor that contract |
| What did tests actually prove? | Cite the regression cases and integration checks. 113 named tests is not a coverage percentage or proof of correctness |

## Read the code in this order

1. `api/Analyzer.java`: the whole pipeline in one small class.
2. `syntax/Ast.java` and `syntax/TreeWalker.java`: Composite and Visitor.
3. `semantic/Scope.java`, `VariableSymbol.java`, and `FlowState.java`: ownership and data flow.
4. `semantic/SemanticAnalyzer.java`: trace one declaration, assignment, branch, and call.
5. `lint/MethodNamingRule.java` and `report/ReportFormatter.java`: extension points.
6. `cli/Cli.java`: dependency injection and separation from `System.exit`.
7. `ui/WorkbenchPanel.java`: background work and stale-result protection.
8. The tests corresponding to the behavior you just read.

Useful exercises: manually trace initialization sets through `branching.sjava`; write a rule warning about too many parameters; add a test for it; explain why a newly added arithmetic node would require changes to more than one visitor. These tasks help establish ownership of the design.

## A possible CV bullet

> Developed a Java static-analysis tool with an immutable AST, Visitor-based semantic analysis, flow-sensitive initialization checks, configurable lint rules, and CLI/Swing interfaces; validated behavior with 113 automated tests.

Only use features and wording you can comfortably explain. Do not list tools that are absent: this version does not use Spring, Maven, Gradle, JUnit, a database, or an LLM at runtime.

## Repository and demo

- Repository: [flowlens-java-analyzer](https://github.com/shayb1187-a11y/flowlens-java-analyzer).
- Run `java Build.java test` from the repository root to compile, verify, and package the app.
- Download the executable JAR from [Releases](https://github.com/shayb1187-a11y/flowlens-java-analyzer/releases); generated build output stays out of source control.
- Check [GitHub Actions](https://github.com/shayb1187-a11y/flowlens-java-analyzer/actions) for hosted verification results.
- Use the repository link on a CV alongside the demonstration above.

No open-source license has been assigned to this repository.

## Sensible next improvements

First use feedback from running the tool. If a concrete next feature is wanted, consider an AST outline view, a SARIF formatter, or migrating the existing checks to the test framework used by your target team. A larger language should add expression and control-flow features with a grammar change, documented semantics and targeted tests.

A database, web service, dependency-injection framework, or additional design pattern would not improve this project by itself. The strongest discussion is why the current boundaries solve actual problems.
