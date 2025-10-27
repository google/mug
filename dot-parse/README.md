# Mug *dot parse*

Small, safe-by-construction parser combinators for Java.

- **Extremely small footprint:** ~**1000 LOC** end-to-end — roughly **1/6 jparsec**.
- **Easy to use:** a handful of primitives; you can read the code and “just write the grammar”.
- **Hard to misuse:** free of two classic footguns (spin loops from `many/optional`, and sneaky left recursion).

---

## API sketch (tiny on purpose)

- **Primitives:** `string("if")`, `consecutive(Character::isWhitespace)`, `single(range('0', '9'))`
- **Compose:** `.thenReturn(true)`, `.followedBy("else")`, `.between("[", "]")`, `.map(Literal::new)`
- **Alternative:** `p1.or(p2)`, `anyOf(p1, p2)`
- **Sequence:** `then()`, `sequence()`, `atLeastOnce()`, `atLeastOnceDelimitedBy()`
- **Optional:** `optionallyFollowedBy()`, `orElse(defaultValue)`, `zeroOrMore()`, `zeroOrMoreDelimitedBy(",")`
- **Operator Precedence:** `OperatorTable<T>` (`prefix()`, `leftAssociative()`, `build()`, etc.)
- **Recursive:** `Parser.define()`, `Parser.Rule<T>`
- **Whitespace:** `parser.parseSkipping(Character::isWhitespace, input)`
- **Lazy Parsing:** `parseToStream(Reader)`, `probe(Reader)`.

That’s essentially the whole surface.

---

## Whitespace — one switch at the entry point

No lexeme ceremony. Turn on skipping at `parse()` time:

```java
var result = Parsers.parseSkipping(Character::isWhitespace, input);
//                       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// Skips all whitespaces in between
```

Keeps grammars clean and reusable. For quoted strings that need to include literal whitespaces,
use `.immediatelyBetween("\"",  "\"")`, which will suppress the whigtespace skipping between the quotes.

---

## Example 1 — Calculator (OperatorTable)

Goal: support `+ - * /`, factorial (`!`), unary negative, parentheses, and whitespace.

```java
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.mu.util.CharPredicate.range;

Parser<Integer> calculator() {
  Parser<Integer> number =
      consecutive(range('0', '9')).map(Integer::parseInt);
  return Parser.define(rule ->
      new OperatorTable<Integer>()
	      .leftAssociative('+', (a,b) -> a + b, 10)           // a+b
	      .leftAssociative('-', (a,b) -> a - b, 10)           // a-b
	      .leftAssociative('*', (a,b) -> a * b, 20)           // a*b
	      .leftAssociative('/', (a,b) -> a / b, 20)           // a/b
	      .prefix('-', i -> -i, 30)                           // -a
	      .postfix('!', i -> factorial(i), 40)                // a!
	      .build(number.or(rule.between("(", ")"))));
}

// Run (whitespace is handled globally):
int v = calculator()
    .parseSkipping(Character::isWhitespace, " -1 + 2 * (3 + 4!) / 5 ");
```

**Why this stays simple**

- `OperatorTable` takes care of infix, infix and postfix with precedences.
- No per-token lexeme. The entry call `parseSkipping(...)` takes care of space everywhere.

---

## Example 2 — Split Json Records

Most JSON parsers can parse a single JSON object enclosed in curly braces `{}`,
a json array ecnlosed by square brackets `[]`, or jsonl files with each JSON record
at a single line.

But what if you need to read a file that may contain a single JSON record, or a list of them,
pretty-printed with indentations and newlines for human readability?

What you need is to be able to split the JSON records one-by-one by matching the top level curly braces.

Since curly braces can appear in quoted string literals, you will also need to recognize double quotes,
as well as escaped double quotes (which are not to start or terminate a string literal).

The following code splits the JSON records so you can feed them to GSON (or any other JSON parser of choice):

