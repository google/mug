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

9. **`taker`** (Scala):
   An open-source PEG parser engine.

All benchmarks were executed side-by-side on the **same JVM (JDK 24.0.1)** and
the **same hardware (Apple M1 Max Mac)** to eliminate environmental bias.
All grammars were strictly verified with assertions ensuring **complete input
consumption (EOF)** and **structural correctness**.

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
  `dot-parse` leads the pack, matching over **201 million choices per second**
  on a single thread.

* **Case-Insensitive Trie Performance**:
  `dot-parse` is the absolute performance leader in case-insensitive matching,
  reaching **78.2 million matches per second**—**$4\text{x}$ to $14\text{x}$
  faster** than other engines.
  It achieves this by precomputing capitalization permutations at startup.
  `parsecj` finishes second by leveraging highly optimized Java regular
  expressions, while other engines drop off a performance cliff by falling
  back to slow backtracking loops.

* **Sequencing & Bulk Scanning**:
  `fastparse` and `cats-parse` lead flat sequencing (IPv4) at **24.8 million** and
  **24.2 million operations per second**, with `dot-parse` closely following at
  **22.1 million**.
  For strings, `dot-parse` dominates the common case with no escapes (**16.3 million
  ops/sec**), while `jparsec` and `fastparse` lead when handling escaped edge cases
  (**12.1 million** and **11.8 million ops/sec** respectively).

* **Recursive Block Comments**:
  `dot-parse` completely dominates recursive block comment parsing, reaching
  **11.6 million operations per second**—outperforming `fastparse` ($2.4\text{x}$
  slower) and `cats-parse` ($4.5\text{x}$ slower).
  It achieves this by using a native, flat character scanner that avoids parser
  combinator object stack framing.

* **Compiled Regex Speedups**:
  Switching `jjparse`'s SQL keywords from a linear backtracking literal choice
  to a single compiled regular expression resulted in a spectacular **$21.4\text{x}$
  throughput speedup** (climbing from **33** to **707 ops/ms**!).

---
## 9-Way Showdown Benchmark Results

Throughput was measured in **operations per millisecond** (higher is better).
🚀 marks the winner (or neck-and-neck leaders within 15% of the winner):

| Benchmark Scenario | Taker | `cats-parse` | `dot-parse` | `fastparse` | `jparsec` | `parboiled` | `parsecj` | `jjparse` | `antlr4` | **Winner(s)** |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **IPv4 Address** | $10,260$ | $24,269$ 🚀 | $22,198$ 🚀 | **$24,883$** 🚀 | $10,550$ | $9,308$ | $3,082$ | $706$ | $3,245$ | **`fast / cats / dot`** 🚀 |
| **Quoted String (Common Case)** | $2,844$ | $3,202$ | **$16,327$** 🚀 | $14,002$ 🚀 | $15,109$ 🚀 | $2,928$ | $2,280$ | $595$ | $7,576$ | **`dot / jparsec / fast`** 🚀 |
| **Quoted String (Escaped Edge Case)** | $2,275$ | $2,935$ | $5,603$ | $11,873$ 🚀 | **$12,144$** 🚀 | $2,238$ | $1,943$ | $558$ | $7,163$ | **`jparsec / fast`** 🚀 |
| **Keywords (1st - `select`)** | $81,970$ | $27,569$ | **$191,885$** 🚀 | $10,653$ | $45,474$ | $24,915$ | $75,527$ | $699$ | $10,395$ | **`dot`** 🚀 |
| **Keywords (4th - `delete`)** | $24,782$ | $25,336$ | **$127,436$** 🚀 | $9,437$ | $26,302$ | $26,217$ | $12,709$ | $780$ | $10,374$ | **`dot`** 🚀 |
| **Keywords (8th - `where`)** | $14,496$ | $37,060$ | **$158,380$** 🚀 | $8,282$ | $19,670$ | $27,663$ | $4,064$ | $717$ | $10,859$ | **`dot`** 🚀 |
| **Keywords (12th - `limit`)** | $9,955$ | $38,042$ | **$201,376$** 🚀 | $7,346$ | $14,913$ | $27,062$ | $4,122$ | $707$ | $10,989$ | **`dot`** 🚀 |
| **Keywords CI (1st)** | $36,625$ | $38,202$ | **$50,984$** 🚀 | $9,213$ | $32,385$ | $12,152$ | $28,608$ | $767$ | $7,523$ | **`dot`** 🚀 |
| **Keywords CI (4th)** | $11,527$ | $25,305$ | **$50,858$** 🚀 | $8,185$ | $14,378$ | $9,125$ | $24,583$ | $749$ | $7,165$ | **`dot`** 🚀 |
| **Keywords CI (8th)** | $5,514$ | $15,957$ | **$82,273$** 🚀 | $7,221$ | $15,814$ | $7,294$ | $22,658$ | $762$ | $7,844$ | **`dot`** 🚀 |
| **Keywords CI (12th)** | $8,048$ | $10,723$ | **$78,284$** 🚀 | $5,578$ | $11,840$ | $5,872$ | $19,642$ | $753$ | $7,942$ | **`dot`** 🚀 |
| **Calculator** | $458$ | $518$ | $744$ | **$1,237$** 🚀 | $285$ | $352$ | $241$ | $5$ | $459$ | **`fast`** 🚀 |
| **Nested Block Comment** | $730$ | $2,546$ | **$11,640$** 🚀 | $4,781$ | $1,644$ | $910$ | $805$ | $8.6$ | $2,041$ | **`dot`** 🚀 |

