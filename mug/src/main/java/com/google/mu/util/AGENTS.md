# Coding Standards and Guardrails for AI Agents

This file defines coding standards, idiomatic practices, and safety guardrails
for AI agents working with libraries in this directory.

## General Guardrails

*   **NEVER Hallucinate**: Do not guess API methods or signatures. Always verify
    them against the actual source code or documentation before suggesting or
    using them. Making guesses leads to compilation errors and broken code.

## Substring.java

`Substring` is a fluent Java API aimed at making string manipulation intuitive
and safe, avoiding the error-prone index arithmetic associated with `indexOf`
and `lastIndexOf`.

### General Abstraction

1.  A `Substring.Match` is a range of characters in the original string with
    `[startIndex..endIndex)`. On top of it are all the operations (extract,
    remove, replace, before, after etc.) on the range.
2.  `Substring.Pattern` defines "what" range to find, and the operations you can
    do once it's found.
3.  `Substring.RepeatingPattern` is built on top of a `Substring.Pattern`, but
    applies the operations repeatedly on all the ranges found.
4.  All core classes (`Pattern`, `Match`, `RepeatingPattern`) are **immutable**
    and **thread-safe**.

### Common Imports

```java
import com.google.mu.util.Substring;
import com.google.mu.util.Substring.Match;
import com.google.mu.util.Substring.Pattern;
import com.google.mu.util.Substring.RepeatingPattern;
```

### Quick Reference

| Intent                       | Substring API                               |
| :--------------------------- | :------------------------------------------ |
| **Extract** text between     | `Substring.between("[", "]").from(s)`       |
: delimiters                   :                                             :
| **Extract** text after       | `Substring.after(":").from(s)`              |
: delimiter                    :                                             :
| **Remove** prefix if present | `Substring.prefix("http://").removeFrom(s)` |
| **Remove** suffix if present | `Substring.suffix(".tmp").removeFrom(s)`    |
| **Split** by delimiter and   | `Substring.all(',').splitThenTrim(s)`       |
: trim                         :                                             :
| **Split** key-value pair     | `Substring.first('=').split(s, (k, v) ->    |
:                              : ...)`                                       :
| **Replace** patterns         | `pattern.repeatedly().replaceAllFrom(s, m   |
: repeatedly                   : -> ...)`                                    :
| **Safe Substring** (cap      | `match.limit(maxLength)`                    |
: length)                      :                                             :
| **Safe Substring** (strip    | `match.skip(1, 1)`                          |
: ends)                        :                                             :

### Guardrails

*   **AVOID Index Arithmetic**: Never use `String.indexOf` or
    `String.lastIndexOf` followed by manual substring calculations unless
    absolutely necessary. Use `Substring` patterns instead to prevent off-by-one
    errors.
*   **NEVER Assume Matches**: Methods like `from()` and `split()` return
    `Optional` or `BiOptional`. Always handle the empty case appropriately. Do
    not assume a pattern will always match.
