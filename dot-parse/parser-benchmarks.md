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
| **Complex JSON Payload** | 0.145 | 0.131 | **0.554** 🚀 ☕ | 0.117 | 0.091 | 0.508 | 0.230 | 0.014 | 0.091 | 0.077 | 0.063 | 0.073 | **`dot`** 🚀 ☕ |
| **Complex JSON with Comments** | 0.061 | 0.058 | **0.296** ☕ | 0.087 | 0.049 | **0.335** 🚀 | 0.079 | 0.001 | 0.030 | 0.028 | 0.021 | 0.034 | **`fast`** 🚀<br>**`dot`** ☕ |
| **`qux2.json` (Medium JSON)** | — | — | **0.219** ☕ | — | — | **0.250** 🚀 | 0.127 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`bla25.json` (Large JSON)** | — | — | **0.088** ☕ | — | — | **0.117** 🚀 | 0.044 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`countries.geo.json` (Geographic JSON)** | — | — | **0.283** ☕ | — | — | **0.350** 🚀 | 0.137 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`ugh10k.json` (Very Large JSON)** | — | — | **0.030** ☕ | — | — | **0.036** 🚀 | 0.017 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against production-grade, hand-written and generated parsers on the exact same JSON payloads:

| Parser Engine | Complex JSON (ops/ms) | Complex JSON with Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Lenient) | 1.004 | 0.308 |
| **Gson** (Lenient) | 0.776 | 0.283 |
| **`dot-parse`** (Our leading Java combinator) | **0.554** | **0.296** |
| **JavaCC** (Tomcat / Best) | 0.131 | 0.058 |

#### Reference Streaming Baselines (1,000 Rows, 8KB JSONL)
To evaluate continuous data ingestion performance, we benchmarked incremental record streaming from a `Reader` on a 1,000-row (~8KB per line, ~8.1 MB total) JSONL file, both clean and with ~30% comments:

| Streaming Parser Engine | Clean JSONL (ops/ms) | JSONL with ~30% Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Streaming) | 0.032 | 0.026 |
| **Gson** (Streaming) | 0.030 | 0.022 |
| **`dot-parse`** (`parseToStream`) | **0.016** | **0.012** |
| **JavaCC** (Parser Generator) | 0.010 | 0.007 |

---

## CSS Parser Shootout (6-Way Showdown)

To evaluate how these frameworks handle a **highly ambiguous, whitespace-sensitive, and recursively nested document format**, we compared their performance on a full CSS stylesheet, [bootstrap.css](../mug-benchmarks/src/test/resources/bootstrap.css) (146 KB).

Every engine was validated against the same test suite and successfully parsed all W3C CSS Syntax Level 3 elements.

Throughput was measured in **operations per millisecond** (higher is better), with Scala's **`fastparse`** serving as the performance baseline (**1.00x**):

| Parser Engine | Throughput (ops/ms) | Relative Performance (vs. `fastparse`) | Notes / Optimizations |
| :--- | :---: | :---: | :--- |
| [**`dot-parse`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/CssParser.java) | **0.465 ± 0.028** | **2.67x** 🚀 ☕ | 128-bit SWAR bitmask skipper and radix prefix matching. |
| [**`cats-parse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/catsparse/CatsParseCssParser.scala) | 0.191 ± 0.038 | 1.10x | Optimized via left-factoring numeric/identifier choices. |
| [**`fastparse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/fastparse/FastparseCssParser.scala) | 0.174 ± 0.040 | 1.00x (Baseline) | Official fastparse benchmark implementation (Scala macro-based). |
| [**`parboiled` (v1)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledCssParser.java) | 0.100 ± 0.008 | 0.57x | Classic PEG combinators with ASM bytecode generation. |
| [**`better-parse`**](../mug-benchmarks/src/test/kotlin/com/google/mu/benchmarks/parsers/betterparse/BetterParseCssParser.kt) | 0.062 ± 0.002 | 0.36x | Kotlin delegated property combinators. |
| [**`htmlUnit` (javacc)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/javacc/HtmlUnitCssParser.java) | 0.021 ± 0.004 | 0.12x | Official HtmlUnit CSS Parser implementation (JavaCC-generated). |
| [**`antlr4`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/antlr4/Antlr4CssParser.java) | 0.007 ± 0.001 | 0.04x | Official ANTLR grammars-v4 CSS3 parser grammar. |
| [**`jparsec`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/jparsec/JparsecCssParser.java) | 0.005 ± 0.001 | 0.03x | Classic monadic combinator implementation. |

---

## 11-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors and 128-bit SWAR skippers on the hot path.

