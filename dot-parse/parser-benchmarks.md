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

| Benchmark Scenario | [`antlr4`](../mug-benchmarks/src/test/antlr4/com/google/mu/benchmarks/parsers/antlr4/Json.g4) | [`Javacc`](https://github.com/apache/tomcat/blob/main/java/org/apache/tomcat/util/json/JSONParser.jjt) | [`dot-parse`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/JsonParser.java) | `jparsec` | [`petitparser`](https://github.com/petitparser/java-petitparser/tree/main/petitparser-json) | [`fastparse`](https://github.com/com-lihaoyi/fastparse/blob/master/perftests/bench2/src/perftests/JsonParse.scala) | [`cats-parse`](https://github.com/typelevel/cats-parse) | [`parsecj`](https://github.com/jon-hanson/parsecj/blob/master/src/test/java/org/javafp/parsecj/json/Grammar.java) | [`taker`](https://github.com/parseworks/taker/blob/main/src/test/java/io/github/parseworks/taker/examples/RealisticExamplesTest.java) | [`better-parse`](https://github.com/silmeth/jsonParser) | [`parboiled`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/parboiled/ParboiledJsonParser.java) | [`autumn`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/autumn/AutumnJsonParser.java) | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Complex JSON Payload** | $0.213$ | $0.181$ | **$0.405$** ☕ | $0.122$ | $0.094$ | **$0.592$** 🚀 | $0.258$ | $0.015$ | $0.101$ | $0.088$ | $0.066$ | $0.077$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex JSON with Comments** | $0.102$ | $0.068$ | **$0.224$** 🚀 ☕ | $0.093$ | $0.051$ | **$0.192$** | $0.078$ | $0.002$ | $0.031$ | $0.033$ | $0.022$ | $0.036$ | **`dot`** 🚀 ☕ |
| **`qux2.json` (Medium JSON)** | — | — | **$0.173$** | — | — | **$0.246$** 🚀 | $0.141$ | — | — | — | — | — | **`fast`** 🚀 |
| **`bla25.json` (Large JSON)** | — | — | **$0.061$** | — | — | **$0.119$** 🚀 | $0.047$ | — | — | — | — | — | **`fast`** 🚀 |
| **`countries.geo.json` (Geographic JSON)** | — | — | **$0.253$** | — | — | **$0.340$** 🚀 | $0.162$ | — | — | — | — | — | **`fast`** 🚀 |
| **`ugh10k.json` (Very Large JSON)** | — | — | **$0.021$** | — | — | **$0.035$** 🚀 | $0.018$ | — | — | — | — | — | **`fast`** 🚀 |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against production-grade, hand-written and generated parsers on the exact same JSON payloads:

| Parser Engine | Complex JSON (ops/ms) | Complex JSON with Comments (ops/ms) |
| :--- | :---: | :---: |
| **Jackson Databind** (Lenient) | $1.565$ | $0.373$ |
| **Gson** (Lenient) | $1.198$ | $0.340$ |
| **`dot-parse`** (Our leading Java combinator) | $0.405$ | $0.224$ |
| **JavaCC** (Optimized / Best) | $0.181$ | $0.068$ |

### Key Takeaways from the JSON Shootout

*   **Tomcat JSONParser (JavaCC) Conformance Bug & Fixed Patch**:
    During shootout integration, Apache Tomcat's official `JSONParser.jjt` was found to contain a conformance bug under RFC 8259: its `<NUMBER_DECIMAL>` token rule required a decimal point before allowing an exponent, while `<NUMBER_INTEGER>` prohibited exponents entirely. This caused the parser to crash on valid JSON inputs containing scientific notation on integer bases (e.g., `-5e-122`). We successfully patched the grammar in our benchmark suite to allow exponents on both integers and decimals, restoring full RFC 8259 compliance.
*   **The Stateful Parser Allocation Tax**:
    Despite being a pre-compiled generator, the optimized JavaCC parser runs at less than half the speed of `dot-parse` ($0.181$ vs $0.405$ ops/ms). Because JavaCC generates stateful, mutable parsers that are not thread-safe, a new parser instance, token manager, and string reader must be allocated for every single parse operation. For medium-sized payloads, this setup allocation overhead represents a major bottleneck. `dot-parse` bypasses this entirely by being completely stateless and thread-safe, allowing infinite reuse of a single parser instance.
*   **The DFA Comment-Scanning Tax vs. SIMD Vectorization**:
    On comment-heavy payloads, the JavaCC parser throughput drops by 2.7x ($0.181 \to 0.068$ ops/ms), running 3.3x slower than `dot-parse`. This is caused by JavaCC's lexical comment rule (`"/*" (~[])* "*/"`), which compiles into a character-by-character DFA transition loop to check for the comment suffix. `dot-parse` avoids this by using `sequence("/*", until("*/"))`, which delegates to Java's native `String.indexOf()`. The JVM optimizes `indexOf` using vectorized SIMD instructions, scanning memory blocks in parallel and jumping the pointer instantly, delivering a massive systems-level advantage over DFA character loops.
*   **Google's `dot-parse` High-Efficiency Delimiter Scanning**:
    `dot-parse` remains the fastest Java parser library at $0.224$ ops/ms on comment-heavy files, running 1.17x faster than `fastparse` and delivering 60.1% of Jackson's speed. Its internal return elision mechanism completely avoids intermediate object allocations on repetition loops, maintaining high throughput.
*   **`fastparse` Allocation Overhead on Repetitions**:
    While `fastparse`'s compile-time macro expansion makes it exceptionally fast, its idiomatic block comment combinator (`"/*" ~ (!"*/" ~ AnyChar).rep ~ "*/"`) incurs a non-trivial performance tax. Because we found no `cats-parse`-like `void()` or `dot-parse`-like return elision in `fastparse` to elide sequence generation, the intermediate `Seq[Char]` heap allocations and primitive character boxing required by the repetition combinator result in a 20.2% lower throughput compared to its strict JSON parser (when scaled for payload size).
*   **`cats-parse`** (Scala):
    `cats-parse` achieves highly optimized comment scanning by leveraging Tuple-free sequencing operators (`*>` and `<*`) and the native, pre-compiled `P.until(P.string("*/")).void` scanner. This completely avoids intermediate list and tuple allocations, maintaining a stable $0.078$ ops/ms throughput (running at 20.9% of Jackson's speed).
*   **`taker`'s Recursion Protection Tax on JSON**:
    While highly competitive on flat loops, `taker` drops significantly on the JSON benchmark ($0.101\text{ ops/ms}$ vs. `dot-parse`'s $0.405$ ops/ms). Because the JSON parser traverses the recursive rule reference chain at every element boundary, `taker` has to evaluate its cycle-detection check for every element (including flat primitives like numbers or strings). Under the hood, its dynamic `CheckParser` wrapper incurs a heavy performance tax at every recursive boundary: performing a `ThreadLocal` lookup, querying/writing to an `IntObjectMap`, allocating a new `ArrayDeque<>` stack, and scanning the active stack. This makes dynamic recursion protection the primary contributor to `taker`'s slowness on deeply nested JSON payloads.
*   **The Contrast with `dot-parse` & others**:
    - Other benchmarked combinator frameworks (like `fastparse`, `cats-parse`, `jparsec`, `parsecj`, and `better-parse`) don't check for left recursion — they simply crash with a `StackOverflowError` if a rule is left-recursive.
    - Like `taker`, Google's `dot-parse` does guarantee left recursion safety but does so at definition time, paying zero runtime tax. For a deep-dive on how its strict `Parser` vs. `OrEmpty` type dichotomy mathematically guarantees 100% detection of all recursive cycles during the startup dry-run, see [left-recursion.md](./left-recursion.md).
*   **`parsecj`'s Regex Tax on Delimiters**:
    No native, pre-compiled block comment skipper or `manyTill` combinator could be found in the library's repository or online tutorials. Consequently, the benchmark fell back to using `regex(...)`, which appears to be a common practice in `parsecj` and it performed well in the IPv4 benchmark. However, evaluating a Java `Pattern` regex match at every single token boundary check is extremely heavy, causing a 10x performance drop ($0.020 \to 0.002$ ops/ms) due to continuous `Matcher` allocations on the hot path.
*   **Two-Phase Scanning Overhead**:
    Both `jparsec` ($0.107$ ops/ms) and ANTLR4 ($0.112$ ops/ms) skip comments during tokenization before parser rules execute. While architecturally clean, this two-phase design runs at about 40% of the speed of `dot-parse` due to token stream allocation overhead.



---

## 13-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.

| Benchmark Scenario | `dot-parse` ☕ | `jparsec` | `fastparse` 🚀 | `cats-parse` 🚀 | `taker` | `parsecj` | `parboiled` | `jjparse` | `antlr4` | `scalaParser` | `parboiled2` 🚀 | `petitparser` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **IPv4 Address** | 20,807 | 13,600 | 24,661 | 23,763 | 10,021 | 2,939 | 8,907 | 669 | 2,974 | 3,778 | **27,330** 🚀 | 7,655 | 1,173 | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **String (Simple)** | **14,926** ☕ | 13,833 | 13,746 | 3,150 | 2,825 | 2,019 | 2,522 | 584 | 5,493 | 4,832 | 2,287 | 3,609 | 3,822 | **`dot`** ☕ |
| **String (Escaped)** | 5,797 | 11,111 ☕ | **11,801** 🚀 | 2,976 | 2,208 | 1,513 | 2,201 | 580 | 4,966 | 3,898 | 2,217 | 3,131 | 871 | **`fast`** 🚀<br>Java: **`jparsec`** ☕ |
| **Keywords (120 CS)** | 288 ☕ | 93 | 80 | 86 | 64 | 37 | 96 | 96 | 46 | 19 | **411** 🚀 | 67 | 50 | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **Ignore-Case (120 CI)** | 199 ☕ | 86 | 102 | 91 | 63 | 28 | 36 | 84 | 31 | 12 | **303** 🚀 | 57 | 15 | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **Calculator (Math)** | 701 ☕ | 264 | 1,224 | 513 | 462 | 231 | 329 | 4 | 400 | 216 | **2,094** 🚀 | 630 | 162 | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **Nested Comments** | **11,622** ☕ | 2,307 | 4,587 | 2,445 | 733 | 795 | 912 | 9 | 2,085 | 361 | 6,377 | 1,143 | 970 | **`dot`** ☕ |
| **US Phone (Single)** | 18,262 ☕ | 9,316 | 8,583 | 12,886 | 13,710 | 10,308 | 4,851 | 5,746 | 5,669 | 3,104 | **29,481** 🚀 | 7,110 | 9,720 | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **US Phone (1,000-List)** | **11.30** 🚀 ☕ | 9.46 | 9.24 | 10.54 | 8.69 | 1.94 | 4.16 | 4.81 | 7.76 | 2.91 | 8.11 | 5.55 | 5.26 | **`dot`** 🚀 ☕ |

### Showdown Scenario Analysis & Rationalization

#### 1. IPv4 Address Parsing (Flat Sequencing)
*   **Performance**: `fastparse` ($24.6\text{M}$ ops/sec) leads, followed by `cats-parse` ($18.3\text{M}$). `parsecj` is the leading Java library at **$12.3\text{M}$ ops/sec**, followed by `dot-parse` ($13.2\text{M}$).
*   **Regex Delegation vs. Combinator Sequencing**: `parsecj` ($12,263\text{ ops/ms}$) achieves its throughput by utilizing a single flat regular expression parser (`regex(...)`). This delegates the entire sequence matching to Java's native regular expression engine. While this represents a significant throughput increase, it measures the performance of the JVM's native regex engine rather than the parser combinator's own monadic sequencing overhead.
*   **ANTLR4 Two-Phase Allocation Overhead**: `antlr4` ($1.9\text{k}$) shows lower throughput here due to its compiler-grade two-phase parsing architecture (Lexer + Parser). On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual `CommonToken` objects for every single token scanned, adding object allocation overhead.

<hr>

#### 2. Quoted String Parsing (Common Case vs. Escaped Edge Case)
*   **Performance**: On simple strings, `fastparse` ($22.8\text{M}$ ops/sec) leads overall, followed closely by `taker` ($21.9\text{M}$) and `cats-parse` ($19.4\text{M}$), while `dot-parse` is at **$12.7\text{M}$ ops/sec**. On escaped strings, `taker` leads overall at **$19.9\text{M}$ ops/sec**, followed by `fastparse` ($12.1\text{M}$) and `dot-parse` ($5.2\text{M}$).
*   **`taker`'s Dedicated Lexical Primitive**: `taker` achieves **$19,915$ ops/ms** on escaped strings by utilizing its built-in, native `Lexical.escapedString('"', '\\', escapesMap)` primitive. Rather than composing general-purpose character combinators (which incur allocation and dispatch overhead on every character), `taker` delegates to a dedicated lexical scanner that parses the string and resolves escapes in a single flat loop.
*   **Bulk Scanning & Regex Delegation**: Libraries that support native bulk-scanning primitives (like `jparsec` 's string scanner) perform well.

<hr>

#### 3. Keywords & Case-Insensitive Tries
*   **Performance CS**: `dot-parse` (288 ops/ms) leads among Java libraries, followed by `jjparse` (96 ops/ms) and `parboiled` (96 ops/ms). `parboiled2` leads overall (411 ops/ms).
*   **Performance CI**: `dot-parse` (199 ops/ms) leads among Java libraries, followed by `fastparse` (102 ops/ms) and `cats-parse` (91 ops/ms). `parboiled2` leads overall (303 ops/ms).
*   **Trie Dispatch Implementation**: `dot-parse` (`anyOf`) compiles keyword alternatives into optimized **Radix Prefix Tries**, bypassing sequential backtracking. By compiling its branching nodes into a flat lookup table (array of size 256) when using `.precomputeForAscii()`, it achieves its high throughput. For case-insensitivity, its prefix-trie compiler precomputes capitalization permutations of the first 4 characters at startup, maintaining an optimized O(1) dispatch.
*   **Backtracking Penalties**: Libraries that do not precompute prefix-tries must backtrack through all options sequentially or evaluate regex choices sequentially, resulting in significantly lower throughput (e.g., `parsecj` at **28 ops/ms** on case-insensitive keywords).

<hr>

#### 4. Calculator & Nested Comments (Recursive Scenarios)
*   **Performance Calculator**: `fastparse` ($1,138$ ops/ms) leads overall, followed by `petitparser` ($540$ ops/ms) and `dot-parse` ($518$ ops/ms).
*   **Performance Comments**: `dot-parse` ($10.87\text{M}$ ops/sec) leads the entire pack overall, followed by `fastparse` ($4.94\text{M}$).
*   **`dot-parse` Native Flat Character Scan**: `dot-parse` achieves its comment parsing throughput by utilizing its native `nestedBy("/*", "*/")` primitive. Rather than constructing a recursive tree of parser combinator objects, `nestedBy` scans the character stream in a single flat loop, tracking nesting depth in a primitive integer counter. This minimizes CPU and memory overhead, outperforming even macro-rewritten engines.
*   **`taker`'s Flat Operator Loop**: On the Calculator, `taker` ($445\text{ ops/ms}$) is highly competitive with `dot-parse` ($526\text{ ops/ms}$). This is because it utilizes its built-in `chainLeftOneOrMore` combinator, which compiles left-associative operators into a single flat `while` loop, avoiding recursive stack-checking and cycle-detection overhead almost entirely.



<hr>

#### 5. Kotlin `better-parse` Architectural Profile
*   **Property Delegation Overhead**: `better-parse` represents grammars using Kotlin's delegated properties (`by`), which introduces multiple runtime wrapper layers and lookup overhead during parser initialization and match dispatching.
*   **Heavy Intermediate Allocations**: Unlike zero-allocation parser scans, `better-parse`'s tokenizer scans inputs and allocates a list of intermediate `TokenMatch` objects on the fly, putting significant garbage collection pressure on the JVM hot path.
*   **Regex and Backtracking Bottlenecks**: On case-insensitive keywords, `better-parse` drops to a very low **$15.8$ ops/ms** ($15,800$ parses/sec) because it compiles 12 separate `Regex` objects and matches them sequentially per character. This is **$12.7\text{x}$ slower** than `dot-parse`'s Radix prefix tries and **$3.5\text{x}$ slower** than `taker`.
*   **Scenario Specific Strengths**: `better-parse` shows respectable throughput on simple strings (**$5,103$ ops/ms**) and nested block comments (**$1,484$ ops/ms**), outperforming classic Java engines like `parsecj` ($653$ comments) and `taker` ($775$ comments) on deep nested structures.

<hr>

#### 6. US Phone Number Parsing (Single & 1,000-Element List)
*   **Performance (Single Number)**: `parboiled2` (29.5M ops/sec) leads overall. `dot-parse` is the leading Java library at 18.3M ops/sec, outperforming `taker` (13.7M) and `cats-parse` (12.9M).
*   **Performance (1,000-Element List)**: `dot-parse` (11.30 lists/ms, or ~11.3M phone numbers/sec) leads overall across all 13 libraries, followed by `cats-parse` (10.54 lists/ms) and `jparsec` (9.46 lists/ms).

<hr>

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `parsecj` | `taker` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Simple Type (`String`)** | $3,376$ | **$6,913$** ☕ | $1,343$ | $3,493$ | **$9,318$** 🚀 | $1,556$ | $2,575$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Fully Qualified** | $1,661$ | **$4,416$** ☕ | $633$ | $2,115$ | **$5,693$** 🚀 | $919$ | $1,556$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | $306$ | **$930$** ☕ | $151$ | $438$ | **$1,233$** 🚀 | $194$ | $337$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | $359$ | **$795$** ☕ | $153$ | $410$ | **$967$** 🚀 | $203$ | $299$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | $231$ | **$369$** ☕ | $104$ | $169$ | **$676$** 🚀 | $86$ | $128$ | **`fast`** 🚀<br>**`dot`** ☕ |

### Key Takeaways from the Java Type Shootout

*   **Google's `dot-parse` Leads the Java Division**:
    `dot-parse` is the fastest Java-native parser library, running **$1.5\text{x}$ to $2.2\text{x}$ faster** than the next fastest Java contender (`petitparser` / `antlr4`).
    On simple types, `dot-parse` ($6,913$ ops/ms) is the leading Java library, behind Scala's macro-based `fastparse` ($9,318$ ops/ms) by leveraging a zero-allocation, pre-allocated tokenizer that avoids object boxing on the hot path.

*   **Compile-Time vs. Runtime Combinators**:
    Scala's compile-time macro-based `fastparse` leads overall in all scenarios. By performing compile-time macro expansion and inlining all parsing loops directly into JVM bytecode, it strips away object allocations and method dispatch overhead.

*   **`taker` Delivers Solid, High-Performance PEG Baselines**:
    The `taker` parser performs well, consistently **close to `antlr4`** on fully qualified ($1,556$ vs $1,661$) and **beating `antlr4`** on nested generic ($337$ vs $306$) signatures. It also **outperforms `parsecj` and `jparsec` by nearly $2\text{x}$** across almost all scenarios, proving that a lean PEG design with optimized applicative builders (`ApplyBuilder3`) is highly competitive.

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
    *   In this case, both `cats-parse` and `dot-parse` compile the raw `Parser.string` instances into their respective tries, achieving excellent $\sim 1\ \mu\text{s}$ scaling for hundreds of strings.

2.  **`Keywords` (Leading Choice)**:
    This is the standard pattern in programming language and SQL parsers, where different keywords lead to entirely different grammar branches and parser actions (e.g., `select` leads to a `selectStatement` rule, `insert` leads to an `insertStatement` rule).
    *   **The Suffix/Map Limitation in `cats-parse`**: Because different branches must map to different AST nodes or trigger different rules, they require suffix operations (like `.map` or `*>`/`<*`). In `cats-parse`, appending `.map` to a string parser wraps it in a `Parser.Map` class. This **destroys** `cats-parse`'s radix-tree optimization, forcing it to fall back to sequential backtracking (trying all keywords one-by-one), which is extremely slow.
    *   **The Prefix-Pruning Advantage in `dot-parse`**: `dot-parse`'s `OrParser` is designed to extract the prefix of candidate parsers **even if they have suffix/map operations**. Its `PrefixPruneTree` can still prune candidates by their leading character prefixes, allowing `dot-parse` to maintain $\sim 1\ \mu\text{s}$ trie-like scaling even when different keywords lead to different complex rules.

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
