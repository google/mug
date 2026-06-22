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

All benchmarks were executed side-by-side on the **same JVM (JDK 24.0.1)** and the **same hardware (Apple M1 Max Mac)** to eliminate environmental bias. All grammars were strictly verified with assertions ensuring **complete input consumption (EOF)** and **structural correctness**.



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

### Executive Summary

Our benchmarks reveal a clear set of trade-offs between **compile-time macro code generation**, **runtime trie dispatching**, and **bytecode generation**:

*   **Trie Dispatching Efficiency**:
    For keyword dispatches, prefix-trie dispatching shows higher throughput than traditional backtracking. 
    `dot-parse` leads case-sensitive keywords at **$363$ ops/ms** utilizing its zero-allocation stream collector, followed by `jparsec` at **$105$ ops/ms**.

*   **Case-Insensitive Trie Performance**:
    `dot-parse` delivers the highest throughput, reaching **$217$ ops/ms** ☕ via its permutation-trie lookup, followed by `cats-parse` at **$118$ ops/ms** and `jparsec` at **$107$ ops/ms**, while other libraries (like `parsecj` at **$33$ ops/ms**) show lower throughput due to sequential backtracking, and Kotlin's `better-parse` sits at the bottom (**$12.5$ ops/ms**) due to runtime Regex evaluation.

*   **Sequencing & Bulk Scanning**:
    `fastparse` leads flat sequencing (IPv4) at **$24.7\text{M}$ ops/sec**, followed closely by `cats-parse` at **$23.5\text{M}$ ops/sec** and `parsecj` as the leading Java library at **$17.5\text{M}$ ops/sec** by delegating the scan to Java's native regex engine. Kotlin's `better-parse` performs moderately on flat sequencing (**$1.5\text{M}$ ops/sec**).
    For strings with no escapes, `taker` achieves a massive **$24.6\text{M}$ ops/sec** (albeit with high variance), while `cats-parse` and `fastparse` show extremely stable high throughput overall (**$24.6\text{M}$** and **$21.9\text{M}$ ops/sec**), and `dot-parse` leads Java libraries at **$15.2\text{M}$ ops/sec**. For escaped strings, `fastparse` leads overall at **$10.6\text{M}$ ops/sec** 🚀, followed by `taker` ($8.8$M) and `jparsec` ($7.3$M), while `better-parse` drops to **$1.08\text{M}$ ops/sec** due to intermediate allocations.

*   **Recursive Expression & Block Comment Performance**:
    `fastparse` leads recursive expression parsing (Calculator) at **$1.22\text{M}$ ops/sec**, while `dot-parse` and `petitparser` show similar throughput in Java ($0.74\text{M}$ and $0.60\text{M}$), and `better-parse` delivers **$0.20\text{M}$ ops/sec**.
    For recursive block comments, `dot-parse` is the fastest contender overall, leading at **$11.08\text{M}$ ops/sec** ☕ using a native character scanner, followed by `fastparse` at **$4.62\text{M}$**, while `better-parse` achieves **$1.18\text{M}$ ops/sec**.

---

