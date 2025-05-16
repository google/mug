# `whileNotNull()`: Generating *finite* Lazy Streams

In Java, generating a stream of values is easy — unless you want it to end naturally.

The standard library gives us:

```java
Stream.generate(() -> ...)
Stream.iterate(seed, unaryOperator)
```

Both are useful. Both are infinite. And both require extra control logic if we want the stream to terminate on its own.
For example, if a supplier returns `null` to indicate the end of input, we might write:

```java
Stream.generate(supplier)
    .takeWhile(Objects::nonNull)
```

This works, but it's awkward. The termination logic is now split across two places.

This is where `whileNotNull()` comes in.

## `whileNotNull(Supplier<T>)`

```java
<T> Stream<T> whileNotNull(Supplier<? extends T> next)
```

This utility does one thing: it repeatedly calls the supplier until it returns `null`, and returns a lazy `Stream<T>` of the non-null results.

It's a compact way to express:

- “Keep pulling values while there are more.”
- “Advance some internal state, and expose it as a stream.”
- “Stop when the input source is exhausted — no counters, no exceptions.”

The elements are evaluated lazily — nothing runs until the stream is consumed.

## Use cases

### 1. Reading lines from a `BufferedReader`

```java
Stream<String> lines = whileNotNull(reader::readLine);
```

This behaves similarly to `reader.lines()`, but works with any API that returns `null` at EOF.

### 2. Paginated APIs

```java
Stream<Page> pages = whileNotNull(() -> {
  Page page = fetch(token);
  token = page.nextToken(); // update external state
  return page.isEmpty() ? null : page;
});
```

This pattern replaces `do/while` loops for paginated API consumption — especially when combined with `.flatMap(Page::items)` or `.limit(n)`.

## Why not just `generate().takeWhile()`?

You can. But there are limitations:

- It always evaluates one more element just to throw it away.
- The termination condition is no longer colocated with generation logic.
- `takeWhile()` is not available in Java 8 (Mug is).

`whileNotNull()` addresses these with a single construct. No sentinels, no mutations, no filtering required.

## A structural primitive

`whileNotNull()` isn't usually used to express high-level logic on its own.  
But it's a structural building block for other lazy APIs.

For example, in the `mug` libraries:

- `Substring.word().repeatedly().from(sentence)` returns a lazy stream of words.
- `new StringFormat(...).scan(input)` yields structured substrings from a format definition.
- `Walker` uses it to walk a graph lazily.

These APIs internally rely on `whileNotNull()` to express "keep extracting values until the input is consumed" — without manual cursor tracking or state mutation.

## Into structured recursion: `Iteration<T>`

In more advanced cases — such as recursive data traversal, paginated depth-first search, or dynamic tree unfolding — `whileNotNull()` also serves as the evaluation driver behind the `Iteration<T>` class.

`Iteration<T>` turns recursive logic into a lazy, stack-safe stream pipeline:

```java
class Fibonacci extends Iteration<Integer> {
  Fibonacci fib(int a, int b) {
    this.yield(a);
    this.yield(() -> fib(b, a + b));
    return this;
  }
}

Stream<Integer> sequence = new Fibonacci().fib(0, 1).iterate().limit(10);
```

The key point: just like `whileNotNull()`, this structure evaluates *only one element at a time*.
It doesn’t consume the whole tree or chain up front, and it avoids stack overflow entirely.

## When to use

`whileNotNull()` is most useful when:

- You want to express stream-based control flow, but don't know in advance how many elements there will be.
- You’re interacting with APIs that use `null` as end-of-input.
- You want to write structured, lazy generation without introducing stateful iterators or mutable fields.
- You want the termination logic and generation logic to be in one place.

It’s not intended for:
- Use cases where `null` is a valid value (consider `Optional`-based APIs instead)
- Parallel streams (like most streams, it’s single-threaded)
- Situations where a `for` loop would be more readable — and there are many.

## Closing thoughts

In a language where `Stream.generate()` and `Stream.iterate()` assume that everything goes on forever,
`whileNotNull()` offers a simple alternative: generate, until there's nothing left.
