# Experiment: Mutable `MatchResult`

Status: experiment, not adopted. Baseline: commit `7ebe947473`.

## Summary

- **Change:** `skipAndMatch()` returns `boolean` and writes its result into one
  mutable `MatchResult` per parse, instead of returning a new immutable
  `Success` or `Failure` record for every match.
- **Result:** allocation fell 39% to 84% in 13 of the 17 `dotParse_*`
  benchmarks, and 14 of them got 3% to 17% faster. `calculator` became 30%
  slower and `escapedString` 21% slower.
- **Cause of the two regressions:** not extra parsing work. The parsing is
  identical. Under C2 with G1, the old records were cheap, while stores into the
  shared holder are not:
  - G1 write barriers on reference stores into the shared holder.
  - C2 could delete some old records outright; it cannot delete the holder.
  - `andZeroOrMore`'s `skipAndMatch` became small enough for C2 to inline,
    which sets off recursive inlining of the parser graph.
- **Not explained:** about 40% of the `escapedString` regression, and why the
  `calculator` regression is larger in `ParserShowdownBenchmark` than in
  isolation.

---

## The Change

- `abstract boolean skipAndMatch(Skipper preskipper, Skipper innerSkipper,
  CharInput input, int start, ErrorContext context, MatchResult result)`.
  It returns true on a match. The match or the failure is written into `result`.
- One `MatchResult` is allocated per `parse()` call, or per element for
  `parseToStream()` and `probe()`. Every parser in that parse reuses it. A
  result is valid only until the next `skipAndMatch()` call on the same holder.
- `ErrorContext` methods write the failure into the holder and return `false`.
- `ErrorTracker` keeps the farthest failure in four fields instead of one
  reference.
- `OrParser` copies the farthest failure among its candidates into four locals,
  and writes it back into the holder at the end.
- Files: `MatchResult.java`, `ErrorContext.java`, `OrParser.java`,
  `Parser.java`, `Parsers.java`, `Scanner.java`.

---

## Correctness

- All dot-parse tests pass: 3,070 run, 0 failures, 181 skipped, the same as the
  baseline. Test results of the dependent modules are unchanged.
- A differential harness compared parse results and exact error messages of both
  versions over 289,560 inputs. It found no difference.
- The tests do not pin how `anyOf` chooses between equally far failures. An
  `OrParser` that skips the final write-back passes every test, but changes the
  message of `anyOf(a.then(b), a.then(c)).parse("ad")` from `<b>` to `<c>`. It
  also changes 8,209 of the harness outputs.
- Omitting the write-back in `notFollowedBy()` passes all 1,132 `ParserTest`
  cases. Only 89 regex tests catch it.

---

## Benchmark Results

`ParserShowdownBenchmark`, 3 interleaved forks per variant, class defaults
(3 x 1 s warmup, 5 x 1 s measurement), `-prof gc`. Throughput is the mean of the
three forks. "Overlap" marks benchmarks where the per-fork ranges of the two
variants overlap.

| Benchmark | Baseline ops/ms | New ops/ms | Change | Baseline B/op | New B/op |
|---|---:|---:|---:|---:|---:|
| `simpleString` | 49,880 | 51,816 | +4% | 168 | 160 |
| `nestedComment` | 24,542 | 25,303 | +3% | 176 | 168 |
| `simpleUsPhone` | 20,768 | 22,166 | +7% | 232 | 104 |
| `simpleIp` | 18,623 | 20,318 | +9%, overlap | 256 | 104 |
| `regexSimpleUsPhone` | 12,928 | 13,801 | +7% | 224 | 240 |
| `escapedString` | 4,549 | 3,579 | -21% | 960 | 504 |
| `calculator` | 770.9 | 536.0 | -30% | 4,859 | 2,328 |
| `simpleKeywords` | 30.39 | 35.52 | +17% (a) | 33,216 | 8,904 |
| `simpleIgnoreCase` | 20.53 | 21.85 | +6% | 54,784 | 8,904 |
| `usPhoneList` | 16.72 | 18.27 | +9% | 267,217 | 75,214 |
| `regexUsPhoneList` | 9.008 | 9.523 | +6%, overlap | 259,339 | 211,338 |
| `usPhone10kList` | 1.722 | 1.802 | +5%, overlap | 2,689,145 | 769,132 |
| `json` | 0.7743 | 0.8084 | +4% | 2,873,472 | 1,742,074 |
| `jsonWithComments` | 0.4069 | 0.4172 | +3% | 4,834,561 | 1,742,009 |
| `usPhone100kList` | 0.1735 | 0.1834 | +6%, overlap | 26,481,224 | 7,281,206 |
| `streaming` | 0.01962 | 0.01887 | -4% | 161,347,446 | 86,675,505 |
| `streamingWithComments` | 0.01354 | 0.01413 | +4% | 225,274,372 | 86,700,367 |

