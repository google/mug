# JVM Parser Showdown & Performance Analysis

This report presents a comprehensive JMH performance benchmark and architectural analysis comparing eight different parser engines on the JVM:

1. **`antlr4`** (Java):
   The industry-standard LL(*) parser generator.

2. **`dot-parse`** (Java):
   Google's lightweight, runtime-optimized parser library.

3. **`jparsec`** (Java):
   A classic, highly-expressive monadic parser combinator library.

4. **`fastparse`** (Scala):
   Li Haoyi's compile-time macro-rewritten parser.

5. **`cats-parse`** (Scala):
   Typelevel's modern, macro-free runtime parser.

6. **`petitparser`** (Java):
   A dynamic, scanner-less parser combinator library supporting packrat parsing.

7. **`parsecj`** (Java):
   A monadic, parser combinator library inspired by Haskell's Parsec.

8. **`taker`** (Java):
   An open-source PEG parser engine.

9. **`better-parse`** (Kotlin):
   A Kotlin-native, highly expressive parser combinator library built on top of property delegation and DSL combinators.

10. **`parboiled`** (Java):
    A classic PEG parser combinator library utilizing runtime bytecode generation.

11. **`autumn`** (Java):
    A highly flexible PEG parser combinator library with left-recursion support.

All benchmarks were executed side-by-side on the **same JVM (JDK 24.0.1)** and the **same hardware (Apple M1 Mac)** to eliminate environmental bias. All grammars were strictly verified with assertions ensuring **complete input consumption (EOF)** and **structural correctness**.

> [!NOTE]
> **Acknowledge on parboiled2**:
> The Scala-based `parboiled2` (compile-time macro PEG) is excluded from the main comparison tables below to focus on libraries with more comparable runtime execution models. In our runs, the macro-optimized `parboiled2` represented the performance ceiling, reaching up to **13.84 million parses/sec** on simple types and **5.92 million parses/sec** on fully qualified types.

> [!IMPORTANT]
> **Scope & Benchmark Nuance**:
> Benchmarking nested grammars requires framework expertise.
> Our benchmark suite covers parsing speed on micro-inputs, which measures framework overhead.
> It highlights performance when grammars are written idiomatically for each framework.
> For example, ANTLR4 is designed for larger files with complex AST generation. It carries a fixed-cost machinery that results in lower throughput on tiny micro-inputs, but is highly scalable on large source files.
> In contrast, combinators demonstrate higher throughput on local micro-parsing tasks.

---

## JSON Parser Shootout (12-Way Showdown)

To evaluate how these frameworks perform when parsing a **large, complex, and heterogeneous data payload**, we implemented a full **JSON parser** across all 12 shootout engines.

Every engine was validated against a large, representative JSON document (~100 containers, maps of size 12, lists of size 250, scientific numbers, and varying strings of length 20 to 128) and strictly verified at setup time to guarantee complete functional correctness and functional parity.

Throughput was measured in **operations per millisecond** (higher is better):

> [!NOTE]
> **Emoji Legend**:
> *   🚀 **Rocket Emoji**: Indicates the **overall #1 leader** across all tested libraries and JVM languages (Java, Scala, Kotlin).
> *   ☕ **Coffee Emoji**: Indicates the **#1 leader among Java-native libraries**. When a Java library leads overall across all languages, it receives both icons (🚀 ☕).

