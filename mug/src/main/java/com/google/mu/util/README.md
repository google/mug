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