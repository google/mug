# Google Mug - Executive Summary

## What is Mug?

**Mug** is Google's internal Java utility library, now open-source. It's a compact, zero-dependency library that fills critical gaps in Java's standard library for string manipulation, stream processing, and structured concurrency.

**Key Stats:**
- **Version:** 9.9-SNAPSHOT
- **Dependencies:** 0 (core module)
- **Lines of Code:** ~15,000 (core), ~30,000+ (all modules)
- **Test Coverage:** ~90%
- **GitHub Stars:** 461
- **Internal Usage:** Widely used across Google's codebase

---

## Top 10 Must-Know Features

### 1. **Substring** (3,170 lines)
Composable string manipulation without regex complexity.

```java
// Extract value between delimiters
String value = Substring.between("(", ")").from("func(foo)").get();
// => "foo"

// Chain patterns
String domain = Substring.after(first('@')).from("user@gmail.com").get();
// => "gmail.com"

// Remove prefix
String clean = Substring.prefix("http://").removeFrom(uri);
```

### 2. **BiStream** (2,345 lines)
Stream over Map entries without Entry boilerplate.

```java
// Transform map keys and filter
Map<String, Integer> result = BiStream.from(map)
  .mapKeys(this::normalize)
  .filterValues(v -> v > 0)
  .toMap();

// Zip two lists
BiStream.zip(names, ages).toMap();
// => {"John": 25, "Jane": 30}
```

### 3. **StringFormat** (324 lines)
Compile-time-safe string parsing with ErrorProne.

```java
// Parse with compile-time validation
new StringFormat("projects/{project}/users/{user}")
  .parse(path, (project, user) -> fetchData(project, user));

// Format with named placeholders (2x faster)
StringFormat.using("Hello {name}, your balance is ${balance}", name, balance);
```

### 4. **Parallelizer** (594 lines)
Structured concurrency for IO-bound operations.

```java
// Parallelize with max concurrency
new Parallelizer(executor, 10)
  .parallelize(items.stream(), this::processItem);

// Collect to BiStream
BiStream<Input, Output> results = inputs.stream()
  .collect(parallelizer.inParallel(this::process));
```

### 5. **BiCollector / BiCollectors** (872 + 71 lines)
Collect pairs to custom data structures.

```java
// Group and reduce
Map<City, Household> richest = households.stream()
  .collect(groupingBy(Household::city, this::richer))
  .toMap();

// Adjacent pairs: (0,1), (1,2), (2,3)...
BiStream<Integer, Integer> pairs = numbers.stream()
  .collect(toAdjacentPairs());
```

### 6. **GraphWalker** (385 lines)
Graph traversal algorithms.

```java
// Topological sort
List<Node> order = Walker.inGraph(node -> node.getDependencies())
  .topologicalOrderFrom(root);

// Detect cycles
Optional<Stream<Node>> cycle = Walker.inGraph(findSuccessors)
  .detectCycleFrom(start);

// Strongly connected components (Tarjan)
Stream<List<Node>> scc = Walker.inGraph(successors)
  .stronglyConnectedComponentsFrom(start);
```

### 7. **Iteration** (448 lines)
Lazy recursion without stack overflow.

```java
// Lazy pagination
Stream<Foo> allFoos = new Iteration<Foo>() {
  Foos paginate(Request req) {
    Response res = service.list(req);
    emit(res.getFoos());
    if (!res.getNextPageToken().isEmpty()) {
      lazily(() -> paginate(req.withToken(res.getNextPageToken())));
    }
    return this;
  }
}.paginate(initialRequest).iterate();
```

### 8. **SafeSql** (2,850 lines)
Library-enforced SQL injection prevention.

```java
// Automatically escapes and parameterizes
SafeSql query = SafeSql.of(
  "SELECT `{cols}` FROM Users WHERE id IN ({ids}) AND name LIKE {search}",
  columnNames, userIds, searchTerm);

// Execute safely
List<User> users = query.query(UserMapper, connection);
```

### 9. **MoreCollectors** (750 lines)
Advanced collectors beyond JDK.

```java
// Validate exact count
Result res = stream.collect(onlyElements(3).andThen(this::build));

// All min values (not just one)
List<Product> cheapest = products.stream()
  .collect(allMin(Comparator.comparing(Product::price), toList()));

// Partition stream
Both<List<Pass>, List<Fail>> results = tests.stream()
  .collect(partitioningBy(Test::isPass, toList(), toList()));
```

### 10. **DateTimeFormats**
Parse datetimes by example.

```java
// Detect format from example
DateTimeFormatter fmt = DateTimeFormats.formatOf("2024-03-14 10:00:00.123 America/New_York");

// Parse similar strings
LocalDateTime dt = LocalDateTime.parse("2024-04-20 15:30:00.123 Europe/London", fmt);
```

---

## Architecture Highlights

### Design Patterns
- **Strategy Pattern:** Pluggable algorithms (Substring.Pattern, Walker)
- **Immutable Objects:** Thread safety by design
- **Static Factory Methods:** All public classes use factory methods
- **Functional Interfaces:** 30+ functional interfaces
- **Template Method:** Iteration, Walker define skeleton algorithms
- **SPI Pattern:** StringFormat.Interpolator, SafeSql.Interpolator

