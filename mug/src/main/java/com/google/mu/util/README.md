# Structured String Processing with StringFormat and Substring

The `mu.util` package provides two complementary libraries designed for safer, more readable, and composable string manipulation in Java:

- **`StringFormat`** – for static, compile-time safe template formatting and parsing  
- **`Substring`** – for dynamic, runtime composable substring extraction

Together, they form a toolkit for replacing brittle `indexOf`/`substring` logic and verbose regular expressions with declarative, intention-revealing code.

---

## When to Use

| Use case                                     | Recommended API         |
|----------------------------------------------|-------------------------|
| Parsing/formatting structured strings        | `StringFormat`          |
| Extracting one or more dynamic ranges        | `Substring`             |
| Rewriting structured strings with placeholders     | `StringFormat`          |
| Matching nested or repeated delimiters       | `Substring` + combinators |
| Dynamically locating a runtime pattern       | `Substring.between(...)` |

---

## StringFormat: Declarative Templates with Named Arguments

`StringFormat` defines a bi-directional template:

- Format structured strings with named placeholders
- Parse structured strings using strongly typed lambda consumers
- Safe to define as constants and reuse without mismatch risks
- Efficient: formatting uses `+` string concatenation internally, and avoids runtime parsing.
  This makes it often more efficient than even `StringBuilder.append()`,
  thanks to the `invokedynamic` optimization for string concatenation.

### Example: Parse and format

```java {.good}
StringFormat format = new StringFormat("User {id} - {name}");

Optional<String> formatted = format.parse("User 42 - Alice", (id, name) -> id + ":" + name);
// => Optional.of("42:Alice")

String output = format.format(/* id */ 42, /* name */ "Alice");
// => "User 42 - Alice"
```

### Example: Scanning repeated records

```java
List<String> results = new StringFormat("[{title}]({url})")
    .scan("[One](link1) and [Two](link2)", (title, url) -> title + ":" + url);
    .toList()
// => ["One:link1", "Two:link2"]
```

### Parsing into typed objects

```java
record User(int id, String name) {}

Optional<User> user = new StringFormat("User {id} - {name}")
    .parse("User 42 - Alice", User::new);
```

### Why not use regex or split?

- Declarative and self-documenting
- Two-way parse + format support
- Compile-time placeholder checks
- Reusable, immutable, side-effect free
- Faster than `String.format()` due to template precompilation and `+` string concatenation

### StringFormat vs `String.format()`

| Feature             | `String.format()`               | `StringFormat`                      |
|---------------------|---------------------------------|--------------------------------------|
| Readability         | Positional specifiers (`%s`)    | Named placeholders (`{name}`)         |
| Parsing support     | ❌                              | ✅                                   |
| Parameter ordering  | ❌ Prone to human errors        | ✅ Checked                              |
| Performance         | ❌ Runtime parsing              | ✅ Precompiled, uses `+`. Faster than `StringBuilder` on Java 17+  |

---

## Substring: Composable Dynamic Extraction

`Substring` provides safe, readable string slicing through a composable API.

### Example: Extracting a file extension

```java
after(last('.')).from("report.csv").orElse("");
// => "csv"
```

### Example: Extract between dynamic delimiters

```java
Optional<String> quotedBy(String open, String close) {
  return between(open, close).from("call(foo)");
}
```

### Comparison to StringUtils

| Task                       | Apache StringUtils              | Substring API                       |
|----------------------------|---------------------------------|-------------------------------------|
| After last `.`             | `substringAfterLast(name, ".")` | `after(last('.')).from(name)`       |
| Between delimiters         | `substringBetween(...)`         | `between(...).from(...)`            |
| Safe fallback              | Null check                      | `Optional<String>`                  |
| Pattern composition        | ❌                              | ✅ Chainable                       |

### Optional-safe chaining

```java
Substring.between("/", "/").from("/users/alice/")
    .map(String::toUpperCase)
    .orElse("default");
```

### Comparison to Guava `Splitter`

If you already use Guava, or don't mind pulling in the Guava dependency, just keep using `Splitter`.

Otherwise, Mug's `Substring.Pattern` can be used as a two-way splitter, and `Substring.RepeatingPattern`
(as returned by `all()` or `repeatedly()`) as an N-way splitter. Both are immutable objects
that can be stored for reuse, and both offer additional functionalities that we will cover in a bit.

For example:

```java {.good}
Substring.first('=')
    .split("k=v", (key, value) -> ...);
```
Or

```java {.good}
Substring.all(',')
    .split(commaSeparated)  // Stream<Substring.Match>
    .filter(item -> item.startsWith("<"))
    .map(CharSequence::toString)
    .toList();
```

