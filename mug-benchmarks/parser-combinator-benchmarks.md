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
the **same hardware (Apple M1 Max Mac)** to eliminate environmental bias. All
grammars were strictly verified with assertions ensuring **complete input
consumption (EOF)**.

> [!IMPORTANT]
> **Scope & Benchmark Nuance**:
> These benchmarks focus primarily on **leaf-level lexical rules** (flat
> sequencing, bulk scanning, and prefix dispatches).
>
> They do not measure performance on complex, deeply-nested grammars (e.g.,
> full programming language compilers, JSON, or HTML parsers).
>
> Benchmarking deep, nested grammars requires deep, hands-on framework
> expertise to write idiomatic and highly optimized parsers for each engine (to
> avoid bias).
>
> For complex grammars, the overhead of heavier engines (like ANTLR4's LL(*)
> ATN simulator) is often amortized, and their advanced lookahead and
> error-recovery capabilities can make them significantly more robust and
> maintainable than manually tuned backtracking combinators.

---

## Executive Summary

Our benchmarks reveal a clear set of trade-offs between **compile-time macro
code generation**, **runtime trie dispatching**, and **bytecode generation**:

* **Prefix Trie Dispatching is King**:
  For matching choices (like keywords), runtime prefix-trie dispatching
  completely demolishes traditional backtracking and compile-time macros.
  `dot-parse` leads the pack, matching over **176 million choices per second**
  on a single thread.

* **Case-Insensitive Trie Performance**:
  `dot-parse` is the absolute performance leader in case-insensitive matching,
  reaching **69.9 million matches per second**—**$3.2\text{x}$ to $94\text{x}$
  faster** than other engines.
  It achieves this by precomputing capitalization permutations at startup.
  `parsecj` finishes second by leveraging highly optimized Java regular
  expressions, while other engines drop off a performance cliff by falling
  back to slow backtracking loops.

* **Sequencing & Bulk Scanning**:
  `fastparse` leads flat sequencing (IPv4) at **24.0 million operations per
  second**, with `dot-parse` closely following at **21.1 million**.
  For strings, `dot-parse` dominates simple string scanning (**16.0 million
  ops/sec**), while `fastparse` leads escaped string scanning (**10.9 million
  ops/sec**).

---

## 9-Way Showdown Benchmark Results

Throughput was measured in **operations per millisecond** (higher is better):

| Benchmark Scenario | Taker | `cats-parse` | `dot-parse` | `fastparse` | `jparsec` | `parboiled` | `parsecj` | `jjparse` | `antlr4` | **Showdown Winner** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **IPv4 Address** | $9,722$ | $20,636$ | $21,170$ | **$24,058$** | $9,544$ | $9,365$ | $2,928$ | $626$ | $2,370$ | **`fastparse`** |
| **Quoted String (Simple)** | $2,701$ | $3,041$ | **$16,016$** | $13,524$ | $14,372$ | $2,624$ | $2,364$ | $544$ | $7,803$ | **`dot-parse`** |
| **Quoted String (Escaped)** | $2,155$ | $2,980$ | $4,934$ | **$10,945$** | $12,259$ | $2,200$ | $1,798$ | $571$ | $6,736$ | **`fastparse`** |
| **Keywords (1st - `select`)** | $73,902$ | $107,995$ | **$176,825$** | $10,370$ | $52,916$ | $24,852$ | $46,858$ | $144$ | $9,431$ | **`dot-parse`** |
| **Keywords (4th - `delete`)** | $22,872$ | $24,169$ | **$148,882$** | $9,224$ | $15,505$ | $25,732$ | $12,744$ | $88$ | $9,876$ | **`dot-parse`** |
| **Keywords (8th - `where`)** | $13,690$ | $36,865$ | **$195,350$** | $8,103$ | $11,412$ | $26,359$ | $5,392$ | $51$ | $10,497$ | **`dot-parse`** |
| **Keywords (12th - `limit`)** | $11,049$ | $39,138$ | **$195,137$** | $7,098$ | $8,383$ | $27,288$ | $4,092$ | $32$ | $10,336$ | **`dot-parse`** |
| **Keywords CI (1st)** | $34,184$ | $36,644$ | **$48,226$** | $8,207$ | $32,063$ | $11,555$ | $33,330$ | $700$ | $6,634$ | **`dot-parse`** |
| **Keywords CI (4th)** | $9,194$ | $21,380$ | **$44,830$** | $7,332$ | $14,111$ | $8,908$ | $27,602$ | $769$ | $7,059$ | **`dot-parse`** |
| **Keywords CI (8th)** | $5,460$ | $13,508$ | **$69,423$** | $6,235$ | $9,156$ | $7,104$ | $25,608$ | $753$ | $8,046$ | **`dot-parse`** |
| **Keywords CI (12th)** | $7,839$ | $9,118$ | **$69,931$** | $5,684$ | $6,730$ | $5,679$ | $21,527$ | $738$ | $7,149$ | **`dot-parse`** |
| **Calculator** | $465$ | $577$ | $779$ | **$1,248$** | $322$ | $331$ | $233$ | $4$ | $457$ | **`fastparse`** |

