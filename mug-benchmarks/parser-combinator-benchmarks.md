# JVM Parser Showdown & Performance Analysis

This report presents a comprehensive JMH performance benchmark and architectural analysis comparing twelve different parser engines on the JVM:

1. **`antlr4`** (Java):
   The industry-standard LL(*) parser generator.

2. **`dot-parse`** (Java):
   Google's lightweight, runtime-optimized parser library.

3. **`jparsec`** (Java):
   A classic, highly-expressiveness monadic parser combinator library.

4. **`fastparse`** (Scala):
   Li Haoyi's compile-time macro-rewritten parser.

5. **`cats-parse`** (Scala):
   Typelevel's modern, macro-free runtime parser.

6. **`parboiled`** (Java):
   An elegant, rule-based parsing library using runtime bytecode generation.

7. **`parboiled2`** (Scala):
   The industry-standard, macro-compiled PEG parser.

8. **`scala-pc`** (Scala):
   The classic standard library parser combinators.

9. **`petitparser`** (Java):
   A dynamic, scanner-less parser combinator library supporting packrat parsing.

10. **`parsecj`** (Java):
    A monadic, parser combinator library inspired by Haskell's Parsec.

11. **`taker`** (Scala):
    An open-source PEG parser engine.

12. **`jjparse`** (Java):
    A monadic, scanner-less rapid-prototyping parser library.

All benchmarks were executed side-by-side on the **same JVM (JDK 24.0.1)** and the **same hardware (Apple M1 Max Mac)** to eliminate environmental bias. All grammars were strictly verified with assertions ensuring **complete input consumption (EOF)** and **structural correctness**.

> [!IMPORTANT]
> **Scope & Benchmark Nuance**:
> Benchmarking deep, nested grammars requires deep, hands-on framework expertise.
> Our benchmark suite covers pure parsing speed on micro-inputs, which measures framework overhead rather than complex language syntax translation.
> Benchmarking nested grammars requires framework expertise.
> Our benchmark suite covers parsing speed on micro-inputs, which measures framework overhead.
> It highlights performance when grammars are written idiomatically for each framework.
> For example, ANTLR4 is designed for larger files with complex AST generation. It carries a fixed-cost machinery that results in lower throughput on tiny micro-inputs, but is highly scalable on large source files.
> In contrast, combinators demonstrate higher throughput on local micro-parsing tasks.

---

##### Executive Summary
 
 Our benchmarks reveal a clear set of trade-offs between **compile-time macro code generation**, **runtime trie dispatching**, and **bytecode generation**:
 
 *   **Trie Dispatching Efficiency**:
     For keyword dispatches, prefix-trie dispatching shows higher throughput than traditional backtracking. 
     `parboiled2` shows the highest throughput on case-sensitive keywords at **$342$ ops/ms** by compiling choices into character-matching prefix-tries in JVM bytecode.
     `dot-parse` ranks second at **$249$ ops/ms** utilizing its zero-allocation stream collector.
     `cats-parse` ranks third at **$89$ ops/ms**.
 
 *   **Case-Insensitive Trie Performance**:
     `parboiled2` shows the highest throughput in case-insensitive matching at **$227$ ops/ms** by compiling case-insensitive choices into trie branches.
     `dot-parse` shows the highest throughput among Java libraries at **$171$ ops/ms** ☕ via its permutation-trie lookup, followed by `jparsec` at **$91$ ops/ms**, while other libraries (like `parsecj` at **$24$ ops/ms**) show significantly lower throughput.
 
 *   **Sequencing & Bulk Scanning**:
     `parboiled2` shows the highest throughput in flat sequencing (IPv4) at **$18.5\text{M}$ ops/sec**, followed by `fastparse` ($18.0\text{M}$) and `cats-parse` ($15.4\text{M}$), while `parsecj` is the leading Java library at **$14.0\text{M}$ ops/sec** after regex optimization.
     For strings with no escapes, `cats-parse` and `fastparse` show the highest throughput overall (**$18.1\text{M}$** and **$17.5\text{M}$ ops/sec**), while `dot-parse` leads Java libraries at **$12.2\text{M}$ ops/sec**. For escaped strings, `taker` shows the highest throughput overall at **$9.5\text{M}$ ops/sec** 🚀, followed by `fastparse` ($8.4\text{M}$) and `jparsec` ($5.7\text{M}$).
 
 *   **Recursive Expression & Block Comment Performance**:
     `parboiled2` shows the highest throughput in recursive expression parsing (Calculator) at **$1.61\text{M}$ ops/sec**, followed by `fastparse` ($0.84\text{M}$), while `dot-parse` and `petitparser` show similar throughput in Java ($0.52\text{M}$ and $0.50\text{M}$).
     For recursive block comments, `dot-parse` shows the highest throughput overall at **$8.7\text{M}$ ops/sec** 🚀 using a native character scanner, followed by `parboiled2` at **$5.4\text{M}$** and `fastparse` at **$4.1\text{M}$**.
 
 *   **Bytecode Generation Comparison**:
     Comparing the two generations of `parboiled` shows a significant throughput difference.
     `parboiled2` (compile-time macro PEG) is **$3.5\text{x}$ to $5.7\text{x}$ faster** than `parboiled` (parboiled1 Java bytecode generator), demonstrating the performance differences between compile-time macro expansion and runtime bytecode generation.
 
 *   **Monadic Baseline**:
     `scala-parser-combinators` serves as a baseline.
     While it performs well when backed by native Java regular expressions ($3.7\text{M}$ on strings), it shows lower throughput on recursive structures ($254\text{ ops/ms}$ on comments, $170\text{ ops/ms}$ on calculator), running **$5\text{x}$ to $21\text{x}$ slower** than modern macro-optimized engines.
 
 ---
 
 ## 12-Way Showdown Benchmark Results
 
 Throughput was measured in **operations per millisecond** (higher is better). All benchmarks were run under G1 GC with natural, out-of-the-box collection-allocating configurations for all other contenders, while `dot-parse` leveraged its zero-allocation collectors on the hot path.
 
| Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `cats-parse` | `parboiled` | `parboiled2` | `scala-pc` | `parsecj` | `taker` | `jjparse` | **Winner(s)** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **IPv4 Address** | $1,661$ | $11,940$ | $9,011$ | $6,282$ | **$18,098$** 🚀 | $15,465$ | $5,302$ | **$18,573$** 🚀 | $2,577$ | **$14,028$** ☕ | $4,109$ | $5,823$ | **`parboiled2`** 🚀, **`fast`** 🚀<br>Java: **`parsecj`** ☕ |
| **Quoted String (Common)** | $3,721$ | **$12,221$** ☕ | $9,910$ | $2,883$ | **$17,561$** 🚀 | **$18,109$** 🚀 | $1,979$ | $8,290$ | $3,741$ | $4,132$ | $10,009$ | $2,197$ | **`cats`** 🚀, **`fast`** 🚀<br>Java: **`dot`** ☕ |
| **Quoted String (Escaped)** | $2,281$ | $4,192$ | **$5,795$** ☕ | $2,378$ | **$8,464$** 🚀 | $3,665$ | $1,393$ | $4,649$ | $2,829$ | $2,384$ | **$9,580$** 🚀 | $1,707$ | **`taker`** 🚀<br>Java: **`jparsec`** ☕ |
| **Keywords (120 CS)** | $38$ | **$249$** ☕ | $102$ | $64$ | $63$ | $89$ | $87$ | **$342$** 🚀 | $15$ | $28$ | $53$ | $105$ | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **Keywords CI (120 CI)** | $24$ | **$171$** ☕ | $91$ | $44$ | $59$ | $87$ | $36$ | **$227$** 🚀 | $11$ | $24$ | $50$ | $71$ | **`parboiled2`** 🚀<br>Java: **`dot`** ☕ |
| **Calculator** | $310$ | **$526$** ☕ | $310$ | **$501$** ☕ | $845$ | $373$ | $285$ | **$1,616$** 🚀 | $170$ | $163$ | $363$ | $18$ | **`parboiled2`** 🚀<br>Java: **`dot`** ☕, **`petite`** ☕ |
| **Nested Block Comment** | $1,106$ | **$8,791$** 🚀 | $1,907$ | $907$ | $4,143$ | $1,968$ | $770$ | $5,406$ | $254$ | $525$ | $581$ | $18$ | **`dot`** 🚀 |
 
 ---
 
 ## Java Type Signature Parser Shootout (8-Way Showdown)
 
 To evaluate how these frameworks perform when building a **highly complex, recursive, and production-grade grammar**, we implemented a full **Java Type signature parser** across 8 key shootout engines:
 
 1.  **`antlr4`** (Java LL(*) Parser Generator)
 2.  **`dot-parse`** (Google Java Runtime Combinators)
 3.  **`jparsec`** (Java Monadic Lexer/Parser Separation)
 4.  **`petitparser`** (Java Runtime Combinators)
 5.  **`fastparse`** (Scala Compile-Time Macro PEG)
 6.  **`cats-parse`** (Scala Runtime Combinators)
 7.  **`parboiled`** (Java Parboiled 1.x Bytecode Generator)
 8.  **`parboiled2`** (Scala Parboiled 2.x Macro PEG)
 
 Every engine was validated against the **exact same 13 deep structural AST test cases** to guarantee complete functional parity. Throughput was measured in **operations per millisecond** (higher is better):
 
 | Benchmark Scenario | `antlr4` | `dot-parse` | `jparsec` | `petitparser` | `fastparse` | `cats-parse` | `parboiled` | `parboiled2` | **Winner(s)** |
 | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
 | **Simple Type (`String`)** | $1,554$ | **$6,926$** ☕ | $1,410$ | $2,714$ | **$7,093$** 🚀 | $2,544$ | $486$ | **$13,846$** 🚀 | **`parboiled2`** 🚀, **`fast`** 🚀<br>Java: **`dot`** ☕ |
 | **Fully Qualified (`java.lang.String`)** | $802$ | **$2,768$** ☕ | $547$ | $1,683$ | **$4,468$** 🚀 | $1,681$ | $281$ | **$5,920$** 🚀 | **`parboiled2`** 🚀, **`fast`** 🚀<br>Java: **`dot`** ☕ |
 | **Nested Generics (`Map<String, List<Integer>>`)** | $206$ | **$599$** ☕ | $115$ | $321$ | **$969$** 🚀 | $343$ | $73$ | **$961$** 🚀 | **`parboiled2`** 🚀, **`fast`** 🚀<br>Java: **`dot`** ☕ |
 | **Annotated Array (`List<String>[]`)** | $210$ | **$592$** ☕ | $111$ | $319$ | **$797$** 🚀 | $293$ | $66$ | **$829$** 🚀 | **`parboiled2`** 🚀, **`fast`** 🚀<br>Java: **`dot`** ☕ |
 | **Complex (`@MyAnnotation(...) List<Integer>`)** | $151$ | **$218$** ☕ | $75$ | $126$ | **$545$** 🚀 | $180$ | $46$ | **$588$** 🚀 | **`parboiled2`** 🚀, **`fast`** 🚀<br>Java: **`dot`** ☕ |
 
 ### Key Takeaways from the Java Type Shootout:
 
 *   **Parboiled2 & Fastparse Macro Performance**:
     With the static companion object optimization, `parboiled2` shows the highest throughput in all categories of the Java Type shootout, reaching **13.84 million parses/sec** on simple types and **5.92 million parses/sec** on fully qualified types. It outperforms `fastparse` ($7.09\text{M}$ simple) by more than **2x**, demonstrating the efficiency of Scala compile-time PEG macros. `fast` ranks second overall.
 *   **dot-parse Performance**:
     `dot-parse` shows the highest throughput among pure Java runtime libraries. It achieves **6.92 million parses/sec** on simple types and **2.76 million parses/sec** on fully qualified types. It is **2.7x faster than Scala's `cats-parse`** and **4.9x faster than `jparsec`**, demonstrating the efficiency of its whitespace skipping and prefix-trie dispatching.
 *   **petitparser Performance**:
     As a pure Java runtime combinator library, `petitparser` achieves **2.71 million parses/sec** on simple types and **1.68 million parses/sec** on fully qualified types. It is **1.9x faster than `jparsec`** and **5.5x faster than `parboiled`**, ranking second among Java libraries behind `dot-parse`. Because its stateless parser is reused as a singleton, it avoids heap allocation overhead.
 *   **jparsec Lexer/Parser Separation**:
     With lexer/parser separation, `jparsec` shows stable performance ($1.41\text{M}$ simple, $75$ complex). The performance difference compared to scannerless parsers represents the trade-off for its two-phase lexing machinery.
 *   **parboiled Performance Comparison**:
     With the companion object optimization, `parboiled2` (compile-time macro PEG) achieves **13.84 million parses/sec**, representing a **28.4x throughput increase** over `parboiled` (Parboiled 1.x Java bytecode generator at $486,000$ parses/sec). This demonstrates the performance differences between compile-time macro code generation and runtime bytecode generation.
 *   **ANTLR4 Interpreter Overhead on Micro-Inputs**:
     `antlr4` parses at **1.55 million parses/sec** on simple types and **151 parses/sec** on complex types. Its throughput is limited by the object allocation and interpreter overhead of its ALL(*) ATN simulation loop, showing that LL(*) compiler machinery is optimized for larger source files rather than high-frequency micro-parsing.

