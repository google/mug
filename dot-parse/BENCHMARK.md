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
| **Complex JSON Payload** | 0.180 | 0.160 | **0.549** 🚀 ☕ | 0.122 | 0.087 | 0.499 | 0.238 | 0.014 | 0.093 | 0.076 | 0.064 | 0.076 | **`dot`** 🚀 ☕ |
| **Complex JSON with Comments** | 0.095 | 0.059 | **0.292** ☕ | 0.090 | 0.047 | **0.325** 🚀 | 0.076 | 0.002 | 0.030 | 0.029 | 0.019 | 0.034 | **`fast`** 🚀<br>**`dot`** ☕ |
| **`qux2.json` (Medium JSON)** | — | — | **0.213** ☕ | — | — | **0.242** 🚀 | 0.131 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`bla25.json` (Large JSON)** | — | — | **0.085** ☕ | — | — | **0.119** 🚀 | 0.049 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`countries.geo.json` (Geographic JSON)** | — | — | **0.282** ☕ | — | — | **0.346** 🚀 | 0.152 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`ugh10k.json` (Very Large JSON)** | — | — | **0.029** ☕ | — | — | **0.035** 🚀 | 0.017 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against production-grade, hand-written and generated parsers on the exact same JSON payloads:

| Parser Engine | Complex JSON (ops/ms) | Complex JSON with Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Lenient) | 1.049 | 0.296 |
| **Gson** (Lenient) | 0.823 | 0.307 |
| **`dot-parse`** (Our leading Java combinator) | **0.549** | **0.292** ☕ |
| **JavaCC** (Tomcat / Best) | 0.160 | 0.059 |

#### Reference Streaming Baselines (8,000 Rows, ~8MB JSONL)
To evaluate continuous data ingestion performance, we benchmarked incremental record streaming from a `Reader` on an 8,000-row (~8.1 MB total) JSONL file:

| Streaming Parser Engine | Clean JSONL (ops/ms) |
| :--- | :---: |
| **Gson** (Streaming) | 0.030 |
| **Jackson Databind** (Streaming) | 0.029 |
| **`dot-parse`** (`parseToStream`) | **0.016** |
| **JavaCC** (Parser Generator) | 0.011 |

---

## CSS Parser Shootout (6-Way Showdown)

To evaluate how these frameworks handle a **highly ambiguous, whitespace-sensitive, and recursively nested document format**, we compared their performance on a full CSS stylesheet, [bootstrap.css](../mug-benchmarks/src/test/resources/bootstrap.css) (146 KB).

Every engine was validated against the same test suite and successfully parsed all W3C CSS Syntax Level 3 elements.

Throughput was measured in **operations per millisecond** (higher is better), with Scala's **`fastparse`** serving as the performance baseline (**1.00x**):

| Parser Engine | Throughput (ops/ms) | Relative Performance (vs. `fastparse`) | Notes / Optimizations |
| :--- | :---: | :---: | :--- |
| [**`dot-parse`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/CssParser.java) | **0.508 ± 0.068** | **2.16x** 🚀 ☕ | 128-bit SWAR bitmask skipper and radix prefix matching. |
| [**`fastparse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/fastparse/FastparseCssParser.scala) | 0.235 ± 0.051 | 1.00x (Baseline) | Official fastparse benchmark implementation (Scala macro-based). |
| [**`cats-parse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/catsparse/CatsParseCssParser.scala) | 0.221 ± 0.038 | 0.94x | Optimized via left-factoring numeric/identifier choices. |
| [**`parboiled` (v1)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledCssParser.java) | 0.108 ± 0.018 | 0.46x | Classic PEG combinators with ASM bytecode generation. |
| [**`better-parse`**](../mug-benchmarks/src/test/kotlin/com/google/mu/benchmarks/parsers/betterparse/BetterParseCssParser.kt) | 0.064 ± 0.025 | 0.27x | Kotlin delegated property combinators. |
| [**`htmlUnit` (javacc)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/javacc/HtmlUnitCssParser.java) | 0.024 ± 0.007 | 0.10x | Official HtmlUnit CSS Parser implementation (JavaCC-generated). |
| [**`antlr4`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/antlr4/Antlr4CssParser.java) | 0.007 ± 0.002 | 0.03x | Official ANTLR grammars-v4 CSS3 parser grammar. |
| [**`jparsec`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/jparsec/JparsecCssParser.java) | 0.006 ± 0.001 | 0.02x | Classic monadic combinator implementation. |

---

## 11-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors and 128-bit SWAR skippers on the hot path.

