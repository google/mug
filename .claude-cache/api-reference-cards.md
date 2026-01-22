# Mug API Reference Cards

Quick reference for the most commonly used Mug APIs.

---

## Substring API Card

### Static Factory Methods
```java
// Basic patterns
Pattern prefix(String str)
Pattern suffix(String str)
Pattern first(String str)
Pattern first(char c)
Pattern first(Pattern regex)
Pattern last(char c)
Pattern consecutive(CharPredicate)

// Delimiters
Pattern before(Pattern p)
Pattern after(Pattern p)
Pattern between(String open, String close)
Pattern spanningInOrder(String... delimiters)

// Constants
Pattern NONE      // Never matches
Pattern BEGINNING // Matches at start (empty)
Pattern END       // Matches at end (empty)
```

### Pattern Instance Methods
```java
// Extract
Optional<Match> from(String input)

// Modify
String removeFrom(String input)
String replaceFrom(String input, String replacement)
<B> Stream<B> mapFrom(String input, Function<Match, B> func)

// Compose
Pattern or(Pattern alternative)
Pattern then(Pattern next)
Pattern extendTo(Pattern end)
Pattern skip(int nChars)
RepeatingPattern repeatedly()

// Split
Stream<Match> split(String input)
Entry<String, String> splitThenTrim(String input)
Stream<Entry<String, String>> splitThenTrimKeyValuesAround(Pattern, String input)
```

### Match Class
```java
int index()      // Position
int length()     // Length
String toString() // Matched text
Match skip(int n) // Skip n chars
boolean isEmpty() // Check empty
```

---

## BiStream API Card

### Static Factories
```java
BiStream<K, V> empty()
BiStream<K, V> of(K k1, V v1)
BiStream<K, V> of(K k1, V v1, K k2, V v2, ...) // up to 8
BiStream<K, V> from(Map<K, V> map)
BiStream<T, T> biStream(Collection<T> elements)
BiStream<L, R> zip(Collection<L> left, Collection<R> right)
BiStream<K, V> concat(BiStream<K, V>... streams)
```

### Transform
```java
BiStream<K2, V2> map(BiFunction<K, V, Entry<K2, V2>>)
BiStream<K2, V> mapKeys(Function<K, K2>)
BiStream<K, V2> mapValues(Function<V, V2>)
BiStream<K, V2> mapValues(BiFunction<K, V, V2>)
BiStream<K2, V2> flatMap(BiFunction<K, V, BiStream<K2, V2>>)
BiStream<K2, V> flatMapKeys(Function<K, Stream<K2>>)
BiStream<K, V2> flatMapValues(Function<V, Stream<V2>>)
```

### Filter
```java
BiStream<K, V> filter(BiPredicate<K, V>)
BiStream<K, V> filterKeys(Predicate<K>)
BiStream<K, V> filterValues(Predicate<V>)
BiStream<K, V> skipIf(BiPredicate<K, V>)  // Remove matching
```

### Collect
```java
Map<K, V> toMap()
<R> R collect(BiCollector<K, V, R> collector)
void forEach(BiConsumer<K, V>)
```

### Streams
```java
Stream<K> keys()
Stream<V> values()
<T> Stream<T> mapToObj(BiFunction<K, V, T>)
```

### Terminal
```java
BiOptional<K, V> findFirst()
BiOptional<K, V> findAny()
boolean noneMatch(BiPredicate<K, V>)
```

### Sort
```java
BiStream<K, V> sortedByKeys(Comparator<K>)
BiStream<K, V> sortedByValues(Comparator<V>)
BiStream<K, V> distinct()
```

---

## StringFormat API Card

### Constructor & Static Methods
```java
StringFormat(String format)              // Constructor
static String using(String template, Object... args)  // Fast format
static Pattern span(String format)       // To Substring.Pattern
static <T> Template<T> to(Function<String, T> creator, String format)
static <T> Template<T> template(String tmpl, Interpolator<T> interpolator)
```

### Format
```java
String format(Object... args)
```

### Parse
```java
<R> R parse(String input, Function<R, ?> extractor)
<R> R parseOrThrow(String input, Function<R, ?> extractor)
Optional<R> parseOptional(String input, Function<R, ?> extractor)
<R> R parseGreedy(String input, Function<R, ?> extractor)
Stream<R> scan(String input, Function<R, ?> extractor)
```

### Template
```java
interface Template<T> {
  T with(Object... params);
  String toString();
}
```

---

## BiCollectors API Card

### Grouping
```java
BiCollector<K, V, Map<K, List<V>>> groupingBy()
BiCollector<K, V, Map<K, V>> groupingBy(BinaryOperator<V> reducer)
Collector<T, ?, BiStream<K, G>> groupingByEach(Function<T, K>, Function<T, Stream<V>>)
```

### Adjacency
```java
Collector<T, ?, BiStream<T, T>> toAdjacentPairs()  // (0,1), (1,2)...
```

### Partitioning
```java
Collector<E, ?, Both<List<E>, List<E>>> partitioningBy(Predicate<Entry<K, V>>)
```