```java
import static com.google.common.labs.parse.Parser.*;
import static com.google.mu.util.CharPredicate.noneOf;

/** Splits input into a lazy stream of top-level JSON records. */
Stream<String> jsonStringsFrom(Reader input) {
  // Either escaped or unescaped, enclosed between double quotes
  Parser<?> stringLiteral =
    anyOf(
            consecutive(noneOf("\"\\"), "quoted chars"),
            string("\\").followedBy(single(any(), "escaped char")))
        .zeroOrMore()
        .immediatelyBetween("\"", "\"");
  
  // Outside of string literal, any non-quote, non-brace characters are passed through
  Parser<?> passThrough = consecutive(noneOf("\"{}"), "pass through");

  // Between curly braces, you can have string literals, nested JSON records, or passthrough chars
  // For nested curly braces, you need forward declaration to define recursive grammar
  Parser<Object> jsonRecord =
      Parser.define(rule ->
	      anyOf(quoted, rule, passThrough)
	          .zeroOrMore()
	          .between("{", "}"));

  return jsonRecord.source()             // take the source of the matched JSON record
      .skipping(Character::isWhitespace) // allow whitespaces for indentation and newline
      .parseToStream(input);
}
```

---

## No More Infinite Loop (as in many combinator libraries)

The infinite loop bug happens when a repeating parser succeeds without moving forward.
It’s like a machine that says "Job done!" but never actually takes the next item off the conveyor belt,
so it gets stuck doing the same thing forever.

Imagine you are writing a parser to parse CSV input: lines of comma-separated texts.

At first, you'll write:
```java
Parser<List<Row>> csv = row.delimitedBy(newline) ✅
```

You parse rows separated by newlines using . Works great.

But what about empty lines? We should allow them, right?

```java
Parser<List<Row>> csv = row.orElse(EMPTY_ROW)
    .delimitedBy(newline) ✅
```

Still safe, because the required newline forces the parser to move forward.

Next day, you realize that some csv inputs may or may not have the newline character at the last line.
So the `delimitedBy()` would not consume the last newline character.

You're like: "easy, I'll just replace `delimitedBy()` with `zeroOrMore()`, and make the newline an optional suffix":

```java
Parser<List<Row>> csv = row.orElse(EMPTY_ROW)
    .followedBy(newline.optional())  💣
    .zeroOrMore();                   💣
```

And you run `parse(input)`. The program hangs!

At the end of the file, `row.orElse()` succeeds (by finding nothing) and `newline.optional()` also succeeds (by finding nothing).
The combined parser succeeds but consumes zero characters. Then `zeroOrMore()` loop sees this success and happily tries again on the exact same spot... forever.

### The Guardrail: How Dot Parse Uses Static Types To Help 🛡
️
Mug's Dot Parse library uses the type system to prevent you from ever falling into this trap. The bug becomes a compile-time error.

When you write `row.orElse(EMPTY_ROW)`, you don't get back a first-class Parser.
It returns a special `Parser<Row>.OrEmpty` type that doesn't have dangerous methods like `zeroOrMore()`.
The compiler stops you cold.

This forces you to define the grammar rule to be always consuming:

```java {.good}
// This is the only way the types let you write it
Parser<List<Row>>.OrEmpty parser =
    row.orElse(EMPTY_ROW)                // Row can be empty
        .followedBy(newline.optional())  // Trailing new line can be optional
        .notEmpty()                      // But you gotta have at least one  ✅
        .zeroOrMore();                   // It's a Parser again, and safe in a loop
```
If your code compiles, `zeroOrMore()` can never loop infinitely.

Similarly, you can never run into **accidental left recursion** (which causes `StackOverflowError`).

## Footprint

- About **1000 lines of Java** (including `OperatorTable`).
- Besides Mug core, no other dependencies.

---

## Install

```xml
<dependency>
  <groupId>com.google.mug</groupId>
  <artifactId>dot-parse</artifactId>
  <version>9.3</version>
</dependency>
```

---

## License

MIT.
