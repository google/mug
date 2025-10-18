# Mug *dot parse*

Small, safe-by-construction parser combinators for Java.

- **Extremely small footprint:** ~**800 LOC** end-to-end — roughly **1/6 jparsec**.
- **Small API, low learning curve:** a handful of primitives; you can read the code and “just write the grammar”.
- **Safe by construction:** free of two classic footguns (spin loops from `many/optional`, and sneaky left recursion).

---

## API sketch (tiny on purpose)

- **Primitives:** `string("if")`, `consecutive(Character::isWhitespace)`, `single(range('0', '9'))`
- **Compose:** `.thenReturn(true)`, `.followedBy("else")`, `.between("[", "]")`, `.map(Literal::new)`
- **Alternative:** `p1.or(p2)`, `anyOf(p1, p2)`
- **Sequence:** `then()`, `sequence()`, `atLeastOnce()`, `atLeastOnceDelimitedBy()`
- **Optional:** `optionallyFollowedBy()`, `orElse(defaultValue)`, `zeroOrMore()`, `zeroOrMoreDelimitedBy(",")`
- **Operator Precedence:** `OperatorTable<T>` (`prefix()`, `leftAssociative()`, `build()`, etc.)
- **Recursive:** `Parser.Rule<T>`
- **Whitespace:** `parser.parseSkipping(Character::isWhitespace, input)`

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
  Parser.Rule<Integer> rule = new Parser.Rule<>(); // forward reference
  Parser<Integer> atom = number.or(rule.between("(", ")");
  Parser<Integer> parser = 
      new OperatorTable<Integer>()
	      .leftAssociative('+', (a,b) -> a + b, 10)           // a+b
	      .leftAssociative('-', (a,b) -> a - b, 10)           // a-b
	      .leftAssociative('*', (a,b) -> a * b, 20)           // a*b
	      .leftAssociative('/', (a,b) -> a / b, 20)           // a/b
	      .prefix('-', i -> -i, 30)                           // -a
	      .postfix('!', i -> factorial(i), 40)                // a!
	      .build(atom);
  return rule.definedAs(parser);
}

// Run (whitespace is handled globally):
int v = calculator()
    .parseSkipping(Character::isWhitespace, " -1 + 2 * (3 + 4!) / 5 ");
```

**Why this stays simple**

- `OperatorTable` takes care of infix, infix and postfix with precedences.
- No per-token lexeme. The entry call `parseSkipping(...)` takes care of space everywhere.

---

## Example 2 — Parse a `Map<String, ?>`

Let's try a more realistic example: parse a tiny JSON-ish subset.

```java
import static com.google.common.labs.parse.Parser.*;
import static com.google.mu.util.CharPredicate.WORD;
import com.google.mu.util.Both;

// Parses a JSON-ish Map<String, ?> supporting:
//   - bare word keys (letters/digits/_),
//   - single-quoted string values,
//   - [list, of, strings],
//   - nested { maps }.
// Whitespace is handled globally via parseSkipping(…).

static Map<String, ?> parseMap(String input) {
  // key := [A-Za-z0-9_]+
  // consecutive(WORD, "key") greedy-consumes ≥1 word char; label "key" only shows up in errors.
  Parser<String> key = consecutive(WORD, "key");

  // value := '...' (single-quoted; no escaping shown here)
  // zeroOrMore(predicate) returns an OrEmpty — by itself it may succeed empty.
  // The immediatelyBetween("'", "'")* frame is what enforces consumption: the quotes themselves
  // must match and consume, turning the whole production into a progress-making success.
  Parser<String> value =
      zeroOrMore(c -> c != '\'', "value")    // body: may be empty (OrEmpty)
          .immediatelyBetween("'", "'")      // frame: guarantees net consumption on success

  // values := '[' value (',' value)* ']'
  // zeroOrMoreDelimitedBy returns empty on 0 items; again, the [ ] frame is what ensures progress.
  Parser<List<String>> values =
      value
          .zeroOrMoreDelimitedBy(",")                  // possibly empty sequence
          .between("[", "]");                          // bracket frame consumes

  // Forward-declared nested map: nested := { pair (',' pair)* }
  Parser.Rule<Map<String, ?>> nested = new Parser.Rule<>();

  // pair := key ':' (value | values | nested)
  // sequence(k:, v, Both::of) builds a (key,value) pair; ':' consumes.
  Parser<Map<String, ?>> mapParser =
      sequence(
          key.followedBy(":"),                         // key plus ':' (consumes)
          anyOf(value, values, nested),                // the first that fits wins
          Both::of                                     // (k,v)
      )
      .zeroOrMoreDelimitedBy(",")                      // 0..n pairs; may be empty
      .between("{", "}")                               // braces guarantee net consumption
      // materialize in insertion order
      .map(kvs -> BiStream.from(kvs.stream()).toMap());

  // Wire the forward ref and run with global head-skipping of whitespace.
  return nested.definedAs(mapParser)
      .parseSkipping(Character::isWhitespace, input);
}

Map<String, Object> m = parseMap(
    """
    {
      name: "Jing",
      hobbies: ["dance", "cat"],
      meta: { "k": "v" }
    }
    """);
```

---

## No Trap!

### The Infinite Loop (in many combinator libraries)

That infinite loop bug happens when a repeating parser can succeed without moving forward.
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

- About **800 lines of Java** (including `OperatorTable`).  
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