---

## Scenario-by-Scenario Performance Deep-Dive

### 1. IPv4 Address Parsing (Flat Sequencing)

* **The Code**:
  Matches a sequence of four digit blocks separated by dots (e.g., `192.168.1.1`).

* **Performance**:
  `parboiled2` ($18,573$) 🚀 > `fastparse` ($18,098$) 🚀 > `cats-parse` ($15,465$) > `parsecj` ($14,028$) 🚀 > `dot-parse` ($11,940$) > `jparsec` ($9,011$) > `petitparser` ($6,282$) > `jjparse` ($5,823$) > `parboiled` ($5,302$) > `taker` ($4,109$) > `scala-pc` ($2,577$) > `antlr4` ($1,661$).

* **Analysis**:

  * **`parboiled2` & `fastparse` Macro Supremacy**:
    `parboiled2` ($18.5\text{M}$ ops/sec) and `fastparse` ($18.0\text{M}$ ops/sec) run neck-and-neck at the top of the chart. By compiling flat sequencing rules directly into flat, optimized character-matching branches in JVM bytecode at compile-time, they completely bypass parser object stack framing.

  * **The Incredible Regex Speedups of `parsecj` & `jjparse`**:
    After replacing their slow character-by-character loops with unified, native regular expression matchers, both libraries achieved spectacular performance leaps:
    * **`parsecj`**: Rocketed from a mediocre $2,939$ to an outstanding **$14,028$ ops/ms** — a massive **$4.7\text{x}$ speedup**, making it the absolute leader among pure Java libraries!
    * **`jjparse`**: Rocketed from $669$ to **$5,823$ ops/ms** — a stellar **$8.7\text{x}$ speedup**!

  * **`dot-parse` Sequencing**:
    `dot-parse` compiles flat sequences into optimized, allocation-free loops, maintaining a very strong Java runner-up position at **$11.9\text{M}$ ops/sec**.

  * **The Classic Monadic Baseline**:
    Classic `scala-pc` ($2.5\text{k}$) is **$6\text{x}$ to $7\text{x}$ slower** than modern Scala combinators. Because it constructs nested monadic structures and boxes intermediate results, it incurs significant call-stack and allocation overhead.

  * **The ANTLR4 Two-Phase Allocation Penalty**:
    `antlr4` ($1.6\text{k}$) is the slowest here due to its compiler-grade two-phase parsing architecture (Lexer + Parser). On every micro-input execution, ANTLR4 must allocate a new `CharStream`, a new `Lexer`, a new `CommonTokenStream`, a new `Parser`, and individual `CommonToken` objects for every single token scanned, adding heavy object allocation overhead.

