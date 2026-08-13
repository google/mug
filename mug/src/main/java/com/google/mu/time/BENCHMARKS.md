# Date-Time Parsing Performance Benchmarks (JMH)

This document presents a JVM performance benchmark comparison between **Hutool 5** (`DateUtil.parse()`) and **Mug** (`DateTimeFormats`).

All benchmarks were executed on the **same JVM (JDK 24.0.1)** and the **same hardware (Apple M1)** to ensure strict comparative validity.

---

## Benchmark Setup

We evaluated the performance of both libraries under three distinct parsing models:

1.  **Hutool Dynamic:** Calls `DateUtil.parse(input)` on every iteration, forcing layout discovery and regex normalization on every call.
2.  **Mug Dynamic:** Calls `DateTimeFormats.parseLocalDate` (for dates) or dynamically resolves formatters with `formatOf(input)` (for date-times) on every iteration.
3.  **Mug Pre-allocated (Recommended Pattern):** Uses a pre-compiled, static `DateTimeFormatter` instance constructed via `DateTimeFormats.formatOf(input)` to parse input strings via JSR-310 standard APIs (`LocalDate.parse` / `LocalDateTime.parse`), bypassing all layout discovery overhead on the hot path.

---

## JMH Benchmark Results

Throughput was measured in **average execution time per operation (nanoseconds/operation)**, where **lower is better**:

| Benchmark Scenario | parsing Target | Hutool Dynamic (ns/op) | Mug Dynamic (ns/op) | Mug Pre-allocated (ns/op) | Speedup (vs. Hutool) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Date-Only (`"2026-08-12"`)** | `LocalDate` | **843.77 ns** | **457.32 ns** | **100.55 ns** | **8.39x faster** (Pre-allocated)<br>**1.84x faster** (Dynamic) |
| **Date-Time (`"2026-08-12 10:30:00"`)** | `LocalDateTime` | **1508.63 ns** | **2663.95 ns** | **173.32 ns** | **8.70x faster** (Pre-allocated) |
| **Date-Time with Zone (`"2020-01-01T00:00:01-07:00[America/New_York]"`)** | `ZonedDateTime` | **2400.08 ns** | **3818.03 ns** | **757.18 ns** | **3.17x faster** (Pre-allocated) |
| **Date-Time with Zone ID Only (`"2020-01-01T12:00:00 Asia/Shanghai"`)** | `ZonedDateTime` | **2233.99 ns** ⚠️ | **3659.26 ns** | **367.11 ns** | **6.08x faster** (Pre-allocated)<br>*(Hutool fails to parse)* |

> ⚠️ *Note: Hutool fails completely (throws `DateException`) when given regional zone names without offsets (e.g. `Asia/Shanghai`). The measured time for Hutool in this scenario represents Java's stack-trace generation and exception-handling overhead during failure.*

---

## Key Performance Insights

### 1. Pre-allocated Performance Advantage
By pre-allocating the formatter with `formatOf()` and parsing directly via JSR-310 standard APIs, Mug runs **3.1x to 8.7x faster** than Hutool. This pattern completely avoids dynamic pattern matching, string normalization, and regex matcher allocations on the hot path.

### 2. Mug's Dynamic Date Lookup
Mug's dynamic date parsing (`parseLocalDate`) is **1.84x faster** than Hutool because it tokenizes layout signatures and resolves them via fast static hash-map lookups instead of executing regex matchers and string replacements (`replaceAll`).

### 3. Timezone parsing and validation cost
Parsing date-times with regional time zones is naturally heavier due to the JSR-310 zone database lookup and daylight-savings validations. However:
*   Parsing **Zone ID only** (`367.11 ns`) is **2x faster** than parsing **both offset + Zone ID** (`757.18 ns`) because JSR-310 doesn't have to perform validation matching between the parsed offset and regional rules.
*   Mug's pre-allocated path still outperforms Hutool's dynamic path by **over 3x**.

### 4. Dynamic Date-Time Compilation Cost
Calling `formatOf()` dynamically on every iteration is an anti-pattern. Compiling a JSR-310 `DateTimeFormatter` on every call is heavier than Hutool's simple layout switcher, which highlights why pre-allocation is the strongly recommended pattern in Mug.
