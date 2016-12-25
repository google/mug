# μ
Some Extra Utilities with Java Lambda ([javadoc](http://mu.github.io/mu/apidocs/)).

## Maybe

Represents a value that may have failed with an exception.
Tunnels checked exceptions through stream, no cheating.

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
