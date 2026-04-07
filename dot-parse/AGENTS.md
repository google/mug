# Rules for AI Agents Generating dot-parse Code

When generating or refactoring code using the `dot-parse` library, you MUST
follow these rules to ensure safety, performance, and idiomatic style.

## 1. Data Model Design

- **Always** make the domain data model immutable (e.g., using Java `record`s).
- **Use "wither" methods** (methods that return a new instance with the updated
  property) for optional properties or modifiers.
- **Rule of thumb for withers**:
  - The language to be parsed represents the user's mental model.
  - If in the mental model, the data can be expressed with or without a
    property (which usually has a default value), the data model should
    reflect that.
  - The convenience factory method should assume that default value, and an
    incremental "wither" should be used to attach it optionally.
- This enables clean functional chaining using **method references** (e.g.,
  `AbcNote::withDuration`) rather than lambdas.
- **Prefer** method references over lambdas when using withers in combinators
  like `optionallyFollowedBy()` and `sequence()`.

  ```java
  public record AbcNote(
      Accidental accidental, char pitch, int octave, NoteDuration duration) {
    // Factory assumes default duration and octave = 0
    public static AbcNote middle(char pitch) {
      return new AbcNote(null, pitch, 0, NoteDuration.of(1));
    }
  
    // Factory assumes default duration and octave = 1
    public static AbcNote high(char pitch) {
      return new AbcNote(null, pitch, 1, NoteDuration.of(1));
    }

    // Wither attaches it optionally
    public AbcNote withDuration(NoteDuration duration) {
      return new AbcNote(accidental, pitch, octave, duration);
    }
  }
  ```

- **Prefer** creating enums in the data model for reserved words, operators, etc.,
  with their `toString()` returning the canonical form (e.g., `DOUBLE_SHARP("^^")`).
  This enables building parsers cleanly using the `anyOf(Enum[])` overload (e.g.,
  `anyOf(MyEnum.values())`).

## 2. Static Import

- **Always** static import `dot-parse` factory methods from the `Parser` class
  and `CharPredicate` classes.
- Do NOT use `Parser.consecutive()`, `Parser.anyOf()`, etc. Use
  `consecutive()`, `anyOf()` directly.

  ```java
  import static com.google.common.labs.parse.Parser.anyOf;
  import static com.google.common.labs.parse.Parser.consecutive;
  import static com.google.common.labs.parse.Parser.string;
  ```

## 3. Safety against Infinite Loops (Zero-Width Parsers)

Unlike most parser combinator libraries, Dot-Parse deliberately outlaws
zero-width parsers because it's too easy to shoot yourself in the foot,
running into an infinite loop by sneakily nesting it within a repetition
parser.

You don't see parser people talk about it often but when it happens, the
program hangs. And even if you manually kill the VM and take a thread dump,
the dump would only show that it failed in a loop deeply inside the `many()`
library method body, but giving you no idea which of *your* grammar had
incorrectly used a zero-width parser, because that parser object construction
code is somewhere in the wild, just not in the stack trace of the `parse()`
call! It's like saying: "Yeah man, you were dead. It was bad, death was
bad.", but just won't tell you what killed you.

The `optional()`, `orElse()` and `zeroOrMore()` are only to be used in safe
places like `followedBy()`, `then()`, `between()`, `immediatelyBetween()` etc.
where the composite parser is guaranteed to consume input.

**Do not** attempt to compose an optional Parser in `sequence()` or `anyOf()`.

While it may feel tempting to want to do something like this:

```java
// Won't compile!
Parser<String> optionalComma = string(",").optional();
Parser<List<String>> list = word().followedBy(optionalComma).zeroOrMore();
```

It would have opened a can of worms named infinite loops, if the API have
allowed it. That's why in the `dot-parse` API, the code above would not
compile (because `optional()` returns a special `OrEmpty` type, not a
`Parser`). So don't try it! You are forced to complete the fluent chain using
methods on `OrEmpty` that ensure safety.

- **Chaining Optional Parsers**: Optional parsers (e.g., from `.optional()`,
  `.orElse()`, `.zeroOrMore()`) return a `Parser.OrEmpty` instance. You can
  chain them using `OrEmpty` methods like `.then()`, `.followedBy()`, and
  `.delimitedBy()`, which continue to return `OrEmpty`.
