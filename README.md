# μ
Extra Java 8 Utilities ([javadoc](http://fluentfuture.github.io/mu/apidocs/)).

This library has 0 dependencies.

## Retryer

Retryer is a helper that makes it easier to retry an operation with configurable backoffs. Backoffs are either done synchronously (through `Thread.sleep()`) or asynchronously (using a `ScheduledExecutorService`).

For example:

```java
CompletionStage<Account> future = new Retryer()
    .upon(IOException.class, Delay.exponentialBackoff(ofMillis(1), 2, 3))
    .retry(this::getAccount, executor);
```

In order to customize retry events such as to log differently, client code can create a subclass of Delay:

```java
class CustomDelay extends Delay {
  CustomDelay(Duration duration) {
    super(duration);
  }

  @Override public void beforeDelay(Throwable e) {
    // log
  }

  @Override public void afterDelay(Throwable e) {
    // log
  }
}
```

## Funnel

Ever had the need to convert a list of objects? It's as simple as it gets:

```java
List<Result> convert(List<Input> inputs) {
  List<Result> list = new ArrayList<>();
  for (Input input : inputs) {
    list.add(convertInput(input));
  }
  return list;
}
```
or, who doesn't love lambda?

```java
return inputs.stream()
    .map(this::convertInput)
    .collect(toList());  
```

Normally such API has the contract that the order of results are in the same order as the inputs.

Well. what if now Input can have two different kinds, and one kind need to be converted through a remote service? Again, it's almost as simple:

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

But most remote services are expensive and hence could benefit from batching. Can you batch the ones needing remote conversion and convert them together?

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

Funnel is a simple library to stop this bleeding:

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

So what if there are 3 kinds of inputs and two kinds require two different batch conversions? Funnel supports arbitrary number of batches. Just register them with ```through()``` and ```through()```.

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
