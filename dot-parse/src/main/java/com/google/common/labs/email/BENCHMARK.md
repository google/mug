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
| [**`InternetAddress` (Jakarta Mail)**](http://github.com/google/mug/blob/master/mug-benchmarks/src/test/java/com/google/mu/benchmarks/EmailAddressBenchmark.java#L66-L68) | **14,798,664** | ± 498,308 | **2.2x** |
| [**`JMail.tryParse` (v2.2.1)**](http://github.com/google/mug/blob/master/mug-benchmarks/src/test/java/com/google/mu/benchmarks/EmailAddressBenchmark.java#L61-L63) | **11,249,380** | ± 174,405 | **1.7x** |
| [**`EmailAddress.of` (Combinator)**](http://github.com/google/mug/blob/master/mug-benchmarks/src/test/java/com/google/mu/benchmarks/EmailAddressBenchmark.java#L46-L48) | **6,770,743** | ± 126,282 | **1.0x** |

### Analysis
- **Jakarta Mail** is the fastest because it uses a relaxed, hand-written state loop that performs minimal validation and avoids constructing intermediate objects. It is less strict and susceptible to certain RFC violations.
- **JMail (v2.2.1)** performs fast validation of plain single addresses.
- **`EmailAddress`** delivers **6.77M ops/s** on single address parsing while enforcing strict RFC 5322 compliance, full display name / bracket support, and high-throughput list parsing.

---

## 2. Email Address List Parsing
Measures the throughput of parsing a comma-separated list of email addresses using `EmailAddress.parseAddressList()`.

### Results (Throughput)

| Benchmark Scenario | Throughput (ops/s) | Error (ops/s) | Description |
| :--- | :--- | :--- | :--- |
| `parseValidList` | **948,052** | ± 20,112 | Parses a list of 4 valid email addresses. |
| `parseValidList_withConsumer` | **899,385** | ± 15,396 | Parses a list of 4 valid email addresses with streaming consumer. |
| `parseMixedList` | **444,802** | ± 7,105 | Parses a list of 5 addresses, discarding 2 invalid ones. |
