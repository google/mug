# Advent of Code 2024 Day 3: Regex vs. dot-parse

[Advent of Code 2024 Day 3](https://adventofcode.com/2024/day/3) is about extracting instructions
from noisy text. The article
[Parser Combinators Beat Regexes](https://entropicthoughts.com/parser-combinators-beat-regexes)
uses it to compare regexes with parser combinators. This document walks through JDK regex and
dot-parse solutions and compares their performance. The code is in
[AdventOfCodeDay3Benchmark.java](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/AdventOfCodeDay3Benchmark.java),
and both parts are benchmarked on the same input (see [Benchmark setup](#benchmark-setup)).

## Part 1

### Problem

The input is corrupted memory with `mul(X,Y)` instructions scattered through noise. The answer is
the sum of `X * Y` over the well-formed instructions. Anything else, such as `mul(9,10]` or
`mul ( 1 , 2 )`, is ignored.

```
xmul(3,4)+mul(9,10]what()mul[5,6]mul(7,8)!mul ( 1 , 2 )
```

Only `mul(3,4)` and `mul(7,8)` count: `3 * 4 + 7 * 8 = 68`.

### Regex

This is the article's regex:

```java
private static final Pattern MUL_REGEX = Pattern.compile("mul\\((\\d+),(\\d+)\\)");

int sum = 0;
Matcher matcher = MUL_REGEX.matcher(input);
while (matcher.find()) {
  sum += parseInt(matcher.group(1)) * parseInt(matcher.group(2));
}
```

- `find()` moves from one match to the next and passes over everything else.
- The pattern starts with the literal `mul(`, so the JDK uses a Boyer-Moore search to skip ahead to
  candidates.

### dot-parse

```java
private static final Parser<Integer> MUL = first("mul(")
    .then(
        sequence(
                digits().followedBy(","),
                digits().followedBy(")"),
                (a, b) -> parseInt(a) * parseInt(b))
            .orElse(null));

MUL.probe(input).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
```

- `first("mul(")` jumps to the next `mul(` with `String.indexOf()`, so the noise needs no grammar.
- `orElse(null)` turns a near miss such as `mul(9,10]` into `null` instead of a failure, so
  `probe()` keeps going until no `mul(` is left. `filter(Objects::nonNull)` drops the nulls.

### Benchmark

| Solution | µs/op | ops/s |
| :--- | ---: | ---: |
| dot-parse | 51.8 | 19,299.6 ± 318.4 |
| regex | 62.6 | 15,986.7 ± 408.9 |

dot-parse is 1.21x as fast as regex.

### Performance analysis

Both solutions look for the literal `mul(` first and parse only from there: dot-parse with
`String.indexOf()`, the regex with a Boyer-Moore search.

## Part 2

### Problem

The memory also contains `don't()` and `do()`. `don't()` disables the `mul` instructions that
follow it, and `do()` enables them again. The state carries across line breaks. Only enabled
instructions count.

```
mul(3,4)&don't()mul(5,5)what()mul(6,6)do()?mul(2,10)
```

`mul(5,5)` and `mul(6,6)` are disabled: `3 * 4 + 2 * 10 = 32`.

There are two approaches: remove the disabled regions (from a `don't()` to the next `do()`, or to
the end of the input) and apply part 1 to the rest, or scan all three instructions in order and
track the state.

### Split with regex

```java
private static final Pattern DISABLED_REGEX =
    Pattern.compile("(?s)don't\\(\\).*?(?:do\\(\\)|\\z)");

DISABLED_REGEX.splitAsStream(input).mapToInt(segment -> regexSum(segment)).sum();
```

`regexSum()` is the part 1 regex loop.

- The lazy `.*?` ends a region at the first `do()`. `\z` ends the last region at the end of the
  input.
- `(?s)` lets `.` match line breaks. Without it, regions that span lines are not removed.

### Split with dot-parse

```java
private static final Substring.RepeatingPattern DISABLED = Substring.between(
        Substring.first("don't()"), INCLUSIVE, Substring.first("do()").or(END), INCLUSIVE)
    .repeatedly();

DISABLED.split(input).mapToInt(segment -> dotParseSum(segment.toString())).sum();
```

`dotParseSum()` is the part 1 dot-parse pipeline, and
[`Substring`](../mug/src/main/java/com/google/mu/util/Substring.java) is from Mug.

- A disabled region runs from `don't()` to the next `do()`, or to the end of the input (`END`).
  `repeatedly().split()` returns the enabled text between the regions.
- `first()` uses `String.indexOf()`, so line breaks need no special handling.
- The part 1 parser runs unchanged on each enabled segment. A single pass such as
  `anyOf(disabledRegion, MUL)` would not work, because `first("mul(")` can jump past a `don't()`.

### Regex + state transition

```java
private static final Pattern INSTRUCTION_REGEX =
    Pattern.compile("mul\\((\\d+),(\\d+)\\)|(do\\(\\))|(don't\\(\\))");

int sum = 0;
boolean enabled = true;
Matcher matcher = INSTRUCTION_REGEX.matcher(input);
while (matcher.find()) {
  if (matcher.start(3) >= 0) {
    enabled = true;
  } else if (matcher.start(4) >= 0) {
    enabled = false;
  } else if (enabled) {
    sum += parseInt(matcher.group(1)) * parseInt(matcher.group(2));
  }
}
```

- The alternation finds all three instructions in input order. Groups 3 and 4 identify `do()` and
  `don't()`, which set the flag.
- The escaped parentheses keep the two apart: with `do|don't`, `do` would match the start of every
  `don't`.

### Benchmark

| Solution | µs/op | ops/s |
| :--- | ---: | ---: |
| split with dot-parse | 44.2 | 22,638.5 ± 343.9 |
| split with regex | 111.6 | 8,961.9 ± 146.6 |
| regex + state transition | 149.6 | 6,686.3 ± 245.8 |

Split with dot-parse is 2.53x as fast as split with regex and 3.39x as fast as regex + state
transition.

### Performance analysis

About half of the input is disabled, and the solutions differ in how they get past it:

- **Split with dot-parse** crosses each disabled region with a single `indexOf("do()")` and never
  parses the disabled text.
- **Split with regex** finds `don't()` with Boyer-Moore, but the lazy `.*?` then advances one
  character at a time to the next `do()`, reading each character of the disabled regions about
  twice.
- **Regex + state transition** has no literal prefix to search for, so the engine tries the
  alternation at each position. It reads about 3x as many characters as the part 1 regex, and it
  still matches every disabled `mul`, which the flag then ignores.

## Benchmark setup

Advent of Code inputs may not be redistributed, so the benchmark generates one with the profile of
real inputs: 19 KB in 6 lines, 760 `mul(` (716 well formed), 71 `do()` and `don't()`, and about
half of the text disabled. Each benchmark method is tested against independently computed sums.
Patterns and parsers are built once, so only per-input work is measured.

Measured with JMH (3 forks, 5 × 10 s warm-up and measurement iterations) on JDK 24.0.1, Apple M3
Pro. To reproduce:

```bash
mvn -pl mug-benchmarks -am test -Dtest=AdventOfCodeDay3Benchmark -Dsurefire.failIfNoSpecifiedTests=false
java -cp "mug/target/classes:dot-parse/target/classes:mug-benchmarks/target/test-classes:<deps>" \
  org.openjdk.jmh.Main "AdventOfCodeDay3Benchmark\..*" -f 3 -wi 5 -w 10s -i 5 -r 10s
```