(a) Driven by one fork at 40.17. The other two new forks are 9% above the
baseline mean.

---

## Does the New Code Do Less Work?

The parsing work is the same. Only the way each result is handed back changed.
In aggregate that change is a saving, but not at every step.

**What is the same:** the parsing itself. Both versions call the same parsers,
in the same order, the same number of times, and produce the same results and
error messages.

**What differs, per result:**

- **Success:** the old code allocates a new record and fills in 3 fields. The new
  code writes the same 3 fields into the shared holder.
- **Failure:** the old code allocates a record with 4 fields, and `ErrorTracker`
  and `anyOf` keep a reference to it. The new code writes the 4 fields into the
  holder, and `ErrorTracker` and `anyOf` copy all 4 fields instead of keeping
  one reference.
- Every call also passes the holder as an extra argument.

So it is a trade, not a pure reduction. It removes one allocation per result,
and adds field copying on failure paths.

**Where the trade is a net saving:** when the old allocations run as written.

- The new code is faster under the interpreter (25% to 27%), under C1 alone
  (10% to 20%), and under C2 with inlining turned off (31% to 34%).
- It allocates about half as much memory per parse.

**Why the saving mostly vanishes under C2 with inlining:**

- C2 had already made the old allocation cheap. An allocation is a pointer bump
  plus field stores that need no GC barrier, and some records are deleted
  entirely.
- The new code's stores go into a shared object, so they keep the G1 barrier
  and can never be deleted.

Most of what the new code removes is work C2 had already reduced. What it adds
is work C2 cannot remove.

---

## What Caused the Two Regressions

