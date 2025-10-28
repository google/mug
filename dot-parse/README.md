# Mug *dot parse*

Low-ceremony Java parser combinators, for your everyday one-off parsing tasks.

- **Easy to use:** a handful of primitives; write parser intuitively.
- **Hard to misuse:** free of the common footguns like infinite loops caused by `many(optional)` or accidental left recursion.
- **Tiny footprint:** ~**1000 LOC** end-to-end — roughly **1/5 jparsec**.

---

## API sketch

- **Primitives:** `string("if")`, `consecutive(Character::isWhitespace)`, `single(range('0', '9'))`
- **Compose:** `.thenReturn(true)`, `.followedBy("else")`, `.between("[", "]")`, `.map(Literal::new)`
- **Alternative:** `p1.or(p2)`, `anyOf(p1, p2)`
- **Sequence:** `then()`, `sequence()`, `atLeastOnce()`, `atLeastOnceDelimitedBy()`
- **Optional:** `optionallyFollowedBy()`, `orElse(defaultValue)`, `zeroOrMore()`, `zeroOrMoreDelimitedBy(",")`
- **Operator Precedence:** `OperatorTable<T>` (`prefix()`, `leftAssociative()`, `build()`, etc.)
- **Recursive:** `Parser.define()`, `Parser.Rule<T>`
- **Whitespace:** `parser.parseSkipping(Character::isWhitespace, input)`
- **Lazy Parsing:** `parseToStream(Reader)`, `probe(Reader)`.

---

## Whitespace — one switch at the entry point

No lexeme ceremony. Turn on skipping at `parse()` time:

```java
var result = Parsers.parseSkipping(Character::isWhitespace, input);
//                       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// Skips all whitespaces in between
```

Keeps grammars clean and reusable. For quoted strings that need to include literal whitespaces,
use `.immediatelyBetween("\"",  "\"")` to retain the whitespaces between the quotes.

---

## Example — Calculator (OperatorTable)

Goal: support `+ - * /`, factorial (`!`), unary negative, parentheses, and whitespace.

```java {.good}
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.mu.util.CharPredicate.range;

Parser<Integer> calculator() {
  Parser<Integer> number = consecutive(range('0', '9')).map(Integer::parseInt);
  return Parser.define(
      rule -> new OperatorTable<Integer>()
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
The `Parser.define(rule -> ...)` method call defines a recursive grammar
where the lambda parameter `rule` is a placeholder of the result parser itself so that
you can nest it between parentheses.

---

## Example — Split JSON Records

Most JSON parsers can parse a single JSON object enclosed in curly braces `{}`,
a json array enclosed by square brackets `[]`, or .jsonl files with each JSON record
at a single line.

But what if you need to read a file that may contain a single JSON record, or a list of them,
pretty-printed with indentations and newlines for human readability?

What you need is to be able to split the JSON records one-by-one by matching the top level curly braces.

Since curly braces can appear in quoted string literals, you will also need to recognize double quotes,
as well as escaped double quotes (which are not to start or terminate a string literal).

The following code splits the JSON records so you can feed them to GSON (or any other JSON parser of choice):

```java {.good}
import static com.google.common.labs.parse.Parser.*;
import static com.google.mu.util.CharPredicate.noneOf;

/** Splits input into a lazy stream of top-level JSON records. */
Stream<String> jsonStringsFrom(Reader input) {
  // Either escaped or unescaped, enclosed between double quotes
  Parser<?> stringLiteral = Parser.quotedStringWithEscapes('"', Object::toString);
  
  // Outside of string literal, any non-quote, non-brace characters are passed through
  Parser<?> passThrough = consecutive(noneOf("\"{}"), "pass through");

  // Between curly braces, you can have string literals, nested JSON records, or passthrough chars
  // For nested curly braces, let's define() it.
  Parser<Object> jsonRecord = Parser.define(
      rule -> anyOf(stringLiteral, rule, passThrough)
	      .zeroOrMore()
	      .between("{", "}"));

  return jsonRecord.source()             // take the source of the matched JSON record
      .skipping(Character::isWhitespace) // allow whitespaces for indentation and newline
      .parseToStream(input);
}
```

## Example — Mini Search Language

Imagine you are building your own search engine, and you want to allow users to search by search terms.

For example: typing `coffee` will search for "coffee"; typing `coffee AND mug` will search for both "coffee" and "mug";
typing `coffee OR tea` will search for articles that include either "coffee" or "tea"; `coffee AND NOT tea` searches for
"coffee" and excludes all "tea" results.

We'll respect normal intuitive operator precedence.

Users can also search for quoted phrases like `"coffee mug"`, which will require exact match of "coffee mug".

And finally, we want to allow users to use parentheses like `mug AND (coffee OR tea)`.

We can use sealed interface and records to model the search criteria ASTs:

```java
sealed interface SearchCriteria
    permits SearchCriteria.Term, SearchCriteria.And, SearchCriteria.Or, SearchCriteria.Not {
  
  record Term(String term) implements SearchCriteria {}
  
  record And(SearchCriteria left, SearchCriteria right) implements SearchCriteria {}
  
  record Or(SearchCriteria left, SearchCriteria right) implements SearchCriteria {}
  
  record Not(SearchCriteria criteria) implements SearchCriteria {}
}
```

Now let's build the parser:

```java {.good}
import static com.google.common.labs.parse.Parser.*;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.common.labs.parse.OperatorTable;

static SearchCriteria parse(String input) {
  Set<String> keywords = Set.of("AND", "OR", "NOT");

  // A search term is either quoted, or unquoted (but cannot be a keyword)
  Parser<Term> unquoted = WORD.suchThat(w -> !keywords.contains(w), "search term").map(Term::new);
  Parser<Term> quoted = Parser.quotedStringWithEscapes('"', Object::toString);

  // Leaf-level search term can be a quoted, unquoted term, or a sub-criteria inside parentheses.
  // They are then grouped by the boolean operators.
  Parser<SearchCriteria> parser = define(
      sub -> new OperatorTable<SearchCriteria>()
          .prefix("NOT", Not::new, 30)
          .leftAssociative("AND", And::new, 20)
          .leftAssociative("OR", Or::new, 10)
          .build(anyOf(unquoted, quoted, sub.between("(", ")"))));
 
   // Skip the whitespaces
  return parser.parseSkipping(Character::isWhitespace, input);
}
```

Didn't take much?

---

## No More Infinite Loops (if you've used other combinator libraries)

The infinite loop bug happens when a repeating parser succeeds without moving forward.
It’s like a machine that says "Job done!" but never actually takes the next item off the conveyor belt,
so it gets stuck doing the same thing forever.

Imagine you are writing a parser to parse CSV input: lines of comma-separated texts.

At first, you'll write:
```java
Parser<List<Row>> csv = row.delimitedBy(newline) ✅
```

You parse rows separated by newlines. Works great.

But what about empty lines? We should allow them, right?

```java
Parser<List<Row>> csv = row.orElse(EMPTY_ROW)
    .delimitedBy(newline) ✅
```

Still safe, because the required newline forces the parser to move forward.

Next day, you realize that some CSV inputs may or may not have the newline character at the last line.
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

### The Guardrail: How Dot Parse Prevents Infinite Loops 🛡
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
