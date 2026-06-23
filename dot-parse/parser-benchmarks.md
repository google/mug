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

All benchmarks were executed side-by-side on the **same JVM (JDK 24.0.1)** and the **same hardware (Apple M1 Mac)** to eliminate environmental bias. All grammars were strictly verified with assertions ensuring **complete input consumption (EOF)** and **structural correctness**.



> [!NOTE]
> **Acknowledge on parboiled & parboiled2**:
> The bytecode-generating parser frameworks `parboiled` (runtime bytecode generation) and `parboiled2` (Scala compile-time macro PEG) are excluded from the main comparison tables below to focus on libraries with more comparable runtime execution models. In our runs, the macro-optimized `parboiled2` represented the performance ceiling, reaching up to **13.84 million parses/sec** on simple types and **5.92 million parses/sec** on fully qualified types.

> [!IMPORTANT]
> **Scope & Benchmark Nuance**:
> Benchmarking nested grammars requires framework expertise.
> Our benchmark suite covers parsing speed on micro-inputs, which measures framework overhead.
> It highlights performance when grammars are written idiomatically for each framework.
> For example, ANTLR4 is designed for larger files with complex AST generation. It carries a fixed-cost machinery that results in lower throughput on tiny micro-inputs, but is highly scalable on large source files.
> In contrast, combinators demonstrate higher throughput on local micro-parsing tasks.

---

## JSON Parser Shootout (9-Way Showdown)

To evaluate how these frameworks perform when parsing a **large, complex, and heterogeneous data payload**, we implemented a full **JSON parser** across all 9 shootout engines.