## 9-Way Showdown Benchmark Results

Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `cats-parse` | `parsecj` | `taker` | `better-parse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **IPv4 Address** | $2,190$ | $15,837$ | $11,912$ | $7,670$ | **$24,763$** 🚀 | $23,550$ | **$17,572$** ☕ | $5,042$ | $1,576$ | **`fast`** 🚀<br>**`parsecj`** ☕ |
| **Quoted String (Common)** | $4,887$ | $15,255$ | $12,200$ | $3,839$ | **$21,980$** 🚀 | **$24,615$** 🚀 | $5,798$ | **$24,672$** 🚀 ☕ | $5,178$ | **`taker`** 🚀 ☕ |
| **Quoted String (Escaped)** | $3,124$ | $5,821$ | $7,307$ | $2,722$ | **$10,636$** 🚀 | $4,591$ | $3,263$ | **$8,821$** ☕ | $1,088$ | **`fast`** 🚀<br>**`taker`** ☕ |
| **Keywords (12 CS)** | $53$ | **$363$** 🚀 ☕ | $105$ | $68$ | $81$ | $123$ | $39$ | $73$ | $60$ | **`dot`** 🚀 ☕ |
| **Keywords CI (12 CI)** | $32$ | **$217$** 🚀 ☕ | $107$ | $52$ | $74$ | $118$ | $33$ | $62$ | $12$ | **`dot`** 🚀 ☕ |
| **Calculator** | $411$ | **$748$** ☕ | $409$ | $609$ | **$1,220$** 🚀 | $511$ | $219$ | $449$ | $208$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Block Comment** | $1,380$ | **$11,088$** 🚀 ☕ | $2,276$ | $1,159$ | $4,624$ | $2,457$ | $664$ | $761$ | $1,188$ | **`dot`** 🚀 ☕ |

### Showdown Scenario Analysis & Rationalization

#### 1. IPv4 Address Parsing (Flat Sequencing)
*   **Performance**: `fastparse` ($24.7\text{M}$ ops/sec) leads, followed by `cats-parse` ($23.5\text{M}$). `parsecj` is the leading Java library at **$17.5\text{M}$ ops/sec**, followed by `dot-parse` ($15.8\text{M}$).
*   **Regex Delegation vs. Combinator Sequencing**: `parsecj` ($17,572\text{ ops/ms}$) achieves its throughput by utilizing a single flat regular expression parser (`regex(...)`). This delegates the entire sequence matching to Java's native regular expression engine. While this represents a significant throughput increase, it measures the performance of the JVM's native regex engine rather than the parser combinator's own monadic sequencing overhead.
*   **ANTLR4 Two-Phase Allocation Overhead**: `antlr4` ($2.1\text{k}$) shows lower throughput here due to its compiler-grade two-phase parsing architecture (Lexer + Parser). On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual `CommonToken` objects for every single token scanned, adding object allocation overhead.

#### 2. Quoted String Parsing (Common Case vs. Escaped Edge Case)
*   **Performance**: On simple strings, `taker` ($24.6\text{M}$ ops/sec) leads overall, followed closely by `cats-parse` ($24.6\text{M}$) and `fastparse` ($21.9\text{M}$), while `dot-parse` is at **$15.2\text{M}$ ops/sec**. On escaped strings, `fastparse` leads overall at **$10.6\text{M}$ ops/sec**, followed by `taker` ($8.8\text{M}$) and `jparsec` ($7.3\text{M}$).
*   **`taker`'s Dedicated Lexical Primitive**: `taker` achieves **$8,821$ ops/ms** on escaped strings by utilizing its built-in, native `Lexical.escapedString('"', '\\', escapesMap)` primitive. Rather than composing general-purpose character combinators (which incur allocation and dispatch overhead on every character), `taker` delegates to a dedicated lexical scanner that parses the string and resolves escapes in a single flat loop.
*   **`dot-parse`'s Trade-off: Escape Efficiency vs. Flexibility**:
    `dot-parse` **trades off escape parsing efficiency for grammatical flexibility.** Its `quotedByWithEscapes(char before, char after, Production<CharSequence> escaped)` primitive accepts a generic escape parser rule. While invoking a parser rule on the character following the backslash incurs extra overhead compared to strictly unescaping a literal character, it enables `dot-parse` to support arbitrary, complex escape grammars: such as variable-length Unicode escapes (like `\u12AF`), or Markdown-style escaping (where a backslash not followed by an escapable character must be interpreted as literal, through passing a rule like `escapable.orElse("\\")`). This is an intentional design choice, given that string literals without escapes are far more common in practice.
*   **Bulk Scanning & Regex Delegation**: Libraries that support native bulk-scanning primitives (like `jparsec`'s string scanner) perform well.

#### 3. Keywords & Case-Insensitive Tries
*   **Performance CS**: `dot-parse` ($363$ ops/ms) leads the entire pack overall, followed by `cats-parse` ($123$ ops/ms) and `jparsec` ($105$ ops/ms).
*   **Performance CI**: `dot-parse` ($217$ ops/ms) leads the entire pack overall, followed by `cats-parse` ($118$ ops/ms) and `jparsec` ($107$ ops/ms).
*   **Trie Dispatch Implementation**: `dot-parse` (`anyOf`) compiles keyword alternatives into optimized **Radix Prefix Tries**, bypassing sequential backtracking. By compiling its branching nodes into a flat lookup table (array of size 256) when using `.precomputeForAscii()`, it achieves its high throughput. For case-insensitivity, its prefix-trie compiler precomputes capitalization permutations of the first 4 characters at startup, maintaining an optimized $O(1)$ dispatch.
*   **Backtracking Penalties**: Libraries that do not precompute prefix-tries must backtrack through all 12 options sequentially, resulting in significantly lower throughput (e.g., `parsecj` at **$24$ ops/ms** on case-insensitive keywords).

#### 4. Calculator & Nested Comments (Recursive Scenarios)
*   **Performance Calculator**: `fastparse` ($1,220$ ops/ms) leads overall, followed by `dot-parse` ($748$ ops/ms) and `petitparser` ($609$ ops/ms).
*   **Performance Comments**: `dot-parse` ($11.08\text{M}$ ops/sec) leads the entire pack overall, followed by `fastparse` ($4.62\text{M}$).
*   **`dot-parse` Native Flat Character Scan**: `dot-parse` achieves its comment parsing throughput by utilizing its native `nestedBy("/*", "*/")` primitive. Rather than constructing a recursive tree of parser combinator objects, `nestedBy` scans the character stream in a single flat loop, tracking nesting depth in a primitive integer counter. This minimizes CPU and memory overhead, outperforming even macro-rewritten engines.

#### 5. Kotlin `better-parse` Architectural Profile
*   **Property Delegation Overhead**: `better-parse` represents grammars using Kotlin's delegated properties (`by`), which introduces multiple runtime wrapper layers and lookup overhead during parser initialization and match dispatching.
*   **Heavy Intermediate Allocations**: Unlike zero-allocation parser scans, `better-parse`'s tokenizer scans inputs and allocates a list of intermediate `TokenMatch` objects on the fly, putting significant garbage collection pressure on the JVM hot path.
*   **Regex and Backtracking Bottlenecks**: On case-insensitive keywords, `better-parse` drops to a very low **$9.8$ ops/ms** ($9,800$ parses/sec) because it compiles 12 separate `Regex` objects and matches them sequentially per character. This is **$17\text{x}$ slower** than `dot-parse`'s Radix prefix tries and **$9\text{x}$ slower** than `taker`.
*   **Scenario Specific Strengths**: `better-parse` shows respectable throughput on simple strings (**$3,822$ ops/ms**) and nested block comments (**$970$ ops/ms**), outperforming classic Java engines like `parsecj` ($795$ comments) and `taker` ($733$ comments) on deep nested structures.

---

## JSON Parser Shootout (9-Way Showdown)

To evaluate how these frameworks perform when parsing a **large, complex, and heterogeneous data payload**, we implemented a full **JSON parser** across all 9 shootout engines.

Every engine was validated against a large, representative JSON document (~100 containers, maps of size 12, lists of size 250, scientific numbers, and varying strings of length 20 to 128) and strictly verified at setup time to guarantee complete functional correctness and functional parity.

Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | [`petitparser`](https://github.com/petitparser/java-petitparser/tree/main/petitparser-json) | [`fastparse`](https://github.com/com-lihaoyi/fastparse/blob/master/perftests/bench2/src/perftests/JsonParse.scala) | [`cats-parse`](https://github.com/typelevel/cats-parse) | [`parsecj`](https://github.com/javafp/parsecj/blob/master/src/main/java/org/javafp/parsecj/examples/JsonParser.java) | [`taker`](https://github.com/parseworks/taker/blob/main/src/test/java/io/github/parseworks/taker/examples/RealisticExamplesTest.java) | [`better-parse`](https://github.com/silmeth/jsonParser) | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Complex JSON Payload** | $0.163$ | **$0.465$** ☕ | $0.155$ | $0.108$ | **$0.506$** 🚀 | $0.386$ | $0.020$ | $0.129$ | $0.115$ | **`fastparse`** 🚀<br>**`dot-parse`** ☕ |

### Key Takeaways from the JSON Shootout

*   **Scala's `fastparse` Wins the Shootout**:
    `fastparse` takes the absolute crown as the fastest engine overall, reaching **$0.506$ ops/ms** utilizing compile-time macro inlining and block-scanning primitives.
*   **Google's `dot-parse` Leads JVM Languages**:
    Among all Java and JVM-specific engines, `dot-parse` is the undisputed leader, delivering **$0.465$ ops/ms** (running at 91.9% of Fastparse's speed and outperforming all other Java combinators by a wide margin!).
*   **Combinator Library Comparison**:
    Within the Java-specific parser combinator libraries, `dot-parse` is **3.00x faster** than `jparsec` ($0.155$ ops/ms) and **23.2x faster** than `parsecj` ($0.020$ ops/ms).
*   **Scala Comparison**:
    `fastparse` ($0.506$ ops/ms) outperforms the runtime-based `cats-parse` ($0.386$ ops/ms) by **1.31x**.

---

## Java Type Signature Parser Shootout (7-Way Showdown)

To evaluate how these frameworks perform when building a **highly complex,
recursive, and production-grade grammar**, we implemented a full **Java Type
signature parser** across 7 shootout engines.

Every engine was validated against the **exact same 14 deep structural AST
test cases** to guarantee complete functional parity. Throughput was measured
in **operations per millisecond** (higher is better):

| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `parsecj` | `taker` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Simple Type (`String`)** | $2,321$ | **$7,034$** ☕ | $1,390$ | $3,579$ | **$9,240$** 🚀 | $1,571$ | $2,566$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Fully Qualified** | $1,054$ | **$3,845$** ☕ | $651$ | $2,139$ | **$5,786$** 🚀 | $933$ | $1,546$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Nested Generics** | $287$ | **$800$** ☕ | $156$ | $435$ | **$1,247$** 🚀 | $187$ | $330$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Annotated Array** | $296$ | **$662$** ☕ | $150$ | $426$ | **$1,164$** 🚀 | $203$ | $308$ | **`fast`** 🚀<br>**`dot`** ☕ |
| **Complex Annotation** | $207$ | **$337$** ☕ | $103$ | $167$ | **$671$** 🚀 | $84$ | $130$ | **`fast`** 🚀<br>**`dot`** ☕ |

### Key Takeaways from the Java Type Shootout

*   **Google's `dot-parse` Leads the Java Division**:
    `dot-parse` is the fastest Java-native parser library, running **$1.5\text{x}$ to $2.0\text{x}$ faster** than the next
    fastest Java contender (`petitparser` / `antlr4`).
    On simple types, `dot-parse` ($7,034$ ops/ms) is the leading Java library,
    behind Scala's macro-based `fastparse` ($9,240$ ops/ms) by leveraging a
    zero-allocation, pre-allocated tokenizer that avoids object boxing on
    the hot path.

*   **Compile-Time vs. Runtime Combinators**:
    Scala's compile-time macro-based `fastparse` leads overall in all
    scenarios. By performing compile-time macro expansion and inlining all
    parsing loops directly into JVM bytecode, it strips away object
    allocations and method dispatch overhead.

*   **`taker` Delivers Solid, High-Performance PEG Baselines**:
    The `taker` parser performs well, consistently **beating
    `antlr4`** on fully qualified ($1,546$ vs $1,054$) and nested generic
    ($330$ vs $287$) signatures. It also **outperforms `parsecj` and `jparsec`
    by nearly $2\text{x}$** across almost all scenarios, proving that a lean
    PEG design with optimized applicative builders (`ApplyBuilder3`) is
    highly competitive.

*   **`parsecj` vs `jparsec` (The Combinator Battle)**:
    `parsecj` performs moderately and **consistently outperforms `jparsec`**
    in 4 out of 5 scenarios. However, in the **Complex Annotation** scenario,
    `parsecj` falls slightly behind `jparsec` ($84$ vs $103$ ops/ms). This is
    due to the heavy monadic bind (`.bind(...)`) nesting and recursive
    backtracking (`.attempt()`) we had to introduce in `parsecj` to handle
    class literals and array parameters, which incurs significant lambda
    allocation overhead.

*   **Scannerless vs. Two-Phase Tokenization**:
    For small, dense inputs with minimal whitespace (such as Java type
    signatures), scannerless parsers (`dot-parse`, `taker`, `parsecj`) are a
    fundamentally better architectural fit than two-phase tokenizing parsers
    (`jparsec`, `antlr4`). Two-phase parsers pay a high object-allocation penalty
    to construct intermediate token lists, whereas scannerless parsers operate
    directly on the character stream with zero token overhead.

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