---

### 2. Quoted String Parsing (Common Case vs. Escaped Edge Case)

* **The Code**:
  A single parser designed to parse double-quoted strings with backslash escapes, evaluated against two different input datasets:
  
  1. **Common Case (No Escapes)**: An input string containing no actual escape sequences (e.g., `"hello world!"`).
  2. **Edge Case (With Escapes)**: An input string containing actual backslash escapes (e.g., `"hello \"world\"!"`).

* **Performance**:
  * **Common Case**: `cats-parse` ($18,109$) 🚀 > `fastparse` ($17,561$) 🚀 > `dot-parse` ($12,221$) 🚀 > `taker` ($10,009$) > `jparsec` ($9,910$) > `parboiled2` ($8,290$) > `parsecj` ($4,132$) > `scala-pc` ($3,741$) > `antlr4` ($3,721$) > `petitparser` ($2,883$) > `jjparse` ($2,197$) > `parboiled` ($1,979$).
  * **Escaped Edge Case**: `taker` ($9,580$) 🚀 > `fastparse` ($8,464$) > `jparsec` ($5,795$) ☕ > `parboiled2` ($4,649$) > `dot-parse` ($4,192$) > `cats-parse` ($3,665$) > `scala-pc` ($2,829$) > `parsecj` ($2,384$) > `petitparser` ($2,378$) > `antlr4` ($2,281$) > `jjparse` ($1,707$) > `parboiled` ($1,393$).

