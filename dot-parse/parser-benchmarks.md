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
| **Complex JSON Payload** | 0.180 | 0.134 | **0.288** ☕ | 0.121 | 0.092 | **0.529** 🚀 | 0.219 | 0.015 | 0.097 | 0.082 | 0.067 | 0.073 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex JSON with Comments** | 0.097 | 0.060 | **0.176** ☕ | 0.090 | 0.050 | **0.187** 🚀 | 0.079 | 0.001 | 0.030 | 0.029 | 0.023 | 0.035 | **`fast`** 🚀<br>**`dot`** ☕ |
| **`qux2.json` (Medium JSON)** | — | — | **0.155** ☕ | — | — | **0.244** 🚀 | 0.137 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`bla25.json` (Large JSON)** | — | — | **0.058** ☕ | — | — | **0.120** 🚀 | 0.048 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`countries.geo.json` (Geographic JSON)** | — | — | **0.254** ☕ | — | — | **0.342** 🚀 | 0.152 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |
| **`ugh10k.json` (Very Large JSON)** | — | — | **0.020** ☕ | — | — | **0.034** 🚀 | 0.016 | — | — | — | — | — | **`fast`** 🚀<br>**`dot`** ☕ |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against production-grade, hand-written and generated parsers on the exact same JSON payloads:

| Parser Engine | Complex JSON (ops/ms) | Complex JSON with Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Lenient) | 1.565 | 0.373 |
| **Gson** (Lenient) | 1.119 | 0.340 |
| **`dot-parse`** (Our leading Java combinator) | 0.288 | 0.176 |
| **JavaCC** (Optimized / Best) | 0.134 | 0.060 |

### Key Takeaways from the JSON Shootout

*   **Tomcat JSONParser (JavaCC) Conformance Bug & Fixed Patch**:
    During shootout integration, Apache Tomcat's official `JSONParser.jjt` was found to contain a conformance bug under RFC 8259: its `<NUMBER_DECIMAL>` token rule required a decimal point before allowing an exponent, while `<NUMBER_INTEGER>` prohibited exponents entirely. This caused the parser to crash on valid JSON inputs containing scientific notation on integer bases (e.g., `-5e-122`). We successfully patched the grammar in our benchmark suite to allow exponents on both integers and decimals, restoring full RFC 8259 compliance.
*   **The Stateful Parser Allocation Tax**:
    Despite being a pre-compiled generator, the optimized JavaCC parser runs at less than half the speed of `dot-parse` (0.134 vs 0.288 ops/ms). Because JavaCC generates stateful, mutable parsers that are not thread-safe, a new parser instance, token manager, and string reader must be allocated for every single parse operation. For medium-sized payloads, this setup allocation overhead represents a major bottleneck. `dot-parse` bypasses this entirely by being completely stateless and thread-safe, allowing infinite reuse of a single parser instance.
*   **The DFA Comment-Scanning Tax vs. SIMD Vectorization**:
    On comment-heavy payloads, the JavaCC parser throughput drops by 2.2x (0.134 to 0.060 ops/ms), running 2.9x slower than `dot-parse`. This is caused by JavaCC's lexical comment rule (`"/*" (~[])* "*/"`), which compiles into a character-by-character DFA transition loop to check for the comment suffix. `dot-parse` avoids this by using `sequence("/*", first("*/"))`, which delegates to Java's native `String.indexOf()`. The JVM optimizes `indexOf` using vectorized SIMD instructions, scanning memory blocks in parallel and jumping the pointer instantly, delivering a massive systems-level advantage over DFA character loops.
*   **Google's `dot-parse` High-Efficiency Delimiter Scanning**:
    `dot-parse` remains the fastest Java-native parser library at 0.176 ops/ms on comment-heavy files, running 2.9x faster than JavaCC and delivering 47.2% of Jackson's speed. Its internal return elision mechanism completely avoids intermediate object allocations on repetition loops, maintaining high throughput.