### Min/Max
```java
BiCollector<T, BiOptional<T, T>> minMax(Comparator)
Collector<T, ?, R> allMin(Comparator, Collector)
Collector<T, ?, R> allMax(Comparator, Collector)
```

### Fixed Size
```java
FixedSizeCollector<T, ?, R> onlyElement()
FixedSizeCollector<T, ?, List<T>> onlyElements(int count)
FixedSizeCollector<T, ?, R> combining(MapFrom3<T, R>) // up to MapFrom8
```

---

## MoreCollectors API Card

### Mapping
```java
Collector<T, ?, R> mapping(Function<T, F>, Collector<F, ?, R>)
Collector<T, ?, R> flatMapping(Function<T, Stream<F>>, Collector<F, ?, R>)
```

### Custom Map
```java
Collector<T, ?, M> toMap(Function<T, K>, Function<T, V>, BinaryOperator<V>, Supplier<M>)
```

### Fixed Size
```java
FixedSizeCollector<T, ?, R> onlyElement()
FixedSizeCollector<T, ?, List<T>> onlyElements(int n)
FixedSizeCollector<T, ?, R> combining(MapFrom3-8<T, R>)
```

### Partitioning
```java
Collector<E, ?, Both<R, R>> partitioningBy(Predicate, Collector, Collector)
```

### Min/Max
```java
Collector<T, ?, BiOptional<T, T>> minMax(Comparator)
Collector<T, ?, R> allMin(Comparator, Collector)
Collector<T, ?, R> allMax(Comparator, Collector)
```

### Switching
```java
Collector<T, ?, R> switching(Predicate, Collector then, Collector otherwise)
```

### Post-Process
```java
Collector<T, ?, List<T>> toListAndThen(Consumer<List<T>> finisher)
```

---

## Parallelizer API Card

### Constructor
```java
Parallelizer(ExecutorService executor, int maxConcurrency)
```

### Parallelize
```java
void parallelize(Stream<T> inputs, Consumer<T> consumer) throws InterruptedException
void parallelize(Stream<T> inputs, Consumer<T> consumer, Duration timeout)
  throws TimeoutException, InterruptedException
void parallelizeUninterruptibly(Stream<T> inputs, Consumer<T> consumer)
void parallelize(Stream<Runnable> tasks) throws InterruptedException
```

### Collector
```java
Collector<I, ?, BiStream<I, O>> inParallel(Function<I, O> func)
```

---

## GraphWalker API Card

### Factory
```java
Walker<N> inGraph(Function<N, Stream<N>> findSuccessors)
Walker<N> inTree(Function<N, Stream<N>> findChildren)
```

### Traversal
```java
Stream<N> preOrderFrom(Iterable<N> startNodes)
Stream<N> postOrderFrom(Iterable<N> startNodes)
Stream<N> breadthFirstFrom(Iterable<N> startNodes)
List<N> topologicalOrderFrom(Iterable<N> startNodes) throws CyclicGraphException
Optional<Stream<N>> detectCycleFrom(Iterable<N> startNodes)
Stream<List<N>> stronglyConnectedComponentsFrom(Iterable<N> startNodes)
```

---

## Iteration API Card

### Emit
```java
void emit(T value)
void emit(T[] values)
void emit(Iterable<T> values)
void emit(Stream<T> values)
```

### Lazy Recursion
```java
void lazily(Runnable recursiveCall)
```

### Control Flow
```java
void ifElse(boolean condition, Runnable ifTrue, Runnable ifFalse)
void ifPresent(Optional<T> value, Consumer<T> consumer)
void whileTrue(BooleanSupplier condition, Runnable body)
void forIndices(int start, int end, IntConsumer body)
```

### Execute
```java
Stream<T> iterate()
```

---

## SafeSql API Card

### Factory
```java
static SafeSql of(String template, Object... args)
static SafeSql of(QueryOptions options, String template, Object... args)
```

### Compose
```java
SafeSql and(SafeSql other)
SafeSql or(SafeSql other)
SafeSql with(String clause, Object... args)
```

### Execute
```java
String toString()  // SQL string
List<Object> parameters()  // Parameters
<T> List<T> query(ResultMapper<T> mapper, Connection conn)
<T> Optional<T> querySingle(ResultMapper<T> mapper, Connection conn)
void execute(StatementSetter stmt)
```

### Template
```java
static Template<Query> template(String template, Interpolator<Query> interpolator)
```

---

## MoreCollections API Card

### Find First N
```java
Optional<R> findFirstElements(Collection<T>, BiFunction<T, T, R>)  // 2 elements
Optional<R> findFirstElements(Collection<T>, MapFrom3<T, R>)       // 3 elements
Optional<R> findFirstElements(Collection<T>, MapFrom4-8<T, R>)     // 4-8 elements
```

### Find Only N (exact count)
```java
Optional<R> findOnlyElements(Collection<T>, BiFunction<T, T, R>)  // exactly 2
Optional<R> findOnlyElements(Collection<T>, MapFrom3-8<T, R>)     // exactly 3-8
```

---

## DateTimeFormats API Card