### Performance Characteristics
| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Substring.first() | O(n) | Uses String.indexOf |
| BiStream.from(Map) | O(1) | Lazy wrapper |
| GraphWalker.SCC | O(V+E) | Tarjan's algorithm |
| Parallelizer | O(n/k) | k = maxConcurrency |

### Compile-Time Safety
ErrorProne integration provides:
- Placeholder count validation
- Parameter name checking
- Type safety for format strings
- SQL injection prevention

---

## Module Breakdown

| Module | Purpose | LOC | Dependencies |
|--------|---------|-----|--------------|
| **mug** | Core library | ~15K | **None** |
| **mug-safesql** | Safe SQL templates | ~2,850 | mug |
| **mug-errorprone** | Compile-time checks | ~2,000 | ErrorProne |
| **mug-guava** | Guava extensions | ~1,500 | Guava |
| **mug-protobuf** | Protobuf utilities | ~1,200 | Protobuf |
| **mug-bigquery** | BigQuery support | ~800 | BigQuery |
| **mug-spanner** | Spanner support | ~600 | Spanner |

---

## When to Use Mug

### ✅ Perfect for:
- String parsing and manipulation
- Map and Multimap streaming
- IO-bound parallel processing
- Safe SQL query building
- Graph traversal
- Lazy recursive algorithms
- Compile-time string format safety

### ❌ Not for:
- CPU-bound parallelism (use parallel streams)
- Complex regex (use Pattern directly)
- Stored procedures (SafeSql limitation)
- Simple one-off string operations (overkill)

---

## Migration Examples

### From Apache Commons StringUtils
```java
// Old
String email = substringAfter(user, "@");

// New
String email = after(first('@')).from(user).orElse("default");
```

### From String.format()
```java
// Old
String.format("User %s has id %d", name, id)

// New (named, 2x faster)
StringFormat.using("User {name} has id {id}", name, id)
```

### From Manual Map.Entry Streams
```java
// Old
map.entrySet().stream()
  .filter(e -> e.getValue() > 0)
  .collect(toImmutableMap(Entry::getKey, Entry::getValue));

// New
BiStream.from(map)
  .filterValues(v -> v > 0)
  .toMap();
```

---

## Expert Tips

1. **Declare patterns as constants:** `private static final Pattern EMAIL = first('@');`
2. **Use BiStream for all Map work:** Avoids Entry boilerplate
3. **Use Iteration for deep recursion:** O(1) stack vs O(n) stack overflow
4. **Enable ErrorProne checks:** Compile-time safety is key value prop
5. **Use SafeSql for ALL SQL:** Library-enforced injection prevention
6. **Use GraphWalker.inGraph() for DAGs:** Automatic cycle detection
7. **Use MoreCollections.findFirstElements():** More efficient than manual checks
8. **Use Substring.repeatedly() for global replace:** Single-pass algorithm

---

## Key Advantages Over Alternatives

| Feature | Mug | Guava | Apache Commons |
|---------|-----|-------|----------------|
| Zero dependencies | ✓ | ✗ | ✗ |
| Compile-time safety | ✓ | ✗ | ✗ |
| Stream extensions | ✓✓ | Limited | ✗ |
| BiStream | ✓✓ | ✗ | ✗ |
| Structured concurrency | ✓ | ✗ | ✗ |
| Safe SQL | ✓ | ✗ | ✗ |
| Graph algorithms | ✓ | ✗ | ✗ |

---

## Project Quality Metrics

- **Test Coverage:** ~90%
- **Public API Classes:** ~80
- **Static Factory Methods:** ~200+
- **Functional Interfaces:** ~30
- **Cyclomatic Complexity:** Low (<10 per method)
- **Internal Google Usage:** Extensive
- **GitHub Stars:** 461
- **Active Development:** Yes (last push: Jan 2025)

---

## Installation

### Maven
```xml
<dependency>
  <groupId>com.google.mug</groupId>
  <artifactId>mug</artifactId>
  <version>9.8</version>
</dependency>

<dependency>
  <groupId>com.google.mug</groupId>
  <artifactId>mug-errorprone</artifactId>
  <version>9.8</version>
</dependency>
```

### Gradle
```gradle
implementation 'com.google.mug:mug:9.8'
implementation 'com.google.mug:mug-safesql:9.8'
```

---

## Recommended Reading Path

1. Start with **Substring** - most intuitive, widely used
2. **BiStream** - core streaming utility
3. **StringFormat** - compile-time safety
4. **BiCollectors** - collection patterns
5. **Parallelizer** - if you need concurrency
6. **GraphWalker** - if you work with graphs
7. **SafeSql** - if you work with databases

---

## Bottom Line

Mug is a **production-grade, battle-tested** utility library that fills critical gaps in Java's standard library. It's especially valuable for:

- **String manipulation** (Substring is incredibly powerful)
- **Stream processing** (BiStream eliminates Map.Entry boilerplate)
- **Safe SQL** (library-enforced injection prevention)
- **Concurrency** (Parallelizer for IO-bound work)
- **Compile-time safety** (ErrorProne integration)

**Zero dependencies** makes it easy to adopt, and **90% test coverage** means it's reliable.

**Highly recommended for any Java 8+ codebase.**

---

**For complete API reference, see: comprehensive-mug-analysis.md**