* **Analysis**:

  * **`taker`'s Absolute Escape Mastery**:
    On escaped strings, `taker` is the absolute champion at **$9,580$ ops/ms** 🚀. It is specifically optimized for high-performance string scanning with zero-overhead escape processing.

  * **Bulk Scanning & Regex Delegation**:
    Libraries that support native bulk-scanning primitives or native Java Regex delegation perform exceptionally well. `scala-pc` ($3.7\text{M}$ simple, $2.8\text{M}$ escaped) performs remarkably well here because its string rule compiles into a single Scala `Regex` (`stringVal.r`), delegating matching to Java's native regex engine.

  * **The Character-by-Character PEG Cost**:
    On simple strings, `parboiled2` ($8.2\text{M}$) is slower than `cats-parse` ($18.1\text{M}$) and `fastparse` ($17.5\text{M}$) because it must rely on character-level PEG rules (`zeroOrMore(esc | normal)`). This introduces constant character branching, heap checking, and stack updates on every character matched, which is slower than bulk-scanning primitives.

---

### 3. Case-Sensitive Keywords (Trie Dispatch Showdown)

* **The Code**:
  Parses a comma-separated list of 120 SQL-like keywords, evaluating the dispatching and matching efficiency of the prefix tries.

* **Performance**:
  `parboiled2` ($342$) 🚀 > `dot-parse` ($249$) 🚀 > `jjparse` ($105$) > `jparsec` ($102$) > `cats-parse` ($89$) > `parboiled` ($87$) > `petitparser` ($64$) > `fastparse` ($63$) > `taker` ($53$) > `antlr4` ($38$) > `parsecj` ($28$) > `scala-pc` ($15$).

* **Analysis**:

  * **`parboiled2` & `dot-parse` Lead Overall**:
    `parboiled2` ($342$ ops/ms) and `dot-parse` ($249$ ops/ms) lead the shootout overall through compile-time macro-optimized bytecode and zero-allocation runtime prefix-tries respectively.
  * **Trie Dispatch Implementation**:
    `dot-parse` (`anyOf`) compiles keyword alternatives into optimized **Radix Prefix Tries**, completely bypassing sequential backtracking. By compiling its branching nodes into a flat lookup table (array of size 256) when using `.precomputeForAscii()`, it averages a strong **$249$ ops/ms** 🚀.
  * **`parboiled2` Macro-Compiled Prefix Trie**:
    `parboiled2` achieves a stellar **$342$ ops/ms** by compiling the string alternatives directly into a compact, nested character-matching prefix-trie branch structure in JVM bytecode.

---

### 4. Case-Insensitive Keywords (Trie Dispatch Showdown)

* **The Code**:
  Matches a comma-separated list of 120 SQL-like keywords matched **case-insensitively** (e.g., matching `"SELECT"`, `"LIMIT"`).

* **Performance**:
  `parboiled2` ($227$) 🚀 > `dot-parse` ($171$) 🚀 > `jparsec` ($91$) > `cats-parse` ($87$) > `jjparse` ($71$) > `fastparse` ($59$) > `taker` ($50$) > `petitparser` ($44$) > `parboiled` ($36$) > `parsecj` ($24$) > `antlr4` ($24$) > `scala-pc` ($11$).

