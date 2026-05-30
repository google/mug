# Strict Email Address Parsing & Domain Model

The `com.google.common.labs.email` package provides a modern, declarative,
and secure email address parser and domain model built using compact parser
combinators. It serves as a lightweight and secure alternative to
`javax.mail.InternetAddress` and standard rule-based validators.

---

## 1. Feature & API Design Comparison

| Feature / Property | `EmailAddress` (Combinator) | `InternetAddress` (Jakarta Mail) | JMail | Apache `EmailValidator` |
| :--- | :--- | :--- | :--- | :--- |
| **Domain Mutability** | **Immutable Record** (Thread-safe, robust as Map keys) | ❌ **Mutable POJO** (Exposes setters, prone to side effects) | **Immutable Value Object** (Thread-safe) | N/A |
| **Footprint & Deps** | **Lightweight** (Zero external dependencies; built on `dot-parse` combinators) | ⚠️ **Heavy EE stack** (Transitive dependencies) | **Lightweight** (Minor standalone deps) | **Lightweight** (Part of commons-validator) |
| **Value Extraction** | **Canonical** (Quotes stripped, escapes unescaped) | ⚠️ **Mixed** (Canonical personal name; raw local part) | ⚠️ **Raw** (Quotes and backslashes left intact) | N/A |

---

## 2. RFC Compliance

| RFC Feature / Section | `EmailAddress` | `InternetAddress` (Jakarta Mail) | JMail | Apache `EmailValidator` |
| :--- | :--- | :--- | :--- | :--- |
| **`local-part@domain`** |  **Compliant** |  **Compliant** |  **Compliant** |  **Compliant** |
| **Quoted Local Parts** |  **Compliant & Canonical** (Strips quotes; re-escapes on output) |  **Compliant** | ⚠️ **Partially Compliant** (Fails to strip/unescape quotes) |  **Compliant** |
| **Folding White Space** (FWS) |  **Compliant** (Parses and canonicalizes them away safely) |  **Compliant** (Supports standard FWS) | ⚠️ **Partially Compliant** (Fails on FWS directly after angle bracket) | 🚫 **Rejected** (Rejects them completely) |
| **Unquoted Display Names** |  **Strictly Compliant** (Forbids special characters `()<>[]:;@\,"` to prevent spoofing) | ⚠️ **Lenient** (Allows special characters unquoted in display names) | ⚠️ **Lenient** (Allows special characters unquoted in display names) | 🚫 **Rejected** (Rejects display names completely, returns `false`) |
| **Group Addresses** (RFC 822) | 🚫 **Intentionally Omitted** (Obsolete, rejected for security) |  **Compliant** (Parses groups as `isGroup()`) | 🚫 **Intentionally Omitted** (Obsolete, rejected for security) | 🚫 **Rejected** (Rejects group syntax completely) |
| **RFC 2047 Encoded Words** | 🚫 **Intentionally Omitted** (Preserved raw to prevent spoofing) |  **Compliant** (Decodes automatically, posing security risks) | 🚫 **Intentionally Omitted** (Preserved raw to prevent spoofing) | 🚫 **Rejected** (Rejects encoded words completely) |
| **Comments & Domain Literals** | 🚫 **Intentionally Omitted** (Legacy comments/IP domains skipped) |  **Compliant** (Supports full legacy feature set) | 🚫 **Intentionally Omitted** (Legacy comments/IP domains skipped) | ⚠️ **Partially Compliant** (Supports IP literals, rejects comments) |

---

## 3. Security & Hardening

| Attack Vector / Vulnerability | `EmailAddress` (Combinator) | `InternetAddress` (Jakarta Mail) | JMail | Apache `EmailValidator` |
| :--- | :--- | :--- | :--- | :--- |
| **Parsing Differentials** (Split Bug) |  **Immune** (Strictly rejects unconsumed trailing characters) | ❌ **Vulnerable** (Silently discards trailing parts like `<a@b>c@d`) |  **Immune** (Natively rejects) |  **Immune** (Rejects display names completely, avoiding parsing differentials) |
| **Display Name Spoofing** (Phishing) |  **Immune** (Strictly rejects unquoted `@` or `<` in display names) | ❌ **Vulnerable** (Decodes and accepts unquoted `@` and `<` in display names) | ❌ **Vulnerable** (Accepts unquoted `@` in display names) | ⚠️ N/A (Rejects all display names) |
| **Group Syntax Abuse** (List splitting) |  **Immune** (obsolete RFC 822 group constructs are strictly rejected) | ❌ **Vulnerable** (Accepts group syntax, bypassing single-recipient controls) |  **Immune** (Natively rejects groups) |  **Immune** (Rejects all group formats) |


---

## 4. Detailed API Functionality Comparison

### A. Bulk / Address List Parsing

