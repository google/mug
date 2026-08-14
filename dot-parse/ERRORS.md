# Error Messages

When the input doesn't match, `parse()` throws
[`Parser.ParseException`](https://google.github.io/mug/apidocs/com/google/common/labs/parse/Parser.ParseException.html)
(an `IllegalArgumentException`), with a message pointing at where the parser gave up:

```java
calculator().parseSkipping(Character::isWhitespace, "(12 34)");
```

```
at 1:5: expecting <)>, encountered:
    (12 34)
        ^
```

The message has three parts:

Part               | Meaning
------------------ | -------
`at 1:5`           | 1-based `line:column` of the failure. The raw index is also available programmatically through `ParseException.getSourceIndex()`.
`expecting <)>`    | the symbol the parser was looking for at that position.
the snippet        | the offending input, with a caret under the failure position.

When several branches of a grammar fail, Dot Parse reports the **farthest** failure, not the
first one: the branch that consumed the most input is usually the one you meant to write.

> [!IMPORTANT]
> **These messages are informational.** They are meant to be readable by a human who is
> looking at a failed parse, and that is the only contract. The wording, the punctuation,
> the set of symbols listed, and the `line:col` prefix may all change between releases
> without notice — none of it is part of the API.
>
> Don't parse them programmatically. If you need the failure position, use
> `ParseException.getSourceIndex()`. If you need a message your own code can act on, raise it
> yourself with [`Parser.fail()`](#custom-messages--parserfail) and match on your own text.

## Alternatives — `expecting one of [...]`

If an `anyOf()` (or `or()`) fails *without any branch consuming input*, all the valid
continuations are reported together:

```java
calculator().parseSkipping(Character::isWhitespace, "123 +");
```

```
at 1:6: expecting one of [digits, (, -], encountered:
    123 +
         ^
```

As soon as one branch does consume input, that branch owns the failure and a single symbol is
reported instead. This is deliberate: the expected symbols of an `anyOf()` are computed once,
up front, so the error reporting stays free at parse time.

### Keeping the full list — left-factoring

That rule has a practical consequence. If two alternatives start with the same prefix, the
first one to match consumes the prefix, and a failure after it reports only what *that* branch
expected next:

```java
// Both alternatives start with expr.
anyOf(
    expr.followedBy("!").map(n -> factorial(n)),
    sequence(expr, exponential, (Expr i, Expr e) -> pow(i, e)));
```

Left-factoring — parsing the shared prefix once, then choosing among the suffixes — fixes both
the message and the wasted work of re-parsing `expr` on every backtrack. The choice now happens
at a point where no branch has consumed anything, so the full set of continuations survives into
the error message.

For an optional suffix, use
[`optionallyFollowedBy()`](https://google.github.io/mug/apidocs/com/google/common/labs/parse/Parser.html#optionallyFollowedBy(com.google.common.labs.parse.Parser,java.util.function.BiFunction))
together with the
[`Parsers.Suffix`](https://google.github.io/mug/apidocs/com/google/common/labs/parse/Parsers.Suffix.html)
helper, which packages a suffix parser with the function that combines it back with its prefix:

```java
import static com.google.common.labs.parse.Parsers.Suffix.suffix;
import com.google.common.labs.parse.Parsers.Suffix;

expr.optionallyFollowedBy(
    anyOf(
        suffix("!", (Expr n) -> factorial(n)),
        suffix(exponential, (Expr i, Expr e) -> pow(i, e))),
    Suffix::apply);
```

When the prefix's result has to be wrapped either way — one AST type if a suffix is present,
a default one if not — pair `sequence()` with `orElse()` on the suffix parser:

```java
Parser.sequence(
    expr,
    anyOf(
            suffix("!", FactorialExpr::new),
            suffix(exponential, PowExpr::new))
        .orElse(LiteralExpr::new),
    Suffix::apply);
```

`Suffix.withPrefixes()` does the mirror image for repeated prefix operators. See the
[`Parsers.Suffix`](https://google.github.io/mug/apidocs/com/google/common/labs/parse/Parsers.Suffix.html)
javadoc for the full set.

## Naming what you expect

Symbol names are what makes a message readable, so the leaf-level parsers that can't name
themselves take a name parameter: `one(charPredicate, name)`, `consecutive(charPredicate, name)`,
`zeroOrMore(charPredicate, name)`, `suchThat(condition, name)`, `notFollowedBy(parser, name)`.

```java
Parser<String> identifier = Parser.word().suchThat(w -> !RESERVED_WORDS.contains(w), "identifier");

identifier.parse("class");
```

```
at 1:1: expecting <identifier>, encountered:
    class
    ^
```

## Handling 3rd-party exceptions — `Parser.fail()`

When an exception is thrown by a library method call (say, the number we are trying to parse is too large),
use [`Parser.fail(message)`](https://google.github.io/mug/apidocs/com/google/common/labs/parse/Parser.html#fail(java.lang.String))
from any `map()`, `flatMap()` or `suchThat()` lambda to report it as a parse error:

```java
Parser<Integer> port = Parser.digits().map(s -> {
  try{
    return Integer.parseInt(s);
  } catch (NumberFormatException e) {
    throw Parser.fail(e.getMessage());  // or use your own custom message
  }
});

port.parse("12345678901");
```

```
at 1:1: For input string: "12345678901"
```

The custom message replaces the whole `expecting <...>` part and is reported at the position
where the chained parser started. *Don't throw it outside of the parser lambdas.*

## Leftover input

`parse()` consumes the **entire** input, so trailing characters are a parse failure too,
reported as `expecting <EOF>`:

```java
Parsers.DURATION.parse("1s 2m");
```

```
at 1:3: expecting <EOF>, encountered:
    1s 2m
      ^
```

## End of input

When the parser runs out of input, the snippet shows `<EOF>` in place of the offending text:

```java
calculator().parseSkipping(Character::isWhitespace, "");
```

```
at 1:1: expecting one of [digits, (, -], encountered:
    <EOF>
    ^
```

## What to expect from the messages

Dot Parse aims for *good enough* error messages at near-zero runtime cost — enough to tell a
user which position is wrong and what was expected there. It intentionally doesn't do error
recovery or ANTLR-grade diagnostics: naming your leaf parsers, left-factoring the alternatives
you care about, and using `suchThat()` for domain-specific validation is how you get messages
tailored to your grammar.
