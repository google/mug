# μ
A few Java 8 util classes ([javadoc](http://fluentfuture.github.io/mu/apidocs/)) for some, with 0 dependencies.

![](https://travis-ci.org/fluentfuture/mu.svg?branch=master)

## [Retryer](https://fluentfuture.github.io/mu/apidocs/org/mu/util/Retryer.html)

<<<<<<< HEAD
* Retry blockingly or async
=======
* Retry blockingly or _async_
>>>>>>> master
* Configurable and _extensible_ backoff strategies
* Retry on exception or by return value

#### To retry blockingly

Blocking the thread for retry isn't always a good idea at server side. It is however simple and being able to propagate exceptions directly up the call stack is nice:
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

`getAccount()` itself runs asynchronously and returns `CompletionStage<Account>`? No problem.
And for demo purpose, how about we also use a bit of randomization in the backoff to avoid bursty traffic?
```java
CompletionStage<Account> fetchAccountWithRetry(ScheduledExecutorService executor) {
  Random rnd = new Random();
  return new Retryer()
      .upon(IOException.class,
            Delay.ofMillis(30).exponentialBackoff(1.5, 4).stream()
                .map(d -> d.randomized(rnd, 0.3)))
      .retryAsync(this::getAccount, executor);
}
```

#### To retry based on return value

Sometimes the API you work with may return error codes instead of throwing exceptions. Retries can be based on return values too:
```java
new Retryer()
    .uponReturn(ErrorCode::BAD, Delay.ofMillis(10).exponentialBackoff(1.5, 4))
    .retryBlockingly(this::depositeMyMoney);
```

#### Backoffs are just `List<Delay>`

`exponentialBackoff()`, `timed()` and `randomized()` are provided out of the box for convenience purpose only. But at the end of the day, backoffs are just old-school boring `List`s. You can create the List in any way you are used to. For example, there isn't a `uniformDelay()` in this library, because there is already `Collections.nCopies(n, delay)`.

Or, to concatenate two different backoff strategies together (first uniform and then exponential), the Java 8 Stream API has a good tool for the job:
```java
new Retryer()
    .upon(RpcException.class,
          Stream.concat(nCopies(3, Delay.ofMillis(1)).stream(),
                        Delay.ofMillis(2).exponentialBackoff(1.5, 4).stream()))
    .retry(...);
```

Want to retry infinitely? Too bad, Java doesn't have infinite List. How about `Collections.nCopies(Integer.MAX_VALUE, delay)`? It's not infinite but probably enough to retry until the death of the universe. JDK is smart enough that it only uses O(1) time and space for creating it (`Delay#exponentialBackoff()` follows suit).

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

#### To keep track of exceptions

If the method succeeds after retry, the exceptions are by default logged. As shown above, one can override `beforeDelay()` and `afterDelay()` to change or suppress the loggging.

If the method fails after retry, the exceptions can also be accessed programmatically through `exception.getSuppressed()`.

## [Maybe](https://fluentfuture.github.io/mu/apidocs/org/mu/util/Maybe.html)

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
    // The following re-throw the exception or wrap it.
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
* An (otherwise) Java Optional that tells the reason of absence.
* A computation result that could have failed with expected exception.
* Helps with awkward situations in Java where checked exception isn't the sweet spot.

#### What's not `Maybe`?
* It's not Haskell Maybe (Optional is her cousin).
* It's not Haskell `Either` either. In Java we think of return values and exceptions, not mathematical "Left" and "Right".
* It's not to replace throwing and catching exceptions. Java code should do the Java way. When in Rome.
* It's not designed to write code more "functional" just because you can. Use it where it helps.


## [Funnel](https://fluentfuture.github.io/mu/apidocs/org/mu/util/Funnel.html)

#### The problem

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

#### The tool

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
