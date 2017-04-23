Disclaimer: This is not an official Google product.

# MµG
A small Java 8 utilities library ([javadoc](http://google.github.io/mug/apidocs/index.html)), with 0 deps. ![](https://travis-ci.org/google/mug.svg?branch=master)

* [Stream utilities](#stream-utilities).
* [Retryer](#retryer) retries.
* [Maybe](#maybe) tunnels checked exceptions through streams or futures.
* [Funnel](#funnel) flows objects through batch conversions in FIFO order.
* [Parallelizer](#parallelizer) Runs a pipeline of tasks in parallel while limiting the number of in-flight tasks.
* Functional interfaces that allow checked exceptions.

## Maven

Add the following to pom.xml:
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug</artifactId>
    <version>1.1</version>
  </dependency>
```

## Stream Utilities

#### [BiStream](https://google.github.io/mug/apidocs/com/google/mu/util/stream/BiStream.html) streams pairs of objects.

This class closely mirrors Jdk `Stream` API (the few extra methods of "its own" are very straight-forward). If you are familiar with Jdk stream, learning curve is minimal.

**Example 1: to iterate over a stream with indices:**

```java
BiStream.indexed(inputs.stream())
    .forEach((i, v) -> System.out.println(i + ": " + v));
```

or to convert and copy to a pre-sized output list:
```java
List<String> outputs = Arrays.asList(new String[inputs.size()]);
BiStream.indexed(inputs.stream())
    .peek((i, v) -> System.out.println(i + ": " + v))
    .mapValues(Object::toString)
    .forEach(outputs::set);
```

**Example 2: to combine two streams:**

```java
BiStream.zip(requests, responses)
    .map(RequestAndResponseLog::new);
```

**Example 3: to fluently build a Map:**

```java
Map<DoctorId, Patient> patientsByDoctorId = BiStream.zip(doctors, patients)
    .filter((doctor, patient) -> patient.likes(doctor))
    .mapKeys(Doctor::getId)
    .toMap();
```

**Example 4: to fluently build Guava ImmutableListMultimap:**

```java
ImmutableListMultimap<ZipCode, Address> addressesByZipCode = biStream(addresses)
    .mapKeys(Address::getZipCode)
    .collect(ImmutableListMultimap::toImmutableListMultimap);
```

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

## [Retryer](https://google.github.io/mug/apidocs/com/google/mu/util/Retryer.html)

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

`exponentialBackoff()`, `fibonacci()`, `timed()` and `randomized()` are provided out of the box for convenience purpose only. But at the end of the day, backoffs are just old-school boring `List`s. You can create the List in any way you are used to. For example, there isn't a `uniformDelay()` in this library, because there is already `Collections.nCopies(n, delay)`.

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

If the method succeeds after retry, the exceptions are by default logged. As shown above, one can override `beforeDelay()` and `afterDelay()` to change or suppress the loggging.

If the method fails after retry, the exceptions can also be accessed programmatically through `exception.getSuppressed()`.

## [Maybe](https://google.github.io/mug/apidocs/com/google/mu/util/Maybe.html)

Represents a value that may have failed with an exception.
Tunnels checked exceptions through streams or futures.

#### Streams

For a stream operation that would have looked like this if checked exception weren't in the way:

```java
return files.stream()
   .map(Files::toByteArray)
   .filter(b -> b.length > 0)
   .collect(toList());
```

`Maybe` can be used to wrap the checked exception through the stream operations:

```java
import static com.google.mu.util.Maybe.byValue;
import static com.google.mu.util.Maybe.maybe;

Stream<Maybe<byte[], IOException>> stream = files.stream()
    .map(maybe(Files::toByteArray))
    .filter(byValue(b -> b.length > 0));
List<byte[]> contents = new ArrayList<>();
Iterate.through(stream, m -> contents.add(m.orElseThrow()));
return contents;
```

#### Futures

In asynchronous programming, checked exceptions are wrapped inside ExecutionException or CompletionException. By the time the caller catches it, the static type of the causal exception is already lost. The caller code usually resorts to `instanceof MyException`. For example, the following code recovers from AuthenticationException:

```java
CompletionStage<User> assumeAnonymousIfNotAuthenticated(CompletionStage<User> stage) {
  return stage.exceptionally((Throwable e) -> {
    Throwable actual = e;
    if (e instanceof ExecutionException || e instanceof CompletionException) {
      actual = e.getCause();
    }
    if (actual instanceof AuthenticationException) {
      return new AnonymousUser();
    }
    // The following re-throws the exception and possibly wraps it.
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    if (e instanceof Error) {
      throw (Error) e;
    }
    throw new CompletionException(e);
  });
}
```

Alternatively, if the asynchronous code returns `Maybe<Foo, AuthenticationException>` instead, then upon getting a `Future<Maybe<Foo, AuthenticationException>>`, the exception can be handled type safely using `maybe.catching()` or `maybe.orElse()` etc.
```java
CompletionStage<User> assumeAnonymousIfNotAuthenticated(CompletionStage<User> stage) {
  CompletionStage<Maybe<User, AuthenticationException>> authenticated =
      Maybe.catchException(AuthenticationException.class, stage);
  return authenticated.thenApply(maybe -> maybe.orElse(e -> new AnonymousUser()));
}
```

#### Conceptually, what is `Maybe`?
* An (otherwise) Java Optional parameterized by the exception type.
* A computation result that could have failed with expected exception.
* Helps with awkward situations in Java where checked exception isn't the sweet spot.

#### What's not `Maybe`?
* It's not Haskell Maybe (Optional is her cousin).
* It's not Haskell `Either` either. In Java we think of return values and exceptions, not mathematical "Left" and "Right".
* It's not to replace throwing and catching exceptions. Java code should do the Java way. When in Rome.
* It's not designed to write code more "functional" just because you can. Use it where it helps.


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

## [Parallelizer](https://google.github.io/mug/apidocs/com/google/mu/util/concurrent/Parallelizer.html)

Runs a (large) pipeline of tasks in parallel while limiting the number of in-flight tasks.

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

Some major shopping-list differences:
* Parallelizer works with any existing `ExecutorService`.
* Parallelizer supports an in-flight tasks limit.
* Thread **unsafe** input streams or `Iterator`s are okay.
* Upon failure, all pending tasks are canceled.
* Exceptions from worker threads are wrapped so that stack trace isn't misleading.

But fundamentally:
* Parallel streams are best when CPU is the bottle-neck. JDK has built-in magic to optimally use the available cores so why manually tune anything?
* Parallelizer is for parallelizing tasks where IO or external services are the bottleneck.

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

Again, use case is different:
1. The thread pool queues all pending tasks. If the input stream is too large to fit in memory, you'll get an `OutOfMemoryError`.
2. Exceptions (including `NullPointerException`, `OutOfMemoryError`) are silently swallowed (but may print stack trace). To propagate the exceptions, the `Future` objects need to be stored in a list and then `Future#get()` needs to be called on every future object after all tasks have been submitted to the executor.
3. Tasks submitted to an executor are independent. One task failing doesn't automatically terminate the pipeline.
