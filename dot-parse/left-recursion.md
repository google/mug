# Left Recursion Protection in dot-parse

## What is Left Recursion?

Left recursion occurs when a grammar rule references itself as its own leftmost
symbol. Consider a standard EBNF example representing a left-associative
subtraction:

```ebnf
expr = expr '-' number | number
```

This rule elegantly represents subtraction operations like `12 - 1 - 2` (which
evaluates to `9`). The rule is left-recursive because the leftmost symbol of
the first alternative of the `expr` rule is `expr` itself.

---

## Why is Left Recursion a Problem?

A traditional recursive descent parser translates grammar rules directly into
recursive method calls. When attempting to parse `expr` using the naive
left-recursive definition:

1. The parser enters `expr()`.
2. The first step of `expr()` is to invoke `expr()`.
3. This immediately invokes `expr()` again, without ever consuming a single
   character from the input stream.

This creates an infinite loop of recursive method calls at index 0, rapidly
exhausting the stack and throwing a `StackOverflowError` at runtime.

---

## How Bad is Left Recursion?

Direct left recursion (where a rule calls itself immediately) is relatively
easy to spot. However, in more realistic grammars, left recursion often
manifests in more insidious forms:

### 1. Indirect Left Recursion

This occurs when a rule recurses through a chain of intermediate rules (e.g.,
`A` calls `B`, which calls `C`, which eventually calls `A`). These loops can
be highly non-obvious in complex grammars.

### 2. Conditional Left Recursion

This is the most deceptive case. It occurs when the recursive call is not the
literal leftmost symbol, but all symbols to its left are optional or can
match "zero-width" (empty). For example:

```ebnf
expr = optionalAnnotation? expr | number
```

If the input does not contain an annotation, `optionalAnnotation?` succeeds
by matching empty, causing the parser to immediately recurse into `expr` at
index 0 without consuming input. This is exceptionally difficult to spot
visually and often evades unit tests unless they happen to execute on inputs
that trigger the empty-match path.

### The Debugging Pain

In traditional parser combinator libraries, a left-recursive loop results in a
catastrophic runtime crash. Because grammar rules are composed as objects,
the recursion goes through the internal methods of the combinator objects
themselves.

As a result, the JVM throws a `StackOverflowError` with a stack trace composed
entirely of anonymous framework-internal frames, offering **no clues** about
which specific user rule (e.g., `expr` vs. `term` vs. `factor`) is the culprit:

```
java.lang.StackOverflowError
    at org.jparsec.Parsers$13.apply(Parsers.java:387)
    at org.jparsec.Parsers$20.apply(Parsers.java:709)
    at org.jparsec.Parser$Reference$1.apply(Parser.java:116)
    at org.jparsec.Parsers$13.apply(Parsers.java:387)
    at org.jparsec.Parsers$20.apply(Parsers.java:709)
    at org.jparsec.Parser$Reference$1.apply(Parser.java:116)
    ... [hundreds of identical frames truncated] ...
```

---

## How to Avoid Left Recursion

To avoid manual left recursion, modern parser libraries provide declarative
primitives to express left-associative operators.

In `dot-parse`, you can express left-associative grammars cleanly using the
`OperatorTable` class:

```java
Parser<Integer> expr = new OperatorTable<Integer>()
    .leftAssociative("-", (a, b) -> a - b, 1)
    .leftAssociative("+", (a, b) -> a + b, 1)
    .leftAssociative("*", (a, b) -> a * b, 2)
    .leftAssociative("/", (a, b) -> a / b, 2)
    .prefix("-", a -> -a)
    .build(Parser.digits().map(Integer::parseInt));
```

Besides eliminating left recursion, `OperatorTable` handles operator precedence
automatically, removing the need to construct a tedious, multi-layered
"precedence ladder" (e.g., separating expression, term, and factor rules).

### Packrat Parsing and its Costs

Some frameworks (such as `petitparser`) employ **Packrat Parsing** (memoized PEG)
to handle left-recursive grammars at runtime without stack overflows. However,
Packrat parsing is not a free solution:

*   **Memory Overhead**: It requires maintaining a large cache of
    `(rule, position)` pairs to memoize intermediate results, leading to a
    massive memory footprint.
*   **Runtime Overhead**: Querying and updating the memoization cache on every
    single parsing step adds a significant runtime performance tax.

For these reasons, high-performance libraries generally avoid the Packrat
model.

---

## How to Detect Left Recursion?

Detecting left recursion requires tracking whether a grammar rule is
re-entered at the exact same input index. The key differentiator among
parser libraries is **when** this detection occurs:

### 1. Runtime (Parsing-Time) Detection

If a library only detects left recursion during active parsing, the developer
still receives a stack trace with no indication of the culprit grammar
rule(s) or definition line:

*   **Throwing an Exception**: If the library throws an exception at runtime,
    it is only marginally better than a standard `StackOverflowError` because
    it still fails to point to the line where the rule was defined.
*   **Returning a Soft Failure (`NoMatch`)**: Some frameworks opt to return a
    soft matching failure, allowing the parser to backtrack and try alternative
    paths. This introduces a dangerous **silent mis-parsing hazard**.

Consider the naive EBNF subtraction rule implemented as a
runtime-backtracking combinator:

```java
Parser<Integer> expr = Parser.define(me ->
    anyOf(
        sequence(me.followedBy("-"), number, (left, right) -> left - right),
        number
    )
);
```

When parsing the input `12-1-2`:

1. The parser enters the `sequence(me, ...)` branch.
2. The runtime recursion detector intercepts the immediate re-entrance and
   returns a soft `NoMatch` failure.
3. The parser backtracks and tries the second alternative: `number`.
4. `number` successfully matches and consumes `12`.
5. The parse completes successfully, returning `12` and leaving `-1-2`
   completely unconsumed in the input stream.

This silently diverges from EBNF semantics, leading to surprising
behavior without throwing any warning or error.

### 2. Build-Time Detection

Parser generators like ANTLRv4 do a superb job by performing static analysis of
the grammar at **build time**. ANTLR automatically rewrites left-recursive
rules into iterative loops under the hood, making them "just work."

---

## Dot-Parse: Definition-Time Detection

As a plain Java library, `dot-parse` operates without a build-time compiler
or macro system. However, it offers a unique, compile-free alternative:
**Definition-Time Left Recursion Detection**.

When you define a recursive parser using `Parser.define()`, `dot-parse`
validates the grammar structure **during class initialization (at JVM startup
time)** before parsing any input. If a left-recursive loop (direct, indirect,
or conditional) exists, the library immediately throws an `IllegalStateException`
during initialization, with a stack trace pointing **directly** to the exact
line of code where the developer defined the recursive parser:

(This means that when parsers are defined as `static final` constants—the
standard pattern in Java—any left-recursion bug is caught immediately at
class-loading time, before the application even begins handling requests.)

```
java.lang.IllegalStateException: Left recursion not supported! Consider using withPostfixes() or the OperatorTable class to define the left recursive grammar.
	at com.google.common.labs.parse.Utils.checkState(Utils.java:35)
	at com.google.common.labs.parse.Parser$Rule.skipAndMatch(Parser.java:2070)
	at com.google.common.labs.parse.Parser$11.skipAndMatch(Parser.java:625)
	at com.google.common.labs.parse.OrParser.skipAndMatch(OrParser.java:63)
	at com.google.common.labs.parse.Parser.match(Parser.java:2140)
	at com.google.common.labs.parse.Parser.matches(Parser.java:1550)
	at com.google.common.labs.parse.Parser$Rule.definedAs(Parser.java:2088)
	at com.google.common.labs.parse.Parser.define(Parser.java:2039)
	at com.google.mu.benchmarks.parsers.dotparse.LeftRecursionCulprit.main(LeftRecursionCulprit.java:27)
```

The stack trace is usually shallow and the bottom of the stack trace
(`LeftRecursionCulprit.java:27`) points directly to the `Parser.define()` call
in your code, making debugging instantaneous.

---

## How Does dot-parse Detect Left Recursion?

`dot-parse` achieves reliable, definition-time left recursion detection (with
zero false positives or false negatives) by leveraging its strongly-typed API
design.

The library divides all rules into two distinct types:

1.  **`Parser<T>`**: Represents a rule that is **guaranteed to consume at least
    one character** to succeed.
2.  **`Parser<T>.OrEmpty`**: Represents a rule that **cannot fail** (if it does
    not match, it succeeds by matching empty / zero-width).

### The Dry-Run Validation Mechanics

When `Parser.define(me -> ...)` is called to construct a recursive parser,
`dot-parse` performs a **dry-run** by executing the grammar against an empty
string: `me.matches("")`.

The framework uses the two type invariants to evaluate the path in $O(1)$ time:

#### Scenario A: Safe Recursion (Non-Left-Recursive)

If the recursive call `me` is preceded by a standard rule (e.g.,
`string("-").then(me)`), the preceding rule is of type `Parser<T>`.

*   Because `Parser<T>` must consume input to succeed, it is guaranteed to
    fail immediately when matching against the empty string `""`.
*   This failure short-circuits the evaluation path, and the recursive call
    `me` is never reached during the dry-run.

#### Scenario B: Direct Left Recursion

If the recursive call `me` is at the leftmost position, the dry-run
immediately enters the recursive call at index 0. `dot-parse` detects this
re-entrance of the same rule at index 0 and immediately throws the
`IllegalStateException`.

(Note that **Indirect Left Recursion** is caught in exactly the same manner:
the dry-run call chain propagates through the intermediate zero-width rules
at index 0 until the initial rule is re-entered, triggering the exception.)

#### Scenario C: Conditional Left Recursion (The Tricky Case)

If the recursive call `me` is preceded by optional or zero-width rules
(e.g., `optionalAnnotation.then(me)`), these preceding rules are of type
`Parser<T>.OrEmpty`.

*   Because `OrEmpty` rules cannot fail on the empty string `""`, they are
    guaranteed to succeed without consuming input.
*   The dry-run passes through them and reaches the recursive call `me`
    at index 0, immediately triggering the left-recursion exception.

By dividing rules into these two types, `dot-parse` can validate complex
grammars for left recursion at startup almost for free, without needing to
perform heavy static analysis or AST scanning.

## In a Nutshell

The left-recursion protection, alongside the compile-time elimination of
infinite loops, makes `dot-parse` free of the notorious footguns seen in other
combinator frameworks. Developers can focus on expressing grammars in
`dot-parse`'s idiomatic and fluent Java API without burning long hours in
debugging.