| Benchmark Scenario | `dot-parse` | `jparsec` | `fastparse` | `cats-parse` | `taker` | `parsecj` | `parboiled` | `antlr4` | `scalaParser` | `petitparser` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **IPv4 Address** | **11,450** ☕ | 8,554 | **23,955** 🚀 | 12,802 | 4,567 | 12,336 | 891 | 1,841 | 3,395 | 6,622 | 1,879 | **`fast`** 🚀<br>Java: **`dot`** ☕ |
| **String (Simple)** | **46,048** 🚀 ☕ | 5,485 | 21,521 | 12,678 | 25,592 | 4,740 | 559 | 5,335 | 3,797 | 2,862 | 5,025 | **`dot`** 🚀 ☕ |
| **String (Escaped)** | 4,168 | 3,948 | 10,491 | 3,535 | **20,759** 🚀 ☕ | 2,646 | 473 | 2,175 | 3,195 | 2,209 | 1,255 | **`taker`** 🚀 ☕ |
| **120 Programming Keywords (CS)** | **28.88** 🚀 ☕ | 0.77 | 0.46 | 0.71 | 0.52 | 0.19 | 14.97 | 6.47 | 0.10 | 0.81 | — | **`dot`** 🚀 ☕ |
| **120 Programming Keywords (CI)** | **18.17** 🚀 ☕ | 0.82 | 0.43 | 0.73 | 0.50 | 0.07 | 0.43 | 5.16 | 0.07 | 0.59 | — | **`dot`** 🚀 ☕ |
| **Calculator (Math)** | **669** ☕ | 347 | **1,056** 🚀 | 431 | 395 | 198 | 104 | 320 | 186 | 344 | 227 | **`fastparse`** 🚀<br>Java: **`dot`** ☕ |
| **Nested Comments** | **12,276** 🚀 ☕ | 2,201 | 5,005 | 2,035 | 660 | 600 | 380 | 1,034 | 246 | 1,016 | 1,285 | **`dot`** 🚀 ☕ |
| **US Phone (Single)** | **15,274** 🚀 ☕ | 9,008 | 6,784 | 12,541 | 13,701 | 9,093 | 4,121 | 5,925 | 3,234 | 6,271 | 9,693 | **`dot`** 🚀 ☕ |
| **US Phone (1,000-List)** | **11.97** 🚀 ☕ | 8.79 | 8.97 | 11.27 | 8.36 | 1.82 | 3.71 | 7.98 | 2.79 | 5.30 | 5.35 | **`dot`** 🚀 ☕ |

---

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `dot-parse` | `fastparse` | `petitparser` | `antlr4` | `taker` | `jparsec` | `parsecj` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Simple Type (`String`)** | **9,323** 🚀 ☕ | 7,144 | 3,266 | 3,177 | 2,420 | 1,302 | 1,413 | **`dot`** 🚀 ☕ |
| **Fully Qualified** | **4,708** 🚀 ☕ | 4,361 | 2,002 | 1,452 | 1,423 | 600 | 862 | **`dot`** 🚀 ☕ |
| **Nested Generics** | **918** 🚀 ☕ | 893 | 425 | 274 | 307 | 148 | 177 | **`dot`** 🚀 ☕ |
| **Annotated Array** | **803** 🚀 ☕ | 710 | 407 | 330 | 283 | 136 | 191 | **`dot`** 🚀 ☕ |
| **Complex Annotation** | **416** ☕ | **589** 🚀 | 158 | 210 | 125 | 91 | 80 | **`fast`** 🚀<br>**`dot`** ☕ |

---

## CEL Expression Parser Shootout (vs. ANTLR4)

We compared the performance of parsing Common Expression Language (CEL) syntax between Google's reference ANTLR4-based parser and `dot-parse` (`dot-cel`).

Average latency was measured in **microseconds per operation** (lower is better):

| Scenario | `dot-parse` (`dot-cel`) (µs/op) | ANTLR4 CEL Engine (µs/op) | `dot-parse` Speedup |
| :--- | :---: | :---: | :---: |
| **`smokeTest`** | **0.860 ± 0.009** | 2.784 ± 0.030 | **3.24x faster** 🚀 ☕ |
| **`anyFieldMessageSelection`** | **0.858 ± 0.007** | 2.428 ± 0.016 | **2.83x faster** 🚀 ☕ |
| **`deepFieldMessageSelection`** | **1.055 ± 0.016** | 3.276 ± 0.019 | **3.11x faster** 🚀 ☕ |
| **`simpleMessageContext`** | **1.634 ± 0.006** | 4.298 ± 0.035 | **2.63x faster** 🚀 ☕ |
| **`mapComprehension`** | **1.737 ± 0.012** | 4.664 ± 0.021 | **2.69x faster** 🚀 ☕ |
| **`listComprehension`** | **1.796 ± 0.020** | 4.469 ± 0.035 | **2.49x faster** 🚀 ☕ |
| **`chainedAnds`** | **3.553 ± 0.017** | 8.083 ± 0.081 | **2.28x faster** 🚀 ☕ |
| **`chainedOrs`** | **3.567 ± 0.020** | 8.314 ± 0.084 | **2.33x faster** 🚀 ☕ |
| **`messageCreation`** | **6.611 ± 0.046** | 13.064 ± 0.106 | **1.98x faster** 🚀 ☕ |
| **`cppSuite`** (Full C++ CEL Test Suite) | **156.4 ± 1.6** | 373.2 ± 4.5 | **2.39x faster** 🚀 ☕ |
| **`longList`** | **436.6 ± 3.9** | 789.4 ± 7.9 | **1.81x faster** 🚀 ☕ |