| Benchmark Scenario | [`antlr4`](../mug-benchmarks/src/test/antlr4/com/google/mu/benchmarks/parsers/antlr4/Json.g4) | [`Javacc`](https://github.com/apache/tomcat/blob/main/java/org/apache/tomcat/util/json/JSONParser.jjt) | [`dot-parse`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/JsonParser.java) | `jparsec` | [`petitparser`](https://github.com/petitparser/java-petitparser/tree/main/petitparser-json) | [`fastparse`](https://github.com/com-lihaoyi/fastparse/blob/master/perftests/bench2/src/perftests/JsonParse.scala) | [`cats-parse`](https://github.com/typelevel/cats-parse) | [`parsecj`](https://github.com/jon-hanson/parsecj/blob/master/src/test/java/org/javafp/parsecj/json/Grammar.java) | [`taker`](https://github.com/parseworks/taker/blob/main/src/test/java/io/github/parseworks/taker/examples/RealisticExamplesTest.java) | [`better-parse`](https://github.com/silmeth/jsonParser) | [`parboiled`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledJsonParser.java) | [`autumn`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/autumn/AutumnJsonParser.java) | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Complex JSON Payload** | 0.179 | 0.137 | **0.535** 🚀 ☕ | 0.120 | 0.095 | 0.522 | 0.228 | 0.014 | 0.092 | 0.079 | 0.065 | 0.077 | **`dot`** 🚀 ☕ |
| **Complex JSON with Comments** | 0.094 | 0.063 | **0.294** ☕ | 0.094 | 0.052 | **0.330** 🚀 | 0.078 | 0.001 | 0.030 | 0.030 | 0.022 | 0.036 | **`fast`** 🚀<br>**`dot`** ☕ |
| **`qux2.json` (Medium JSON)** | — | — | **0.220** ☕ | — | — | **0.253** 🚀 | 0.144 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`bla25.json` (Large JSON)** | — | — | **0.088** ☕ | — | — | **0.128** 🚀 | 0.051 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`countries.geo.json` (Geographic JSON)** | — | — | **0.296** ☕ | — | — | **0.367** 🚀 | 0.167 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`ugh10k.json` (Very Large JSON)** | — | — | **0.030** ☕ | — | — | **0.036** 🚀 | 0.018 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against production-grade, hand-written and generated parsers on the exact same JSON payloads:

| Parser Engine | Complex JSON (ops/ms) | Complex JSON with Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Lenient) | 1.565 | 0.373 |
| **Gson** (Lenient) | 1.122 | 0.336 |
| **`dot-parse`** (Our leading Java combinator) | **0.535** | **0.294** |
| **JavaCC** (Tomcat / Best) | 0.137 | 0.063 |

---

## CSS Parser Shootout (6-Way Showdown)

To evaluate how these frameworks handle a **highly ambiguous, whitespace-sensitive, and recursively nested document format**, we compared their performance on a full CSS stylesheet, [bootstrap.css](../mug-benchmarks/src/test/resources/bootstrap.css) (146 KB).

Every engine was validated against the same test suite and successfully parsed all W3C CSS Syntax Level 3 elements.

Throughput was measured in **operations per millisecond** (higher is better), with Scala's **`fastparse`** serving as the performance baseline (**1.00x**):

| Parser Engine | Throughput (ops/ms) | Relative Performance (vs. `fastparse`) | Notes / Optimizations |
| :--- | :---: | :---: | :--- |
| [**`dot-parse`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/CssParser.java) | **0.345 ± 0.005** | **1.47x** 🚀 ☕ | Stateless, zero-allocation radix-tree scanning on hot paths. |
| [**`fastparse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/fastparse/FastparseCssParser.scala) | 0.235 ± 0.002 | 1.00x (Baseline) | Official fastparse benchmark implementation (Scala macro-based). |
| [**`cats-parse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/catsparse/CatsParseCssParser.scala) | 0.219 ± 0.004 | 0.93x | Optimized via left-factoring numeric/identifier choices. |
| [**`parboiled` (v1)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledCssParser.java) | 0.109 ± 0.001 | 0.46x | Classic PEG combinators with ASM bytecode generation. |
| [**`htmlUnit` (javacc)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/javacc/HtmlUnitCssParser.java) | 0.023 ± 0.001 | 0.10x | Official HtmlUnit CSS Parser implementation (JavaCC-generated). |
| [**`antlr4`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/antlr4/Antlr4CssParser.java) | 0.007 ± 0.001 | 0.03x | Official ANTLR grammars-v4 CSS3 parser grammar. |

