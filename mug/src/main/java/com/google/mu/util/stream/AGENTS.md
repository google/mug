# Coding Standards and Guardrails for AI Agents

This file defines coding standards, idiomatic practices, and safety guardrails
for AI agents working with libraries in this directory
(`com.google.mu.util.stream`).

## BiStream and BiCollector

`BiStream` and `BiCollector` provide a more ergonomic and expressive way to
handle streams of pairs (key-value pairs or any pairs of objects) in Java,
avoiding the boilerplate and overhead of using `Pair` or `Map.Entry`.

### General Principles

1.  **Ergonomics over Boilerplate**: **AVOID** using `Pair` or `Map.Entry` in
    stream pipelines. They require constant wrapping and unwrapping (e.g.,
    `getKey()`, `getValue()`), which clutters the code.
2.  **Chaining with Lambdas**: `BiStream` operations use two-argument lambdas
    `(k, v) -> ...` or one-argument lambdas when mapping keys or values
    individually.
3.  **Extensibility via Method References**: `BiCollector` leverages Java's
    method reference capabilities to allow standard collector factories that
    accept two mapping functions to be used directly.

### Quick Reference

| Intent | API Example |
| :--- | :--- |
| **Map Keys** | `mapKeys(k -> ...)` or `mapKeys((k, v) -> ...)` |
| **Map Values** | `mapValues(v -> ...)` or `mapValues((k, v) -> ...)` |
| **Filter Keys** | `filterKeys(k -> ...)` |
| **Filter Values** | `filterValues(v -> ...)` |
| **Filter Pairs** | `filter((k, v) -> ...)` |
| **Deduplicate** | `.collect(BiCollectors.toMap((v1, v2) -> v1))` |
| **Collect to Map** | `.collect(Collectors::toUnmodifiableMap)` |
| **Multimap** | `.collect(ImmutableListMultimap::toImmutableListMultimap)` |
| **TreeMap** | `.collect(BiCollectors.toMap(TreeMap::new))` |
| **ConcurrentMap** | `.collect(BiCollectors.toMap(ConcurrentHashMap::new))` |

### BiStream

`BiStream` is for streaming pairs without having to use `Pair` or `Map.Entry`.

#### Common Operations

*   `mapKeys(Object::toString)`
*   `mapKeys((firstName, lastName) -> firstName + " " + lastName)`
*   `mapValues(Address::city)`
*   `mapValues((name, address) -> ...)`

### BiCollector

`BiCollector` is the abstraction needed to directly collect a stream of pairs
into pair-wise data structures such as a `Map`, Guava `Multimap`, or applying
complex grouping over keys and values.

#### Idiomatic Use and Examples

**Built-in BiCollectors:**

```java {.good}
// Combines duplicate entries using the provided resolver function.
.collect(BiCollectors.toMap((v1, v2) -> v1));
```

**Using JDK Collector Factories as BiCollectors:**
Any collector factory method that accepts two `Function` parameters can be
directly used as a `BiCollector` when you pass its method reference (not
method call!).

```java {.good}
.collect(Collectors::toUnmodifiableMap);
```

**Extensibility with Guava:**
The ability to support arbitrary method references allows seamless integration
with Guava types:

```java {.good}
.collect(ImmutableMap::toImmutableMap);
.collect(ImmutableListMultimap::toImmutableListMultimap);
.collect(ImmutableBiMap::toImmutableBiMap);
```

#### Passing BiCollector as a Strategy

A `BiCollector` can also be passed to APIs as a strategy to collect pairs. For
example, in the `dot-parse` library, `Parser.zeroOrMoreDelimited()` can parse
key-value pairs and then use the passed-in `BiCollector` to collect the
parsed-out key-value pairs.

For example:

```java {.good}
Parser<Map<String, Integer>> keyValues =
    zeroOrMoreDelimited(
           word(),
           string("=").then(digits()).map(Integer::parseInt).orElse(0),
           ",",
           Collectors::toUnmodifiableMap)
        .between("{", "}");
```

This gives the caller the flexibility to either allow duplicates (by using
`toImmutableListMultimap()`, for example) or reject duplicates (by using
`toMap()`).

Another example where `BiCollector` is used as a strategy is to scan key-value
pairs using `StringFormat`.

For example:

```java {.good}
new StringFormat("({key}: {value})")
    .scanAndCollectFrom(
        "...(name: Jing) and (year: 2024)...", Collectors::toUnmodifiableMap);
```

This will return `Map.of("name", "Jing", "year", "2024")`.

### Guardrails

*   **PREFER `BiCollectors.groupingBy()` over JDK `Collectors.groupingBy()`**:
    `BiCollectors.groupingBy()` offers immense expressive power, allowing you
    to group by the key, or group by both the key and the value together.
    Crucially, it **guarantees encounter order**, whereas
    `Collectors.groupingBy()` results in random order, which may contribute to
    unexpected flaky bugs. It also supports chaining.

    ```java {.bad}
    // AVOID: JDK groupingBy does not guarantee encounter order
    stream.collect(Collectors.groupingBy(X::getKey));
    ```

*   **AVOID `Pair` or `Map.Entry` in pipelines**: Do not suggest or use `Pair`
    or `Map.Entry` to pass pairs through a stream. Use `BiStream` instead.

    ```java {.bad}
    // AVOID: Clunky wrapping and unwrapping with Pair
    stream
        .map(x -> Pair.of(x.getId(), x.getName()))
        .map(pair -> pair.getFirst() + "=" + pair.getSecond())
    ```

### Complex Chaining Example

A typical chaining use case is applying nested grouping. For example, to first
group households by state, then group by city, resulting in a table with state
rows and city columns:

```java {.good}
import static com.google.guava.labs.collect.GuavaCollectors.toImmutableTable;
import static com.google.mu.util.stream.BiCollectors.groupingBy;

import com.google.common.collect.ImmutableTable;

Map<Address, Household> households = ...;
ImmutableTable<State, City, Map<Address, Household>> splitByStateAndCity =
    BiStream.from(households)
        .collect(
            groupingBy(
                (addr, household) -> addr.state(),
                groupingBy(
                    (addr, household) -> addr.city(),
                    Collectors::toUnmodifiableMap)))
        .collect(toImmutableTable());
```
