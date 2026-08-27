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
| **Complex JSON Payload** | 0.179 | 0.161 | **0.548** 🚀 ☕ | 0.123 | 0.090 | 0.521 | 0.228 | 0.015 | 0.092 | 0.081 | 0.065 | 0.078 | **`dot`** 🚀 ☕ |
| **Complex JSON with Comments** | 0.094 | 0.063 | **0.251** ☕ | 0.093 | 0.048 | **0.330** 🚀 | 0.077 | 0.001 | 0.030 | 0.031 | 0.022 | 0.037 | **`fast`** 🚀<br>**`dot`** ☕ |
| **`qux2.json` (Medium JSON)** | — | — | **0.225** ☕ | — | — | **0.250** 🚀 | 0.130 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`bla25.json` (Large JSON)** | — | — | **0.090** ☕ | — | — | **0.118** 🚀 | 0.045 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`countries.geo.json` (Geographic JSON)** | — | — | **0.292** ☕ | — | — | **0.354** 🚀 | 0.137 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`ugh10k.json` (Very Large JSON)** | — | — | **0.030** ☕ | — | — | **0.036** 🚀 | 0.017 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against production-grade, hand-written and generated parsers on the exact same JSON payloads:

| Parser Engine | Complex JSON (ops/ms) | Complex JSON with Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Lenient) | 1.033 | 0.314 |
| **Gson** (Lenient) | 0.788 | 0.300 |
| **`dot-parse`** (Our leading Java combinator) | **0.548** | **0.251** |
| **JavaCC** (Tomcat / Best) | 0.161 | 0.063 |

#### Reference Streaming Baselines (1,000 Rows, 8KB JSONL)
To evaluate continuous data ingestion performance, we benchmarked incremental record streaming from a `Reader` on a 1,000-row (~8KB per line, ~8.1 MB total) JSONL file, both clean and with ~30% comments:

| Streaming Parser Engine | Clean JSONL (ops/ms) | JSONL with ~30% Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Streaming) | 0.033 | 0.027 |
| **Gson** (Streaming) | 0.031 | 0.023 |
| **`dot-parse`** (`parseToStream`) | **0.016** | **0.011** |
| **JavaCC** (Parser Generator) | 0.010 | 0.007 |

---

## CSS Parser Shootout (6-Way Showdown)

To evaluate how these frameworks handle a **highly ambiguous, whitespace-sensitive, and recursively nested document format**, we compared their performance on a full CSS stylesheet, [bootstrap.css](../mug-benchmarks/src/test/resources/bootstrap.css) (146 KB).

Every engine was validated against the same test suite and successfully parsed all W3C CSS Syntax Level 3 elements.

Throughput was measured in **operations per millisecond** (higher is better), with Scala's **`fastparse`** serving as the performance baseline (**1.00x**):

| Parser Engine | Throughput (ops/ms) | Relative Performance (vs. `fastparse`) | Notes / Optimizations |
| :--- | :---: | :---: | :--- |
| [**`dot-parse`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/CssParser.java) | **0.353 ± 0.012** | **1.48x** 🚀 ☕ | Stateless, zero-allocation radix-tree scanning on hot paths. |
| [**`fastparse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/fastparse/FastparseCssParser.scala) | 0.238 ± 0.009 | 1.00x (Baseline) | Official fastparse benchmark implementation (Scala macro-based). |
| [**`cats-parse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/catsparse/CatsParseCssParser.scala) | 0.223 ± 0.008 | 0.94x | Optimized via left-factoring numeric/identifier choices. |
| [**`parboiled` (v1)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledCssParser.java) | 0.106 ± 0.004 | 0.45x | Classic PEG combinators with ASM bytecode generation. |
| [**`htmlUnit` (javacc)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/javacc/HtmlUnitCssParser.java) | 0.023 ± 0.001 | 0.10x | Official HtmlUnit CSS Parser implementation (JavaCC-generated). |
| [**`antlr4`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/antlr4/Antlr4CssParser.java) | 0.007 ± 0.001 | 0.03x | Official ANTLR grammars-v4 CSS3 parser grammar. |

---

## 11-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.

| Benchmark Scenario | `dot-parse` | `jparsec` | `fastparse` | `cats-parse` | `taker` | `parsecj` | `parboiled` | `antlr4` | `scalaParser` | `petitparser` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **IPv4 Address** | **9,446** ☕ | 8,513 | **24,635** 🚀 | 13,100 | 4,752 | 12,214 | 831 | 1,880 | 3,261 | 6,894 | 1,880 | **`fast`** 🚀<br>Java: **`dot`** ☕ |
| **String (Simple)** | **49,218** 🚀 ☕ | 5,832 | 21,548 | 12,476 | 32,310 | 4,992 | 548 | 5,327 | 3,911 | 2,969 | 5,114 | **`dot`** 🚀 ☕ |
| **String (Escaped)** | 4,017 | 4,091 | 10,589 | 2,976 | **20,748** 🚀 ☕ | 2,680 | 502 | 3,640 | 3,331 | 2,237 | 1,462 | **`taker`** 🚀 ☕ |
| **120 Programming Keywords (CS)** | **32.08** 🚀 ☕ | 0.82 | 0.45 | 0.70 | 0.47 | 0.20 | 15.41 | 7.13 | 0.10 | 0.82 | — | **`dot`** 🚀 ☕ |
| **120 Programming Keywords (CI)** | **19.98** 🚀 ☕ | 0.83 | 0.43 | 0.63 | 0.52 | 0.08 | 0.42 | 6.21 | 0.07 | 0.61 | — | **`dot`** 🚀 ☕ |
| **Calculator (Math)** | **547** ☕ | 351 | **1,117** 🚀 | 417 | 405 | 198 | 104 | 360 | 192 | 353 | 242 | **`fastparse`** 🚀<br>Java: **`dot`** ☕ |
| **Nested Comments** | **12,118** 🚀 ☕ | 2,402 | 5,028 | 1,863 | 712 | 607 | 380 | 1,109 | 243 | 1,011 | 1,325 | **`dot`** 🚀 ☕ |
| **US Phone (Single)** | **14,746** 🚀 ☕ | 9,698 | 8,695 | 12,431 | 13,325 | 9,053 | 4,349 | 5,756 | 3,248 | 6,675 | 8,837 | **`dot`** 🚀 ☕ |
| **US Phone (1,000-List)** | **11.32** 🚀 ☕ | 9.43 | 9.17 | 10.82 | 8.48 | 1.85 | 3.78 | 6.72 | 2.86 | 5.35 | 4.92 | **`dot`** 🚀 ☕ |