---

## Scenario-by-Scenario Performance Deep-Dive

### 1. IPv4 Address Parsing (Flat Sequencing)

* **The Code**:
  Matches a sequence of four digit blocks separated by dots (e.g.,
  `192.168.1.1`).

* **Performance**:
  `fastparse` ($24.0\text{k}$) > `dot-parse` ($21.1\text{k}$) $\approx$
  `cats-parse` ($20.6\text{k}$) > `taker` ($9.7\text{k}$) $\approx$ `jparsec`
  ($9.5\text{k}$) $\approx$ `parboiled` ($9.3\text{k}$) > `parsecj` ($2.9\text{k}$)
  > `antlr4` ($2.3\text{k}$) > `jjparse` ($626$).

* **Analysis**:

  * **`fastparse` and `dot-parse` Dominance**:
    Both libraries compile flat sequences into highly optimized, allocation-free
    inner loops.
    
    `fastparse` leads slightly through macro-rewriting speed, but `dot-parse`
    achieves an outstanding **21.1 million operations per second** through pure,
    lightweight Java combinator design.

  * **The ANTLR4 Two-Phase Allocation Penalty**:
    `antlr4` ($2.3\text{k}$) is significantly slower here due to its compiler-grade
    two-phase parsing architecture (Lexer + Parser).
    
    On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a
    new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual
    `CommonToken` objects for every single token scanned (4 numbers, 3 dots, 1
    EOF = 8 token allocations).
    
    This results in a heavy, 12+ object allocation loop per run.
    
    Conversely, parser combinators are **scanner-less (single-phase)** and use a
    **thread-safe static singleton parser** that directly scans the characters
    from the input string without allocating a single token wrapper.

  * **Monadic Combinator Cost**:
    Monadic libraries like `parsecj` ($2.9\text{k}$) and `jjparse` ($626$) run
    miserably slower due to their deep object call stacks and boxing of
    intermediate character and parser results.

---

### 2. Quoted String Parsing (Simple vs. Escaped)

* **The Code**:
  Matches double-quoted strings under two scenarios:
  
  1. **Simple**: Without any escape sequences (e.g., `"hello world!"`).
  2. **Escaped**: Containing backslash escapes (e.g., `"hello \"world\"!"`).

* **Performance**:
  * **Simple**: `dot-parse` ($16.0\text{k}$) > `jparsec` ($14.3\text{k}$) >
    `fastparse` ($13.5\text{k}$) > `antlr4` ($7.8\text{k}$) > `cats-parse`
    ($3.0\text{k}$) > `taker` ($2.7\text{k}$) $\approx$ `parboiled`
    ($2.6\text{k}$) $\approx$ `parsecj` ($2.3\text{k}$) > `jjparse` ($544$).
  * **Escaped**: `jparsec` ($12.2\text{k}$) $\approx$ `fastparse` ($10.9\text{k}$)
    > `antlr4` ($6.7\text{k}$) > `dot-parse` ($4.9\text{k}$) > `cats-parse`
    ($2.9\text{k}$) > `parboiled` ($2.2\text{k}$) $\approx$ `taker` ($2.1\text{k}$)
    > `parsecj` ($1.7\text{k}$) > `jjparse` ($571$).