- **Exiting the Unsafe Zone**: To convert an `OrEmpty` chain back into a
  standard `Parser`, you must eventually attach it to a non-empty `Parser`
  using methods like `Parser.then()`, `Parser.followedBy()`, or
  `OrEmpty.between()` / `OrEmpty.immediatelyBetween()`. This ensures the
  composite parser is guaranteed to consume input.

Instead, consider these safe patterns:
- **Prefer** `optionallyFollowedBy()` for an optional suffix that may be
  present zero or one time:

  ```java
  Parser<String> wordAllowingTrailingComma = word().optionallyFollowedBy(",");
  Parser<Note> noteWithOptionalOctave = note.optionallyFollowedBy(octave, Note::withOctave);
  ```
- **Use** `followedBy(suffix.zeroOrMore())` for a suffix that may occur zero
  or many times.
- **Use** `optionallyFollowedBy(suffixString)` if an optional suffix string
  doesn't change the result.
- **Use** `anyOf()` to split the rule into two choices for an optional
  prefix:

  ```java
  anyOf(
      sequence(accidental, note, (a, n) -> n.withAccidental(a)),  // with prefix
      note)  // without prefix
  ```
  But remember to put the choice with the prefix first.
- **Use** `withPrefixes()` if the prefix can show zero or more times, and each
  time incrementally modifies the result but returns the same type:

  ```java
  note.withPrefixes(accidental, (a, n) -> n.withAccidental(a))
  ```
- **Use** `anyOf()` again if the prefixes are supposed to be accumulated into
  a collection like a `List`:

  ```java
  anyOf(
      sequence(accidental.atLeastOnce(), note, (a, n) -> n.withAccidentals(a)),  // with non-empty prefixes
      note);  // no prefix
  ```
- **Use** `OperatorTable` class to define a grammar with prefixes, postfixes,
  and infix operators declaratively, particularly when they have different
  precedents.

## 4. Optional Suffixes

- **Always prefer** `optionallyFollowedBy()`.
- It is not only more efficient (avoids backtracking), but it also produces
  more readable and intentional code than listing alternatives using `anyOf`:

  ```java
  // Good: Expresses intent clearly ("A number, optionally followed by a denominator")
  NUM.map(NoteDuration::of)
      .optionallyFollowedBy(DURATION_DENOMINATOR, NoteDuration::withDenominator)
  ```

## 5. Left Recursion

Left recursion isn't supported. Use `.optionallyFollowedBy()` for optional
suffixes; use `OperatorTable` for prefix/postfix/infix.

A common left recursive grammar is like Java's instance method calls, where
the method call is an expression and the method receiver is also an
expression.

Or, Java's nested types: `Enclosing.Nested` is a `Type`, so is `Enclosing`
itself.

The typical way to parse these naturally left-recursive grammars is to model
it as a postfix unary operator. In the method call expression, the
`.methodName(argExpressions)` can be modeled as a postfix operator:

```java
Parser<Expr> literal = anyOf(
    quotedByWithEscapes('"', '"', chars(1)).map(StringLiteral::new),
    digits().map(IntLiteral::new));
Parser<Expr> atomic = anyOf(word().map(Variable::new), literal);
Parser<Expr> expr = Parser.define(self ->
    new OperatorTable<Expr>()
      .postfix(
          sequence(
              string(".").then(word()), self.zeroOrMoreDelimitedBy(",").between("(", ")"),
              UnqualifiedMethodCall::new),
          (receiver, call) -> call.withReceiver(receiver))
      // ...
      .build(atomic));
```

Nested type parser:

```java
Parser<TypeDecl> simpleType = word().map(TypeDecl::simple);
Parser<TypeDecl> typeDecl =
    simpleType.withPostfixes(string(".").then(word()), TypeDecl::nested);
```

## 6. Idiomatic Combinators

- **Always use** `.parseSkipping(CharPredicate, String)` or
  `.skipping(charPredicate).parse(String)` to omit whitespace and comments.
- **Never** try to skip them manually in the grammar (e.g., using
  `.followedBy(zeroOrMore(whitespace()))`).
- **Prefer** attaching `simplerParser.suchThat(customPredicate, name)` over
  encoding a complex parser.
  - **Example**: `word().suchThat(w -> w.length() < 8, "short word")`, as
    opposed to constructing a more complex parser.