Every engine was validated against a large, representative JSON document (~100 containers, maps of size 12, lists of size 250, scientific numbers, and varying strings of length 20 to 128) and strictly verified at setup time to guarantee complete functional correctness and functional parity.

Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | [`antlr4`](../mug-benchmarks/src/test/antlr4/com/google/mu/benchmarks/parsers/antlr4/Json.g4) | [`dot-parse`](../mug-benchmarks/src/test/java/com/google/mu/benchmarks/parsers/dotparse/JsonParser.java) | `jparsec` | [`petitparser`](https://github.com/petitparser/java-petitparser/tree/main/petitparser-json) | [`fastparse`](https://github.com/com-lihaoyi/fastparse/blob/master/perftests/bench2/src/perftests/JsonParse.scala) | [`cats-parse`](https://github.com/typelevel/cats-parse) | [`parsecj`](https://github.com/jon-hanson/parsecj/blob/master/src/test/java/org/javafp/parsecj/json/Grammar.java) | [`taker`](https://github.com/parseworks/taker/blob/main/src/test/java/io/github/parseworks/taker/examples/RealisticExamplesTest.java) | [`better-parse`](https://github.com/silmeth/jsonParser) | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Complex JSON Payload** | $0.186$ | **$0.480$** ☕ | $0.159$ | $0.108$ | **$0.506$** 🚀 | $0.375$ | $0.020$ | $0.130$ | $0.112$ | **`fast`** 🚀<br>**`dot`** ☕ |

#### Reference Production Baselines (JSON)
To provide an absolute performance ceiling, we stacked our combinator shootout against two industry-standard, hand-written production JSON parsers on the exact same JSON payload:

| Parser Engine | Throughput (ops/ms) | Relative Speed to `dot-parse` |
| :--- | :---: | :---: |
| **Jackson Databind** (`ObjectMapper`) | $1.833$ | **3.82x** |
| **Gson** (`JsonParser`) | $1.554$ | **3.24x** |
| **`dot-parse`** (Our leading Java combinator) | $0.480$ | **1.00x** |

### Key Takeaways from the JSON Shootout

*   **Scala's `fastparse` Overall Results**:
    `fastparse` achieved the highest throughput overall, reaching **$0.506$ ops/ms** utilizing compile-time macro inlining and block-scanning primitives.
*   **Google's `dot-parse` Java Division Results**:
    Among all Java-native / Java-specific engines, `dot-parse` achieved the highest throughput, delivering **$0.480$ ops/ms** (running at 94.9% of Fastparse's speed and outperforming other Java combinators).
*   **Combinator Library Comparison**:
    Within the Java-specific parser combinator libraries, `dot-parse` is **3.02x faster** than `jparsec` ($0.159$ ops/ms) and **24.0x faster** than `parsecj` ($0.020$ ops/ms).
*   **Scala Comparison**:
    `fastparse` ($0.506$ ops/ms) outperforms the runtime-based `cats-parse` ($0.375$ ops/ms) by **1.35x**.

---

## 9-Way Showdown Benchmark Results (Micro-Benchmarks)

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `cats-parse` | `parsecj` | `taker` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **IPv4 Address** | $1,850$ | **$11,836$** ☕ | $9,266$ | $6,657$ | **$24,645$** 🚀 | $18,665$ | $12,434$ | $4,990$ | $1,943$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Quoted String (Common)** | $4,726$ | $12,321$ | $6,946$ | $3,040$ | $19,391$ | **$19,731$** 🚀 | $5,039$ | **$21,054$** ☕ | $5,358$ | **`cats`** 🚀<br>**`taker`** ☕ |
| **Quoted String (Escaped)** | $3,549$ | $5,289$ | $4,917$ | $2,372$ | $12,358$ | $3,644$ | $2,771$ | **$17,969$** 🚀 ☕ | $1,224$ | **`taker`** 🚀 ☕ |
| **Keywords (12 CS)** | $46$ | **$281$** 🚀 ☕ | $91$ | $66$ | $80$ | $101$ | $37$ | $66$ | $50$ | **`dot`** 🚀 ☕ |
| **Keywords CI (12 CI)** | $32$ | **$201$** 🚀 ☕ | $84$ | $56$ | $71$ | $99$ | $29$ | $55$ | $16$ | **`dot`** 🚀 ☕ |
| **Calculator** | $367$ | $518$ | $347$ | **$536$** ☕ | **$1,140$** 🚀 | $425$ | $212$ | $451$ | $241$ | **`fast`** 🚀<br>**`petit`** ☕ |
| **Nested Block Comment** | $1,227$ | **$9,538$** 🚀 ☕ | $2,385$ | $1,077$ | $4,937$ | $2,154$ | $651$ | $689$ | $1,415$ | **`dot`** 🚀 ☕ |

### Showdown Scenario Analysis & Rationalization

#### 1. IPv4 Address Parsing (Flat Sequencing)
*   **Performance**: `fastparse` ($24.6\text{M}$ ops/sec) leads, followed by `cats-parse` ($18.7\text{M}$). `parsecj` is the leading Java library at **$12.4\text{M}$ ops/sec**, followed by `dot-parse` ($11.8\text{M}$).
*   **Regex Delegation vs. Combinator Sequencing**: `parsecj` ($12,434\text{ ops/ms}$) achieves its throughput by utilizing a single flat regular expression parser (`regex(...)`). This delegates the entire sequence matching to Java's native regular expression engine. While this represents a significant throughput increase, it measures the performance of the JVM's native regex engine rather than the parser combinator's own monadic sequencing overhead.
*   **ANTLR4 Two-Phase Allocation Overhead**: `antlr4` ($1.9\text{k}$) shows lower throughput here due to its compiler-grade two-phase parsing architecture (Lexer + Parser). On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual `CommonToken` objects for every single token scanned, adding object allocation overhead.

<hr>

#### 2. Quoted String Parsing (Common Case vs. Escaped Edge Case)
*   **Performance**: On simple strings, `taker` ($21.1\text{M}$ ops/sec) leads overall, followed closely by `cats-parse` ($19.7\text{M}$) and `fastparse` ($19.4\text{M}$), while `dot-parse` is at **$12.3\text{M}$ ops/sec**. On escaped strings, `taker` leads overall at **$18.0\text{M}$ ops/sec**, followed by `fastparse` ($12.4\text{M}$) and `dot-parse` ($5.3\text{M}$).
*   **`taker`'s Dedicated Lexical Primitive**: `taker` achieves **$17,969$ ops/ms** on escaped strings by utilizing its built-in, native `Lexical.escapedString('"', '\\', escapesMap)` primitive. Rather than composing general-purpose character combinators (which incur allocation and dispatch overhead on every character), `taker` delegates to a dedicated lexical scanner that parses the string and resolves escapes in a single flat loop.
*   **`dot-parse`'s Trade-off: Escape Efficiency vs. Flexibility**:
    `dot-parse` **trades off escape parsing efficiency for grammatical flexibility.** Its `quotedByWithEscapes(char before, char after, Production<CharSequence> escaped)` primitive accepts a generic escape parser rule. While invoking a parser rule on the character following the backslash incurs extra overhead compared to strictly unescaping a literal character, it enables `dot-parse` to support arbitrary, complex escape grammars: such as variable-length Unicode escapes (like `\u12AF`), or Markdown-style escaping (where a backslash not followed by an escapable character must be interpreted as literal, through passing a rule like `escapable.orElse("\\")`). This is an intentional design choice, given that string literals without escapes are far more common in practice.
*   **Bulk Scanning & Regex Delegation**: Libraries that support native bulk-scanning primitives (like `jparsec`'s string scanner) perform well.

<hr>

#### 3. Keywords & Case-Insensitive Tries
*   **Performance CS**: `dot-parse` ($281$ ops/ms) leads the entire pack overall, followed by `cats-parse` ($101$ ops/ms) and `jparsec` ($91$ ops/ms).
*   **Performance CI**: `dot-parse` ($201$ ops/ms) leads the entire pack overall, followed by `cats-parse` ($99$ ops/ms) and `jparsec` ($84$ ops/ms).
*   **Trie Dispatch Implementation**: `dot-parse` (`anyOf`) compiles keyword alternatives into optimized **Radix Prefix Tries**, bypassing sequential backtracking. By compiling its branching nodes into a flat lookup table (array of size 256) when using `.precomputeForAscii()`, it achieves its high throughput. For case-insensitivity, its prefix-trie compiler precomputes capitalization permutations of the first 4 characters at startup, maintaining an optimized $O(1)$ dispatch.
*   **Backtracking Penalties**: Libraries that do not precompute prefix-tries must backtrack through all 12 options sequentially, resulting in significantly lower throughput (e.g., `parsecj` at **$29$ ops/ms** on case-insensitive keywords).

<hr>

#### 4. Calculator & Nested Comments (Recursive Scenarios)
*   **Performance Calculator**: `fastparse` ($1,140$ ops/ms) leads overall, followed by `petitparser` ($536$ ops/ms) and `dot-parse` ($518$ ops/ms).
*   **Performance Comments**: `dot-parse` ($9.54\text{M}$ ops/sec) leads the entire pack overall, followed by `fastparse` ($4.94\text{M}$).
*   **`dot-parse` Native Flat Character Scan**: `dot-parse` achieves its comment parsing throughput by utilizing its native `nestedBy("/*", "*/")` primitive. Rather than constructing a recursive tree of parser combinator objects, `nestedBy` scans the character stream in a single flat loop, tracking nesting depth in a primitive integer counter. This minimizes CPU and memory overhead, outperforming even macro-rewritten engines.

<hr>

#### 5. Kotlin `better-parse` Architectural Profile
*   **Property Delegation Overhead**: `better-parse` represents grammars using Kotlin's delegated properties (`by`), which introduces multiple runtime wrapper layers and lookup overhead during parser initialization and match dispatching.
*   **Heavy Intermediate Allocations**: Unlike zero-allocation parser scans, `better-parse`'s tokenizer scans inputs and allocates a list of intermediate `TokenMatch` objects on the fly, putting significant garbage collection pressure on the JVM hot path.
*   **Regex and Backtracking Bottlenecks**: On case-insensitive keywords, `better-parse` drops to a very low **$15.9$ ops/ms** ($15,900$ parses/sec) because it compiles 12 separate `Regex` objects and matches them sequentially per character. This is **$12.6\text{x}$ slower** than `dot-parse`'s Radix prefix tries and **$3.5\text{x}$ slower** than `taker`.
*   **Scenario Specific Strengths**: `better-parse` shows respectable throughput on simple strings (**$5,358$ ops/ms**) and nested block comments (**$1,415$ ops/ms**), outperforming classic Java engines like `parsecj` ($651$ comments) and `taker` ($689$ comments) on deep nested structures.

<hr>

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `parsecj` | `taker` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Simple Type (`String`)** | $3,657$ | **$7,060$** ☕ | $1,502$ | $3,453$ | **$8,831$** 🚀 | $1,535$ | $2,555$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Fully Qualified** | $1,603$ | **$4,183$** ☕ | $650$ | $2,020$ | **$5,662$** 🚀 | $841$ | $1,522$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | $292$ | **$870$** ☕ | $149$ | $444$ | **$1,219$** 🚀 | $188$ | $332$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | $346$ | **$744$** ☕ | $145$ | $418$ | **$982$** 🚀 | $198$ | $306$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | $244$ | **$351$** ☕ | $103$ | $164$ | **$671$** 🚀 | $83$ | $129$ | **`fast`** 🚀<br>**`dot`** ☕ |

### Key Takeaways from the Java Type Shootout

*   **Google's `dot-parse` Leads the Java Division**:
    `dot-parse` is the fastest Java-native parser library, running **$1.5\text{x}$ to $2.0\text{x}$ faster** than the next fastest Java contender (`petitparser` / `antlr4`).
    On simple types, `dot-parse` ($7,060$ ops/ms) is the leading Java library, behind Scala's macro-based `fastparse` ($8,831$ ops/ms) by leveraging a zero-allocation, pre-allocated tokenizer that avoids object boxing on the hot path.

*   **Compile-Time vs. Runtime Combinators**:
    Scala's compile-time macro-based `fastparse` leads overall in all scenarios. By performing compile-time macro expansion and inlining all parsing loops directly into JVM bytecode, it strips away object allocations and method dispatch overhead.

*   **`taker` Delivers Solid, High-Performance PEG Baselines**:
    The `taker` parser performs well, consistently **close to `antlr4`** on fully qualified ($1,522$ vs $1,603$) and **beating `antlr4`** on nested generic ($332$ vs $292$) signatures. It also **outperforms `parsecj` and `jparsec` by nearly $2\text{x}$** across almost all scenarios, proving that a lean PEG design with optimized applicative builders (`ApplyBuilder3`) is highly competitive.

*   **`parsecj` vs `jparsec` (The Combinator Battle)**:
    `parsecj` performs moderately and **consistently outperforms `jparsec`** in 4 out of 5 scenarios. However, in the **Complex Annotation** scenario, `parsecj` falls slightly behind `jparsec` ($83$ vs $103$ ops/ms). This is due to the heavy monadic bind (`.bind(...)`) nesting and recursive backtracking (`.attempt()`) we had to introduce in `parsecj` to handle class literals and array parameters, which incurs significant lambda allocation overhead.

*   **Scannerless vs. Two-Phase Tokenization**:
    For small, dense inputs with minimal whitespace (such as Java type signatures), scannerless parsers (`dot-parse`, `taker`, `parsecj`) are a fundamentally better architectural fit than two-phase tokenizing parsers (`jparsec`, `antlr4`). Two-phase parsers pay a high object-allocation penalty to construct intermediate token lists, whereas scannerless parsers operate directly on the character stream with zero token overhead.

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