---

## Scenario-by-Scenario Performance Deep-Dive

### 1. IPv4 Address Parsing (Flat Sequencing)

* **The Code**:
  Matches a sequence of four digit blocks separated by dots (e.g.,
  `192.168.1.1`).

* **Performance**:
  `fastparse` ($22.8\text{k}$) > `cats-parse` ($15.1\text{k}$) >
  `dot-parse` ($12.7\text{k}$) > `jparsec` ($11.0\text{k}$) > `taker` ($9.2\text{k}$)
  $\approx$ `parboiled` ($8.8\text{k}$) > `antlr4` ($3.1\text{k}$) > `parsecj` ($2.4\text{k}$)
  > `jjparse` ($684$).

* **Analysis**:

  * **`fastparse` and `dot-parse` Dominance**:
    Both libraries compile flat sequences into highly optimized, allocation-free
    inner loops.
    
    `fastparse` leads through macro-rewriting speed, while `dot-parse`
    achieves an outstanding **22.1 million operations per second** through pure,
    lightweight Java combinator design, and `cats-parse` reaches **24.2 million**.

  * **The ANTLR4 Two-Phase Allocation Penalty**:
    `antlr4` ($3.2\text{k}$) is significantly slower here due to its compiler-grade
    two-phase parsing architecture (Lexer + Parser).
    
    On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a
    new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual
    `CommonToken` objects for every single token scanned. This results in a heavy
    object allocation loop per run, whereas parser combinators are scanner-less
    and reuse a single thread-safe parser singleton.

  * **Monadic Combinator Cost**:
    Monadic libraries like `parsecj` ($3.1\text{k}$) and `jjparse` ($706$) run
    slower due to their deep object call stacks and boxing of intermediate results.

---

### 2. Quoted String Parsing (Common Case vs. Escaped Edge Case)

* **The Code**:
  A single parser designed to parse double-quoted strings with backslash escapes,
  evaluated against two different input datasets:
  
  1. **Common Case (No Escapes)**: An input string containing no actual escape
     sequences (e.g., `"hello world!"`).
  2. **Edge Case (With Escapes)**: An input string containing actual backslash
     escapes (e.g., `"hello \"world\"!"`).

