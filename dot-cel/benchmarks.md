# Common Expression Language (CEL) Shootout (Parity Comparison)

We compared the performance of Google's official ANTLR-based Java CEL parser (`cel-java` using `dev.cel:cel`) against our lightweight `dot-parse`-based `CelParser` (module `dot-cel`) on a variety of representative CEL expressions. The benchmark scenarios and target expressions are implemented in [`CelParserBenchmark.java`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/CelParserBenchmark.java). Both parsers output compatible proto ASTs (`com.google.api.expr.v1alpha1.ParsedExpr`) with full position tracking (`positions`), original macro invocation context (`macro_calls`), and line offset records (`line_offsets`).

Both parsers were strictly validated at setup time to guarantee 100% parity:
1. Identical AST structures.
2. Identical `line_offsets` arrays (including EOF offset mapping).
3. Identical `positions` map size.
4. Identical `macro_calls` map size.

Throughput was measured in **microseconds per operation** (lower is better):

| Benchmark Scenario / Expression | ANTLR Parser (`cel-java`) | `dot-parse` Parser (`dot-cel`) | Speedup |
| :--- | :---: | :---: | :---: |
| **`smokeTest`** (`1 + 2 == 3`) | 2.845 μs | 1.066 μs | **2.67x** |
| **`chainedOrs`** (`1 > 2 || 2 > 3 || 3 > 4 ...`) | 9.551 μs | 4.471 μs | **2.14x** |
| **`chainedAnds`** (`1 < 2 && 2 < 3 && 3 < 4 ...`) | 9.310 μs | 4.035 μs | **2.31x** |
| **`messageCreation`** (Nested struct instantiation) | 15.413 μs | 7.465 μs | **2.06x** |
| **`anyFieldMessageSelection`** (`payload.single_any.single_int64 == 42`) | 2.869 μs | 1.003 μs | **2.86x** |
| **`deepFieldMessageSelection`** (`child.child.child.child.payload...`) | 4.159 μs | 1.177 μs | **3.53x** |
| **`longList`** (`size([1, 2, ... 1000]) == 1000`) | 859.976 μs | 510.390 μs | **1.68x** |
| **`simpleMessageContext`** (`payload.single_int64 == 42 && ...`) | 4.968 μs | 1.862 μs | **2.67x** |
| **`listComprehension`** (`payload.repeated_int64.exists(...)`) | 4.771 μs | 2.333 μs | **2.04x** |
| **`mapComprehension`** (`payload.map_int64_int64.exists(...)`) | 4.776 μs | 2.328 μs | **2.05x** |
| **`cppSuite`** (Batch parsing of 115 test expressions) | 385.200 μs | 168.055 μs | **2.29x** |

### Key Takeaways from the CEL Shootout

*   **Stateless Compilation Advantage**:
    The official `cel-java` ANTLR parser carries overhead from parsing machinery initialization and stateful DFA lookahead caches. Our `dot-parse` implementation uses a stateless combinator structure, allowing the JVM to optimize the hot path directly.
*   **Reduced Object Allocations**:
    During parsing of complex expressions (such as list and map comprehensions or deep selections), `dot-parse`'s return elision significantly reduces the allocation rate on the JVM heap, leading to a 2.0x to 3.3x reduction in latency.