---

## Email Address Parser Benchmark (RFC 5322 Parsing)

Throughput was measured in **operations per second** (higher is better):

| Scenario | `dot-parse` Throughput (ops/s) | Reference Baselines (ops/s) |
| :--- | :---: | :---: |
| **Single Plain Address (`user@host.com`)** | **4,954,347 ± 1,555,643** | *JMail*: 2,027,660 / *Jakarta*: 13,331,332 |
| **Single Bracketed Address (`<user@host.com>`)** | **4,822,281 ± 1,210,296** | — |
| **Bracketed with Display Name (`"User" <user@host.com>`)** | **3,128,498 ± 432,611** | — |
| **Valid Address List** | **762,194 ± 90,802** | — |
| **Valid Address List (with streaming consumer)** | **703,163 ± 163,685** | — |
| **Mixed Address List** | **363,727 ± 120,764** | — |

## StringIn vs. Keywords: Trie-Based Optimizations

We compared the performance of matching one of many literal strings in a flat choice. In `cats-parse`, this is represented by the `Parser.stringIn` primitive. In `dot-parse`, this is represented by collecting individual string parsers using the `Parser.or()` collector.

### Benchmark Results (Average Time, Lower is Better)

| Scenario | Candidate Strings | `dot-parse` (ns/op) | `cats-parse` (ns/op) |
| :--- | :--- | :---: | :---: |
| **`stringIn` (foo)** | 5 overlapping strings | **58.5 ns** | **73.7 ns** |
| **`stringIn` (broad)** | 676 generated strings | **62,481 ns** | **1,160 ns** |

---

## Key Performance Insights

Our benchmarks highlight four key architectural factors that govern parser performance on the JVM:

### 1. Radix Prefix Trie Optimization (Keywords)
*   **The Problem**: In programming languages and SQL, matching keywords (like `select`, `insert`) usually triggers different parser actions, wrapping string parsers in maps (e.g., `string("select").map(SelectNode::new)`). In most libraries (like `cats-parse`, `fastparse`), this mapping prevents trie-based prefix matching, forcing sequential backtracking through the vocabulary.
*   **The Solution**: `dot-parse`'s `OrParser` is designed to extract prefix alternatives even across map/suffix actions, compiling them into a single `PrefixPruneTree` (trie). This maintains O(k) lookup scaling (proportional to word length) instead of O(N) sequential scans (proportional to vocabulary size), resulting in a **25x-40x speedup** on large keyword sets (e.g., 500 city names).

### 2. Statelessness vs. Instance Allocations
*   **The Problem**: Classic generator tools (like JavaCC and ANTLR) produce stateful, mutable parser instances that are not thread-safe. For micro-parsing tasks (like parsing a single JSON payload or a type signature), allocating a new parser instance, token manager, and input stream wrapper on every call dominates the execution time.
*   **The Solution**: `dot-parse` and modern combinator libraries are stateless and thread-safe. A single parser instance can be pre-allocated and reused indefinitely across multiple threads, bypassing the instance creation tax on hot paths.

### 3. Scannerless vs. Two-Phase Tokenization
*   **The Problem**: Two-phase parsers (like ANTLR4 and `jparsec`) tokenize the input into a list of token objects before executing grammar rules. On small, dense inputs (such as Java type signatures or short JSON payloads), object allocation overhead for the token stream degrades performance.
*   **The Solution**: Scannerless combinators (`dot-parse`, `fastparse`) match directly on the character stream. They avoid token object allocations entirely, scanning text in-place.

### 4. Vectorized Delimiter & SWAR Bitmask Scanning
*   **The Problem**: Scanning comments (like `/* ... */`), quoted strings, or character classes (like ASCII whitespace and identifiers) character-by-character incurs sequential branch checks on hot loops.
*   **The Solution**: `dot-parse` leverages native vectorized string searches (`String.indexOf`) for multi-character delimiters and precomputes 128-bit SWAR bitmasks (`Skipper`) for character predicates to evaluate characters in parallel.
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