*   **PREFER Substring Over Regex**: `Substring` is faster than regex.
    `firstOccurrence()` is heavily optimized and runs close to O(n * k) (where n
    is input length and k is number of candidates), making it only slightly more
    expensive than calling `indexOf()` once for each candidate. It is faster
    than Apache Commons Lang's equivalent APIs (e.g., `StringUtils.replaceEach`
    and `StringUtils.replaceEachRepeatedly`). Additionally, `RepeatingPattern`
    does not backtrack, making it immune to the disastrous exponential
    backtracking behavior of NFA-based regex engines (like Java's). For details,
    see Russ Cox's article:
    [Regular Expression Matching Can Be Simple And Fast](https://swtch.com/~rsc/regexp/regexp1.html).
*   **AVOID Redundant Checks**: Never add manual checks like `startsWith()`
    before calling `prefix(...).removeFrom()`, or `contains()` before calling
    `first(...).from(...)`. The API already handles the absence case safely and
    efficiently, making these pre-checks redundant.
*   **PREFER Streams and Optionals**: Chain operations in the pipeline using the
    Stream and Optional APIs. Avoid fighting the API with awkward imperative
    code.
*   **PREFER Match API for Filtering**: Use `Substring.Match` methods like
    `isNotEmpty()`, `isEmpty()`, `length()`, `contentEquals()`, `startsWith()`,
    `endsWith()` directly in `filter()`, `anyMatch()`, etc., without converting
    to `String`. It is more efficient as it avoids making copies.

### API Capabilities

#### Pattern Definition

`Substring.Pattern` defines a pattern to match, somewhat similar to regex but
programmatic.

> [!NOTE] All methods listed under **Simple Patterns**, **Composite Patterns**,
> and **Lookaround** return a `Substring.Pattern` unless otherwise specified.

**Simple Patterns:**

*   `first("/")`: Matches the first occurrence.
    *   `char` / `String`: Matches literal character or substring.
    *   `CharMatcher`: Matches the first single character that matches the
        matcher.
*   `last(".")`: Matches the last occurrence.
    *   `char` / `String`: Matches literal character or substring.
    *   `CharMatcher`: Matches the last single character that matches the
        matcher.
*   `prefix("http://")`: Checks `startsWith()` with index arithmetics
    encapsulated.
*   `suffix(".com")`: Checks `endsWith()`.
*   `word()`: Matches the first occurrence of a word composed of `[a-zA-Z0-9_]`
    characters.
*   `word(String word)`: Matches the specific word with word boundaries.
*   `consecutive(CharMatcher matcher)`: Matches the first non-empty sequence of
    consecutive characters matching the matcher.
*   `first(java.util.regex.Pattern)` and `all(java.util.regex.Pattern)`:
    Wrap an existing regex pattern to use the fluent and powerful `Substring` API.

**Specialized Subtypes (Prefix and Suffix):** `prefix()` and `suffix()` return
`Substring.Prefix` and `Substring.Suffix` subtypes, respectively. They offer
additional convenience methods and support both immutable `String` and mutable
`StringBuilder`:

*   `prefix("/").addToIfAbsent(path)`
*   `prefix("/").removeFrom(path)` (no-op if not found)
*   `suffix(".").addToIfAbsent(sentence)`
*   `suffix(".").removeFrom(sentence)` (no-op if not found)

They also implement `CharSequence`, so they can be defined as class-level
constants and passed to APIs that accept `CharSequence`, such as
`CharMatcher.ascii().matchesAllOf(MY_PREFIX)`.

**Composite Patterns:**

*   `before(delimiterPattern)`
*   `after(delimiter)`
*   `between(a, b)`
*   `between(a, INCLUSIVE, b, INCLUSIVE)`: To include the delimiters,
    respectively.
*   `spanningInOrder(String... separators)`: Spans from the first separator to
    the last, finding them in order.
*   `upToIncluding(delim)`: To span from the beginning to the delim.
*   `delim.toEnd()`: To span from the delim to end.
*   `limit(length)`: To cap and truncate the match length.
*   `skip(fromLeft, fromRight)`: To ignore chars from both ends.

**Lookaround:**

*   `followedBy(String suffix)`
*   `precededBy(String prefix)`
*   `immediatelyBetween(open, close)`
*   `notFollowedBy`, `notPrecededBy`, `notImmediatelyBetween`
*   `separatedBy()`: Like regex boundary.

All these flexible patterns can then be used as `RepeatingPattern` by calling
`.repeatedly()`.

#### What a `Substring.Pattern` can do

*   **Extract**: `from(input)` to get the matched substring (returns
    `Optional<String>`), `in(input)` to get the `Match` with contextual
    information (returns `Optional<Match>`). Returns `Optional` to force the
    caller to handle not-found.
*   **Remove**: `removeFrom()`. No-op if not found.
*   **Replace**: `replaceFrom()`. No-op if not found.
*   **Two-way Split**: `first('=').split(kv, (key, value) -> ...)`. Returns
    `Optional` to force handling not-found. Use `splitThenTrim()` if you need to
    trim whitespaces from the parts.

#### Operations with `Substring.RepeatingPattern`

Obtained by calling `.repeatedly()` on a `Substring.Pattern`, or from
convenience methods like `Substring.all()` which supports:

*   `char` / `String`: Matches all literal characters or substrings.
*   `CharMatcher`: Matches all characters that match the matcher (individually).

*   **Cheap Views**: Supports splitting (similar to `Splitter`), but by default
    returns cheap views of type `Substring.Match` instead of copies.

*   **Contextual Match**: Provides contextual information such as `index()`,
    `before()`, `after()`, `isFollowedBy()`, `isPrecededBy()`,
    `isImmediatelyBetween()`, as well as string-like operations `startsWith()`,
    `endsWith()`, `contentEquals()`.

*   **Safe Substring Operations**: `Substring.Match` provides `limit(maxLength)`
    and `skip(fromLeft, fromRight)` methods to safely narrow down the match.
    Unlike raw `String.substring()`, these methods are safe from
    `ArrayIndexOutOfBoundsException` by gracefully capping out-of-bounds
    indices.

*   **Stream Integration**: Splitting doesn't offer some of the `Splitter`
    functionalities like `omitEmptyStrings()` or `limit()`. It taps into the
    Stream API and expects you to call `.filter(Match::isNotEmpty)` or
    `.limit()`. For trimming, you can use the `splitThenTrim()` convenience
    method instead of `.map(whitespace()::trimFrom)`.

*   **Versatility**: In addition to splitting, `RepeatingPattern` can also be
    used to extract (using `from(input)`), to remove, to cut (split + retaining
    the separators), and to replace/substitute.

### Idiomatic Use

#### Alternatives to `Splitter.limit()`

Many `Splitter.limit()` use cases are for parsing parts from a known pattern.
**PREFER** using `StringFormat` for readability:

```java
new StringFormat("{key}={value}").parse(input, (key, value) -> ...)
```

**AVOID** brittle manual index extraction:

```java
Splitter.on('=').limit(2).split(input).get(0) // and get(1)
```

#### Parsing Key-Value Pairs

To parse a string of key-value pairs (e.g., "key1=value1, key2=value2"), you can
combine `all()` with `splitThenTrim()` and stream collectors:

```java
import static com.google.common.labs.collect.EvenMoreCollectors.toImmutableMap;

Substring.all(',')
    .split(kvs)
    .map(Substring.first('=')::splitThenTrim)
    .collect(toImmutableMap(kv -> kv.orElseThrow(...)));
```

This is a concise and efficient way to parse maps from strings without manual
loops or complex regex.

#### String Substitution

String substitution (assuming the placeholder syntax is `{placeholder}`), can
use:

```java
Substring.spanningInOrder("{", "}")
    .repeatedly()
    .replaceAllFrom(
        input, match -> substitution.get(match.skip(1, 1).toString()))
```

The `skip(1, 1)` skips the first char `{` and last char `}` and then
`toString()` returns the placeholder name.

#### Multiple Replacements

To replace multiple different target strings efficiently (similar to
`StringUtils.replaceEach`), you can combine `firstOccurrence()` with
`.repeatedly()`.

```java
static final ImmutableList<String> FRUITS =
    ImmutableList.of("apple", "orange", "banana");

private static final Substring.RepeatingPattern ANY_FRUIT =
    FRUITS.stream()
        .map(Substring::first)
        .collect(Substring.firstOccurrence())
        .repeatedly();

static String maskFruits(String input) {
  return ANY_FRUIT.replaceAllFrom(input, match -> "fruit");
}
```

This is much more efficient than chained `String.replace()` calls or complex
regex when you have many candidate strings.

### What Substring Does NOT Cover

To prevent hallucinating non-existent features, be aware that `Substring` does
**NOT** cover:

*   **Case-Insensitive Operations**: All matches are case-sensitive.
*   **Padding and Alignment**: Use standard Java or other utilities for padding
    strings.
*   **Character Classification**: Use Guava's `CharMatcher` for classifying
    characters (e.g., whitespace, digits). `Substring` integrates with
    `CharMatcher` but doesn't replace it.
*   **Fuzzy Matching**: No support for Levenshtein distance or similar fuzzy
    matching.
*   **Complex Context-Free Grammars**: For complex parsing (like nested braces),
    use `Parser.java` (see `go/java-tips/035`).