- **Prefer** `thenReturn(value)` over `.map(unused -> value)` when mapping a
  successful match to a constant.

  ```java
  // Good
  string("true").thenReturn(true)
  ```

-   **Prefer** the `anyOf(Enum[])` overload (passing `Enum.values()`) when
    parsing a fixed set of enum values over manual alternation of string parsers
    using `anyOf(Parser...)`.

    -   **Example**: `anyOf(Accidental.values())`
    -   It automatically handles prefix matching by trying longer strings first
        (e.g., trying "++" before "+").

- **Use** `quotedBy(before, after)` and `quotedByWithEscapes(before, after, escaped)` to parse quoted strings instead of reinventing the wheel.

  - `quotedBy` is for simple cases where no escaping is needed.
  - `quotedByWithEscapes` handles backslash escapes. The `escaped` parser parameter defines what happens *after* the backslash.

  - **Simple case** (any character can be escaped): Pass `chars(1)` as the `escaped` parser.

    ```java
    quotedByWithEscapes('"', '"', chars(1))
    ```

  - **C-style escaping** (handle `\n`, `\t`, `\r` etc.):

    ```java
    Parser<String> cStyleEscape = anyOf(
        string("n").thenReturn("\n"),
        string("t").thenReturn("\t"),
        string("r").thenReturn("\r"),
        chars(1)); // fallback for other escaped chars
    Parser<String> quoted = quotedByWithEscapes('"', '"', cStyleEscape);
    ```

  - **Unicode escaping** (e.g., `\u1234`): Use `bmpCodeUnit()` to parse the 4-digit hex code.

    ```java
    Parser<String> unicodeEscape = string("u")
        .then(bmpCodeUnit())
        .map(Character::toString);
    Parser<String> escaped = anyOf(unicodeEscape, chars(1));
    Parser<String> quoted = quotedByWithEscapes('"', '"', escaped);
    ```

- **Use** `then()` with `orElse(defaultValue)` when a parser is followed by an
  optional component that should fall back to a default value if missing.
  - The `then()` method accepts both a standard `Parser` and a `Parser.OrEmpty`
    (returned by `orElse()`).
  - **Example**: `string("/").then(NUM.orElse(2))`

- **Prefer** `Parser.sequence(...)` static method over awkwardly plumbing data
  through chained `.flatMap()` when handling 2-4 sequential rules.
  - **Example**: `sequence(owner, nested, args, (o, n, a) -> ...);`

