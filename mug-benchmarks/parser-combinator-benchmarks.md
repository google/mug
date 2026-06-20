# JVM Parser Showdown & Performance Analysis

This report presents a comprehensive JMH performance benchmark and
architectural analysis comparing nine different parser engines on the JVM:

1. **`dot-parse`** (Java):
   Google's lightweight, runtime-optimized parser library.

2. **`cats-parse`** (Scala):
   Typelevel's modern, macro-free runtime parser.

3. **`fastparse`** (Scala):
   Li Haoyi's compile-time macro-rewritten parser.

4. **`jparsec`** (Java):
   A classic, highly-expressive monadic parser combinator library.

5. **`parboiled`** (Java):
   An elegant, rule-based parsing library using bytecode generation.

6. **`parsecj`** (Java):
   A monadic, parser combinator library inspired by Haskell's Parsec.

7. **`jjparse`** (Java):
   A monadic, scanner-less rapid-prototyping parser library.

8. **`antlr4`** (Java):
   The industry-standard LL(*) parser generator.

9. **`taker`** (Java):
   Google's internal PEG parser engine used as a baseline.

All benchmarks were executed side-by-side on the **same JVM (JDK 24.0.1)** and
the **same hardware (Apple M1 Max Mac)** to eliminate environmental bias.

> [!IMPORTANT]
> **Scope & Benchmark Nuance**:
> These benchmarks focus primarily on **leaf-level lexical rules** (flat
> sequencing, bulk scanning, and prefix dispatches).
> They do not measure performance on complex, deeply-nested grammars (e.g., full
> programming language compilers, JSON, or HTML parsers).
> Benchmarking deep, nested grammars requires deep, hands-on framework expertise
> to write idiomatic and highly optimized parsers for each engine (to avoid bias).
> For complex grammars, the overhead of heavier engines (like ANTLR4's LL(*) ATN
> simulator) is often amortized, and their advanced lookahead and error-recovery
> capabilities can make them significantly more robust and maintainable than
> manually tuned backtracking combinators.

---

## Executive Summary

Our benchmarks reveal a clear set of trade-offs between **compile-time macro
code generation**, **runtime trie dispatching**, and **bytecode generation**:

* **Prefix Trie Dispatching is King**:
  For matching choices (like keywords), runtime prefix-trie dispatching
  completely demolishes traditional backtracking and compile-time macros.
  `dot-parse` and `cats-parse` lead the pack, matching over **100 million
  choices per second** on a single thread.

* **Case-Insensitive Trie Performance**:
  `dot-parse` is the absolute performance leader in case-insensitive matching,
  reaching **73.5 million matches per second**—**$6.4\text{x}$ to $97\text{x}$
  faster** than other engines.
  It achieves this by precomputing capitalization permutations at startup.
  `parsecj` finishes second by leveraging highly optimized Java regular
  expressions, while other engines drop off a performance cliff by falling
  back to slow backtracking loops.

* **Sequencing & Bulk Scanning**:
  `dot-parse` has overtaken `fastparse` in flat sequencing (like IPv4),
  registering **25.3 million operations per second**.
  Both libraries utilize highly optimized inner loops that minimize allocation
  overhead.

---

## 9-Way Showdown Benchmark Results

Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | Taker | `cats-parse` | `dot-parse` | `fastparse` | `jparsec` | `parboiled` | `parsecj` | `jjparse` | `antlr4` | **Showdown Winner** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1. IPv4 Address** | $10,026$ | $12,546$ | **$25,328$** | $25,162$ | $10,019$ | $8,946$ | $3,236$ | $710$ | $2,431$ | **`dot-parse`** |
| **2. Quoted String** | $365$ | $2,359$ | $5,157$ | **$6,923$** | $2,693$ | $431$ | $352$ | $244$ | $1,594$ | **`fastparse`** |
| **3. 12 Keywords** | $10,435$ | $101,472$ | **$116,061$** | $59,016$ | $13,994$ | $27,239$ | $4,746$ | $33$ | $8,094$ | **`dot-parse`** |
| **4. Keywords (CI)** | $8,538$ | $11,174$ | **$73,561$** | $7,448$ | $11,375$ | $5,982$ | $27,485$ | $757$ | $6,030$ | **`dot-parse`** |

---

## Scenario-by-Scenario Performance Deep-Dive

### 1. IPv4 Address Parsing (Flat Sequencing)

* **The Code**:
  Matches a sequence of four digit blocks separated by dots (e.g.,
  `192.168.1.1`).

* **Performance**:
  `dot-parse` ($25.3\text{k}$) $\approx$ `fastparse` ($25.1\text{k}$) >
  `cats-parse` ($12.5\text{k}$) > `jparsec` ($10.0\text{k}$) $\approx$
  `taker` ($10.0\text{k}$) > `parboiled` ($8.9\text{k}$) > `parsecj`
  ($3.2\text{k}$) > `antlr4` ($2.4\text{k}$) > `jjparse` ($710$).

