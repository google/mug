# μ
Some Extra Utilities with Java Lambda ([javadoc](http://fluentfuture.github.io/mu/apidocs/)).

This library has 0 dependencies.

## Maybe

Represents a value that may have failed with an exception.
Tunnels checked exceptions through streams or futures.

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

List<String> getPendingJobNames() {
  return pendingJobIds.stream()
      .map(Maybe.wrap(this::fetchJob))
      .flatMap(this::logAndSwallow)
      .filter(Job::isPending)
      .map(Job::getName)
      .collect(Collectors.toList());
}
```

In asynchronous programming, checked exceptions are wrapped inside ExecutionException. By the time the caller catches it, the static type of the causal exception is already lost. The caller code usually resorts to `instanceof MyException`.

Alternatively, if the asynchronous code returns `Maybe<Foo, MyException>` instead, then upon getting a `Future<Maybe<Foo, MyException>>`, the exception can handled static type safely using `maybe.catching()` or `maybe.orElse()`.

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
