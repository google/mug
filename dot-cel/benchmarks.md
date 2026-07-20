# Common Expression Language (CEL) Shootout

We benchmarked the performance of Google's official ANTLR-based Java CEL parser (`cel-java` using `dev.cel:cel`) against the `dot-cel` parser on a variety of expressions. Both parsers construct identical ASTs (`com.google.api.expr.v1alpha1.ParsedExpr`) with the same source position metadata (`positions`, `macro_calls`, `line_offsets`).

The benchmark scenarios are implemented in [`CelParserBenchmark.java`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/CelParserBenchmark.java).

Throughput was measured in **microseconds per operation** (lower is better):

| Benchmark Scenario / Expression | cel-java (ANTLR parser) | dot-cel (dot-parse Parser) | Speedup |
| :--- | :---: | :---: | :---: |
| **`deepFieldMessageSelection`** (`child.child.child.child.payload...`) | 3.299 μs | 1.226 μs | **2.69x** |
| **`smokeTest`** (`1 + 2 == 3`) | 2.712 μs | 1.020 μs | **2.66x** |
| **`anyFieldMessageSelection`** (`payload.single_any.single_int64 == 42`) | 2.481 μs | 1.072 μs | **2.31x** |
| **`simpleMessageContext`** (`payload.single_int64 == 42 && ...`) | 4.335 μs | 1.871 μs | **2.32x** |
| **`mapComprehension`** (`payload.map_int64_int64.exists(...)`) | 4.570 μs | 2.039 μs | **2.24x** |
| **`cppSuite`** (Batch parsing of 115 test expressions) | 368.501 μs | 167.147 μs | **2.20x** |
| **`listComprehension`** (`payload.repeated_int64.exists(...)`) | 4.337 μs | 2.012 μs | **2.16x** |
| **`chainedOrs`** (`1 > 2 \|\| 2 > 3 \|\| 3 > 4 ...`) | 8.299 μs | 3.917 μs | **2.12x** |
| **`chainedAnds`** (`1 < 2 && 2 < 3 && 3 < 4 ...`) | 7.881 μs | 3.795 μs | **2.08x** |
| **`messageCreation`** (Nested struct instantiation) | 14.000 μs | 7.345 μs | **1.91x** |
| **`longList`** (`size([1, 2, ... 1000]) == 1000`) | 793.293 μs | 477.317 μs | **1.66x** |

### Why is it faster?

*   **Prefix Pruning**:
    Traditional parser combinators sequentially evaluate alternative grammar branches. `dot-parse` extracts character prefixes to prune candidate paths early, avoiding deep backtracking on the hot path.
*   **Reduced Object Allocations**:
    During parsing of complex nested structures (e.g. deep field selections, long lists, and struct instantiations), `dot-parse` uses return elision to bypass allocating intermediate list and tuple wrappers, drastically reducing JVM heap allocation rate and GC overhead.
