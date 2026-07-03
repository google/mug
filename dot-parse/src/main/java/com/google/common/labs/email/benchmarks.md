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
| **`InternetAddress` (Jakarta Mail)** | **10,809,935** | ± 313,162 | **2.6x** *(Fastest)* |
| **`EmailAddress.of` (Combinator)** | **4,102,993** | ± 32,943 | **1.0x** |
| **`JMail.tryParse`** | **2,102,128** | ± 45,527 | **0.5x** *(Slowest)* |

### Analysis
- **Jakarta Mail** is the fastest because it uses a relaxed, hand-written state loop that performs minimal validation and avoids constructing intermediate objects. It is less strict and susceptible to certain RFC violations.
- **`EmailAddress`** is **2x faster than JMail** while enforcing strict RFC 5322 compliance. It leverages `dot-parse` combinators, which are optimized for character-level matching.
- **JMail** is the slowest, likely due to more complex validation regex/logic and higher object allocation overhead per parse.

---

## 2. Email Address List Parsing
Measures the throughput of parsing a comma-separated list of email addresses using `EmailAddress.parseAddressList()`.

### Results (Throughput)

| Benchmark Scenario | Throughput (ops/s) | Error (ops/s) | Description |
| :--- | :--- | :--- | :--- |
| `parseValidList` | **205,169** | ± 2,248 | Parses a list of 4 valid email addresses. |
| `parseMixedList` | **171,817** | ± 1,537 | Parses a list of 5 addresses, discarding 2 invalid ones. |