* **`EmailAddress`**: Fully supports robust list parsing.

  * `parseAddressList(String)`: Parses a comma- or semicolon-delimited list.
  * `parseAddressList(String, Consumer<? super String>)`: Parses list
    fault-tolerantly, returning valid elements while collecting invalid
    entries in a callback (ideal for logging or user feedback).

* **`InternetAddress`**: Supports list parsing via
  `InternetAddress.parse(String)`. However, it lacks graceful error
  accumulation (either throws `AddressException` on the whole string or
  returns silently truncated/corrupted addresses).

* **JMail**: No built-in list parsing or tokenization capabilities. Because
  delimiters (commas `,` and semicolons `;`) can legally reside inside
  double-quoted display names (e.g., `"John, Doe" <john@example.com>`) or
  double-quoted local-parts (e.g., `"john,doe"`), a naive string split
  (`split(",")`) is infeasible and will corrupt valid addresses. Correctly
  tokenizing an address list for JMail requires developers to implement their
  own custom parser.

  > **Why not use a regular expression to tokenize?**
  > 
  > While a developer's first instinct might be to use a regular expression
  > to match tokens while ignoring delimiters inside quotes, this is highly
  > fragile and discouraged:
  > 
  > * **Handling Escapes is Complex**: A correct regex must handle nested
  >   combinations of escaped quotes (`\"`), double backslashes (`\\`), and
  >   unclosed quotes. This makes the pattern extremely complex and
  >   difficult to read or debug.
  > * **Security Risk (Catastrophic Backtracking)**: Complex regular
  >   expressions with nested quantifiers are highly vulnerable to Regular
  >   Expression Denial of Service (ReDoS) attacks, where malicious or
  >   extremely long malformed inputs can easily freeze the JVM thread.
  > * **Lack of Graceful Recovery**: A regex-based tokenizer cannot isolate
  >   individual corrupt elements and continue parsing the rest of the list
  >   cleanly.

* **Apache `EmailValidator`**: Same as JMail; has no support for list parsing.



### B. Lenient vs. Strict Parsing Modes

* **`EmailAddress`**: Follows **Strict-by-Default** architecture. All primary
  parsing endpoints (`of()`, `PARSER`) enforce strict validation.
  `parseAddressList()` supports fault tolerance mode to prevent a single invalid entry
  from defeating the list parsing.

* **`InternetAddress`**: Supports both strict and lenient modes via
  `new InternetAddress(address, strict)`. However, even its "strict" mode
  remains highly vulnerable to parsing differentials and spoofing attacks.

* **JMail**: Strictly validates single email strings. No list parsing.


### C. Features Omitted in `EmailAddress` (Supported by Others)

To maintain compatibility with modern MTAs and guarantee safety,
`EmailAddress` intentionally omits several obsolete features:

1. **RFC 822 Group Address Lists** (e.g.,
   `group-name:addr1@b.com,addr2@c.com;`): Supported by Jakarta Mail.
   Omitted by both `EmailAddress` and JMail to enforce a secure
   single-recipient mailbox paradigm.
2. **Nested Parenthetical Comments** (e.g., `john(comment)@example.com`):
   Supported by Jakarta Mail. Omitted by both `EmailAddress` and JMail
   because comments are obsolete and increase downstream parsing complexity.
3. **Domain IP Literals** (e.g., `user@[192.168.1.1]`): Supported by both
   Jakarta Mail and JMail. Omitted by `EmailAddress` to align with modern
   secure routing where IP-based email routing is practically obsolete.
4. **Dynamic MIME Header Decoding (RFC 2047)**: Supported by Jakarta Mail
   (which decodes automatically, exposing visual spoofing risks). Omitted by
   both `EmailAddress` and JMail to prevent spoofing; however, JMail still
   accepts display names containing unquoted `@` characters in encoded
   blocks, whereas `EmailAddress` strictly rejects them.

### D. Composability & Extensibility

Unlike Jakarta Mail and JMail, where the parsing code is closed and
hard-coded inside static methods, `EmailAddress` exposes the underlying
combinator parser as `public static final Parser<EmailAddress> PARSER`.

This allows developers to effortlessly compose `EmailAddress.PARSER` inside
larger, custom parsers to support specialized requirements. For instance, if
an application specifically needs to support legacy RFC 822 group address
syntax, developers can define a custom group parser:

```java {.good}
// Example: Composing EmailAddress.PARSER to support group address lists
Parser<GroupAddress> groupParser = Parser.sequence(
    Parser.word().followedBy(":"),                  // Group name (e.g. "admin")
    EmailAddress.PARSER.zeroOrMoreDelimitedBy(",")
        .followedBy(";"),                           // Core email parser for members
    GroupAddress::new);
```

This composable architecture keeps the core domain model secure and
simple, while providing open extensibility for application-specific
protocols.
