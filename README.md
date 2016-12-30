# μ
Tiny Java 8 utilities ([javadoc](http://fluentfuture.github.io/mu/apidocs/)) for some, with 0 dependencies.

## Retryer

Retryer is a helper that makes it easier to retry an operation with configurable backoffs.

Retry blockingly:
```java
Account fetchAccountWithRetry() throws IOException {
  return new Retryer()
      .upon(IOException.class, Delay.ofMillis(1).exponentialBackoff(1.5, 3))
      .retryBlockingly(this::getAccount);
}
```

or asynchronously:
```java
CompletableStage<Account> fetchAccountWithRetry(ScheduledExecutorService executor) {
  return new Retryer()
      .upon(IOException.class, Delay.ofMillis(1).exponentialBackoff(1.5, 3))
      .retry(this::getAccount, executor);
}
```

`getAccount()` itself runs asynchronously and returns `CompletionStage<Account>`? No problem.
And for demo purpose, how about we also use a bit of randomization in the backoff to avoid bursty traffic?
```java
CompletableStage<Account> fetchAccountWithRetry(ScheduledExecutorService executor) {
  Random rnd = new Random();
  return new Retryer()
      .upon(IOException.class,
            Delay.ofMillis(1).exponentialBackoff(1.5, 3).stream()
                .map(d -> d.randomized(rnd, 0.5))
                .collect(toList())),
      .retryAsync(this::getAccount, executor);
}
```

Sometimes the program may need custom handling of retry events, like, for example, to increment a stats counter based on the error code in the exception. Requirements like this can be done with a custom Delay implementation:

```java
class RpcDelay extends Delay<RpcException> {

  @Override public Duration duration() {
    ...
  }

  @Override protected Delay withDuration(Duration duration) {
    return new RpcDelay(duration);
  }

  @Override public void beforeDelay(RpcException e) {
    UpdateStatsCounter(e.getErrorCode(), "before delay", duration());
  }

  @Override public void afterDelay(MySpecialException e) {
    UpdateStatsCounter(e.getErrorCode(), "after delay", duration());
  }
}

return new Retryer()
    .upon(RpcException.class, new RpcDelay(ofMillis(10)).exponentialBackoff(...))
    .retry(this::sendRpcRequest, executor);
```

## Funnel

Ever needed to convert a list of objects? It's trivial:

```java
List<Result> convert(List<Input> inputs) {
  List<Result> list = new ArrayList<>();
  for (Input input : inputs) {
    list.add(convertInput(input));
  }
  return list;
}
```

Normally such API has the contract that the order of results are in the same order as the inputs.

Well. what if Input can be of two different kinds, and one kind needs to be converted through a remote service? Pretty straight-forward too:

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

In reality though, most remote services are expensive and could use batching as an optimization. Can you batch the ones needing remote conversion and convert them in one remote call?

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
  List<Result> remote = remoteService.convertInputs(needRemote);
  return concat(local, remote);
}
```

Close. Except it breaks the ordering of inputs. The caller no longer knows which result is for which input.

Tl;Dr: maintaining the encounter order while dispatching objects to batches requires careful juggling of the indices and messes up the code rather quickly.

Funnel is a simple library to stop the bleeding:

```java
List<Result> convert(List<Input> inputs) {
  Funnel<Result> funnel = new Funnel<>();
  Consumer<Input> remoteBatch = funnel.through(remoteService::convertInputs);
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
All the code has to do is to define the batch with ```funnel.through()``` and then inputs can be added to the batch without breaking encounter order.

What happens if there are 3 kinds of inputs and two kinds require two different batch conversions? Funnel supports arbitrary number of batches. Just define them with ```through()``` and ```through()```.

## Maybe

Represents a value that may have failed with an exception.
Tunnels checked exceptions through streams or futures.

#### Streams

For a stream operation that would have looked like this if checked exception weren't in the way:

```java
return files.stream()
   .map(Files::toByteArray)
   .collect(toList());
```

The code can remain mostly as is by using `Maybe`:

```java
Stream<Maybe<byte[], IOException>> contents = files.stream()
    .map(Maybe.wrap(Files::toByteArray));
return Maybe.collect(contents);
```
And what if you want to log and swallow?

```java
private <T> Stream<T> logAndSwallow(Maybe<T> maybe) {
  return maybe.catching(logger::warn);
}

List<String> getJobNames() {
  return pendingJobIds.stream()
      .map(Maybe.wrap(this::fetchJob))
      .flatMap(this::logAndSwallow)
      .map(Job::getName)
      .collect(Collectors.toList());
}
```

#### Futures

In asynchronous programming, checked exceptions are wrapped inside ExecutionException. By the time the caller catches it, the static type of the causal exception is already lost. The caller code usually resorts to `instanceof MyException`.

Alternatively, if the asynchronous code returns `Maybe<Foo, MyException>` instead, then upon getting a `Future<Maybe<Foo, MyException>>`, the exception can be handled type safely using `maybe.catching()` or `maybe.orElse()`.

#### Conceptually, what is `Maybe`?
* A computation result that could have failed.
* Helps overcome awkward situations in Java where checked exception isn't the sweet spot.

#### What isn't `Maybe`?
* It's not Haskell Maybe (Optional is her cousin).
* It's not Haskell `Either` either. In Java we think of return values and exceptional cases, not "Left" or "Right".
* It's not to replace throwing and catching exceptions. Java code should do the Java way. When in Rome.
* It's not designed for writing more "functional" code or shifting your programming paradigm. Use it where it helps.
