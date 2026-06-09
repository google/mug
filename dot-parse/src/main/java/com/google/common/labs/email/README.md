# Strict Email Address Parsing & Domain Model

The `com.google.common.labs.email` package provides a modern, declarative,
and secure email address parser and domain model built using compact parser
combinators. It serves as a lightweight and secure alternative to
`javax.mail.InternetAddress` and standard rule-based validators.

---

## 1. Feature & API Design Comparison

| Feature / Property | `EmailAddress` (Combinator) | `InternetAddress` (Jakarta Mail) | JMail | Apache `EmailValidator` |
| :--- | :--- | :--- | :--- | :--- |
| **Domain Mutability** | **Immutable Record** (Thread-safe) | **Mutable POJO** (Exposes setters) | **Immutable Value Object** (Thread-safe) | N/A |
| **Footprint & Deps** | **Lightweight** (Zero external dependencies; built on `dot-parse`) | **Standard API** (Part of Jakarta Mail API) | **Lightweight** (Minor standalone deps) | **Lightweight** (Part of commons-validator) |
| **Value Extraction** | **Canonical** (Quotes stripped, escapes unescaped) | **Mixed** (Canonical personal name; raw local part) | **Literal / Raw** (Quotes and backslashes preserved) | N/A |

---

## 2. RFC Compliance

| RFC Feature / Section | `EmailAddress` | `InternetAddress` (Jakarta Mail) | JMail | Apache `EmailValidator` |
| :--- | :--- | :--- | :--- | :--- |
| **`local-part@domain`** |  **Supported** |  **Supported** |  **Supported** |  **Supported** |
| **Quoted Local Parts** |  **Canonical** (Strips quotes; re-escapes on output) |  **Supported (Raw)** (Preserves quotes) |  **Supported (Raw)** (Preserves quotes) |  **Supported** |
| **Folding White Space** (FWS) |  **Full Support** (Supports standard FWS with CR/LF) |  **Full Support** (Supports standard FWS with CR/LF) | ⚠️ **Partial Support** (Fails on FWS directly after angle bracket) | 🚫 **Not Supported** (Rejects FWS completely) |
| **Unquoted Display Names** |  **Strict** (Forbids special characters `()<>[]:;@\,"` to prevent spoofing) |  **Lenient** (Allows special characters unquoted) |  **Lenient** (Allows special characters unquoted) | 🚫 **Not Supported** (Rejects display names) |
| **Group Addresses** (RFC 822) | 🚫 **Omitted** |  **Supported** (Parses groups as `isGroup()`) | 🚫 **Omitted** | 🚫 **Not Supported** (Rejects group syntax) |
| **RFC 2047 Encoded Words in Local Part** | 🚫 **Rejected Defensively** (Rejects `=?`...`?=` inside local-part/domain to prevent downstream spoofing) |  **Supported** (Decodes automatically) | 🚫 **Omitted** (Preserved raw; downstream spoofing) | 🚫 **Not Supported** (Rejects encoded words) |
| **Comments & Domain Literals** | 🚫 **Omitted** (Obsolete comments/IP domains skipped) |  **Supported** (Supports full legacy features) | 🚫 **Omitted** (Obsolete comments/IP domains skipped) | ⚠️ **Partial Support** (Supports IP literals, rejects comments) |

---

## 3. Security & Validation Profiles

| Input Vector / Behavior | `EmailAddress` (Combinator) | `InternetAddress` (Jakarta Mail) | JMail | Apache `EmailValidator` |
| :--- | :--- | :--- | :--- | :--- |
| **Trailing Unconsumed Input** |  **Rejected** (EOF-enforced) |  **Permissive** (Discards trailing parts like `<a@b>c@d`) |  **Rejected** (EOF-enforced) |  **Rejected** (EOF-enforced) |
| **Unquoted Specials in Display Name** |  **Rejected** (Throws exception) |  **Permissive** (Allows unquoted `@` and `<`) |  **Permissive** (Allows unquoted `@`) | ⚠️ N/A (Rejects all display names) |
| **RFC 822 Group Addresses** |  **Rejected** (Throws exception) |  **Permissive** (Accepted as group) |  **Rejected** (Throws exception) |  **Rejected** (Throws exception) |
| **CRLF / SMTP Command Injection** |  **Permissive** (Allows folding CR/LF newlines) |  **Permissive** (Allows folding CR/LF newlines) | ⚠️ **Partial Support** (Allows folding newlines in whitespace; rejects them inside local-part/domain) |  **Rejected** (Newlines forbidden) |


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
  returns silently truncated/corrupted addresses); rejects common and harmless
  human errors like double comma (`user1@a.com,,user2@a.com`); and offers no
  support for display names with the period character
  (`J.R.R. Tolkien <tolkien@lotr.org>`), which is practically common.

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

* **`EmailAddress`**: A trustable data model. Any code operating on an
  `EmailAddress` can be assured that it's fully validated and secure
  solely on the basis of its compile-time type, regardless of how, where,
  or from what input the instance was constructed.

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
   (which decodes automatically, exposing visual spoofing risks). Both `EmailAddress` and JMail omit decoding to prevent spoofing. However, to prevent downstream mailer spoofing exploits (where MTAs like Postfix decode these strings even inside address parts), `EmailAddress` **defensively rejects** encoded-words inside the local-part and domain (e.g., `=?UTF-8?Q?...?=`), whereas JMail and Jakarta Mail permit them, exposing applications to routing hijacking. Additionally, JMail still accepts display names containing unquoted `@` characters in encoded blocks, whereas `EmailAddress` strictly rejects them.

### D. Composability & Extensibility

Unlike Jakarta Mail, where the parsing code is closed and
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