Note that the N-way split of `RepeatingPattern.split()` returns a stream of `Substring.Match`,
which are *views* of the underlying substring without copying the characters
(it's a subtype of `CharSequence`). So you could do filtering, or short-circuiting without
paying the cost of always copying the characters.

This makes `Substring` splitting more efficient.

#### Map Splitting

Similar to Guava `MapSplitter`, you can split key-value pairs into a `Map<String, String>`:

```java {.good}
import static com.google.mu.util.Substring.all;
import static com.google.mu.util.Substring.first;

Map<String, String> keyValues = all(',')
    .splitThenTrimKeyValuesAround(first(':'), "k1 : v1, k2 : v2")  // BiStream<String, String>
    .toMap();
```

The `Substring` API offers more flexibility because `splitThenTrimKeyValuesAround()`
and `splitKeyValuesAround()` return a `BiStream<String, String>`.

This allows you to chain from the result `BiStream`, collecting into a `Multimap` to more gracefully
handle duplicate keys:

```java {.good}
import static com.google.common.collect.ImmutableListMultimap;

ImmutableListMultimap<String, String> keyValues = all(',')
    .splitThenTrimKeyValuesAround(first(':'), "k1 : v1, k2 : v2")  // BiStream<String, String>
    .collect(ImmutableListMultimap::toImmutableListMultimap);
```

#### Flexible Splitting

Similar to Guava `Splitter`, you can pass a `CharPredicate` or even a regex pattern to the
`Substring.first()` method to get a more flexible `Substring.Pattern` object. And if you call
`.repeatedly()`, the `Substring.Pattern` object is turned into a `Substring.RepeatingPattern`
object that can be used to do N-way splitting, as well as other operations such as extraction (`from()`),
replacement (`replaceAllFrom()`) or removal (`removeAllFrom()`).

Beyond using `CharPredicate` or regex, you can also use `Substring` API's lookaround capability,
such as:

```java {.good}
first(',')
    .notPrecededBy("\\")
    .repeatedly()
    .split(...)
```

Or:

```java {.good}
first(',')
    .immediatelyBetween(" ", " ")
    .repeatedly()
    .splitThenTrim(...)
```

Using regex can achieve similar lookaround, but `Substring` is generally more efficient
and more readable.

#### Splitting With a Limit

In Guava, you can call `limit(int)` on a `Splitter` object to limit the number of parts to be split.

For example, the following code finds the project id, location and job id from a BigQuery fully qualified job id:

```java
List<String> projectAndLocationAndJobId = Splitter.on(':')
    .limit(3)
    .splitToList("my-project:US:department:job-id");

if (projectAndLocationAndJobId.size() < 3) {
  throw ...;
}
String projectId = projectAndLocationAndJobId.get(0); // my-project
String location = projectAndLocationAndJobId.get(1); // US
String jobId = projectAndLocationAndJobId.get(2); // department:job-id
```

The `Substring` API doesn't provide a `limit()` method for this purpose (the `Substring.Pattern.limit()`
method is to cap the size of the found substring, in a way similar to `Stream.limit(int)`).

But consider using `StringFormat` for this kind of compile-time patterns, because the code is usually
more readable and safer against accidental human errors:

```java {.good}
private static final StringFormat FULLY_QUALIFIED_JOB_ID =
    new StringFormat("{project}:{location}:{job}");

  FULLY_QUALIFIED_JOB_ID.parseOrThrow(
      "my-project:US:department:job-id",
      (project, location, jobId) -> ...);
```

#### Omitting Empty Values

Guava `Splitter` offers `.omitEmptyStrings()` to, well, omit empty strings.

Since `Substring.RepeatingPattern.split()` returns a `Stream<Substring.Match>`, you can directly
`.filter()` on the stream:

```java {.good}
all(',')
    .splitThenTrim(input)
    .filter(Substring.Match::isNotEmpty)
    ...;
``` 

---

## More Examples

### 🔁 Extract repeated patterns with `repeatedly()`

```java
// Extract all values between { and } in a snippet
List<String> values = Substring.between("{", "}").repeatedly().from(snippet).toList();

// Remove all block comments from code
String noComments = Substring.spanningInOrder("/*", "*/").repeatedly().removeAllFrom(code);

// Replace all placeholders in template
String output = Substring.spanningInOrder("{", "}").repeatedly().replaceAllFrom(
    template,
    placeholder -> resolve(placeholder.skip(1, 1).toString()));
```

### 🧱 Tokenize complex structures with `consecutive()` and `firstOccurrence()`

```java
Substring.RepeatingPattern tokens =
    Stream.of(consecutive(DIGIT), consecutive(ALPHA), first(PUNCTUATION))
        .collect(firstOccurrence())
        .repeatedly();
```

### 🔍 Match the first of multiple patterns

```java
Substring.Pattern keyword = keywords.stream()
    .map(Substring::first)
    .collect(firstOccurrence());
```

### 📚 Split into words

```java
Substring.word().repeatedly().from("quick_brown-fox").toList();
// => ["quick", "brown", "fox"]
```

---

## Regex Comparison

| Use case                        | Regex pattern                        | Substring / StringFormat                                |
|----------------------------------|---------------------------------------|----------------------------------------------------------|
| After `=`                        | `(?<=\=).*`                         | `after("=")`                                   |
| Inside square brackets           | `\[(.*?)\]`                        | `between("[", "]")`                            |
| Path segments                    | `[^/]+` with `split("/")`            | `all("/").split(path)`                         |
| Parse `id=value` string          | `id=(\w+)`                          | `new StringFormat("id={value}")`                         |
| Replace `{...}` placeholders     | `\{.*?\}` with matcher.replaceAll  | `spanningInOrder("{", "}").replaceAllFrom(template, ...)`|

Regex is powerful but often opaque. Substring and StringFormat prioritize readability and correctness through clear intent and composition.

---

## StringFormat vs Substring

| Feature             | Substring                          | StringFormat                          |
|---------------------|--------------------------------------|--------------------------------------|
| Composition         | Fine-grained                        | Template-driven                       |
| Readability         | Expression-like                     | Sentence-like                         |
| Use case            | Supports dynamic values             | Compile-time pattern like `"User {id} - {name}"`    |

They complement each other. Substring excels in runtime slicing and dynamic logic. StringFormat shines in readability and structured transformations.

---

## Summary

- **`StringFormat`**: for structured templates with named placeholders  
- **`Substring`**: for precise dynamic range selection  
- Both support optional-safe composition, clear semantics, and testable behavior

---

## See Also

- [Javadoc for StringFormat](https://google.github.io/mug/apidocs/com/google/mu/util/StringFormat.html)  
- [Javadoc for Substring](https://google.github.io/mug/apidocs/com/google/mu/util/Substring.html)