# Language specification: FlowLens dialect 1

The dialect intentionally differs from both full Java and the uploaded academic verifier. Source files use `.sjava` and UTF-8. A leading UTF-8 BOM is accepted by the file adapter. Files are independent compilation units.

## Grammar

The EBNF below is the authoritative supported subset. Braces around a production mean repetition; quoted braces are actual tokens.

```ebnf
program       = { declaration | assignment | method } ;
method        = "void", methodName, "(", [ parameters ], ")", block ;
parameters    = parameter, { ",", parameter } ;
parameter     = [ "final" ], type, variableName ;
block         = "{", { statement }, "}" ;
statement     = declaration | assignment | call | ifStatement
              | whileStatement | "return", ";" ;
declaration   = [ "final" ], type, binding, { ",", binding }, ";" ;
binding       = variableName, [ "=", expression ] ;
assignment    = write, { ",", write }, ";" ;
write         = variableName, "=", expression ;
call          = methodName, "(", [ expression, { ",", expression } ], ")", ";" ;
ifStatement   = "if", "(", expression, ")", block, [ "else", block ] ;
whileStatement= "while", "(", expression, ")", block ;
expression    = conjunction, { "||", conjunction } ;
conjunction   = unary, { "&&", unary } ;
unary         = "!", unary | "(", expression, ")" | literal | variableName ;
type          = "int" | "double" | "boolean" | "char" | "String" ;
```

Logical expressions are allowed in initializers and arguments as well as conditions. Calls are statements only. Braces are mandatory for control bodies; `else if` is written as a nested `if` inside an `else` block.

Names use ASCII letters, digits and underscores. Method names start with a letter. Variable names start with a letter or an underscore followed by a letter or digit. `_` and `__x` are invalid; `_1` is valid. Language keywords cannot be identifiers. Method and variable names occupy separate namespaces. Method overloading is unsupported.

## Lexing

- Whitespace may separate tokens, and statements may span lines or share a line.
- `//` comments end at the newline; non-nested `/* ... */` comments are supported.
- Comment markers and punctuation inside strings and chars are ordinary literal content.
- Integers are optionally signed sequences of ASCII digits. Doubles contain a decimal point, with digits on at least one side. Examples: `1`, `-2`, `+.5`, `2.`, `-3.25`. Exponents, suffixes, numeric separators, and whitespace between sign and digits are unsupported.
- Strings use double quotes and cannot contain literal newlines. Chars use single quotes and contain one UTF-16 code unit or a supported escape.
- Supported escapes are `\n`, `\r`, `\t`, `\\`, `\"`, and `\'`. Unicode escape syntax is unsupported.
- Integer ranges, floating overflow, and numeric values are not evaluated. Literal spelling determines its type.
- Positions use one-based lines and UTF-16 columns, and zero-based UTF-16 offsets. CRLF counts as one newline. Tabs occupy one column in diagnostics.

## Types and scope

| Target type | Accepted source types |
| --- | --- |
| `int` | `int` |
| `double` | `int`, `double` |
| `boolean` | `boolean`, `int`, `double` |
| `char` | `char` |
| `String` | `String` |

Numeric-to-boolean compatibility is inherited from the s-Java concept; it is not Java's rule. A condition or logical operand must be `boolean`, `int`, or `double`. Logical operators produce `boolean`; `!` has higher precedence than `&&`, which has higher precedence than `||`.

Declarations within one comma-separated declaration and writes within a comma-separated assignment are processed left to right. A variable is in scope during its initializer but is not initialized yet, so `int x = x;` is invalid even if a global `x` exists. Redeclaration within the same scope is invalid. Nested scopes may shadow enclosing names. Method parameters and the method's immediate body share one scope.

Final variables require an initializer at declaration and cannot be reassigned. Final parameters begin initialized and also cannot be reassigned. This dialect does not support Java's delayed assignment of blank final variables.

Global statements are processed in source order. All methods are registered before any bodies are checked; forward calls and recursion are valid. Method bodies see the completed global environment, including globals written later in the file. Each method receives an independent initialization state. A call does not propagate the callee's side effects.

## Definite assignment and returns

Every read must refer to a variable initialized on every continuing path. Both branches of an `if/else` must establish a fact for it to survive the merge, except that a branch which unconditionally returns does not reach the merge. An `if` without `else` includes an unchanged path. A `while` may execute zero times, so assignments inside it do not initialize an outer variable after the loop. Constant conditions are not folded.

Every method must explicitly return on every continuing path. A final top-level `return;` is sufficient, as is an `if/else` whose two branches return. `return;` inside a loop alone is insufficient. Statements after an unconditional return get a lint warning, but still receive semantic validation. Warnings alone do not invalidate source.

## Diagnostic codes

| Code | Meaning |
| --- | --- |
| `E001` | Invalid token, quoted literal, escape, or comment |
| `E002` | Grammar error or parser nesting/complexity limit |
| `E101` | Duplicate variable, parameter, or method |
| `E102` | Unknown variable |
| `E103` | Possibly uninitialized variable |
| `E104` | Incompatible assignment or argument type |
| `E105` | Final variable without initializer |
| `E106` | Reassignment of final variable |
| `E107` | Unknown method |
| `E108` | Wrong number of call arguments |
| `E109` | Method may complete without explicit return |
| `E110` | Invalid condition or logical operand type |
| `E900` | Source exceeds the character limit |
| `EIO` | CLI file access, encoding, extension, or byte-limit failure |
| `W001` | Method name is not lowerCamelCase |
| `W002` | Complexity above 7 or control nesting above 3 by default |
| `W003` | Unreachable statement |

Lexical failures stop the pipeline before parsing. Parser failures recover to collect additional syntax errors, then stop before semantics and metrics to avoid misleading reports about an incomplete tree. Semantically invalid but syntactically valid programs still receive metrics and lint results.

## Metrics

- **Statements:** every declaration statement, assignment statement, call, `if`, `while`, and `return`, including nested and unreachable statements. Multiple declarators in one statement count once.
- **Cyclomatic complexity:** 1 plus each `if`, each `while`, and each `&&` or `||` node anywhere in the method. `else` and `!` add nothing. This is a documented syntactic metric, not an inferred runtime path count.
- **Maximum nesting:** maximum depth of `if` and `while` constructs. A method with no control statements has depth zero. An `else` is at the same control depth as its `if`.

## Compatibility differences

| Original uploaded implementation | FlowLens dialect |
| --- | --- |
| Line-oriented regex parsing | Tokens; line breaks do not define statements |
| Comments restricted to unindented full lines | Indented, inline, and block comments supported |
| Commas and equals split by string operations | Delimiters inside quotes are preserved |
| `if`, `while` | Adds `else`, grouped logical expressions, and `!` |
| Return required as final source statement | Explicit return required on every continuing path; unreachable code is a warning |
| Code result printed as `0`, `1`, or `2` | Useful reports plus actual process exit codes |
| Existing `ex5.main.Sjavac` entry point | `dev.sjavainspector.cli.Main` and an executable JAR |

The original grading specification is not available. This file defines the portfolio edition rather than claiming exact assignment compatibility.
