# Date-Time Parsing Performance Benchmarks (JMH)

This document presents a JVM performance benchmark comparison between **Hutool 5**
(`DateUtil.parse()`) and **Mug** (`DateTimeFormats`).

All benchmarks were executed on the **same JVM (JDK 24.0.1)** and the **same
hardware (Apple M1)** to ensure comparative validity.

---

## JMH Benchmark Results

Throughput was measured in **average execution time per operation
(nanoseconds/operation)**, where **lower is better**:

| Benchmark Scenario                                                        | `DateUtil.parse()` (ns/op) | `DateTimeFormatter.parse()` (pre-inferred) (ns/op)  | Speedup   |
| :------------------------------------------------------------------------ | :------------------------: | :-------------------------------------------------: | :-------: |
| **Date-Time (`"2026-08-12 10:30:00"`)**                                   | **1501.47 ns**             | **178.49 ns**                                       | **8.41x** |
| **Instant (`"2020-01-01T00:00:01-07:00"`)**                               | **1577.02 ns**             | **623.20 ns**                                       | **2.53x** |
| **Date-Time with Zone (`"2020-01-01T00:00:01-07:00[America/New_York]"`)** | **2367.73 ns**             | **786.01 ns**                                       | **3.01x** |

---

## Key Performance Insights & Rationale

*   For production code that requires high predictability and efficiency,
    `formatOf()` is preferred to pre-allocate a JDK `DateTimeFormatter` object
    to be used in inner loops (running **2.5x to 8.4x faster** than Hutool).
*   Pre-allocating through `formatOf()` offers both compile-time validation
    through ErrorProne, and startup-time validation to ensure the format is
    correct.
*   The inferred `DateTimeFormatter` object is shown to parse 2-8x faster than
    HuTool's `parse()` on-the-fly.
*   **Dynamic convenience methods (like `parseToInstant()`)** are designed for
    **convenience and rapid development** in command-line tools, scripts,
    migration tools, and test code, where code readability and developer
    velocity are more important than raw speed or predictability.
*   `parseToInstant()` and similar convenience methods
    perform strict roundtrip verification (inferring the `DateTimeFormatter`
    and then using it to parse the source string).
    This ensures that the inferred pattern is correct according to JDK specs.
    No accidental third-party bug can silently corrupt your production.
    By contrast, Hutool infers but does no validation or format verification.
