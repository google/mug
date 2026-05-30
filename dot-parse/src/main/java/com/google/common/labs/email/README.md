# Strict Email Address Parsing & Domain Model

The `com.google.common.labs.email` package provides a modern, declarative, and secure email address parser and domain model built using compact parser combinators. It serves as a lightweight and secure alternative to `javax.mail.InternetAddress` and standard rule-based validators.

---

## 1. Feature & API Design Comparison

| Feature / Property | `EmailAddress` (Combinator) | `InternetAddress` (Jakarta Mail) | JMail |
| :--- | :--- | :--- | :--- |
| **Domain Mutability** | **Immutable Record** (Thread-safe, robust as Map keys) | ❌ **Mutable POJO** (Exposes setters, prone to side effects) | **Immutable Value Object** (Thread-safe) |
| **Footprint & Deps** | **Zero external dependencies** (Uses `dot-parse` combinators) | ❌ **Heavy EE stack** (Transitive dependencies) | **Lightweight** (Minor standalone deps) |
| **Value Extraction** | **Canonical** (Quotes stripped, escapes unescaped) | ⚠️ **Mixed** (Canonical personal name; raw local part) | ❌ **Raw** (Quotes and backslashes left intact) |
| **Fluent Modifiers** |  **Supported** (e.g., `.withDisplayName(...)`) | ❌ **None** (Requires manual mutations) | ❌ **None** |

---

## 2. RFC Compliance Comparison

| RFC Feature / Section | `EmailAddress` | `InternetAddress` (Jakarta Mail) | JMail |
| :--- | :--- | :--- | :--- |
| **`local-part@domain`** |  **Compliant** |  **Compliant** |  **Compliant** |
| **Quoted Local Parts** |  **Compliant & Canonical** (Strips quotes; re-escapes on output) |  **Compliant** | ⚠️ **Partially Compliant** (Fails to strip/unescape quotes) |
| **Unquoted Display Names** |  **Strictly Compliant** (Forbids "specials" to prevent injection) | ⚠️ **Lenient** (Allows invalid special characters) | ⚠️ **Lenient** (Allows invalid special characters) |
| **Group Addresses** (RFC 822) | 🚫 **Intentionally Omitted** (Obsolete, rejected for security) |  **Compliant** (Parses groups as `isGroup()`) | 🚫 **Intentionally Omitted** (Obsolete, rejected for security) |
| **RFC 2047 Encoded Words** | 🚫 **Intentionally Omitted** (Preserved raw to prevent spoofing) |  **Compliant** (Decodes automatically, posing security risks) | 🚫 **Intentionally Omitted** (Preserved raw to prevent spoofing) |
| **Comments & Domain Literals** | 🚫 **Intentionally Omitted** (Legacy comments/IP domains skipped) |  **Compliant** (Supports full legacy feature set) | 🚫 **Intentionally Omitted** (Legacy comments/IP domains skipped) |

---

## 3. Security Comparison & Hardening

| Attack Vector / Vulnerability | `EmailAddress` (Combinator) | `InternetAddress` (Jakarta Mail) | JMail |
| :--- | :--- | :--- | :--- |
| **Parsing Differentials** (Split Bug) |  **Immune** (Strictly rejects unconsumed trailing characters) | ❌ **Vulnerable** (Silently discards trailing parts like `<a@b>c@d`) |  **Immune** (Natively rejects) |
| **Display Name Spoofing** (Phishing) |  **Immune** (Preserves raw headers; forbids literal `@` or `<` in unquoted display names) | ❌ **Vulnerable** (Automatically decodes spoofed visual headers) | ❌ **Vulnerable** (Allows arbitrary addresses in display name fields) |
| **Group Syntax Abuse** (List splitting) |  **Immune** (obsolete RFC 822 group constructs are strictly rejected) | ❌ **Vulnerable** (Accepts group syntax, bypassing single-recipient controls) |  **Immune** (Natively rejects groups) |

---

## 4. Detailed API Functionality Comparison

### A. Bulk / Address List Parsing
* **`EmailAddress`**: Fully supports robust list parsing.
  - `parseAddressList(String)`: Parses a comma- or semicolon-delimited list.
  - `parseAddressList(String, Consumer<? super String>)`: Parses list leniently, returning valid elements while collecting invalid entries in a callback (ideal for logging or user feedback).
* **`InternetAddress`**: Supports list parsing via `InternetAddress.parse(String)`. However, it lacks graceful error accumulation (either throws `AddressException` on the whole string or returns silently truncated/corrupted addresses).
* **JMail**: No built-in list parsing capabilities. Validation must be performed individually by tokenizing the string manually beforehand.

### B. Lenient vs. Strict Parsing Modes
* **`EmailAddress`**: Follows **Strict-by-Default** architecture. All primary parsing endpoints (`of`, `PARSER`) enforce strict syntax. Lenient filtering is supported exclusively at the collection level (`parseAddressList`) to prevent single invalid entries from corrupting bulk inputs.
* **`InternetAddress`**: Supports both strict and lenient modes via `new InternetAddress(address, strict)`. However, even its "strict" mode remains highly vulnerable to parsing differentials and spoofing attacks.
* **JMail**: Implements rules-based strict validation without standard lenient parsing alternatives.

### C. Features Omitted in `EmailAddress` (Supported by Others)
To maintain compatibility with modern MTAs and guarantee safety, `EmailAddress` intentionally omits several obsolete features:
1. **RFC 822 Group Address Lists** (e.g., `group-name:addr1@b.com,addr2@c.com;`): Supported by Jakarta Mail. Omitted by `EmailAddress` to enforce a secure single-recipient mailbox paradigm.
2. **Nested Parenthetical Comments** (e.g., `john(comment)@example.com`): Supported by Jakarta Mail. Omitted by `EmailAddress` because comments are obsolete and increase downstream parsing complexity.
3. **Domain IP Literals** (e.g., `user@[192.168.1.1]`): Supported by Jakarta Mail and JMail. Omitted by `EmailAddress` to align with modern secure routing where IP-based email routing is practically obsolete.
4. **Dynamic MIME Header Decoding (RFC 2047)**: Supported by Jakarta Mail. Omitted by `EmailAddress` to completely prevent phishing and visual spoofing vectors.