| Benchmark Scenario | `dot-parse` | `jparsec` | `fastparse` | `cats-parse` | `taker` | `parsecj` | `parboiled` | `antlr4` | `scalaParser` | `petitparser` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **IPv4 Address** | **16,155** ☕ | 8,692 | **23,863** 🚀 | 13,087 | 4,646 | 11,594 | 845 | 1,904 | 3,402 | 7,089 | 1,833 | **`fast`** 🚀<br>Java: **`dot`** ☕ |
| **String (Simple)** | **45,121** 🚀 ☕ | 5,430 | 21,405 | 11,290 | 32,064 | 4,805 | 519 | 4,804 | 3,691 | 2,784 | 4,911 | **`dot`** 🚀 ☕ |
| **String (Escaped)** | 3,783 | 3,955 | 10,633 | 3,035 | **20,827** 🚀 ☕ | 1,570 | 501 | 3,606 | 3,330 | 2,206 | 1,361 | **`taker`** 🚀 ☕ |
| **120 Programming Keywords (CS)** | **28.72** 🚀 ☕ | 0.92 | 0.47 | 0.71 | 0.49 | 0.19 | 14.23 | 6.67 | 0.10 | 0.53 | — | **`dot`** 🚀 ☕ |
| **120 Programming Keywords (CI)** | **19.00** 🚀 ☕ | 0.82 | 0.45 | 0.73 | 0.45 | 0.07 | 0.44 | 6.66 | 0.07 | 0.60 | — | **`dot`** 🚀 ☕ |
| **Calculator (Math)** | **701** ☕ | 345 | **1,163** 🚀 | 407 | 411 | 190 | 113 | 366 | 184 | 350 | 238 | **`fastparse`** 🚀<br>Java: **`dot`** ☕ |
| **Nested Comments** | **10,681** 🚀 ☕ | 2,229 | 5,037 | 2,162 | 706 | 591 | 352 | 1,087 | 245 | 971 | 1,349 | **`dot`** 🚀 ☕ |
| **US Phone (Single)** | **15,005** 🚀 ☕ | 7,107 | 8,446 | 11,439 | 13,992 | 8,501 | 4,226 | 5,851 | 3,183 | 6,666 | 9,607 | **`dot`** 🚀 ☕ |
| **US Phone (1,000-List)** | **11.67** 🚀 ☕ | 9.31 | 8.97 | 11.20 | 8.53 | 1.83 | 3.68 | 7.50 | 2.81 | 5.32 | 5.28 | **`dot`** 🚀 ☕ |

---

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `dot-parse` | `fastparse` | `petitparser` | `antlr4` | `taker` | `jparsec` | `parsecj` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Simple Type (`String`)** | **8,356** ☕ | **9,003** 🚀 | 3,527 | 3,554 | 2,540 | 1,437 | 1,445 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Fully Qualified** | **5,181** ☕ | **5,670** 🚀 | 2,095 | 1,580 | 1,532 | 663 | 924 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | **1,088** ☕ | **1,206** 🚀 | 433 | 305 | 325 | 161 | 187 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | **929** ☕ | **1,002** 🚀 | 420 | 352 | 309 | 150 | 202 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | **449** ☕ | **675** 🚀 | 165 | 246 | 126 | 100 | 85 | **`fast`** 🚀<br>**`dot`** ☕ |

---

## CEL Expression Parser Shootout (vs. ANTLR4)

We compared the performance of parsing Common Expression Language (CEL) syntax between Google's reference ANTLR4-based parser and `dot-parse` (`dot-cel`).

Average latency was measured in **microseconds per operation** (lower is better):

| Scenario | `dot-parse` (`dot-cel`) (µs/op) | ANTLR4 CEL Engine (µs/op) | `dot-parse` Speedup |
| :--- | :---: | :---: | :---: |
| **`smokeTest`** | **0.825 ± 0.310** | 2.718 ± 0.694 | **3.29x faster** 🚀 ☕ |
| **`anyFieldMessageSelection`** | **0.797 ± 0.044** | 2.571 ± 0.590 | **3.22x faster** 🚀 ☕ |
| **`deepFieldMessageSelection`** | **1.005 ± 0.465** | 3.397 ± 1.069 | **3.38x faster** 🚀 ☕ |
| **`simpleMessageContext`** | **1.441 ± 0.453** | 4.408 ± 0.191 | **3.06x faster** 🚀 ☕ |
| **`mapComprehension`** | **1.636 ± 0.642** | 4.544 ± 0.557 | **2.78x faster** 🚀 ☕ |
| **`listComprehension`** | **1.634 ± 0.207** | 4.436 ± 0.245 | **2.71x faster** 🚀 ☕ |
| **`chainedAnds`** | **3.067 ± 1.637** | 8.187 ± 2.005 | **2.67x faster** 🚀 ☕ |
| **`chainedOrs`** | **3.180 ± 1.074** | 8.424 ± 2.344 | **2.65x faster** 🚀 ☕ |
| **`messageCreation`** | **6.377 ± 0.251** | 13.547 ± 3.697 | **2.12x faster** 🚀 ☕ |
| **`cppSuite`** (Full C++ CEL Test Suite) | **146.4 ± 10.1** | 378.3 ± 26.0 | **2.58x faster** 🚀 ☕ |
| **`longList`** | **399.5 ± 3.9** | 806.7 ± 176.4 | **2.02x faster** 🚀 ☕ |

---

## Email Address Parser Benchmark (RFC 5322 Parsing)

Throughput was measured in **operations per second** (higher is better):

| Scenario | `dot-parse` Throughput (ops/s) | Reference Baselines (ops/s) |
| :--- | :---: | :---: |
| **Single Plain Address (`user@host.com`)** | **6,770,743 ± 126,282** | *JMail (2.2.1)*: 11,249,380 / *Jakarta*: 14,798,664 |
| **Single Bracketed Address (`<user@host.com>`)** | **5,732,352 ± 56,649** | — |
| **Bracketed with Display Name (`"User" <user@host.com>`)** | **3,594,687 ± 90,061** | — |
| **Valid Address List** | **948,052 ± 20,112** | — |
| **Valid Address List (with streaming consumer)** | **899,385 ± 15,396** | — |
| **Mixed Address List** | **444,802 ± 7,105** | — |

## StringIn vs. Keywords: Trie-Based Optimizations

We compared the performance of matching one of many literal strings in a flat choice. In `cats-parse`, this is represented by the `Parser.stringIn` primitive. In `dot-parse`, this is represented by collecting individual string parsers using the `Parser.or()` collector.

### Benchmark Results (Average Time, Lower is Better)

| Scenario | Candidate Strings | `dot-parse` (ns/op) | `cats-parse` (ns/op) |
| :--- | :--- | :---: | :---: |
| **`stringIn` (foo)** | 5 overlapping strings | **54.8 ns** | **65.1 ns** |
| **`stringIn` (broad)** | 676 generated strings | **56,071 ns** | **939 ns** |

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