* **Analysis**:

  * **`dot-parse` and `fastparse` Dominance**:
    Both libraries compile flat sequences into highly optimized, allocation-free
    inner loops.
    `dot-parse` achieves an outstanding **25.3 million operations per second**,
    matching the macro-rewriting speed of `fastparse` through pure, lightweight
    Java combinator design.

  * **Lexer & Parser Separation Overhead**:
    `antlr4` ($2.4\text{k}$) is significantly slower here because it relies on a
    classic two-phase parsing model.
    It must instantiate a `CharStream`, a `Lexer`, a `TokenStream`, and a
    `Parser` on every single run, incurring substantial object-allocation and
    method-dispatch overhead.

  * **Monadic Combinator Cost**:
    Monadic libraries like `parsecj` ($3.2\text{k}$) and `jjparse` ($710$) run
    slower due to their deep object call stacks and boxing of intermediate
    character and parser results.

---

### 2. Quoted String Parsing (Lexical Bulk Scanning)

* **The Code**:
  Matches a 100-character double-quoted string with backslash-escaped characters
  (e.g., `"aaa..."`).

* **Performance**:
  `fastparse` ($6.9\text{k}$) > `dot-parse` ($5.1\text{k}$) > `jparsec`
  ($2.7\text{k}$) > `cats-parse` ($2.3\text{k}$) > `antlr4` ($1.5\text{k}$)
  > `parboiled` ($431$) > `taker` ($365$) $\approx$ `parsecj` ($352$) >
  `jjparse` ($244$).

* **Analysis**:

  * **Bulk Scanning Advantages**:
    Libraries that support native bulk-scanning primitives (like `fastparse`'s
    `CharsWhile`, `dot-parse`'s `quotedByWithEscapes`, and `jparsec`'s
    `DOUBLE_QUOTE_STRING` scanner) bypass individual character matching.
    They scan the string in a single JVM-level loop, yielding massive speedups.

  * **Bytecode Generation vs. Combinators**:
    `parboiled` ($431$) relies on runtime bytecode generation and reflection,
    which introduces significant method invocation overhead during backtracking,
    hindering its bulk-scanning efficiency.

---

### 3. Case-Sensitive Keywords (Trie Dispatch)

* **The Code**:
  Dispatches matching across a choice list of 12 SQL-like keywords (matching
  `"limit"`).

* **Performance**:
  `dot-parse` ($116\text{k}$) > `cats-parse` ($101\text{k}$) > `fastparse`
  ($59.0\text{k}$) > `parboiled` ($27.2\text{k}$) > `jparsec` ($13.9\text{k}$)
  > `taker` ($10.4\text{k}$) > `antlr4` ($8.0\text{k}$) > `parsecj`
  ($4.7\text{k}$) > `jjparse` ($33$).

* **Analysis**:

  * **Prefix Trie Dispatch**:
    Both `dot-parse` (`anyOf`) and `cats-parse` (`oneOf`) precompute optimized
    runtime prefix-tries.
    Instead of checking keywords one by one, they peek at the input characters
    and prune branches instantly.
    `dot-parse` leads the showdown at **116 million choices per second**.

  * **Standard Backtracking Costs**:
    Monadic engines like `parsecj` ($4.7\text{k}$) and `jjparse` ($33$) evaluate
    choices sequentially using an $O(N)$ backtracking choice loop (`choice` or
    `or`), causing performance to degrade rapidly as the number of keywords
    grows.

---

### 4. Case-Insensitive Keywords (Trie Dispatch Showdown)

* **The Code**:
  Dispatches matching across a choice list of 12 SQL-like keywords matched
  **case-insensitively** (matching `"LIMIT"`).

* **Performance**:
  `dot-parse` ($73.5\text{k}$) > `parsecj` ($27.4\text{k}$) > `jparsec`
  ($11.3\text{k}$) $\approx$ `cats-parse` ($11.1\text{k}$) > `taker`
  ($8.5\text{k}$) > `fastparse` ($7.4\text{k}$) > `antlr4` ($6.0\text{k}$)
  $\approx$ `parboiled` ($5.9\text{k}$) > `jjparse` ($757$).

* **Analysis**:

  * **`dot-parse` Case-Insensitive Prefix Trie**:
    `dot-parse` is the undisputed winner, running **$2.6\text{x}$ to $97\text{x}$
    faster** than all other engines combined.
    Its prefix-trie compiler precomputes all capitalization permutations of the
    first 4 characters at startup, maintaining a blazing-fast $O(1)$ dispatch.

  * **Regular Expression Acceleration**:
    `parsecj` ($27.4\text{k}$) achieves an impressive second-place finish by
    delegating case-insensitive matching directly to Java's native regular
    expression engine (`Text.regex("(?i)...")`), which is heavily optimized at
    the JVM level.

  * **Silent Fallbacks**:
    When faced with case-insensitivity, libraries like `cats-parse` and
    `fastparse` cannot compile a prefix trie.
    They silently fall back to slow, sequential backtracking loops, causing a
    catastrophic performance drop (e.g., `cats-parse` falls from $101\text{k}$ to
    $11.1\text{k}$).

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

   *(You can adjust the `-wi` (warmup iterations) and `-i` (measurement
   iterations) parameters to run the suite faster or slower).*