This section covers the mode used in production: C2 with inlining, and G1. The
time is measured per parse in an isolated benchmark that uses the same fixtures
and inputs (see [Method](#method)).

### `calculator`: 174 ns Slower per Parse (896 to 1,071 ns)

- **About +145 ns: G1 write barriers.**
  - The new code stores references into objects that already exist: the
    matched values, and the failure template and symbol. Those objects are the
    shared holder and `ErrorTracker`. Under G1, each such store runs a write
    barrier.
  - The old code stored the same references into newly allocated records. C2
    can skip the barrier for stores that initialize a new object.
  - The share is inferred, not measured per store: it is the cost that
    disappears when switching to ParallelGC, whose barrier is much cheaper.
- **About +115 ns: records C2 used to delete.** When the code that creates a
  result record and the code that reads it were inlined into one method, C2
  removed the old record and kept its fields in registers (scalar replacement).
  The shared holder is passed into every call, so its stores always happen.
- **About -85 ns: what the new design saves**, mainly allocation.

The first two figures overlap by about 10 ns, because the effects interact.
Together the three add up to the 174 ns.

### `escapedString`: 80 ns Slower per Parse (159 to 239 ns)

- **+48 ns: recursive inlining.**
  - `andZeroOrMore`'s `skipAndMatch` went from 386 to 128 bytecode bytes, under
    C2's 325-byte limit for inlining hot calls (`FreqInlineSize`). C2 now
    inlines it.
  - Each combinator class has one receiver profile per call site, shared by all
    its instances. Following those profiles, C2 inlines
    `anyOf -> sequence -> andZeroOrMore -> anyOf -> ...`, a path that never runs.
    It stops only at its node and recursion limits, with 60 KB compiled methods.
  - The calls that actually run (the scanners and `source()`) are left as
    virtual calls.
- **+32 ns: unexplained.** With that inlining blocked, the new code inlines the
  same methods as the baseline and is still slower. Neither the G1 barriers nor
  scalar replacement account for this part.

---

## Evidence

All interventions are JVM flags. No source was changed for the analysis.

### Only C2-Compiled Code Is Slower

New / baseline throughput:

| JVM mode | `calculator`, isolated | `escapedString`, isolated | `calculator`, showdown | `escapedString`, showdown |
|---|---:|---:|---:|---:|
| C2 with inlining, G1 (default) | 0.84 | 0.66 | 0.70 to 0.82 | 0.79 to 0.87 |
| Interpreter (`-Xint`) | not run | not run | 1.27 | 1.25 |
| C1 only (`-XX:TieredStopAtLevel=1`) | 1.11 | 1.20 | 1.10 | 1.17 |
| C2 without inlining (`-XX:-Inline`) | 1.34 | 1.32 | 1.31 | 1.31 |

The default-mode showdown ratios are the range over two separate runs.

### `calculator`: Barriers and Scalar Replacement

Isolated benchmark, ns per parse:

| | G1 | ParallelGC |
|---|---:|---:|
| Baseline | 896 | 867 |
| New | 1,071 | 902 |
| Baseline, `-XX:-EliminateAllocations` | 1,012 | 966 |
| New, `-XX:-EliminateAllocations` | 1,080 | 881 |

- Switching from G1 to ParallelGC narrows the gap by 139 ns with scalar
  replacement on, and by 152 ns with it off.
- Turning scalar replacement off narrows the gap by 107 ns under G1, and by
  120 ns under ParallelGC.
- With both effects removed, the new code is 85 ns faster.

### `escapedString`: Inlining

From `-XX:+LogCompilation` of the isolated benchmark:

| | Baseline | New | New, `andZeroOrMore` not inlined |
|---|---|---|---|
| `andZeroOrMore` `skipAndMatch` bytecode | 386 bytes | 128 bytes | 128 bytes |
| Inlined at hot call sites | no ("hot method too big", 11 sites) | yes | no (blocked by flag) |
| Largest dot-parse compiled methods | 20 KB, 9 KB | 60 KB, 57 KB, 57 KB | 23 KB |
| Calls cut off by `NodeCountInliningCutoff` | 0 | 65 | 0 |
| Calls cut off as "recursive inlining is too deep" | 0 | 16 | 0 |

- The baseline's largest methods are `andZeroOrMore` and `anyOf`. The new
  variant's are `sequence`, `anyOf` and `andZeroOrMore`.
- Only `andZeroOrMore` crossed the size limit. The other combinators changed
  size too, but were below it in both versions: `sequence` went from 201 to 109
  bytes, `source()` from 193 to 56, and `OrParser` from 181 to 206.

Causal tests, using
`-XX:CompileCommand=dontinline,com.google.common.labs.parse.Parser$15::skipAndMatch`
(`Parser$15` is the anonymous class in `andZeroOrMore()` in both versions):

- **Blocking the inlining in the new code:** 239 to 190 ns per parse. The
  baseline stays at 159 ns with the same flag.
- **Forcing the inlining in the baseline** (`CompileCommand=inline`): one fork
  slowed to 203 ns, and the other stayed at 155 ns.
- **The remaining 32 ns** with the inlining blocked: the same under ParallelGC
  (190 vs 157 ns). The baseline does not gain from scalar replacement in this
  benchmark.

---

## Not Explained

- **The remaining 32 ns of `escapedString`.** JFR profiles of that configuration
  could not be used. With JFR attached, the new code ran 15% to 19% below its
  unprofiled throughput, while the baseline lost about 2%. At 1 ms sampling,
  the JFR sampler thread crashed.
- **`ParserShowdownBenchmark`.**
  - `calculator` loses 30% there, compared with 16% in isolation.
  - Blocking the `andZeroOrMore` inlining does not help there: `calculator`
    541 vs 786 ops/ms, `escapedString` 3,561 vs 4,396.
  - The benchmark's state constructs every library's fixtures and parses two
    JSON documents with dot-parse before measuring, so the type profiles C2
    compiles from include other grammars.
  - Untested observation, in the compile-logged `calculator` forks:
    - The baseline's `andZeroOrMore` got its type-profiling compile only after
      about 3.3 s, so its profile reflects only the measured grammar.
    - The new code's came at about 1.1 s, during the state setup. Its first call
      site then saw more than two receiver classes and stayed a virtual call,
      where the baseline inlined it.

---

## Method

- JDK 24.0.1 with its default collector (G1), JMH 1.37.
- Apple M3 Pro, 12 cores, 36 GB, macOS 15.8.2. The machine was not idle: the
  load average was 2 to 5.
- **Baseline:** Maven-built classes of `7ebe947473`. **New:** the working tree.
  The two classpaths differ only in the dot-parse classes directory.
- **Isolated benchmark:** the same fixtures, inputs and JMH settings as
  `ParserShowdownBenchmark`, but each fork constructs only the fixture it
  measures. Default rows use 3 interleaved forks per variant; other rows use 2.
- **Compile analysis:** `-XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation`.
- **Flags used:** `-Xint`, `-XX:TieredStopAtLevel=1`, `-XX:-Inline`,
  `-XX:-TieredCompilation`, `-XX:-DoEscapeAnalysis`,
  `-XX:-EliminateAllocations`, `-XX:+UseParallelGC`,
  `-XX:CompileCommand=dontinline|inline,...`.

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class IsolatedBenchmark {
  @State(Scope.Benchmark)
  public static class CalculatorState {
    public final DotParseShowdown.CalculatorFixture calculator =
        new DotParseShowdown.CalculatorFixture();
  }

  @State(Scope.Benchmark)
  public static class StringState {
    public final DotParseShowdown.StringFixture string = new DotParseShowdown.StringFixture();
  }

  @Benchmark
  public void calculator(CalculatorState s, Blackhole bh) {
    bh.consume(s.calculator.run());
  }

  @Benchmark
  public void escapedString(StringState s, Blackhole bh) {
    bh.consume(s.string.run(BenchmarkInputs.STRING_ESCAPED));
  }
}
```
