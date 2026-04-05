# Rules for AI Agents Generating dot-parse Code

When generating or refactoring code using the `dot-parse` library, you MUST follow these rules to ensure safety, performance, and idiomatic style.

## 1. Data Model Design
- **Always** make the domain data model immutable (e.g., using Java `record`s).
- **Use "wither" methods** (methods that return a new instance with the updated property) for optional properties or modifiers.
- **Rule of thumb for withers**:
  - The language to be parsed represents the user's mental model.
  - If in the mental model, the data can be expressed with or without a property (which usually has a default value), the data model should reflect that.
  - The convenience factory method should assume that default value, and an incremental "wither" should be used to attach it optionally.
- This enables clean functional chaining with combinators like `optionallyFollowedBy(..., DomainType::withProperty)`.
  ```java
  public record AbcNote(Accidental accidental, char pitch, int octave, NoteDuration duration) {
    // Factory assumes default duration
    public static AbcNote of(char pitch, int octave) {
      return new AbcNote(Accidental.NONE, pitch, octave, NoteDuration.of(1));
    }
    
    // Wither attaches it optionally
    public AbcNote withDuration(NoteDuration duration) {
      return new AbcNote(accidental, pitch, octave, duration);
    }
  }
  ```

## 2. Static Import
- **Always** static import `dot-parse` factory methods from the `Parser` class and `CharPredicate` classes.
- Do NOT use `Parser.one()`, `Parser.anyOf()`, etc. Use `one()`, `anyOf()` directly.
  ```java
  import static com.google.common.labs.parse.Parser.anyOf;
  import static com.google.common.labs.parse.Parser.one;
  import static com.google.common.labs.parse.Parser.string;
  ```

## 3. Safety against Infinite Loops (Zero-Width Parsers)

Unlike most parser combinator libraries, Dot-Parse deliberately outlaws zero-width parsers because it's too easy to shoot yourself in the foot, running into an infinite loop by sneakily nesting it within a repetition parser.

You don't see parser people talk about it often but when it happens, the program hangs. And even if you take a thread dump, the dump would only show that it failed in a loop deeply inside the `many()` library method body, but giving you no idea which of *your* grammar had incorrectly used a zero-width parser! It's like saying: "Yeah man, look: you died, it was bad, death was bad.", but just won't tell you what killed you.

The `optional()`, `orElse()` and `zeroOrMore()` are only to be used in safe places like `followedBy()`, `then()`, `between()`, `immediatelyBetween()` etc. where the composite parser is guaranteed to consume input.

**Do not** attempt to compose an optional Parser in `sequence()` or `anyOf()`. 

While it may feel tempting to want to do something like this:

```java
// Won't compile!
Parser<String> optionalComma = string(",").optional();
Parser<List<String>> list = word().followedBy(optionalComma).zeroOrMore();
```

It would have opened a can of worms named infinite loops, if the API have allowed it. That's why in the `dot-parse` API, the code above would not compile (because `optional()` returns a special `OrEmpty` type, not a `Parser`). So don't try it! You are forced to complete the fluent chain using methods on `OrEmpty` that ensure safety.

Instead, consider:
- For an optional suffix that may be present zero or one time, use `optionallyFollowedBy()`:

  ```java
  Parser<String> wordAllowingTrailingComma = word().optionallyFollowedBy(",");
  Parser<Note> noteWithOptionalOctave = note.optionallyFollowedBy(octave, Note::withOctave);
  ```
- For suffix that may occur zero or many times, use `followedBy(suffix.zeroOrMore())`.
- If an optional suffix string doesn't change the result, use `optionallyFollowedBy(suffixString)`..
- For an optional prefix, split the rule into two choices, such as:

  ```java
  anyOf(
      sequence(accidental, note, (a, n) -> n.withAccidental(a)),  // with prefix
      note)  // without prefix
  ```
  But remember to put the with prefix choice as the first.
- If the prefix can show zero or more times, and each time incrementally modifies the result but returns the same type, use `withPrefixes()`:

  ```java
  note.withPrefixes(accidental, (a, n) -> n.withAccidental(a))
  ```
- If the prefixes are supposed to be accumulated into a collection like a `List`, split the rule into two choices again:

  ```java
  anyOf(
      sequence(accidental.atLeastOnce(), note, (a, n) -> n.withAccidentals(a)),  // with non-empty prefixes
      note);  // no prefix
  ```
- For a grammar with prefixes, postfixes, and infix operators, particularly with different precedents, use `OperatorTable` class to define them declaratively.

## 4. Optional Suffixes
- For optional suffix rules, **always prefer** `optionallyFollowedBy()`.
- It is not only more efficient (avoids backtracking), but it also produces more readable and intentional code than listing alternatives using `anyOf`:

  ```java
  // Good: Expresses intent clearly ("A number, optionally followed by a denominator")
  NUM.map(NoteDuration::of)
      .optionallyFollowedBy(DURATION_DENOMINATOR, NoteDuration::withDenominator)
  ```

## 5. Left Recursion
Left recursion isn't supported. Use `.optionallyFollowedBy()` for optional suffixes; use `OperatorTable` for prefix/postfix/infix.

A common left recursive grammar is like Java's instance method calls, where the method call is an expression and the method receiver is also an expression.

Or, Java's nested types: `Enclosing.Nested` is a `Type`, so is `Enclosing` itself.

The typical way to parse these naturally left-recursive grammars is to model it as a postfix unary operator. In the method call expression, the `.methodName(argExpressions)` can be modeled as a postfix operator:

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

## 5. Idiomatic Combinators
- **Prefer** `thenReturn(value)` over `.map(unused -> value)` when mapping a successful match to a constant.
  ```java
  // Good
  string("true").thenReturn(true)
  ```
- **Prefer** `Parser.sequence(...)` static method over awkwardly plumbing data through chained `.map()` and `.flatMap()` when handling 2-4 sequential rules.
  ```java
  // Good
  sequence(owner, nested, args, (o, n, a) -> ...);
  ```
- **Use** `Parser.followedBy(suffix)` to ignore a suffix when nested in a `sequence()` call. Then you won't need to declare an unused lambda parameter for that ignored suffix.
  ```java
  // Good
  sequence(owner.followedBy("."), nested, args, (o, n, a) -> ...);
  ```
- **Use** `prefix.then(parser)` to ignore a prefix when nested in a `sequence()` call. 

## 6. Coding Style
- **Avoid** long lambdas. Consider if the lambda or the majority of the lambda body should live in the domain record itself (often the logic benefits direct callers even when not parsing).


## 7. Performance

- **Prefer** `string("c")` or `one('c')` over `one(is('c'))`. They avoid parse-time allocation and are more friendly to prefix-based pruning.
- **Prefer** `consecutive(CharacterSet.charsIn("[a-fA-F0-9]"))` over `consecutive(CharPredicate predicate, String name)` because the latter is more verbose and doesn't enable prefix-based pruning.
- **Prefer** `p1.optionallyFollowedBy(p2, The::wither)` over `anyOf(sequence(p1, p2, ...), p1)`. `optionallyFollowedBy()` is both more readable and more efficient.