### Format Detection
```java
DateTimeFormatter formatOf(String example)
DateTimeFormatter formatOf(String example, String zoneId)
```

### Examples
```java
formatOf("2024-03-14 10:00:00.123 America/New_York")
formatOf("Mar 14, 2024")
formatOf("2024/03/14")
```

---

## Checked* API Card

### Interfaces
```java
@FunctionalInterface interface CheckedFunction<T, R> {
  R apply(T t) throws Throwable;
}

@FunctionalInterface interface CheckedConsumer<T> {
  void accept(T t) throws Throwable;
}

@FunctionalInterface interface CheckedSupplier<T> {
  T get() throws Throwable;
}

@FunctionalInterface interface CheckedRunnable {
  void run() throws Throwable;
}

@FunctionalInterface interface CheckedBiFunction<T, U, R> {
  R apply(T t, U u) throws Throwable;
}

@FunctionalInterface interface CheckedBiConsumer<T, U> {
  void accept(T t, U u) throws Throwable;
}

@FunctionalInterface interface CheckedBiPredicate<T, U> {
  boolean test(T t, U u) throws Throwable;
}
```

---

## Functional Interfaces (MapFrom*)

### Arity-Specific Functions
```java
@FunctionalInterface interface TriFunction<T, U, V, R> {
  R apply(T t, U u, V v);
}

@FunctionalInterface interface Function4<T, U, V, W, R> {
  R apply(T t, U u, V v, W w);
}

@FunctionalInterface interface MapFrom3<T, R> {
  R map(T t1, T t2, T t3);
}

@FunctionalInterface interface MapFrom4<T, R> {
  R map(T t1, T t2, T t3, T t4);
}

// ... MapFrom5 through MapFrom8
```

---

## Optional Extensions

### BiOptional<K, V>
```java
static BiOptional<K, V> of(K key, V value)
static BiOptional<K, V> empty()
static BiOptional<K, V> ofNullable(K key, V value)

boolean isPresent()
void ifPresent(BiConsumer<K, V> consumer)
BiOptional<K, V> filter(BiPredicate<K, V>)
BiOptional<K2, V> mapKeys(Function<K, K2>)
BiOptional<K, V2> mapValues(Function<V, V2>)
Optional<Entry<K, V>> toOptional()
```

### Both<L, R>
```java
static Both<L, R> of(L left, R right)
L left()
R right()
```

### Optionals
```java
static Optional<T> optionally(boolean condition, Supplier<T> supplier)
static Optional<T> fromNullable(T value)
static Optional<T> fromJavaUtil(java.util.Optional<T> optional)
```

---

## MoreStreams API Card

### Stream Creation
```java
Stream<T> whileNotNull(Supplier<T> supplier)
Stream<T> withSideEffect(Stream<T> upstream, Consumer<T> sideEffect)
Stream<T> iterateOnce(Iterator<T> iterator)
Stream<T> concat(Stream<T>... streams)
Stream<Entry<L, R>> zip(Stream<L> left, Stream<R> right)
```

---

## Quick Examples

### Substring
```java
// Extract domain from email
String domain = Substring.after(first('@')).from("user@gmail.com").get();

// Remove prefix
String clean = Substring.prefix("http://").removeFrom(url);

// Between delimiters
String value = Substring.between("(", ")").from("func(foo)").get();

// Split key-value
Entry<String, String> kv = Substring.first('=').splitThenTrim("key=value").get();
```

### BiStream
```java
// From Map
BiStream.from(userMap)
  .filterValues(user -> user.isActive())
  .mapKeys(User::getId)
  .toMap();

// Zip
BiStream.zip(names, ages).toMap();

// Custom collector
ImmutableMap<String, Integer> result = BiStream.from(map)
  .collect(ImmutableMap::toImmutableMap);
```

### StringFormat
```java
// Parse
new StringFormat("users/{userId}/posts/{postId}")
  .parse(path, (userId, postId) -> loadPost(userId, postId));

// Format
String msg = StringFormat.using("Hello {name}", name);
```

### Parallelizer
```java
new Parallelizer(executor, 10)
  .parallelize(items.stream(), this::process);

BiStream<Input, Output> results = inputs.stream()
  .collect(parallelizer.inParallel(this::process));
```

### GraphWalker
```java
List<Node> order = Walker.inGraph(n -> n.getDependencies())
  .topologicalOrderFrom(root);

Optional<Stream<Node>> cycle = Walker.inGraph(successors)
  .detectCycleFrom(start);
```

### Iteration
```java
Stream<Foo> foos = new Iteration<Foo>() {
  Foos paginate(Request req) {
    Response res = service.list(req);
    emit(res.getItems());
    if (!res.getNextPageToken().isEmpty()) {
      lazily(() -> paginate(req.withToken(res.getNextPageToken())));
    }
    return this;
  }
}.paginate(initialRequest).iterate();
```

### SafeSql
```java
SafeSql query = SafeSql.of(
  "SELECT `{cols}` FROM Users WHERE id = {id}",
  "name, email", userId);

List<User> users = query.query(UserMapper, connection);
```

---

**See comprehensive-mug-analysis.md for full details.**