---

## 11-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.

| Benchmark Scenario | `dot-parse` | `jparsec` | `fastparse` | `cats-parse` | `taker` | `parsecj` | `parboiled` | `antlr4` | `scalaParser` | `petitparser` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **IPv4 Address** | 4,463 | **8,997** ☕ | **24,624** 🚀 | 12,340 | 4,956 | 12,125 | 883 | 1,996 | 3,562 | 7,051 | 1,831 | **`fast`** 🚀<br>Java: **`jparsec`** ☕ |
| **String (Simple)** | **64,706** 🚀 ☕ | 5,433 | **21,814** | 11,658 | 13,671 | 4,870 | 561 | 5,373 | 3,903 | 3,027 | 5,037 | **`dot`** 🚀 ☕ |
| **String (Escaped)** | 4,194 | 3,943 | 12,889 | 3,020 | **21,018** 🚀 ☕ | 2,480 | 529 | 3,640 | 3,152 | 2,392 | 1,556 | **`taker`** 🚀 ☕ |
| **120 Programming Keywords (CS)** | **216.83** 🚀 ☕ | 15.43 | 12.08 | 13.74 | 10.59 | 5.11 | 88.22 | 36.29 | 1.70 | 13.06 | — | **`dot`** 🚀 ☕ |
| **120 Programming Keywords (CI)** | **152.10** 🚀 ☕ | 14.64 | 11.31 | 13.37 | 10.67 | 4.01 | 8.25 | 34.10 | 1.28 | 10.76 | — | **`dot`** 🚀 ☕ |
| **500 City Names (CS)** | **28.82** 🚀 ☕ | 0.91 | 0.47 | 0.70 | 0.57 | 0.18 | 15.12 | 6.02 | 0.11 | 0.77 | — | **`dot`** 🚀 ☕ |
| **500 City Names (CI)** | **18.92** 🚀 ☕ | 0.82 | 0.45 | 0.74 | 0.48 | 0.08 | 0.45 | 6.82 | 0.07 | 0.64 | — | **`dot`** 🚀 ☕ |
| **Calculator (Math)** | 366 | 336 | **1,098** 🚀 | 400 | **436** ☕ | 194 | 111 | 360 | 196 | 377 | 234 | **`fastparse`** 🚀<br>Java: **`taker`** ☕ |
| **Nested Comments** | **11,110** 🚀 ☕ | 2,234 | 4,451 | 2,144 | 735 | 617 | 388 | 1,076 | 259 | 1,050 | 1,317 | **`dot`** 🚀 ☕ |
| **US Phone (Single)** | **14,976** 🚀 ☕ | 9,734 | 8,483 | 12,099 | 12,854 | 8,707 | 4,144 | 5,749 | 3,376 | 7,044 | 9,309 | **`dot`** 🚀 ☕ |
| **US Phone (1,000-List)** | **11.20** 🚀 ☕ | 9.52 | 8.93 | 10.89 | 8.51 | 1.73 | 3.68 | 8.12 | 2.98 | 5.56 | 5.05 | **`dot`** 🚀 ☕ |

---

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `parsecj` | `taker` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Simple Type (`String`)** | 3,646 | **8,625** ☕ | 1,513 | 3,457 | **9,149** 🚀 | 1,485 | 2,575 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Fully Qualified** | 1,699 | **4,627** ☕ | 656 | 2,119 | **5,572** 🚀 | 895 | 1,512 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | 309 | **899** ☕ | 153 | 437 | **1,231** 🚀 | 187 | 332 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | 353 | **838** ☕ | 148 | 411 | **956** 🚀 | 213 | 301 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | 242 | **326** ☕ | 106 | 168 | **685** 🚀 | 84 | 128 | **`fast`** 🚀<br>**`dot`** ☕ |