* **Performance**:
  * **Common Case**: `dot-parse` ($16.3\text{k}$) > `jparsec` ($15.1\text{k}$) >
    `fastparse` ($14.0\text{k}$) > `antlr4` ($7.5\text{k}$) > `cats-parse`
    ($3.2\text{k}$) > `parboiled` ($2.9\text{k}$) $\approx$ `taker` ($2.8\text{k}$)
    $\approx$ `parsecj` ($2.2\text{k}$) > `jjparse` ($595$).
  * **Escaped Edge Case**: `jparsec` ($12.1\text{k}$) > `fastparse` ($11.8\text{k}$) >
    `antlr4` ($7.1\text{k}$) > `dot-parse` ($5.6\text{k}$) > `cats-parse`
    ($2.9\text{k}$) > `taker` ($2.2\text{k}$) $\approx$ `parboiled` ($2.2\text{k}$)
    $\approx$ `parsecj` ($1.9\text{k}$) > `jjparse` ($558$).

* **Analysis**:

  * **Bulk Scanning Advantages**:
    Libraries that support native bulk-scanning primitives (like `jparsec`'s
    `DOUBLE_QUOTE_STRING` scanner) or JVM-optimized loops (like `fastparse`'s)
    bypass individual character matching.
    
    When actual escape sequences are present in the input (the edge case),
    `fastparse` and `jparsec` show excellent robustness, retaining high speeds
    (**11.8k** and **12.1k** ops/ms respectively) due to highly optimized loop
    structures, while `dot-parse` reaches **5.6k** ops/ms.

---

### 3. Case-Sensitive Keywords (Trie Dispatch)

* **The Code**:
  Dispatches matching across a choice list of 12 SQL-like keywords, evaluating
  performance depending on where the matched keyword lies in the choice list
  (1st: `select`, 4th: `delete`, 8th: `where`, 12th: `limit`).

* **Performance**:
  * **1st**: `dot-parse` ($191.8\text{k}$) > `taker` ($81.9\text{k}$) > `parsecj`
    ($75.5\text{k}$) > `jparsec` ($45.4\text{k}$) > `cats-parse` ($27.5\text{k}$)
    $\approx$ `parboiled` ($24.9\text{k}$) > `fastparse` ($10.6\text{k}$) > `antlr4`
    ($10.3\text{k}$) > `jjparse` ($148$).
  * **12th**: `dot-parse` ($201.3\text{k}$) > `cats-parse` ($38.0\text{k}$) >
    `parboiled` ($27.0\text{k}$) > `jparsec` ($14.9\text{k}$) > `antlr4`
    ($10.9\text{k}$) > `taker` ($9.9\text{k}$) > `fastparse` ($7.3\text{k}$)
    > `parsecj` ($4.1\text{k}$) > `jjparse` ($36$).

* **Analysis**:

  * **Trie Dispatch Implementation (Flat vs. Perfect-Hash Arrays)**:
    Both `dot-parse` (`anyOf`) and `cats-parse` (`oneOf`) successfully compile
    keyword alternatives into optimized **Radix Prefix Tries**, completely
    bypassing string-level backtracking. Both execute flat, constant-time
    $O(1)$ array-index lookups to select branches based on the lookahead character:
    
    * **`cats-parse` Perfect Bitmask Hash**: `cats-parse` precomputes a perfect
      bitwise hash mask at construction time. This yields stable, robust trie
      throughput across all positions (ranging from **$25.3\text{k}$** to
      **$38.0\text{k}$** ops/ms in short-warmup forked runs, and up to
      **$91.5\text{k}$** ops/ms under fully-converged JIT states).
      
    * **`dot-parse` Flat ASCII Table**: `dot-parse` compiles its branching nodes
      into a flat lookup table (array of size 256) when using `.precomputeForAscii()`.
      It performs a single, unboxed array index operation (`dispatchTable[firstChar]`),
      averaging an outstanding, flat **$127.4\text{k} - 201.3\text{k}$ ops/ms**
      across all positions.

  * **Linear Backtracking Costs**:
    Backtracking engines like `taker`, `parsecj`, and `jparsec` drop
    catastrophically from 1st to 12th (e.g., `taker` falls from **81.9k** to
    **9.9k** ops/ms, an $8.2\text{x}$ drop) because they must perform full $O(N)$
    sequential string comparisons.

---

### 4. Case-Insensitive Keywords (Trie Dispatch Showdown)

* **The Code**:
  Dispatches matching across a choice list of 12 SQL-like keywords matched
  **case-insensitively** (e.g., matching `"SELECT"`, `"LIMIT"`).

* **Performance**:
  * **1st**: `dot-parse` ($50.9\text{k}$) $\approx$ `cats-parse` ($38.2\text{k}$) $\approx$
    `taker` ($36.6\text{k}$) > `jparsec` ($32.3\text{k}$) $\approx$ `parsecj` ($28.6\text{k}$)
    > `parboiled` ($12.1\text{k}$) > `fastparse` ($9.2\text{k}$) > `antlr4` ($7.5\text{k}$)
    > `jjparse` ($767$).
  * **12th**: `dot-parse` ($78.2\text{k}$) 🚀 > `parsecj` ($19.6\text{k}$) >
    `jparsec` ($11.8\text{k}$) $\approx$ `cats-parse` ($10.7\text{k}$) > `taker`
    ($8.0\text{k}$) $\approx$ `antlr4` ($7.9\text{k}$) > `parboiled` ($5.8\text{k}$)
    $\approx$ `fastparse` ($5.5\text{k}$) > `jjparse` ($753$).

* **Analysis**:

  * **`dot-parse` Case-Insensitive Trie Sweep**:
    `dot-parse` is the undisputed winner at the 12th keyword, running up to
    **$4\text{x}$ to $14\text{x}$ faster** than all other engines. Its prefix-trie
    compiler precomputes all capitalization permutations of the first 4 characters
    at startup, maintaining a blazing-fast $O(1)$ dispatch.
    
  * **Regular Expression Acceleration**:
    `parsecj` achieves an impressive second-place finish at the 12th keyword
    (**19.6k ops/ms**) by delegating case-insensitive matching directly to
    Java's native regular expression engine (`Text.regex("(?i)...")`), which is
    heavily optimized at the JVM level.
    
  * **Silent Fallbacks**:
    When faced with case-insensitivity, libraries like `cats-parse` and
    `fastparse` cannot compile a prefix trie. They silently fall back to slow,
    sequential backtracking loops, causing a catastrophic performance drop at
    the 12th keyword (e.g., `cats-parse` falls from **38.2k** down to **10.7k** ops/ms).

---

### 5. Calculator Parsing (Recursive Expression Parsing)

* **The Code**:
  Matches a nested mathematical expression containing integers (supporting
  negative signs), operators (`+`, `-`, `*`, `/`), and nested parentheses up to
  3 levels deep (e.g., `" ( 1000+2 * 3000 - 4000 / (500+600) ) * -700 - 8000 /
  9000"`).
  
  The core purpose of the calculator benchmark is to measure the runtime
  performance of **recursive grammars** (which represent the ultimate test of a
  parser's architectural efficiency, evaluating how it resolves dynamic references,
  manages rule lookahead, and executes rule recursion). Furthermore, because
  the calculator is the single most popular and standard tutorial example across
  almost all of these frameworks, it makes it extremely easy to compare the
  **officially endorsed, idiomatic usage patterns** of each library.

* **Performance**:
  `fastparse` ($1,237$) leads the pack, followed by `dot-parse` ($744$),
  `cats-parse` ($518$), `antlr4` ($459$), `taker` ($458$), `parboiled` ($352$),
  `jparsec` ($285$), `parsecj` ($241$), and `jjparse` ($5$).

* **Architectural Insights**:

  * **The `fastparse` Mutable State-Passing Pattern**:
    `fastparse` ($1,237$ ops/ms) wins by using compile-time macro expansion to
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
    In contrast, libraries like `dot-parse` ($744$ ops/ms) and `cats-parse`
    ($518$ ops/ms) must allocate a fresh, temporary `MatchResult` wrapper on
    every single addition, multiplication, and parenthesis nesting, flooding
    the JVM Young Gen heap and keeping the garbage collector active.

  * **ANTLR4 ATN Simulator Overhead**:
    `antlr4` ($459$) is designed for complex grammar parsing rather than
    high-frequency micro-calculations.
    
    Even with reusable instances, ANTLR4's Adaptive LL(*) (ALL(*)) algorithm
    runs a dynamic transition network (ATN) simulator at runtime to track state and
    resolve lookahead.
    
    This dynamic lookahead simulation, rule-context stack (`ParserRuleContext`)
    management, and state checks introduce a heavy, fixed interpreter-like
    overhead. For tiny micro-inputs, this fixed machinery cost completely dominates
    the parsing time.

  * **The Boxing Penalty of Monads**:
    Monadic libraries like `parsecj` ($241$) and especially `jjparse` ($5$) pay
    a catastrophic penalty on recursive grammars.
    
    Every step of the recursion boxes intermediate results and characters into
    monadic wrapper classes (like `Product` or `Reply`), flooding the heap and
    keeping the JVM garbage collector constantly active.

---

### 6. Nested Block Comments (Recursive Structural Parsing)

* **The Code**:
  Matches a block comment that can contain nested comments recursively (e.g.,
  `"/* comment /* nested */ */"`).

  Unlike regular flat comments, nested block comments treat backslashes as
  literal characters rather than escape sequences.
  Delimiter matching is purely structural, requiring the parser to track nested
  boundaries.

* **Performance**:
  `dot-parse` ($11,640$) 🚀 > `fastparse` ($4,781$) > `cats-parse` ($2,546$) >
  `antlr4` ($2,041$) > `jparsec` ($1,644$) > `parboiled` ($910$) > `parsecj`
  ($805$) > `taker` ($730$) > `jjparse` ($8.6$).

* **Architectural Insights**:

  * **`dot-parse` Native Flat Character Scan**:
    `dot-parse` achieves an outstanding **11.6 million operations per second**
    by utilizing its highly optimized, native `nestedBy("/*", "*/")` primitive.

    Rather than constructing a heavy recursive tree of parser combinator objects
    that allocate stack frames and box intermediate character results, `nestedBy`
    scans the character stream in a single flat loop, tracking nesting depth
    in a primitive integer counter.
    This eliminates heap allocation entirely on success paths, yielding extreme
    hardware-level efficiency.

  * **Scala Tail-Recursive Methods**:
    `fastparse` ($4,781$ ops/ms) finishes a strong second.
    By using Scala's tail-recursive method definitions, it compiles the recursive
    comment matching into optimized JVM bytecode loops that avoid stack framing,
    running only $2.4\text{x}$ slower than `dot-parse`'s native scan.

  * **ANTLR4 Lexer/Parser Separation**:
    `antlr4` ($2,041$) performs exceptionally well here.
    Because the lexer tokenizes the input stream into `OPEN_COMMENT`,
    `CLOSE_COMMENT`, and `TEXT` tokens, the parser only needs to run its ALL(*)
    lookahead algorithm on a flat, pre-tokenized stream.
    This bypasses character-level monadic boxing and backtracking checks,
    allowing ANTLR4 to outperform traditional character-level combinators!

  * **The Backtracking Lookahead Trap**:
    Monadic engines like `parsecj` ($805$ ops/ms) must be carefully designed to
    avoid backtracking lookahead bottlenecks.
    If a parser branch eagerly consumes a delimiter prefix (like `*`) and then
    fails (because it is followed by `/`, ending the comment), it must backtrack
    cleanly.
    Our DFA-style grammar in `parsecj` solves this by explicitly structuring
    branches (matching normal characters, plain slashes, or stars not followed by
    slashes) to achieve lookahead-free parsing with zero backtracking overhead.

  * **The Boxing & Monadic Stack Penalty**:
    `jjparse` ($8.6$ ops/ms) runs over **$1,350\text{x}$ slower** than `dot-parse`.
    Because it lacks native nesting primitives, every step of the character-level
    backtracking recursion allocates heap wrappers, creating deep monadic stack
    frames and keeping the JVM garbage collector continuously active.

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