-   **When handling more than 4 sequential rules**, you have two options:

    -   **Option 1 (Reduce Cardinality)**: If some rules don't produce a value
        (or the value is ignored), use `.followedBy(unusedRule)` or
        `prefix.then(parser)` to reduce the number of values passed to
        `sequence()`.
    -   **Option 2 (Custom Helper)**: Create the corresponding functional
        interface (assume it's named `Fun6`). Then create a custom `sequence()`
        helper that nests calls using `Map::entry` to pair up results, and then
        unpacks them in a final mapper.

	    ```java
	    static <R> Parser<R> sequence(
	        Parser<A> a, Parser<B> b, Parser<C> c,
	        Parser<D> d, Parser<E> e, Parser<F> f,
	        Fun6<? super A, ? super B, ? super C,
	             ? super D, ? super E, ? super F,
	             ? extends R> mapper) {
	      return sequence(
	          sequence(a, b, Map::entry),
	          sequence(c, d, Map::entry),
	          sequence(e, f, Map::entry),
	          (p1, p2, p3) ->
	               mapper.apply(
	                   p1.getKey(), p1.getValue(),
	                   p2.getKey(), p2.getValue(),
	                   p3.getKey(), p3.getValue()));
	    }
	    ```
	
	    And then just use it (e.g., to parse a Java method definition):
	
	    ```java {.good}
	    sequence(
	        modifier, access, returnType, methodName, args, throwsSpec,
	        MethodDef::new);
    ```
- **Use** `Parser.followedBy(suffix)` to ignore a suffix when nested in a
  `sequence()` call. Then you won't need to declare an unused lambda parameter
  for that ignored suffix.

  ```java
  // Good
  sequence(owner.followedBy("."), nested, args, (o, n, a) -> ...);
  ```
- **Use** `prefix.then(parser)` to ignore a prefix when nested in a
  `sequence()` call.
- **Minimize top-level parsers per domain type**: Ideally, create at most one
  canonical parser per domain type to ensure consistency and avoid misuse.
- **Minimize primitive-type parsers**: Avoid creating top-level parsers that
  return primitive types like `String` or `Integer` unless they are explicitly
  needed for reuse. Map them to domain types as early as possible to leverage
  strong typing.
- **Minimize the lambda body of recursive parsers**: In `Parser.define(self ->
  ...)`, keep the lambda body as small as possible. Extract out sub-parsers that
  do not depend on the fixed point `self`.

## 7. Coding Style

- **Avoid** long lambdas. Consider if the lambda or the majority of the lambda
  body should live in the domain record itself (often the logic benefits
  direct callers even when not parsing).

## 8. Performance

- **Prefer** `string("c")` or `one('c')` over `one(is('c'))`. They avoid
  parse-time allocation (they just return the passed-in string as is) and are
  more friendly to prefix-based pruning.
- **Prefer** `consecutive(CharacterSet.charsIn("[a-fA-F0-9]"))` over
  `consecutive(CharPredicate predicate, String name)` because the latter is
  more verbose and doesn't enable prefix-based pruning.
- **Prefer** `p1.optionallyFollowedBy(p2, The::wither)` over
  `anyOf(sequence(p1, p2, ...), p1)`. `optionallyFollowedBy()` is both more
  readable and more efficient.

## 9. Avoiding API Hallucination

- **Never** guess or invent methods on `Parser`. AI agents tend to hallucinate
  methods like `many()` or `sepBy1()` from other libraries. In `dot-parse`,
  use `atLeastOnce()`, `atLeastOnceDelimitedBy()`, `zeroOrMore()` and
  `zeroOrMoreDelimitedBy()` instance methods, and `consecutive()` and
  `zeroOrMore()` static factory methods.
- **Always** verify the existence of a method in `Parser.java`,
  `CharPredicate.java` or `CharacterSet.java`, `OperatorTable.java` before
  generating code using it.
- **Whitelist of Common Methods** to keep you grounded:
  - **Use** primitives: `string(s)`, `one(char)`, `one(charsIn(...))`,
    `digits()`, `word()`, `consecutive(charsIn(...))`
  - **Use** combinators: `anyOf(...)`, `sequence(...)`, `zeroOrMore()`,
    `atLeastOnce()`, `zeroOrMoreDelimitedBy()`, `atLeastOnceDelimitedBy()`
  - **Use** safe optionals: `optionallyFollowedBy(...)`, `withPrefixes(...)`,
    `withPostfixes(...)`
  - **Use** boundaries: `between(...)`, `immediatelyBetween(...)`,
    `followedBy(...)`, `then(...)`
- If you need a method not listed above, you MUST open and read `Parser.java`
  to check if it exists.

## 10. Common Pitfalls & Guardrails

- **CharacterSet Syntax**: `CharacterSet.charsIn()` expects a string enclosed
  in square brackets, mimicking regex character classes.
  - **Never** use `charsIn("abc")`. Always use `charsIn("[abc]")`.
- **Greedy Matching**: `zeroOrMore` and `atLeastOnce` are greedy. They will
  consume as much as possible and do not automatically backtrack to give back
  characters to a suffix parser.
  - **Example**: If you want to parse a word ending with 's' (like "words"),
    `word().followedBy("s")` will fail because `word()` consumes the 's' and
    leaves nothing for `followedBy("s")`.
- **Predicate Names in Error Messages**: When passing a `CharPredicate` to
  methods like `one()`, `consecutive()`, `zeroOrMore()`, or using
  `suchThat()`, you **must** provide a descriptive name parameter for error
  messages.
  - **Example**: `consecutive(Character::isDigit, "digit")` or `suchThat(p ->
    p.isValid(), "valid item")`.
  - **Exception**: If you use `CharacterSet` overloads like
    `consecutive(charsIn("[a-z]"))`, the character set string is automatically
    used, so you don't need to pass a name parameter.
- **Avoid Type Casts**: Never use type casts like `.map(e -> (Part) e)` to
  satisfy type inference in `anyOf()`.
  - **Option 1**: Explicitly define the local variable holding the result of
    `anyOf()`, e.g., `Parser<Part> p = anyOf(elementPart, groupPart);`.
  - **Option 2**: Use explicit type witness like
    `Parser.<SuperType>anyOf(...)`.
