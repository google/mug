Disclaimer: This is not an official Google product.

# Mug
A small Java 8 utilities library ([javadoc](http://google.github.io/mug/apidocs/index.html)), with 0 deps. ![](https://travis-ci.org/google/mug.svg?branch=master)

* Stream utilities ([BiStream](https://github.com/google/mug/wiki/BiStream-Explained), [MoreStreams](#morestreams), [Iteration](https://github.com/google/mug/wiki/Iteration-Explained), [Guava Addons](https://google.github.io/mug/mug-guava/apidocs/index.html)):  
    `histogram = zip(times, counts).toMap();`
* [Optionals](#optionals) provides extra utilities for Optional:  
    `optional(id.length() > 0, id)`
* [Substring](https://github.com/google/mug/wiki/Substring-Explained) finds a substring in a string:  
    `String user = first('@').toEnd().removeFrom(email);`
* [Parallelizer](https://github.com/google/mug/wiki/Parallelizer-Explained) An _Executor-friendly_, _interruptible_ alternative to parallel streams.
* Graph utilities ([Walker](https://google.github.io/mug/apidocs/com/google/mu/util/graph/Walker.html), [ShortestPath](https://google.github.io/mug/apidocs/com/google/mu/util/graph/ShortestPath.html))
* [Google Protobuf Java 8 Utilities](https://google.github.io/mug/mug-protobuf/apidocs)

## Installation
### Maven

Add the following to pom.xml:
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug</artifactId>
    <version>6.4</version>
  </dependency>
```

Protobuf utils:
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-protobuf</artifactId>
    <version>6.4</version>
  </dependency>
```

Guava add-ons:
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-guava</artifactId>
    <version>6.4</version>
  </dependency>
```

### Gradle

Add to build.gradle:
```
  implementation 'com.google.mug:mug:6.4'
  implementation 'com.google.mug:mug-guava:6.4'
  implementation 'com.google.mug:mug-protobuf:6.4'
```


## Stream

#### [BiStream](https://google.github.io/mug/apidocs/com/google/mu/util/stream/BiStream.html) streams pairs of objects.

This class closely mirrors JDK `Stream` API (the few extra methods of "its own" are very straight-forward). If you are familiar with Jdk stream, learning curve is minimal.

**Example 1: to concatenate `Map`s:**

```java
import static com.google.mu.util.stream.BiStream.concat;

Map<AccountId, Account> allAccounts = concat(primaryAccouunts, secondaryAccounts).toMap();
```

**Example 2: to combine two streams:**

```java
BiStream.zip(requests, responses)
    .mapToObj(RequestAndResponseLog::new);
```

**Example 3: to build a Map fluently:**

```java
Map<DoctorId, Patient> patientsByDoctorId = BiStream.zip(doctors, patients)
    .filter((doctor, patient) -> patient.likes(doctor))
    .mapKeys(Doctor::getId)
    .collect(toMap());
```

**Example 4: to build Guava ImmutableListMultimap fluently:**

```java
ImmutableListMultimap<ZipCode, Address> addressesByZipCode = BiStream.from(addresses)
    .mapKeys(Address::getZipCode)
    .collect(ImmutableListMultimap::toImmutableListMultimap);
```

**Example 5: to 
a `Map` into sub-maps:**

```java
import static com.google.mu.util.stream.BiCollectors.groupingBy;

Map<Address, PhoneNumber> phonebooks = ...;
Map<State, Map<Address, PhoneNumber>> statePhonebooks = BiStream.from(phonebooks)
    .collect(groupingBy(Address::state, Collectors::toMap))
    .toMap();
```

**Example 6: to merge `Map` entries:**

```java
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;

Map<Account, Money> totalPayouts = projects.stream()
    .map(Project::payments)  // Stream<Map<Account, Money>>
    .collect(flatteningMaps(toMap(Money::add)));
```

**Example 7: to apply grouping over `Map` entries:**

```java
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;
import static java.util.stream.Collectors.summingInt;

Map<EmployeeId, Integer> workerHours = projects.stream()
    .map(Project::getTaskAssignments)  // Stream<Map<Employee, Task>>
    .collect(flatteningMaps(toMap(summingInt(Task::hours))));
```

**Example 8: to turn a `Collection<Pair<K, V>>` to `BiStream<K, V>`:**

```java
BiStream<K, V> stream = RiStream.from(pairs, Pair::getKey, Pair::getValue);
```

**Q: Why not `Map<Foo, Bar>` or `Multimap<Foo, Bar>`?**

A: Sometimes Foo and Bar are just an arbitrary pair of objects, with no key-value relationship. Or you may not trust `Foo#equals()` and `hashCode()`. Instead, drop-in replace your `Stream<Pair<Foo, Bar>>`/`List<Pair<Foo, Bar>>` with `BiStream<Foo, Bar>`/`BiCollection<Foo, Bar>` to get better readability.

**Q: Why not `Stream<FooAndBar>`?**

A: When you already have a proper domain object, sure. But you might find it cumbersome to define a bunch of FooAndBar, PatioChairAndKitchenSink one-off classes especially if the relationship between the two types is only relevant in the local code context.

**Q: Why not `Stream<Pair<Foo, Bar>>`?**

A: It's distracting to read code littered with opaque method names like `getFirst()` and `getSecond()`.


## Substring

**Example 1: strip off a prefix if existent:**
```java
String httpStripped = Substring.prefix("http://").removeFrom(uri);
```

**Example 2: strip off any scheme prefix from a uri:**
```java
String schemeStripped = Substring.upToIncluding(first("://")).removeFrom(uri);
```

**Example 3: split a string in the format of "name=value" into `name` and `value`:**
```java
Substring.first('=').split("name=value").map((name, value) -> ...);
```

**Example 4: replace trailing "//" with "/" :**
```java
Substring.suffix("//").replaceFrom(path, "/");
```


**Example 5: strip off the suffix starting with a dash (-) character :**
```java
last('-').toEnd().removeFrom(str);
```

**Example 6: extract a substring using regex :**
```java
String quoted = Substring.first(Pattern.compile("'(.*?)'"), 1)
    .from(str)
    .orElseThrow(...);
```

**Example 7: find the substring between the first and last curly braces ({) :**
```java
String body = Substring.between(first('{'), last('}'))
    .from(source)
    .orElseThrow(...);
```

## Optionals

**Example 1: to combine two Optional instances into a single one:**
```java
Optional<Couple> couple = Optionals.both(optionalHusband, optionalWife).map(Couple::new);
```

**Example 2: to run code when two Optional instances are both present:**
```java
Optionals.both(findTeacher(), findStudent()).ifPresent(Teacher::teach);
```

**Example 3: or else run a fallback code block:**
```java
static import com.google.mu.util.Optionals.ifPresent;

Optional<Teacher> teacher = findTeacher(...);
Optional<Student> student = findStudent(...);
ifPresent(teacher, student, Teacher::teach)             // teach if both present
    .or(() -> ifPresent(teacher, Teacher::workOut))     // teacher work out if present
    .or(() -> ifPresent(student, Student::doHomework))  // student do homework if present
    .orElse(() -> log("no teacher. no student"));       // or else log
```

**Example 4: wrap a value in Optional if it exists:**
```java
static import com.google.mu.util.Optionals.optionally;

Optional<String> id = optionally(request.hasId(), request::getId);
```

All Optionals utilites propagate checked exception from the the lambda/method references.


#### [MoreStreams](https://google.github.io/mug/apidocs/com/google/mu/util/stream/MoreStreams.html)

**Example 1: to split a stream into smaller-size chunks (batches):**

```java
int batchSize = 5;
MoreStreams.dice(requests, batchSize)
    .map(BatchRequest::new)
    .forEach(batchClient::sendBatchRequest);
```

**Example 2: to iterate over `Stream`s in the presence of checked exceptions or control flow:**

The `Stream` API provides `forEach()` to iterate over a stream, if you don't have to throw checked exceptions.

When checked exception is in the way, or if you need control flow (`continue`, `return` etc.), `iterateThrough()` and `iterateOnce()` can help. The following code uses `iterateThrough()` to write objects into an `ObjectOutputStream`, with  `IOException` propagated:

```java
Stream<?> stream = ...;
ObjectOutput out = ...;
iterateThrough(stream, out::writeObject);
```

with control flow:
```java
for (Object obj : iterateOnce(stream)) {
  if (...) continue;
  else if (...) return;
  out.writeObject(obj);
}
```

**Example 3: to generate a BFS stream:**
```java
Stream<V> bfs = MoreStreams.generate(root, node -> node.children().stream())
    .map(Node::value);
```

**Example 4: to merge maps:**
```java
interface Page {
  Map<Day, Long> getTrafficHistogram();
}

List<Page> pages = ...;

// Merge traffic histogram across all pages of the web site
Map<Day, Long> siteTrafficHistogram = pages.stream()
    .map(Page::getTrafficHistogram)
    .collect(flatMapping(BiStream::from, groupingBy(day -> day, Long::sum)))
    .toMap();
```

#### [BinarySearch](https://google.github.io/mug/mug-guava/apidocs/com/google/mu/util/BinarySearch.html)

The JDK offers binary search algorithms out of the box, for sorted arrays and lists.

But the binary search algorithm is applicable to more use cases. For example:

* You may want to search in a `double` array with a tolerance factor.
    ```java
    Optional<Integer> index =
        BinarySearch.inSortedArrayWithTolerance(doubles, 0.0001).find(3.14)
    ```
* Or search for the range of indexes when the array can have duplicates (at least according to the tolerance factor).
    ```java
    Range<Integer> indexRange =
        BinarySearch.inSortedArrayWithTolerance(doubles, 0.0001).rangeOf(3.14)
    ```
* Or search for the solution to a monotonic polynomial equation.
    ```java
    long polynomial(int x) {
      return 5 * x * x * x + 3 * x + 2;
    }
    
    Optional<Integer> solvePolynomial(long y) {
      return BinarySearch.forInts()
          .find((low, x, high) -> Long.compare(y, polynomial(x));
    }
    ```

#### [Iteration](https://google.github.io/mug/apidocs/com/google/mu/util/stream/Iteration.html)

**Example 1: turn your recursive algorithm into a _lazy_ Stream:**

```java
class DepthFirst<N> extends Iteration<N> {
  private final Set<N> visited = new HashSet<>();
  
  DepthFirst<N> postOrder(N node) {
    if (visited.add(node)) {
      for (N successor : node.successors()) {
        yield(() -> postOrder(successor));
      }
      yield(node);
    }
    return this;
  }
}

Stream<N> postOrder = new DepthFirst<N>().postOrder(root).iterate();
```

**Example 2: implement Fibonacci sequence as a stream:**

```java
class Fibonacci extends Iteration<Long> {
  Fibonacci from(long v0, long v1) {
    yield(v0);
    yield(() -> from(v1, v0 + v1));
    return this;
  }
}

Stream<Long> fibonacci = new Fibonacci().from(0, 1).iterate();
    => [0, 1, 2, 3, 5, 8, ...]
```

## [Selection](https://google.github.io/mug/apidocs/com/google/mu/util/Selection.html)

Have you needed to specify a whitelist of things, while also needed to allow _all_ when the feature becomes ready for prime time?

A common work-around is to use a `Set` to capture the whitelist, and then treat empty set as _all_.

This has a few caveats:

1. The code that checks the whitelist is error prone. You'll need to remember to check the special case for an empty Set:

  ```java
  if (allowedUsers.isEmpty() || allowedUsers.contains(userId)) {
    ...;
  }
  ```
2. If "empty" means all, how do you disable the feature completely, blocking all users?
3. Assume `allowedUsers` in the above example is a flag, it's not super clear to the staff configuring this flag about what it means when it's empty. When your system has a dozen components, each with its own flavor of whitelist, allowlist flags, it's easy to mis-understand and mis-configure.

Instead, use `Selection` to differentiate and make explicit the *all* vs. *none* cases. Code checking against a `Selection` is straight-forward:

```java
if (allowedUsers.has(userId)) {
  ...
}
```

To use it in a flag, you can use the `Selection.parser()` which will accept the '\*' character as indication of accepting all, while setting it empty means to accept none. For example `--allowed_users=*`, `--allowed_users=joe,amy`.


## [MoreCollections](https://google.github.io/mug/apidocs/com/google/mu/util/MoreCollections.html)

Sometimes you may have a short list with elements representing structured data points. For example, if you are trying to parse a human name, which can either be first name only, or in the format of `firstName lastName`, or in addition with middle name, you can do:

```java
import static com.google.mu.util.MoreCollections.findOnlyElements;

String fullName = ...;
List<String> nameParts =
    Substring.first(' ').repeatedly().splitThenTrim(fullName).collect(toList());
Optional<Result> result =
    findOnlyElements(nameParts, firstName -> ...)
        .or(() -> findOnlyElements(nameParts, (firstName, lastName) -> ...))
        .or(() -> findOnlyElements(nameParts, (firstName, middleName, lastName) -> ...));
```


## [MoreCollectors](https://google.github.io/mug/apidocs/com/google/mu/util/stream/MoreCollectors.html)

In the above example, the short list may have 1, 2 or 3 elements. If you know the exact number of elements,
you can use the `onlyElements()` Collector instead.

For example, you may need to parse out the hour, minute and second from a string that looks like `12:05:10`:

```java
import static com.google.mu.util.stream.MoreCollectors.onlyElements;

HourMinuteSecond result =
    Substring.first(':')
        .repeatedly()
        .split("12:05:10")
        .collect(onlyElements((h, m, s) -> new HourMinuteSecond(h, m, s));
```

## [Parallelizer](https://google.github.io/mug/apidocs/com/google/mu/util/concurrent/Parallelizer.html)

An _Executor-friendly_, _interruptible_ alternative to parallel streams.

Designed for running a (large) pipeline of _IO-bound_ (as opposed to CPU-bound) sub tasks in parallel, while limiting max concurrency.

For example, the following snippet uploads a large number of pictures in parallel:
```java
ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
try {
  new Parallelizer(threadPool, numThreads)
      .parallelize(pictures, this::upload);
} finally {
  threadPool.shutdownNow();
}
```

Note that this code will terminate if any picture fails to upload. If `upload()` throws `IOException` and an `IOException` should not terminate the batch upload, the exception needs to be caught and handled:
```java
  new Parallelizer(threadPool, numThreads)
      .parallelize(pictures, pic -> {
        try {
          upload(pic);
        } catch (IOException e) {
          log(e);
        }
      });
```

#### Why not parallel stream?

Like:
```java
pictures.parallel().forEach(this::upload);
```

Reasons not to:
* Parallelizer works with any existing `ExecutorService`.
* Parallelizer supports an in-flight tasks limit.
* Thread **unsafe** input streams or `Iterator`s are okay.
* Upon failure, all pending tasks are canceled.
* Exceptions from worker threads are wrapped so that stack trace isn't misleading.

Fundamentally:
* Parallel streams are for CPU-bound tasks. JDK has built-in magic to optimally use the available cores.
* Parallelizer is for IO-bound tasks.

#### Why not just submitting to a fixed thread pool?

Like:
```java
ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
try {
  pictures.forEach(pic -> threadPool.submit(() -> upload(pic)));
  threadPool.shutdown();
  threadPool.awaitTermination(100, SECONDS);
} finally {
  threadPool.shutdownNow();
}
```

Reasons not to:
1. The thread pool queues all pending tasks. If the input stream is too large to fit in memory, you'll get an `OutOfMemoryError`.
2. Exceptions (including `NullPointerException`, `OutOfMemoryError`) are silently swallowed (but may print stack trace). To propagate the exceptions, the `Future` objects need to be stored in a list and then `Future#get()` needs to be called on every future object after all tasks have been submitted to the executor.
3. Tasks submitted to an executor are independent. One task failing doesn't automatically terminate the pipeline.



## [Retryer](https://google.github.io/mug/apidocs/com/google/mu/util/concurrent/Retryer.html)

* Retry blockingly or _async_
* Configurable and _extensible_ backoff strategies
* Retry on exception or by return value
* Everything is @Immutable and @ThreadSafe

#### To retry blockingly

Blocking the thread for retry isn't always a good idea at server side. It is however simple and being able to propagate exceptions directly up the call stack is convenient:
```java
Account fetchAccountWithRetry() throws IOException {
  return new Retryer()
      .upon(IOException.class, Delay.ofMillis(1).exponentialBackoff(1.5, 4))
      .retryBlockingly(this::getAccount);
}
```

#### To retry asynchronously

```java
CompletionStage<Account> fetchAccountWithRetry(ScheduledExecutorService executor) {
  return new Retryer()
      .upon(IOException.class, Delay.ofMillis(1).exponentialBackoff(1.5, 4))
      .retry(this::getAccount, executor);
}
```

#### To retry an already asynchronous operation
If `getAccount()` itself already runs asynchronously and returns `CompletionStage<Account>`, it can be retried using the `retryAsync()` method.

And for demo purpose, let's use Fibonacci backoff strategy, with a bit of randomization in the backoff to avoid bursty traffic, why not?
```java
CompletionStage<Account> fetchAccountWithRetry(ScheduledExecutorService executor) {
  Random rnd = new Random();
  return new Retryer()
      .upon(IOException.class,
            Delay.ofMillis(30).fibonacci(4).stream()
                .map(d -> d.randomized(rnd, 0.3)))
      .retryAsync(this::getAccount, executor);
}
```
_A side note_: using Stream to transform will eagerly evaluate all list elements before `retryAsync()` is called. If that isn't desirable (like, you have nCopies(10000000, delay)), it's best to use some kind of lazy List transformation library. For example, if you use Guava, then:
```java
Lists.transform(nCopies(1000000, Delay.ofMillis(30)), d -> d.randomized(rnd, 0.3))
```

#### To retry based on return value

Sometimes the API you work with may return error codes instead of throwing exceptions. Retries can be based on return values too:
```java
new Retryer()
    .uponReturn(ErrorCode::BAD, Delay.ofMillis(10).exponentialBackoff(1.5, 4))
    .retryBlockingly(this::depositeMyMoney);
```

Or, use a predicate:
```java
new Retryer()
    .ifReturns(r -> r == null, Delay.ofMillis(10).exponentialBackoff(1.5, 4))
    .retryBlockingly(this::depositeMyMoney);
```

#### Backoffs are just `List<Delay>`

`exponentialBackoff()`, `fibonacci()`, `timed()` and `randomized()` are provided out of the box for convenience purpose only. But at the end of the day, backoffs are just old-school boring `List`s. This makes the backoff strategies extensible. You can create the List in any way you are used to, using any Java library. For example, there isn't a `uniformDelay()` in this library, because there is already `Collections.nCopies(n, delay)`.

Or, to concatenate two different backoff strategies together (first uniform and then exponential), the Java 8 Stream API has a good tool for the job:
```java
new Retryer()
    .upon(RpcException.class,
          Stream.concat(nCopies(3, Delay.ofMillis(1)).stream(),
                        Delay.ofMillis(2).exponentialBackoff(1.5, 4).stream()))
    .retry(...);
```

What about to retry infinitely? `Collections.nCopies(Integer.MAX_VALUE, delay)` isn't infinite but close. JDK only uses O(1) time and space for creating it; same goes for `Delay#exponentialBackoff()` and `Delay#fibonacci()`.

#### To handle retry events

Sometimes the program may need custom handling of retry events, like, for example, to increment a stats counter based on the error code in the exception. Requirements like this can be done with a custom Delay implementation:

```java
class RpcDelay extends Delay<RpcException> {

  @Override public Duration duration() {
    ...
  }

  @Override public void beforeDelay(RpcException e) {
    updateStatsCounter(e.getErrorCode(), "before delay", duration());
  }

  @Override public void afterDelay(RpcException e) {
    updateStatsCounter(e.getErrorCode(), "after delay", duration());
  }
}

return new Retryer()
    .upon(RpcException.class,
          Delay.ofMillis(10).exponentialBackoff(...).stream()
              .map(Delay::duration)
              .map(RpcDelay::new))
    .retry(this::sendRpcRequest, executor);
```

Or, to get access to the retry attempt number, which is also the list's index, here's an example:
```java
class RpcDelay extends Delay<RpcException> {
  RpcDelay(int attempt, Duration duration) {...}

  @Override public void beforeDelay(RpcException e) {
    updateStatsCounter(e.getErrorCode(), "before delay " + attempt, duration());
  }

  @Override public void afterDelay(RpcException e) {...}
}

List<Delay<?>> delays = Delay.ofMillis(10).fibonacci(...);
return new Retryer()
    .upon(RpcException.class,
          IntStream.range(0, delays.size())
              .mapToObj(i -> new RpcDelay(i, delays.get(i).duration())))
    .retry(...);
```

#### To keep track of exceptions

If the method succeeds after retry, the exceptions are by default logged. As shown above, one can override `beforeDelay()` and `afterDelay()` to change or suppress the logging.

If the method fails after retry, the exceptions can also be accessed programmatically through `exception.getSuppressed()`.

## [Funnel](https://google.github.io/mug/apidocs/com/google/mu/util/Funnel.html)

#### The problem

The following code converts a list of objects:

```java
List<Result> convert(List<Input> inputs) {
  List<Result> list = new ArrayList<>();
  for (Input input : inputs) {
    list.add(convertInput(input));
  }
  return list;
}
```

Intuitively, the contract is that the order of results are in the same order as the inputs.

Now assume the input can be of two different kinds, with one kind to be converted through a remote service. Like this:

```java
List<Result> convert(List<Input> inputs) {
  List<Result> list = new ArrayList<>();
  for (Input input : inputs) {
    if (input.needsRemoteConversion()) {
      list.add(remoteService.convertInput(input));
    } else {
      list.add(convertInput(input));
    }
  }
  return list;
}
```

In reality, most remote services are expensive and could use batching as an optimization. How do you batch the ones needing remote conversion and convert them in one remote call?

Perhaps this?

```java
List<Result> convert(List<Input> inputs) {
  List<Result> local = new ArrayList<>();
  List<Input> needRemote = new ArrayList<>();
  for (Input input : inputs) {
    if (input.needsRemoteConversion()) {
      needRemote.add(input);
    } else {
      local.add(convertInput(input));
    }
  }
  List<Result> remote = remoteService.batchConvert(needRemote);
  return concat(local, remote);
}
```

Close. Except it breaks the ordering of results. The caller no longer knows which result is for which input.

Tl;Dr: maintaining the encounter order while dispatching objects to batches requires careful juggling of the indices and messes up the code rather quickly.

#### The tool

Funnel is a simple class designed for this use case:

```java
List<Result> convert(List<Input> inputs) {
  Funnel<Result> funnel = new Funnel<>();
  Funnel.Batch<Input, Result> remoteBatch = funnel.through(remoteService::batchConvert);
  for (Input input : inputs) {
    if (input.needsRemoteConversion()) {
      remoteBatch.accept(input);
    } else {
      funnel.add(convertInput(input));
    }
  }
  return funnel.run();
}
```
That is, define the batches with ```funnel.through()``` and then inputs can flow through arbitrary number of batch conversions. Conversion results flow out of the funnel in the same order as inputs entered the funnel. 