---

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `dot-parse` | `fastparse` | `petitparser` | `antlr4` | `taker` | `jparsec` | `parsecj` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Simple Type (`String`)** | **9,932** 🚀 ☕ | 9,222 | 3,509 | 3,487 | 2,570 | 1,569 | 1,524 | **`dot`** 🚀 ☕ |
| **Fully Qualified** | **5,097** ☕ | **5,712** 🚀 | 2,167 | 1,653 | 1,539 | 679 | 913 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | **977** ☕ | **1,249** 🚀 | 440 | 315 | 324 | 161 | 188 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | **958** ☕ | **989** 🚀 | 418 | 357 | 312 | 158 | 200 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | **378** ☕ | **688** 🚀 | 171 | 246 | 130 | 106 | 86 | **`fast`** 🚀<br>**`dot`** ☕ |

---

## CEL Expression Parser Shootout (vs. ANTLR4)

We compared the performance of parsing Common Expression Language (CEL) syntax between Google's reference ANTLR4-based parser and `dot-parse` (`dot-cel`).

Average latency was measured in **microseconds per operation** (lower is better):

| Scenario | `dot-parse` (`dot-cel`) (µs/op) | ANTLR4 CEL Engine (µs/op) | `dot-parse` Speedup |
| :--- | :---: | :---: | :---: |
| **`smokeTest`** | **0.864 ± 0.070** | 2.755 ± 1.527 | **3.19x faster** 🚀 ☕ |
| **`anyFieldMessageSelection`** | **0.859 ± 0.023** | 2.448 ± 0.023 | **2.85x faster** 🚀 ☕ |
| **`deepFieldMessageSelection`** | **1.060 ± 0.052** | 3.273 ± 2.027 | **3.09x faster** 🚀 ☕ |
| **`simpleMessageContext`** | **1.624 ± 0.763** | 4.271 ± 0.272 | **2.63x faster** 🚀 ☕ |
| **`mapComprehension`** | **1.746 ± 0.296** | 4.622 ± 6.441 | **2.65x faster** 🚀 ☕ |
| **`listComprehension`** | **1.802 ± 0.894** | 4.446 ± 0.155 | **2.47x faster** 🚀 ☕ |
| **`chainedAnds`** | **3.567 ± 0.215** | 8.101 ± 0.117 | **2.27x faster** 🚀 ☕ |
| **`chainedOrs`** | **3.573 ± 0.284** | 8.371 ± 0.190 | **2.34x faster** 🚀 ☕ |
| **`messageCreation`** | **6.634 ± 0.129** | 13.189 ± 1.402 | **1.99x faster** 🚀 ☕ |
| **`cppSuite`** (Full C++ CEL Test Suite) | **156.7 ± 3.2** | 378.9 ± 31.8 | **2.42x faster** 🚀 ☕ |
| **`longList`** | **437.6 ± 67.1** | 785.8 ± 20.6 | **1.80x faster** 🚀 ☕ |

---

## Email Address Parser Benchmark (RFC 5322 Parsing)

Throughput was measured in **operations per second** (higher is better):

| Scenario | `dot-parse` Throughput (ops/s) | Reference Baselines (ops/s) |
| :--- | :---: | :---: |
| **Single Plain Address (`user@host.com`)** | **6,821,750 ± 182,410** | *JMail*: 2,648,104 / *Jakarta*: 15,132,073 |
| **Single Bracketed Address (`<user@host.com>`)** | **5,570,577 ± 142,390** | — |
| **Bracketed with Display Name (`"User" <user@host.com>`)** | **3,362,200 ± 89,240** | — |
| **Valid Address List** | **743,676 ± 21,350** | — |
| **Valid Address List (with streaming consumer)** | **851,888 ± 24,190** | — |
| **Mixed Address List** | **476,303 ± 12,850** | — |

## StringIn vs. Keywords: Trie-Based Optimizations

We compared the performance of matching one of many literal strings in a flat choice. In `cats-parse`, this is represented by the `Parser.stringIn` primitive. In `dot-parse`, this is represented by collecting individual string parsers using the `Parser.or()` collector.

### Benchmark Results (Average Time, Lower is Better)

| Scenario | Candidate Strings | `dot-parse` (ns/op) | `cats-parse` (ns/op) |
| :--- | :--- | :---: | :---: |
| **`stringIn` (foo)** | 5 overlapping strings | **71.4 ns** | **64.9 ns** |
| **`stringIn` (broad)** | 676 generated strings | **1091 ns** | **941 ns** |

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