---

## StringIn vs. Keywords: Trie-Based Optimizations

We compared the performance of matching one of many literal strings in a flat choice. In `cats-parse`, this is represented by the `Parser.stringIn` primitive. In `dot-parse`, this is represented by collecting individual string parsers using the `Parser.or()` collector.

### Benchmark Results (Average Time, Lower is Better)

| Scenario | Candidate Strings | `dot-parse` (ns/op) | `cats-parse` (ns/op) |
| :--- | :--- | :---: | :---: |
| **`stringIn` (foo)** | 5 overlapping strings | **74 ns** | **75 ns** |
| **`stringIn` (broad)** | 676 generated strings | **1112 ns** | **1069 ns** |

---

## Key Performance Insights

Our benchmarks highlight four key architectural factors that govern parser performance on the JVM:

### 1. Radix Prefix Trie Optimization (Keywords)
*   **The Problem**: In programming languages and SQL, matching keywords (like `select`, `insert`) usually triggers different parser actions, wrapping string parsers in maps (e.g., `string("select").map(SelectNode::new)`). In most libraries (like `cats-parse`, `fastparse`), this mapping prevents trie-based prefix matching, forcing sequential backtracking through the vocabulary.
*   **The Solution**: `dot-parse`'s `OrParser` is designed to extract prefix alternatives even across map/suffix actions, compiling them into a single `PrefixPruneTree` (trie). This maintains $O(k)$ lookup scaling (proportional to word length) instead of $O(N)$ sequential scans (proportional to vocabulary size), resulting in a **25x-40x speedup** on large keyword sets (e.g., 500 city names).

### 2. Statelessness vs. Instance Allocations
*   **The Problem**: Classic generator tools (like JavaCC and ANTLR) produce stateful, mutable parser instances that are not thread-safe. For micro-parsing tasks (like parsing a single JSON payload or a type signature), allocating a new parser instance, token manager, and input stream wrapper on every call dominates the execution time.
*   **The Solution**: `dot-parse` and modern combinator libraries are stateless and thread-safe. A single parser instance can be pre-allocated and reused indefinitely across multiple threads, bypassing the instance creation tax on hot paths.

### 3. Scannerless vs. Two-Phase Tokenization
*   **The Problem**: Two-phase parsers (like ANTLR4 and `jparsec`) tokenize the input into a list of token objects before executing grammar rules. On small, dense inputs (such as Java type signatures or short JSON payloads), object allocation overhead for the token stream degrades performance.
*   **The Solution**: Scannerless combinators (`dot-parse`, `fastparse`) match directly on the character stream. They avoid token object allocations entirely, scanning text in-place.

### 4. Vectorized Delimiter Scanning
*   **The Problem**: Scanning comments (like `/* ... */`) or quoted strings in traditional parsers relies on character-by-character DFA transition loops, which scan memory slowly.
*   **The Solution**: `dot-parse` leverages native string search (`String.indexOf`) for block delimiters. The JVM JIT compiler optimizes these calls using vectorized SIMD instructions, allowing it to scan blocks in parallel and skip pointers instantly.
---

## How to Run the Benchmarks

To run these mixed Java/Scala/ANTLR4 benchmarks locally in the `mug` project:

1. **Compile and build the project**:

   ```bash
   mvn clean test-compile -pl mug-benchmarks -Pshowdown
   ```

2. **Execute the showdown JMH suite**:

   ```bash
   mvn exec:exec -pl mug-benchmarks -Pshowdown \
     -Dexec.executable="java" \
     -Dexec.classpathScope="test" \
     -Dexec.args="-classpath %classpath org.openjdk.jmh.Main ParserShowdownBenchmark -wi 1 -i 1 -f 1 -w 1 -r 1"
   ```

   *(You can adjust the `-wi` (warmup iterations) and `-i` (measurement iterations) parameters to run the suite faster or slower).*