* **Analysis**:

  * **Bulk Scanning Advantages**:
    Libraries that support native bulk-scanning primitives (like `dot-parse`'s
    `quotedByWithEscapes` and `jparsec`'s `DOUBLE_QUOTE_STRING` scanner) bypass
    individual character matching.
    
    For simple strings, `dot-parse` leads the showdown at **16.0 million
    ops/sec**.
    
    When escape characters are introduced, `fastparse` and `jparsec` show
    excellent robustness, retaining high speeds (**10.9k** and **12.2k** ops/ms
    respectively) due to highly optimized JVM-level loop structures, while
    `dot-parse` drops to **4.9k** ops/ms due to its internal use of parser
    compositions and intermediary string allocation and joining.

---

### 3. Case-Sensitive Keywords (Trie Dispatch)

* **The Code**:
  Dispatches matching across a choice list of 12 SQL-like keywords, evaluating
  performance depending on where the matched keyword lies in the choice list
  (1st: `select`, 4th: `delete`, 8th: `where`, 12th: `limit`).

* **Performance**:
  * **1st**: `dot-parse` ($176.8\text{k}$) > `cats-parse` ($107.9\text{k}$) >
    `taker` ($73.9\text{k}$) > `jparsec` ($52.9\text{k}$) > `parsecj`
    ($46.8\text{k}$) > `parboiled` ($24.8\text{k}$) > `fastparse` ($10.3\text{k}$)
    > `antlr4` ($9.4\text{k}$) > `jjparse` ($144$).
  * **12th**: `dot-parse` ($195.1\text{k}$) > `cats-parse` ($39.1\text{k}$) >
    `parboiled` ($27.2\text{k}$) > `taker` ($11.0\text{k}$) > `antlr4`
    ($10.3\text{k}$) > `jparsec` ($8.3\text{k}$) > `fastparse` ($7.0\text{k}$)
    > `parsecj` ($4.0\text{k}$) > `jjparse` ($32$).

* **Analysis**:

  * **Prefix Trie Dispatch vs. Linear Backtracking**:
    Both `dot-parse` (`anyOf`) and `cats-parse` (`oneOf`) precompute optimized
    runtime prefix-tries.
    
    Instead of checking keywords one by one, they peek at the input characters
    and prune branches instantly.
    
    This is why `dot-parse` and `cats-parse` maintain high performance.
    `dot-parse` compiles a true, flat prefix trie, running at a dominant, flat
    **148k - 195k ops/ms** across all positions.
    
    Conversely, backtracking engines like `taker`, `parsecj`, and `jparsec`
    drop catastrophically from 1st to 12th (e.g., `taker` falls from **73.9k**
    to **11.0k** ops/ms) because they must perform $O(N)$ sequential
    comparisons.

---

### 4. Case-Insensitive Keywords (Trie Dispatch Showdown)

* **The Code**:
  Dispatches matching across a choice list of 12 SQL-like keywords matched
  **case-insensitively** (e.g., matching `"SELECT"`, `"LIMIT"`).

* **Performance**:
  * **1st**: `dot-parse` ($48.2\text{k}$) > `cats-parse` ($36.6\text{k}$) >
    `taker` ($34.1\text{k}$) > `parsecj` ($33.3\text{k}$) > `jparsec` ($32.0\text{k}$)
    > `parboiled` ($11.5\text{k}$) > `fastparse` ($8.2\text{k}$) > `antlr4`
    ($6.6\text{k}$) > `jjparse` ($700$).
  * **12th**: `dot-parse` ($69.9\text{k}$) > `parsecj` ($21.5\text{k}$) >
    `cats-parse` ($9.1\text{k}$) > `taker` ($7.8\text{k}$) > `antlr4` ($7.1\text{k}$)
    > `jparsec` ($6.7\text{k}$) > `fastparse` ($5.6\text{k}$) > `parboiled`
    ($5.6\text{k}$) > `jjparse` ($738$).

* **Analysis**:

  * **`dot-parse` Case-Insensitive Trie Sweep**:
    `dot-parse` is the undisputed winner, running up to **$94\text{x}$ faster**
    than other engines at the 12th keyword.
    
    Its prefix-trie compiler precomputes all capitalization permutations of the
    first 4 characters at startup, maintaining a blazing-fast $O(1)$ dispatch.
    
  * **Regular Expression Acceleration**:
    `parsecj` achieves an impressive second-place finish at the 12th keyword
    (**21.5k ops/ms**) by delegating case-insensitive matching directly to
    Java's native regular expression engine (`Text.regex("(?i)...")`), which is
    heavily optimized at the JVM level.
    
  * **Silent Fallbacks**:
    When faced with case-insensitivity, libraries like `cats-parse` and
    `fastparse` cannot compile a prefix trie.
    
    They silently fall back to slow, sequential backtracking loops, causing a
    catastrophic performance drop at the 12th keyword (e.g., `cats-parse` falls
    from **110.9k** down to **9.1k** ops/ms).

---

### 5. Calculator Parsing (Recursive Expression Parsing)

* **The Code**:
  Matches a nested mathematical expression containing integers (supporting
  negative signs), operators (`+`, `-`, `*`, `/`), and nested parentheses up to
  3 levels deep (e.g., `" ( 1000+2 * 3000 - 4000 / (500+600) ) * -700 - 8000 /
  9000"`).
  
  It evaluates the expression using standard operator precedence
  (multiplication/division bind tighter than addition/subtraction) and
  left-associativity.

* **Performance**:
  `fastparse` ($1,248$) leads the pack, followed by `dot-parse` ($778$),
  `cats-parse` ($576$), `taker` ($465$), `antlr4` ($457$), `parboiled` ($331$),
  `jparsec` ($322$), `parsecj` ($233$), and `jjparse` ($4$).

* **Architectural Insights**:

  * **The `fastparse` Mutable State-Passing Pattern**:
    `fastparse` ($1,248$ ops/ms) wins by using compile-time macro expansion to
    rewrite declarative combinators into a **single final mutable context object
    passing pattern (`ParsingRun`)**.
    
    Instead of allocating short-lived intermediate `MatchResult` or `Reply`
    objects at every recursive step, `fastparse` passes a single, mutable
    state reference through its call chain, modifying primitive `index` and
    `isSuccess` fields in-place.
    
    This eliminates heap allocation and monadic boxing during success paths.
    Because the generated parsing methods are final and monomorphic, the JVM's
    JIT compiler can easily inline the entire recursive chain into a flat native
    bytecode loop.
    
  * **Traditional Combinator Allocation Penalty**:
    In contrast, libraries like `dot-parse` ($778$ ops/ms) and `cats-parse`
    ($576$ ops/ms) must allocate a fresh, temporary `MatchResult` wrapper on
    every single addition, multiplication, and parenthesis nesting, flooding
    the JVM Young Gen heap and keeping the garbage collector active.

  * **ANTLR4 ATN Simulator Overhead**:
    `antlr4` ($457$) is designed for complex grammar parsing rather than
    high-frequency micro-calculations.
    
    Even with reusable instances, ANTLR4's Adaptive LL(*) (ALL(*)) algorithm
    runs a dynamic transition network (ATN) simulator at runtime to track state and
    resolve lookahead.
    
    This dynamic lookahead simulation, rule-context stack (`ParserRuleContext`)
    management, and state checks introduce a heavy, fixed interpreter-like
    overhead. For tiny micro-inputs, this fixed machinery cost completely dominates
    the parsing time.

  * **The Boxing Penalty of Monads**:
    Monadic libraries like `parsecj` ($233$) and especially `jjparse` ($4$) pay
    a catastrophic penalty on recursive grammars.
    
    Every step of the recursion boxes intermediate results and characters into
    monadic wrapper classes (like `Product` or `Reply`), flooding the heap and
    keeping the JVM garbage collector constantly active.

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
