# Email Address Parsing Benchmarks

This document records the performance benchmarks of `EmailAddress` compared to other popular Java email parsing libraries, namely **Jakarta Mail (`InternetAddress`)** and **JMail**.

## Benchmark Environment
- **JMH Version**: 1.37
- **JDK Version**: 24 (Java HotSpot(TM) 64-Bit Server VM, 24.0.1+9-30)
- **OS**: macOS

---

## 1. Single Plain Email Address Parsing
Measures the throughput of parsing/validating a single plain email address (`"user@company.com"`) with no display name or angle brackets.

### Results (Throughput)

| Parser / Library | Throughput (ops/s) | Error (ops/s) | Relative Performance |
| :--- | :--- | :--- | :--- |
| [**`InternetAddress` (Jakarta Mail)**](http://github.com/google/mug/blob/master/mug-benchmarks/src/test/java/com/google/mu/benchmarks/EmailAddressBenchmark.java#L51-L53) | **10,683,342** | ± 228,078 | **2.7x** |
| [**`EmailAddress.of` (Combinator)**](http://github.com/google/mug/blob/master/mug-benchmarks/src/test/java/com/google/mu/benchmarks/EmailAddressBenchmark.java#L41-L43) | **3,842,677** | ± 52,220 | **1.0x** |
| [**`JMail.tryParse`**](http://github.com/google/mug/blob/master/mug-benchmarks/src/test/java/com/google/mu/benchmarks/EmailAddressBenchmark.java#L46-L48) | **2,205,359** | ± 65,951 | **0.6x** |

### Analysis
- **Jakarta Mail** is the fastest because it uses a relaxed, hand-written state loop that performs minimal validation and avoids constructing intermediate objects. It is less strict and susceptible to certain RFC violations.
- **`EmailAddress`** is **1.7x faster than JMail** while enforcing strict RFC 5322 compliance. It leverages `dot-parse` combinators, which are optimized for character-level matching and utilize blocklist-based prefix pruning.

---

## 2. Email Address List Parsing
Measures the throughput of parsing a comma-separated list of email addresses using `EmailAddress.parseAddressList()`.

### Results (Throughput)

| Benchmark Scenario | Throughput (ops/s) | Error (ops/s) | Description |
| :--- | :--- | :--- | :--- |
| `parseValidList` | **273,263** | ± 4,811 | Parses a list of 4 valid email addresses. |
| `parseMixedList` | **217,357** | ± 1,252 | Parses a list of 5 addresses, discarding 2 invalid ones. |
