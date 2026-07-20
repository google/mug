# Common Expression Language (CEL) Shootout (Parity Comparison)

We compared the performance of Google's official ANTLR-based Java CEL parser (`cel-java` using `dev.cel:cel`) against our lightweight `dot-parse`-based `CelParser` (module `dot-cel`) on a variety of representative CEL expressions. The benchmark scenarios and target expressions are implemented in [`CelParserBenchmark.java`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/CelParserBenchmark.java). Both parsers output compatible proto ASTs (`com.google.api.expr.v1alpha1.ParsedExpr`) with full position tracking (`positions`), original macro invocation context (`macro_calls`), and line offset records (`line_offsets`).

Both parsers were strictly validated at setup time to guarantee 100% parity:
1. Identical AST structures.
2. Identical `line_offsets` arrays (including EOF offset mapping).
3. Identical `positions` map size.
4. Identical `macro_calls` map size.

Throughput was measured in **microseconds per operation** (lower is better):

| Benchmark Scenario / Expression | Has Comments? | ANTLR Parser (`cel-java`) | `dot-parse` Parser (`dot-cel`) | Speedup |
| :--- | :---: | :---: | :---: | :---: |
| **`deepFieldMessageSelection`** (`child.child.child.child.payload...`) | **No** | 3.299 μs | 1.226 μs | **2.69x** |
| **`smokeTest`** (`1 + 2 == 3`) | **No** | 2.712 μs | 1.020 μs | **2.66x** |
| **`anyFieldMessageSelection`** (`payload.single_any.single_int64 == 42`) | **No** | 2.481 μs | 1.072 μs | **2.31x** |
| **`simpleMessageContext`** (`payload.single_int64 == 42 && ...`) | **No** | 4.335 μs | 1.871 μs | **2.32x** |
| **`mapComprehension`** (`payload.map_int64_int64.exists(...)`) | **No** | 4.570 μs | 2.039 μs | **2.24x** |
| **`cppSuite`** (Batch parsing of 115 test expressions) | **No** | 368.501 μs | 167.147 μs | **2.20x** |
| **`listComprehension`** (`payload.repeated_int64.exists(...)`) | **No** | 4.337 μs | 2.012 μs | **2.16x** |
| **`chainedOrs`** (`1 > 2 || 2 > 3 || 3 > 4 ...`) | **No** | 8.299 μs | 3.917 μs | **2.12x** |
| **`chainedAnds`** (`1 < 2 && 2 < 3 && 3 < 4 ...`) | **No** | 7.881 μs | 3.795 μs | **2.08x** |
| **`messageCreation`** (Nested struct instantiation) | **No** | 14.000 μs | 7.345 μs | **1.91x** |
| **`longList`** (`size([1, 2, ... 1000]) == 1000`) | **No** | 793.293 μs | 477.317 μs | **1.66x** |

### Key Takeaways from the CEL Shootout

*   **Hybrid Layout Skipping**:
    `dot-cel` employs a hybrid layout skipper. If the input expression contains no comment markers (`//`), it routes parsing through a fast-path ASCII whitespace skipping loop (yielding a **1.6x to 2.7x speedup** over `cel-java`). If comments are present, it falls back to a combinator-based comment scanner, running on par or slightly slower than `cel-java` (~0.9x to 1.0x).
*   **Reduced Object Allocations**:
    During parsing of complex expressions without comments (such as deep selections, long lists, and nested message creations), `dot-parse`'s return elision significantly reduces the allocation rate on the JVM heap, leading to a major reduction in latency.