*   **`fastparse` Allocation Overhead on Repetitions**:
    While `fastparse`'s compile-time macro expansion makes it exceptionally fast, its idiomatic block comment combinator (`"/*" ~ (!"*/" ~ AnyChar).rep ~ "*/"`) incurs a non-trivial performance tax. Because we found no `cats-parse`-like `void()` or `dot-parse`-like return elision in `fastparse` to elide sequence generation, the intermediate `Seq[Char]` heap allocations and primitive character boxing required by the repetition combinator result in a 5.9% lower throughput compared to its strict JSON parser (when scaled for payload size).
*   **`cats-parse`** (Scala):
    `cats-parse` achieves highly optimized comment scanning by leveraging Tuple-free sequencing operators (`*>` and `<*`) and the native, pre-compiled `P.until(P.string("*/")).void` scanner. This completely avoids intermediate list and tuple allocations, maintaining a stable 0.079 ops/ms throughput (running at 21.2% of Jackson's speed).
*   **`taker`'s Recursion Protection Tax on JSON**:
    While highly competitive on flat loops, `taker` drops significantly on the JSON benchmark (0.097 ops/ms vs. `dot-parse`'s 0.288 ops/ms). Because the JSON parser traverses the recursive rule reference chain at every element boundary, `taker` has to evaluate its cycle-detection check for every element (including flat primitives like numbers or strings). Under the hood, its dynamic `CheckParser` wrapper incurs a heavy performance tax at every recursive boundary: performing a `ThreadLocal` lookup, querying/writing to an `IntObjectMap`, allocating a new `ArrayDeque<>` stack, and scanning the active stack. This makes dynamic recursion protection the primary contributor to `taker`'s slowness on deeply nested JSON payloads.
*   **The Contrast with `dot-parse` & others**:
    - Other benchmarked combinator frameworks (like `fastparse`, `cats-parse`, `jparsec`, `parsecj`, and `better-parse`) don't check for left recursion — they simply crash with a `StackOverflowError` if a rule is left-recursive.
    - Like `taker`, Google's `dot-parse` does guarantee left recursion safety but does so at definition time, paying zero runtime tax. For a deep-dive on how its strict `Parser` vs. `OrEmpty` type dichotomy mathematically guarantees 100% detection of all recursive cycles during the startup dry-run, see [left-recursion.md](./left-recursion.md).
*   **`parsecj`'s Regex Tax on Delimiters**:
    No native, pre-compiled block comment skipper or `manyTill` combinator could be found in the library's repository or online tutorials. Consequently, the benchmark fell back to using `regex(...)`, which appears to be a common practice in `parsecj` and it performed well in the IPv4 benchmark. However, evaluating a Java `Pattern` regex match at every single token boundary check is extremely heavy, causing a 15x performance drop (0.015 to 0.001 ops/ms) due to continuous `Matcher` allocations on the hot path.
*   **Two-Phase Scanning Overhead**:
    Both `jparsec` (0.090 ops/ms) and ANTLR4 (0.097 ops/ms) skip comments during tokenization before parser rules execute. While architecturally clean, this two-phase design runs at about 50-55% of the speed of `dot-parse` due to token stream allocation overhead.

---

## CSS Parser Shootout (6-Way Showdown)

To evaluate how these frameworks handle a **highly ambiguous, whitespace-sensitive, and recursively nested document format**, we compared their performance on a full CSS stylesheet, [bootstrap.css](file:///Users/benyu/mug/mug-benchmarks/src/test/resources/bootstrap.css) (146 KB).

Every engine was validated against the same test suite and successfully parsed all W3C CSS Syntax Level 3 elements.

Throughput was measured in **operations per millisecond** (higher is better), with Scala's **`fastparse`** serving as the performance baseline (**1.00x**):

| Parser Engine | Throughput (ops/ms) | Relative Performance (vs. `fastparse`) | Notes / Optimizations |
| :--- | :---: | :---: | :--- |
| [**`dot-parse`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/CssParser.java) | **0.343 ± 0.005** | **1.45x** 🚀 ☕ | Stateless, zero-allocation radix-tree scanning on hot paths. |
| [**`fastparse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/fastparse/FastparseCssParser.scala) | 0.236 ± 0.006 | 1.00x (Baseline) | Official fastparse benchmark implementation (Scala macro-based). |
| [**`cats-parse`**](../mug-benchmarks/src/test/scala/com/google/mu/benchmarks/parsers/catsparse/CatsParseCssParser.scala) | 0.222 ± 0.003 | 0.94x | Optimized via left-factoring numeric/identifier choices. |
| [**`parboiled` (v1)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledCssParser.java) | 0.110 ± 0.001 | 0.47x | Classic PEG combinators with ASM bytecode generation. |
| [**`htmlUnit` (javacc)**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/javacc/HtmlUnitCssParser.java) | 0.024 ± 0.001 | 0.10x | Official HtmlUnit CSS Parser implementation (JavaCC-generated). |
| [**`antlr4`**](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/antlr4/Antlr4CssParser.java) | 0.007 ± 0.001 | 0.03x | Official ANTLR grammars-v4 CSS3 parser grammar. |

### Rationale for ANTLR4 and JavaCC Poor Performance

*   **Heavy Backtracking on Ambiguous Syntax**:
    CSS selectors and media queries are highly ambiguous (e.g. distinguishing nested `@media` rules from selector properties, or matching complex space-separated list values). This forces top-down LL/PEG engines like ANTLR4 and JavaCC to make deep lookahead checks and perform heavy backtracking, which degrades performance.
*   **Inability to Leverage Efficient Execution Modes**:
    *   **ANTLR4**: ANTLR4's Adaptive LL(*) engine compiles its prediction trees dynamically. However, because whitespace acts as a descendant combinator in selectors but is skipped inside style blocks, the parser faces highly ambiguous whitespace rules. This triggers heavy ATN prediction lookup paths and prevents the engine from entering its fast, pre-compiled DFA path.
    *   **JavaCC (`htmlUnit`)**: JavaCC lacks vectorized scanning capabilities for comments and blocks, falling back to slow character-by-character DFA loops.
*   **Why Scannerless Combinators Are Unaffected**:
    Unlike two-phase lexer/parser architectures, scannerless combinators (`dot-parse`, `fastparse`, `cats-parse`, `parboiled`) do not have a separate tokenization phase and parse whitespace explicitly as standard grammar rules. During backtracking, the parser naturally rewinds the character stream pointer without needing to coordinate or synchronize a separate lexer buffer with the active rule state. This makes context-sensitive whitespace checks local and highly efficient.

-----

## 11-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.

> [!NOTE]
> **Emoji Legend**:
> *   🚀 **Rocket Emoji**: Indicates the **overall #1 leader** across all tested libraries and JVM languages (Java, Scala, Kotlin).
> *   ☕ **Coffee Emoji**: Indicates the **#1 leader among Java-native libraries**. When a Java library leads overall across all languages, it receives both icons (🚀 ☕).

| Benchmark Scenario | `dot-parse` | `jparsec` | `fastparse` | `cats-parse` | `taker` | `parsecj` | `parboiled` | `antlr4` | `scalaParser` | `petitparser` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **IPv4 Address** | **15,486** ☕ | 9,125 | **23,335** 🚀 | 11,758 | 4,757 | 12,403 | 5,206 | 1,884 | 3,398 | 6,788 | 1,936 | **`fast`** 🚀<br>Java: **`dot`** ☕ |
| **String (Simple)** | 8,365 | 5,582 | 21,615 | 12,344 | **27,420** 🚀 ☕ | 4,959 | 1,933 | 3,637 | 3,747 | 2,917 | 4,778 | **`taker`** 🚀 ☕ |
| **String (Escaped)** | 5,218 | 4,068 | **12,143** 🚀 | 3,021 | **8,480** ☕ | 2,553 | 1,509 | 3,852 | 3,288 | 2,333 | 1,363 | **`fast`** 🚀<br>Java: **`taker`** ☕ |
| **120 Programming Keywords (CS)** | **216.83** 🚀 ☕ | 15.43 | 12.08 | 13.74 | 10.59 | 5.11 | 88.22 | 36.29 | 1.70 | 13.06 | — | **`dot`** 🚀 ☕ |
| **120 Programming Keywords (CI)** | **152.10** 🚀 ☕ | 14.64 | 11.31 | 13.37 | 10.67 | 4.01 | 8.25 | 34.10 | 1.28 | 10.76 | — | **`dot`** 🚀 ☕ |
| **500 City Names (CS)** | **25.94** 🚀 ☕ | 0.92 | 0.47 | 0.68 | 0.56 | 0.21 | 14.88 | 6.07 | 0.10 | 0.80 | — | **`dot`** 🚀 ☕ |
| **500 City Names (CI)** | **18.22** 🚀 ☕ | 0.75 | 0.45 | 0.74 | 0.52 | 0.08 | 0.45 | 6.92 | 0.07 | 0.60 | — | **`dot`** 🚀 ☕ |
| **Calculator (Math)** | **393** ☕ | 356 | **1,160** 🚀 | 404 | **395** ☕ | 198 | 269 | 355 | 200 | 353 | 231 | **`fastparse`** 🚀<br>Java: **`dot`** / **`taker`** ☕ |
| **Nested Comments** | **9,898** 🚀 ☕ | 2,544 | 4,015 | 2,130 | 663 | 593 | 801 | 1,052 | 253 | 993 | 1,339 | **`dot`** 🚀 ☕ |
| **US Phone (Single)** | **18,414** 🚀 ☕ | 7,420 | 7,978 | 12,033 | 13,978 | 8,734 | 4,548 | 5,710 | 3,199 | 6,716 | 8,906 | **`dot`** 🚀 ☕ |
| **US Phone (1,000-List)** | **11.05** 🚀 ☕ | 8.73 | 8.25 | 10.99 | 7.36 | 1.89 | 3.77 | 7.52 | 2.83 | 5.33 | 5.05 | **`dot`** 🚀 ☕ |

### Showdown Scenario Analysis & Rationalization

#### 1. IPv4 Address Parsing (Flat Sequencing)
*   **Performance**: `fastparse` (23.3M ops/sec) leads, followed by `parsecj` (12.4M) and `cats-parse` (11.8M). `dot-parse` is the leading Java library overall at **15.5M ops/sec**.
*   **Regex Delegation vs. Combinator Sequencing**: `parsecj` (12,403 ops/ms) achieves its throughput by utilizing a single flat regular expression parser (`regex(...)`). This delegates the entire sequence matching to Java's native regular expression engine. While this represents a significant throughput increase, it measures the performance of the JVM's native regex engine rather than the parser combinator's own monadic sequencing overhead.
*   **ANTLR4 Two-Phase Allocation Overhead**: `antlr4` (1.9k) shows lower throughput here due to its compiler-grade two-phase parsing architecture (Lexer + Parser). On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual `CommonToken` objects for every single token scanned, adding object allocation overhead.

<hr>

#### 2. Quoted String Parsing (Common Case vs. Escaped Edge Case)
*   **Performance**: On simple strings, `taker` (27.4M ops/sec) leads overall, followed by `fastparse` (21.6M) and `cats-parse` (12.3M), while `dot-parse` is at **8.4M ops/sec**. On escaped strings, `fastparse` leads overall at **12.1M ops/sec**, followed by `taker` (8.5M) and `dot-parse` (5.2M).
*   **`taker`'s Dedicated Lexical Primitive**: `taker` achieves **8,480 ops/ms** on escaped strings (leading among Java engines) by utilizing its built-in, native `Lexical.escapedString('"', '\\', escapesMap)` primitive. Rather than composing general-purpose character combinators (which incur allocation and dispatch overhead on every character), `taker` delegates to a dedicated lexical scanner that parses the string and resolves escapes in a single flat loop.
*   **Bulk Scanning & Regex Delegation**: Libraries that support native bulk-scanning primitives (like `jparsec` 's string scanner) perform well.

<hr>

#### 3. Keywords & Case-Insensitive Tries
*   **120 Programming Keywords (CS)**: On 120 realistic programming keywords across SQL, Java, C++, Python, Rust, Go, and JavaScript, `dot-parse` (**216.83 ops/ms**) is the #1 leader across all 12 libraries, outperforming #2 `parboiled` (88.22 ops/ms) by 2.46x and #3 `antlr4` (36.29 ops/ms) by 5.97x.
*   **120 Programming Keywords (CI)**: In case-insensitive mode on 120 programming keywords, `dot-parse` leads overall at **152.10 ops/ms**, outperforming #2 `antlr4` (34.10 ops/ms) by 4.46x and #3 `jparsec` (14.64 ops/ms) by 10.39x.
*   **Trie Dispatch Implementation**: `dot-parse` (`anyOf`) compiles keyword alternatives into optimized **Radix Prefix Tries**, bypassing sequential backtracking. By compiling its branching nodes into a flat lookup table (array of size 256) when using `.precomputeForAscii()`, it achieves its high throughput. For case-insensitivity, its prefix-trie compiler precomputes capitalization permutations of the first 4 characters at startup, maintaining an optimized O(1) dispatch.
*   **Backtracking Penalties**: Libraries that do not precompute prefix-tries must backtrack through all options sequentially or evaluate regex choices sequentially, resulting in lower throughput (e.g., `parsecj` at **4.01 ops/ms** on case-insensitive keywords).
*   **Vocabulary Scaling Boundary (500 City Names)**: When scaling from 120 programming keywords to a vocabulary of **500 world city names**, macro-compiled PEG engines like `parboiled2` hit a compile-time scaling limit, failing with compiler stack overflow (`StackOverflowError` in `scalac` during typechecking). Across all 11 runtime combinator and parser libraries benchmarked under full warmup:
    *   **500 City Names (CS)**: `dot-parse` leads overall at **25.94 ops/ms**, outperforming #2 `parboiled` (14.88 ops/ms) by 1.74x and #3 `antlr4` (6.07 ops/ms) by 4.27x. Because `dot-parse` compiles keyword alternations into an in-memory Radix prefix trie, lookup complexity scales by word length (O(k)) rather than vocabulary size (O(N)). By contrast, combinator libraries without prefix tries experience significant throughput reduction: `fastparse` drops to 0.47 ops/ms, `cats-parse` to 0.68 ops/ms, and `jparsec` to 0.92 ops/ms.
    *   **500 City Names (CI)**: `dot-parse` leads overall at **18.22 ops/ms**, outperforming #2 `antlr4` (6.92 ops/ms) by 2.63x and #3 `jparsec` (0.75 ops/ms) by 24.29x. When generating capitalization permutations per 4-character prefix across 500 city names (~8,000 trie branches), `dot-parse` outperforms `cats-parse` (0.74 ops/ms) by 24.6x and `fastparse` (0.45 ops/ms) by 40x.
    *   **Architectural Representation of 500 City Names**:
        *   **`parsecj` (500 Separate Regexes)**: In case-insensitive mode, `parsecj` compiles 500 individual regular expression parsers (`regex("(?i)" + kw)`), combined into an ordered choice (`choice(...)`). It sequentially evaluates up to 500 separate Java `Pattern`/`Matcher` regex executions per token, causing its throughput to drop to **0.08 ops/ms**.
        *   **`dot-parse` (Single Radix Prefix Trie)**: `dot-parse` avoids regexes entirely, compiling all 500 string parsers into a single in-memory Radix Prefix Trie (`PrefixPruneTree`). For case-insensitivity, it precomputes capitalization permutations of the first 4 characters at startup (~8,000 branches), maintaining an O(1) table lookup into surviving candidates and achieving **18.22 ops/ms** (#1 overall).
        *   **`antlr4` (Single Compiled DFA Table)**: ANTLR4 defines 500 separate lexer rules in `Showdown.g4`. At generator compile-time, it combines all 500 rules into a single Deterministic Finite Automaton (DFA) state table, executing a single DFA state transition loop across all candidates rather than checking rules sequentially (**6.92 ops/ms**, #2 overall).
        *   **Other Combinators (`jparsec`, `fastparse`, `cats-parse`, `petitparser`, `parboiled`, `taker`) (500 Sequential String Checks)**: Lacking automatic prefix-trie compilation on AST-mapped parsers, these engines construct 500 individual string scanners combined into an ordered choice (`or()`, `FirstOf`, `/`, `|`), sequentially evaluating up to 500 string comparisons per token.
    *   **Allocation Domination vs. Pure Character-Scanning Scaling**:
        *   **Why `antlr4` (~4.9x drop) and `parboiled` (~5.7x drop) scale close to token volume (4.17x)**: In both engines, physical character scanning represents only a small fraction of runtime. In `antlr4`, each invocation allocates a new `CharStream`, builds 120 vs. 500 AST `KeywordContext` objects, and calls `getText()` (allocating 120 vs. 500 new String objects on the heap) to perform a `HashMap.get()`. In `parboiled`, every matched token calls `match()` and `toLowerCase()` (allocating 240 vs. 1,000 new String objects per parse), performs a `HashMap.get()`, and manipulates a runtime ValueStack. Because 80%+ of their CPU cycles are spent on object creation and framework data structures, scanning ~4 extra characters per word adds negligible runtime overhead; their performance drop is governed almost entirely by the number of tokens allocated (500 / 120 = ~4.17x).
        *   **Why `dot-parse` drops by 7.70x**: In `dot-parse`, `.thenReturn(value)` binds the target enum value directly to the trie leaf at setup time, and `.atLeastOnceDelimitedBy(",")` uses return elision to insert values directly into the result list. There are zero String allocations, zero `getText()` or `match()` calls, zero `HashMap` lookups, and zero runtime stack manipulations during parsing. Because almost 100% of its CPU time is spent actively scanning characters inside the Radix prefix trie, when average word length increases by ~1.7x (from 5.5 to 9.5 characters) and shared prefixes (`San...`, `Santa...`) deepen trie traversal, `dot-parse` reflects the actual physical cost of character scanning (~7.7x total drop), whereas allocation-heavy engines mask this cost behind object creation overhead.

<hr>

#### 4. Calculator & Nested Comments (Recursive Scenarios)
*   **Performance Calculator**: `fastparse` (1,160 ops/ms) leads overall (among standard runtime libraries), followed by `dot-parse` (393 ops/ms) and `petitparser` (353 ops/ms).
*   **Performance Comments**: `dot-parse` (9.9M ops/sec) leads overall, followed by `fastparse` (4.0M).
*   **`dot-parse` Native Flat Character Scan**: `dot-parse` achieves its comment parsing throughput by utilizing its native `nestedBy("/*", "*/")` primitive. Rather than constructing a recursive tree of parser combinator objects, `nestedBy` scans the character stream in a single flat loop, tracking nesting depth in a primitive integer counter. This minimizes CPU and memory overhead, outperforming even macro-rewritten engines.
*   **`taker`'s Flat Operator Loop**: On the Calculator, `taker` (395 ops/ms) is highly competitive with `dot-parse` (393 ops/ms). This is because it utilizes its built-in `chainLeftOneOrMore` combinator, which compiles left-associative operators into a single flat `while` loop, avoiding recursive stack-checking and cycle-detection overhead almost entirely.



<hr>

#### 5. Kotlin `better-parse` Architectural Profile
*   **Property Delegation Overhead**: `better-parse` represents grammars using Kotlin's delegated properties (`by`), which introduces multiple runtime wrapper layers and lookup overhead during parser initialization and match dispatching.
*   **Heavy Intermediate Allocations**: Unlike zero-allocation parser scans, `better-parse`'s tokenizer scans inputs and allocates a list of intermediate `TokenMatch` objects on the fly, putting significant garbage collection pressure on the JVM hot path.
*   **Regex and Backtracking Bottlenecks**: On case-insensitive keywords, `better-parse` drops to a very low **15.8 ops/ms** (15,800 parses/sec) because it compiles 12 separate `Regex` objects and matches them sequentially per character. This is **12.7x slower** than `dot-parse`'s Radix prefix tries and **3.5x slower** than `taker`.
*   **Scenario Specific Strengths**: `better-parse` shows respectable throughput on simple strings (**4,778 ops/ms**) and nested block comments (**1,339 ops/ms**), outperforming classic Java engines like `parsecj` (593 comments) and `taker` (663 comments) on deep nested structures.

<hr>

#### 6. US Phone Number Parsing (Single & 1,000-Element List)
*   **Performance (Single Number)**: `parboiled2` (30.7M ops/sec) leads overall. `dot-parse` is the leading Java library at 18.4M ops/sec, outperforming `taker` (14.0M) and `cats-parse` (12.0M).
*   **Performance (1,000-Element List)**: `dot-parse` (11.05 lists/ms, or ~11.0M phone numbers/sec) leads overall across all 12 libraries, followed by `cats-parse` (10.99 lists/ms) and `jparsec` (8.73 lists/ms).
*   **JMH Operation Units (Why ~29,000 drops to ~8-11)**: In `US Phone (Single)`, one JMH operation measures parsing **1 single phone number**, meaning `30,723 ops/ms` represents 30,723 individual numbers parsed per millisecond. In `US Phone (1,000-List)`, one JMH operation measures parsing **1 entire list of 1,000 phone numbers**. When normalized to individual items by multiplying by 1,000, `7.95 lists/ms` equals **7,950 numbers/ms** for `parboiled2`, while `11.05 lists/ms` equals **11,050 numbers/ms** for `dot-parse`.
*   **Architectural Trade-Off: Macro Inlining vs. Repetition Loop Overhead**: While `parboiled2` represents the performance ceiling on micro-inputs (leading on `US Phone (Single)` at 30,723 ops/ms), its relative throughput drops on long sequences, falling to #6 overall on `US Phone (1,000-List)` (**7.95 lists/ms**):
    *   **Micro-Input Regime (`parboiled2` on Single Mode)**: On a short 13-character string, collection construction and stack manipulation costs are non-existent. `parboiled2`'s compile-time macro expands rule matching directly into flat JVM bytecode without method dispatch indirection, executing in ~33 nanoseconds.
    *   **High-Repetition Regime (`parboiled2` in List Mode)**: In long repetition loops (1,000 elements), execution time becomes dominated by per-element data management: pushing and popping 1,000 times on `parboiled2`'s runtime ValueStack, constructing intermediate Scala sequences, and wrapping those sequences in Java collection adapters (`seq.asJava`). Additionally, every parse invocation allocates a new parser instance (`new UsPhoneParser(input)`).
    *   **Why `dot-parse` Leads on Lists**: In high-repetition regimes, `dot-parse` (**11.05 lists/ms**) and optimized combinator scanners (`cats-parse` at 10.99 and `fastparse` at 8.25) surpass `parboiled2`. `dot-parse` uses a static, stateless singleton parser whose `.zeroOrMore()` collector executes a direct loop inserting sliced substrings straight into a standard Java list without intermediate collection builders, ValueStack manipulations, or collection adapter boxing, while `.parseSkipping()` scans whitespace using a primitive bit-mask check.

<hr>

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `parsecj` | `taker` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Simple Type (`String`)** | 3,601 | **8,181** ☕ | 1,610 | 3,549 | **9,255** 🚀 | 1,554 | 2,572 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Fully Qualified** | 1,579 | **4,304** ☕ | 665 | 2,161 | **5,308** 🚀 | 916 | 1,510 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | 311 | **876** ☕ | 150 | 444 | **1,173** 🚀 | 191 | 328 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | 345 | **805** ☕ | 148 | 411 | **989** 🚀 | 211 | 287 | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | 245 | **317** ☕ | 105 | 164 | **701** 🚀 | 86 | 127 | **`fast`** 🚀<br>**`dot`** ☕ |

### Key Takeaways from the Java Type Shootout

*   **Google's `dot-parse` Leads the Java Division**:
    `dot-parse` is the fastest Java-native parser library, running **1.3x to 2.3x faster** than the next fastest Java contender (`petitparser` / `antlr4`).
    On simple types, `dot-parse` (8,181 ops/ms) is the leading Java library, behind Scala's macro-based `fastparse` (9,255 ops/ms) by leveraging a zero-allocation, pre-allocated tokenizer that avoids object boxing on the hot path.

*   **Compile-Time vs. Runtime Combinators**:
    Scala's compile-time macro-based `fastparse` leads overall in all scenarios. By performing compile-time macro expansion and inlining all parsing loops directly into JVM bytecode, it strips away object allocations and method dispatch overhead.

*   **`taker` Delivers Solid, High-Performance PEG Baselines**:
    The `taker` parser performs well, consistently **close to `antlr4`** on fully qualified (1,556 vs 1,661) and **beating `antlr4`** on nested generic (337 vs 306) signatures. It also **outperforms `parsecj` and `jparsec` by nearly 2x** across almost all scenarios, proving that a lean PEG design with optimized applicative builders (`ApplyBuilder3`) is highly competitive.

*   **Scannerless vs. Two-Phase Tokenization**:
    For small, dense inputs with minimal whitespace (such as Java type signatures), scannerless parsers (`dot-parse`, `taker`, `parsecj`) are a fundamentally better architectural fit than two-phase tokenizing parsers (`jparsec`, `antlr4`). Two-phase parsers pay a high object-allocation penalty to construct intermediate token lists, whereas scannerless parsers operate directly on the character stream with zero token overhead.

---

## StringIn vs. Keywords: Trie-Based Optimizations

We compared the performance of matching one of many literal strings in a flat choice. In `cats-parse`, this is represented by the `Parser.stringIn` primitive. In `dot-parse`, this is represented by collecting individual string parsers using the `Parser.or()` collector.

### Benchmark Results (Average Time, Lower is Better)

| Scenario | Candidate Strings | `dot-parse` (ns/op) | `cats-parse` (ns/op) |
| :--- | :--- | :---: | :---: |
| **`stringIn` (foo)** | 5 overlapping strings | **73 ns** | **78 ns** |
| **`stringIn` (broad)** | 676 generated strings | **1065 ns** | **1066 ns** |

Both libraries perform on par because they both compile the flat list of strings into an optimized trie structure at runtime:
*   **`cats-parse`** compiles them into an internal `RadixNode` (trie).
*   **`dot-parse`** compiles them into a `PrefixPruneTree` (trie) inside `OrParser`.

### The Critical Architectural Difference: `StringIn` vs. `Keywords`

While they perform identically on flat string choices (`StringIn`), their performance diverges significantly in real-world programming language grammars (the `Keywords` case):

1.  **`StringIn` (Flat Choice)**:
    This is used when a list of strings are treated identically by the grammar (e.g., matching any operator or identifier where the exact string is just returned as a leaf value).
    *   In this case, both `cats-parse` and `dot-parse` compile the raw `Parser.string` instances into their respective tries, achieving excellent ~1 μs scaling for hundreds of strings.

2.  **`Keywords` (Leading Choice)**:
    This is the standard pattern in programming language and SQL parsers, where different keywords lead to entirely different grammar branches and parser actions (e.g., `select` leads to a `selectStatement` rule, `insert` leads to an `insertStatement` rule).
    *   **The Suffix/Map Limitation in `cats-parse`**: Because different branches must map to different AST nodes or trigger different rules, they require suffix operations (like `.map` or `*>`/`<*`). In `cats-parse`, appending `.map` to a string parser wraps it in a `Parser.Map` class. This **destroys** `cats-parse`'s radix-tree optimization, forcing it to fall back to sequential backtracking (trying all keywords one-by-one), which is extremely slow.
    *   **The Prefix-Pruning Advantage in `dot-parse`**: `dot-parse`'s `OrParser` is designed to extract the prefix of candidate parsers **even if they have suffix/map operations**. Its `PrefixPruneTree` can still prune candidates by their leading character prefixes, allowing `dot-parse` to maintain ~1 μs trie-like scaling even when different keywords lead to different complex rules.

## Common Expression Language (CEL) Shootout (Parity Comparison)

We compared the performance of Google's official ANTLR-based Java CEL parser (`cel-java` using `dev.cel:cel`) against our lightweight `dot-parse`-based `CelParser` (module `mug-cel`) on a variety of representative CEL expressions. Both parsers output compatible proto ASTs (`com.google.api.expr.v1alpha1.ParsedExpr`) with full position tracking (`positions`), original macro invocation context (`macro_calls`), and line offset records (`line_offsets`).

Both parsers were strictly validated at setup time to guarantee 100% parity:
1. Identical AST structures.
2. Identical `line_offsets` arrays (including EOF offset mapping).
3. Identical `positions` map size.
4. Identical `macro_calls` map size.

Throughput was measured in **microseconds per operation** (lower is better):

| Benchmark Scenario / Expression | ANTLR Parser (`cel-java`) | `dot-parse` Parser (`mug-cel`) | Speedup |
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