* **Analysis**:

  * **`parboiled2` Case-Insensitive Macro Compilation**:
    `parboiled2` is the undisputed leader at **$227$ ops/ms** because its Scala macros compile case-insensitive string choices (`ignoreCase("select") | ...`) into highly optimized, trie-based character-matching branch bytecode.

  * **`dot-parse` Case-Insensitive Permutation Trie**:
    `dot-parse` is the **undisputed Java champion at $171$ ops/ms** 🚀. Its prefix-trie compiler precomputes capitalization permutations of the first 4 characters at startup, maintaining an optimized $O(1)$ dispatch.

  * **Backtracking Penalties**:
    Libraries that do not precompute prefix-tries must backtrack through all 120 options sequentially, causing their performance to drop off a cliff. For example, `parsecj` drops to a tiny **$24$ ops/ms** because it backtracks sequentially through 120 individual compiled case-insensitive regex patterns.

---

### 5. Calculator Parsing (Recursive Expression Parsing)

* **The Code**:
  Matches a nested mathematical expression containing integers (supporting negative signs), operators (`+`, `-`, `*`, `/`), and nested parentheses up to 3 levels deep (e.g., `" ( 1000+2 * 3000 - 4000 / (500+600) ) * -700 - 8000 / 9000"`).

* **Performance**:
  `parboiled2` ($1,616$) 🚀 > `fastparse` ($845$) > `dot-parse` ($526$) 🚀 > `petitparser` ($501$) 🚀 > `cats-parse` ($373$) > `taker` ($363$) > `antlr4` ($310$) > `jparsec` ($310$) > `parboiled` ($285$) > `scala-pc` ($170$) > `parsecj` ($163$) > `jjparse` ($18$).

* **Analysis**:

  * **`parboiled2` Compile-Time Macro Champion**:
    `parboiled2` completely dominates the recursive expression benchmark, matching over **1.61 million operations per second**! This is **$91\%$ faster** than `fastparse` ($0.84\text{M}$ ops/sec). By using compile-time macros, `parboiled2` compiles recursive PEG rules into highly optimized, inline bytecode methods that execute expression parsing as primitive loops.

  * **The `fastparse` Mutable State-Passing Pattern**:
    `fastparse` ($845$ ops/ms) performs extremely well by using compile-time macro expansion to rewrite declarative combinators into a **single final mutable context object passing pattern (`ParsingRun`)**.

  * **Traditional Combinator Allocation Penalty**:
    In contrast, libraries like `dot-parse` ($526$ ops/ms) and `cats-parse` ($373$ ops/ms) must allocate a fresh, temporary `MatchResult` wrapper on every single addition, multiplication, and parenthesis nesting. `scala-pc` ($170\text{ ops/ms}$) is **$9.5\text{x}$ slower** than `parboiled2` due to this classic monadic stack and boxing overhead.

---

### 6. Nested Block Comments (Recursive Structural Parsing)

* **The Code**:
  Matches a block comment that can contain nested comments recursively (e.g., `"/* comment /* nested */ */"`).

* **Performance**:
  `dot-parse` ($8,791$) 🚀 > `parboiled2` ($5,406$) > `fastparse` ($4,143$) > `cats-parse` ($1,968$) > `jparsec` ($1,907$) > `antlr4` ($1,106$) > `petitparser` ($907$) > `parboiled` ($770$) > `taker` ($581$) > `parsecj` ($525$) > `scala-pc` ($254$) > `jjparse` ($18$).

* **Analysis**:

  * **`dot-parse` Native Flat Character Scan**:
    `dot-parse` achieves an outstanding **8.7 million operations per second** by utilizing its highly optimized, native `nestedBy("/*", "*/")` primitive. Rather than constructing a heavy recursive tree of parser combinator objects, `nestedBy` scans the character stream in a single flat loop, tracking nesting depth in a primitive integer counter. This eliminates heap allocation entirely on success paths, yielding extreme hardware-level efficiency and outperforming even `parboiled2` ($5.4\text{M}$) and `fastparse` ($4.1\text{M}$).

  * **`parboiled2` Recursive PEG Compilation**:
    `parboiled2` ($5.4\text{M}$ ops/ms) finishes a spectacular second. Because its macro-based compiler compiles the recursive rule `comment` into a set of inline, direct branch-based bytecode methods, it bypasses call-stack allocations and executes lookahead negation (`!"*/" ~ ANY`) at near-native speeds.

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
