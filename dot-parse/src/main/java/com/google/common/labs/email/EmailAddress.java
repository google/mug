/*****************************************************************************
 * Copyright (C) google.com                                                  *
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.common.labs.email;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.mu.util.CharPredicate.anyOf;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.CharPredicate.range;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.all;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.filtering;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.net.IDN;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collector;

import com.google.common.labs.parse.Parser;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.InlineMe;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;

/**
 * Represents a strictly validated email address according to RFC 5322, designed as a modern,
 * light-weight alternative API to {@code javax.mail.InternetAddress}.
 *
 * <p>See <a href="https://github.com/google/mug/tree/master/dot-parse/src/main/java/com/google/common/labs/email/README.md">
 * README</a> for a comprehensive architectural, security,
 * and performance comparison against Jakarta Mail and JMail.
 *
 * <p>For example: <pre>{@code
 * EmailAddress address = EmailAddress.of("J.R.R. Tolkien <tolkien@lotr.org>");
 * // address.displayName() => "J.R.R. Tolkien"
 * // address.localPart() => "tolkien"
 * // address.domain() => "lotr.org"
 * }</pre>
 *
 * <h3>RFC 5322 Compliance Profile</h3>
 * <ul>
 *   <li><b>Address Specification (addr-spec):</b> Supports the standard
 *       {@code local-part@domain} format (RFC 5322 §3.4.1).</li>
 *   <li><b>Quoted Local-Parts:</b> Fully supports double-quoted local-parts
 *       (RFC 5322 §3.4.1), with backslash-escaped characters. In order to provide a clean,
 *       canonical representation, enclosing quotes are automatically stripped and backslash
 *       escapes are unescaped when stored in the {@code localPart} property (e.g.,
 *       {@code "john doe" -> "john doe"}). While serializing via {@link #address()} or
 *       {@link #toString()}, appropriate quotes and escapes are dynamically and safely
 *       re-introduced if necessary to maintain syntactical validity under RFC 5322 (since v10.3).</li>
 *   <li><b>Name-Addr:</b> Fully supports {@code "display-name" <addr-spec>}
 *       syntax (RFC 5322 §3.4).</li>
 *   <li><b>Quoted-Strings:</b> Complies with RFC 5322 §3.2.4, supporting
 *       backslash-escaped characters within double-quoted display names.</li>
 *   <li><b>Phrases (unquoted names):</b> Supports RFC 5322 "atoms" in
 *       display names, forbidding specials, i.e. the {@code <}, {@code >}, {@code ;},
 *       {@code \}, and {@code "} characters, while allowing periods, commas, colons,
 *       brackets, and parentheses for real-world usability (e.g., "[JIRA] (PROJ-123)").</li>
 *   <li><b>Folding White Space (FWS):</b> Supports optional whitespace
 *       between the display name and the angle-bracketed address.</li>
 *   <li><b>Address-List:</b> Supports semicolon as separators; allows real-world
 *       variations like trailing commas, two-commas-in-a-row etc.</li>
 * </ul>
 *
 * <h3>Strict Email Address Validation</h3>
 * <p>Unlike legacy {@code javax.mail.InternetAddress} from Java/Jakarta Mail, this class is
 * backed by a strict email address parser and a validated domain model. This improves security,
 * preventing common email parsing exploits such as:
 * <ul>
 *   <li><b>Email Parsing Differentials:</b> Inputs like
 *       {@code <legitimate@trusted.com>attacker@evil.com} are strictly rejected instead of being
 *       silently truncated or partially parsed, preventing privilege escalation or routing
 *       bypasses.</li>
 *   <li><b>Display Name Spoofing (RFC 2047):</b> Display names are preserved literally
 *       in their raw/encoded form by default (preventing visual spoofing/phishing side-channels).
 *       Safe opt-in decoding is provided explicitly via {@link #unicodeDisplayName()}.</li>
 *   <li><b>Group Addresses and Multi-@ Local-Parts:</b> Group address syntaxes and unquoted
 *       multi-@ injections are strictly disallowed.</li>
 * </ul>
 *
 * <h3>Comparison with {@code javax.mail.InternetAddress} (Java / Jakarta Mail)</h3>
 * <table border="1">
 *   <tr>
 *     <th>Feature/Security Aspect</th>
 *     <th>{@code InternetAddress}</th>
 *     <th>{@code EmailAddress}</th>
 *   </tr>
 *   <tr>
 *     <td><b>Immutability</b></td>
 *     <td>Mutable POJO</td>
 *     <td>Immutable {@code record}</td>
 *   </tr>
 *   <tr>
 *     <td><b>DNS labels</b></td>
 *     <td>Permissive (allows illegal hyphens)</td>
 *     <td>Rejects invalid/misplaced hyphens</td>
 *   </tr>
 *   <tr>
 *     <td><b>I18n</b></td>
 *     <td>Often requires Encoded-Words</td>
 *     <td>Native BMP support</td>
 *   </tr>
 *   <tr>
 *     <td><b>Parsing Differential</b></td>
 *     <td>Vulnerable (silently discards trailing parts like {@code <a@b.com>c@d.com})</td>
 *     <td>Secure (strictly rejects trailing unconsumed text via parser exceptions)</td>
 *   </tr>
 *   <tr>
 *     <td><b>Group Addresses</b></td>
 *     <td>Permissive (parses RFC-822 group syntax implicitly)</td>
 *     <td>Strictly rejected (enforces single address structure)</td>
 *   </tr>
 *   <tr>
 *     <td><b>RFC 2047 Encoded Words</b></td>
 *     <td>Automatic or permissive (decodes or accepts encoded words in display name, local-part, or domain, risking address spoofing and routing hijacking)</td>
 *     <td>Defensively rejected in local-part and domain. Supported in display name via safe, explicit opt-in {@link #unicodeDisplayName()}</td>
 *   </tr>
 *   <tr>
 *     <td><b>Multi-@ Local-Parts</b></td>
 *     <td>Inconsistent (allows unquoted {@code @} in local-part)</td>
 *     <td>Strictly rejected (unquoted {@code @} is forbidden)</td>
 *   </tr>
 * </table>
 *
 * <h3>Intentionally Omitted Legacy Features</h3>
 * <p>To maintain compatibility with modern MTAs (Gmail, Outlook) and mitigate
 * header injection risks, the following RFC 5322 edge cases are excluded:</p>
 *
 * <ul>
 *   <li><b>Comments (CFWS):</b> (e.g., {@code name(comment) <addr>}) - De facto obsolete.
 *   <li><b>Domain Literals:</b> (e.g., {@code user@[192.168.1.1]}) - IP routing is rarely supported.
 *   <li><b>RFC 2047 Encoded Words in address fields:</b> (e.g., {@code =?UTF-8?Q?Admin?=@domain.com})
 *      - Strictly rejected to prevent downstream mailer decoding exploits.
 * </ul>
 *
 * @since 9.9.4
 */
@Immutable
@CheckReturnValue
public final class EmailAddress {
  private static final StringFormat WITH_QUOTED_DISPLAY_NAME =
      new StringFormat("\"{name}\" <{address}>");
  private static final StringFormat WITH_UNQUOTED_DISPLAY_NAME =
      new StringFormat("{name} <{address}>");
  private static final StringFormat.Template<IllegalArgumentException> DOTLESS_DOMAIN_BANNED =
      StringFormat.to(
          IllegalArgumentException::new, "domain must contain at least one dot: {domain}");
  private static final StringFormat ENCODED_WORD =
      new StringFormat("{...}=?{charset}?{encoding}?{text}?={...}");
  private static final CharPredicate NON_DIGIT = range('0', '9').not();
  private static final CharPredicate INLINE_WHITESPACE = anyOf(" \t");
  private static final CharPredicate DANGEROUS_WHITESPACE =
      anyOf("\u2028\u2029\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069");
  private static final CharPredicate DANGEROUS =
      DANGEROUS_WHITESPACE.or(Character::isISOControl).precomputeForAscii();
  private static final CharPredicate SAFE_WHITESPACE =
      DANGEROUS_WHITESPACE.not().and(Character::isWhitespace).precomputeForAscii();
  private static final Substring.Pattern TLD = after(last('.'));

  // While most letters and digits are supplementary chars, using it is strictly better than
  // [a-zA-Z0-9] because it natively supports internationalized BMP characters (for example,
  // successfully parsing valid non-ASCII local-parts like "müller" or "björn",
  // which [a-zA-Z0-9] rejects).
  //
  // Email Address Internationalization (EAI) identifiers (RFC 6530, 6531, 6532) are
  // standardly restricted to the BMP plane, and excluding supplementary characters (like emojis or
  // historical symbols) is an intentional, beneficial security boundary.
  //
  // It is also vastly safer than using the RFC 6532 range (UTF8-non-ascii: [\u0080-\u10FFFF]),
  // which is a massive superset containing dangerous formatting controls, Bidirectional overrides
  // (e.g., LRE, RLE, RLO, LRO U+202A-202E / U+2066-2069 that trick visual paths in logging/UIs),
  // and line/paragraph separators (U+2028-2029 that can trigger MTA header injections).
  // javaLetterOrDigit() strictly limits the character class to printable classified letters and
  // digits, successfully neutralizing these injection vectors.
  private static final CharPredicate LETTER_OR_DIGIT = Character::isLetterOrDigit;
  private static final CharPredicate ATEXT =
      LETTER_OR_DIGIT.or("!#$%&'*+-/=?^_`{|}~").precomputeForAscii();
  private static final CharPredicate I18N_DOMAIN_LABEL_CHARS = LETTER_OR_DIGIT.or('-').precomputeForAscii();
  private static final Parser<?> ADDRESS_LIST_DELIMITER = one("[,;]").atLeastOnce(counting());

  private static final Parser<String> QUOTED =
      quotedByWithEscapes('"', '"', chars(1))
          .suchThat(DANGEROUS::matchesNoneOf, "quoted string without control or formatting chars");
  private static final Parser<String> LOCAL_PART =
      anyOf(consecutive(ATEXT, "local part").atLeastOnceDelimitedBy(".", counting()).source(), QUOTED)
          .suchThat(local -> !ENCODED_WORD.matches(local), "no encoded words");
  private static final Parser<String> DOMAIN =
      consecutive(I18N_DOMAIN_LABEL_CHARS, "domain label chars")
          .suchThat(label -> !label.startsWith("-") && !label.endsWith("-"), "domain without -. or .-")
          .atLeastOnceDelimitedBy(".", counting())
          .suchThat(count -> count > 1, "domain name with at least one dot")
          .source()
          .suchThat(d -> NON_DIGIT.matchesAnyOf(topLevelDomainOrThrow(d)), "domain with valid TLD");;
  private static final Parser<AddrSpecAlike> ADDR_SPEC_ALIKE =
      literally(sequence(LOCAL_PART.followedBy("@"), DOMAIN, AddrSpecAlike::new));

  /**
   * Parser that strictly matches only the RFC 5322 {@code addr-spec} (i.e., {@code
   * local-part@domain}), rejecting display names and angle brackets.
   *
   * @since 10.4
   */
  public static final Parser<EmailAddress> ADDR_SPEC_PARSER =
      ADDR_SPEC_ALIKE.map(AddrSpecAlike::toEmailAddress);

  /**
   * The parser for email address, according to RFC 5322, and supporting BMP characters.
   *
   * <p>Prefer using the {@link #of} convenience method. This constant is to be used for
   * composition, for example to parse a group addresses: <pre>{@code
   * Parser.sequence(
   *     Parser.word().followedBy(":"),
   *     EmailAddress.PARSER.zeroOrMoreDelimitedBy(",").followedBy(";"),
   *     GroupAddress::new);
   * }</pre>
   */
  public static final Parser<EmailAddress> PARSER = makeParser();

  private final String localPart;
  private final String domain;
  private final Optional<String> displayName;

  private EmailAddress(String localPart, String domain, Optional<String> displayName) {
    checkArgument(
        localPart.length() + domain.length() + 1 <= 254,
        "<%s@%s> must be <= 254 chars", localPart, domain);
    this.localPart = localPart;
    this.domain = domain;
    this.displayName = displayName.filter(n -> !n.isBlank());
  }

  /** Returns an otherwise equivalent {@link EmailAddress} but with {@code displayName}. */
  public EmailAddress withDisplayName(String displayName) {
    return new EmailAddress(
        localPart, domain,
        Optional.ofNullable(displayName).map(EmailAddress::checkDisplayName));
  }

  /**
   * Returns the display name in Unicode (with any RFC 2047 encoded-words decoded),
   * or {@code Optional.empty()} if no display name is present.
   *
   * <p>To prevent visual spoofing and phishing attacks in standard rendering, the main
   * {@link #displayName()} component is kept in its raw transport-safe encoded form. This method
   * provides a safe, explicit opt-in to decode the display name.
   *
   * <p>Only standard, ASCII-compatible charsets (specifically UTF-8, ISO-8859-1, and US-ASCII)
   * are decoded, guarding against null-byte injection exploits in downstream systems.
   * Unsupported charsets or syntactically malformed encoded-words are safely left in their
   * encoded form.
   *
   * @since 10.3.1
   */
  public Optional<String> unicodeDisplayName() {
    return displayName.map(EncodedWord::decodeRfc2047);
  }

  /** For example: {@code EmailAddress.of("user", "mycompany.com")}. */
  public static EmailAddress of(String localPart, String domain) {
    return new EmailAddress(
        checkLocalPart(localPart), DOMAIN.parse(canonicalizeDomain(domain)), Optional.empty());
  }

  /**
   * Parses {@code address} and throws {@link Parser.ParseException} if failed.
   *
   * @since 9.9.8
   */
  public static EmailAddress of(String address) {
    return PARSER.parseSkipping(SAFE_WHITESPACE, address);
  }

  /**
   * Returns the local-part of the email address, e.g. the {@code "tolkien"} from
   * {@code J.R.R. Tolkien <tolkien@lotr.org>}.
   */
  public String localPart() {
    return localPart;
  }

  /**
   * Returns the domain of the email address, e.g. the {@code "lotr.org"} from
   * {@code J.R.R. Tolkien <tolkien@lotr.org>}.
   *
   * <p>Note that for internationalized domain, this is the punycode in ASCII. You can
   * use {@link #unicodeDomain} to access the non-encoded domain. {@link #hasI18nDomain}
   * can be used to check if the domain is internationalized.
   */
  public String domain() {
    return domain;
  }

  /**
   * Returns the display name of the email address (e.g. the {@code "J.R.R. Tolkien"} from
   * {@code J.R.R. Tolkien <tolkien@lotr.org>}), or {@code Optional.empty()} if no display name is present.
   *
   * <p>Note that this holds the raw transport-safe format (including any RFC 2047 encoded-words).
   * You can use {@link #unicodeDisplayName} to access the decoded Unicode representation.
   */
  public Optional<String> displayName() {
    return displayName;
  }

  /** Returns the {@code addr-spec}, in the form of {@code user@mycompany.com}. */
  public String address() {
    return showLocalPart() + '@' + domain;
  }

  /**
   * Returns the "user" part of the local-part before the first {@code +} separator,
   * according to RFC 5233 subaddressing. If no {@code +} is present, the full
   * {@code localPart} is returned.
   *
   * @since 10.3
   */
  public String user() {
    return first('+').toEnd().removeFrom(localPart);
  }

  /**
   * Returns the "alias" (or "detail") part of the local-part after the first {@code +} separator,
   * according to RFC 5233 subaddressing. If no {@code +} is present, or if the {@code +} is the
   * last character of the local-part, {@code Optional.empty()} is returned.
   *
   * @since 10.3
   */
  public Optional<String> alias() {
    return after(first('+')).from(localPart).filter(a -> !a.isEmpty());
  }

  /**
   * Returns the domain in Unicode.
   *
   * @since 10.3
   */
  public String unicodeDomain() {
    return IDN.toUnicode(domain, IDN.ALLOW_UNASSIGNED);
  }

  /**
   * Returns true if this address has an internationalized domain, in which case {@link #domain()}
   * will be puny-coded.
   *
   * @since 10.3
   */
  public boolean hasI18nDomain() {
    return all('.').split(domain).anyMatch(label -> label.startsWith("xn--"));
  }

  private String showLocalPart() {
    return localPart.startsWith(".")
        || localPart.endsWith(".")
        || localPart.contains("..")
        || !ATEXT.or('.').matchesAllOf(localPart)
      ? '"' + escape(localPart) + '"'
      : localPart;
  }

  /**
   * Returns the full email address, in the form of {@code local-part@domain} or
   * {@code "display name" <local-part@domain>}. Backslashes and double quotes in
   * the display name are auto-escaped.
   */
  @Override public String toString() {
    return displayName
        .map(name -> requiresQuoting(name)
            ? WITH_QUOTED_DISPLAY_NAME.format(escape(name), address())
            : WITH_UNQUOTED_DISPLAY_NAME.format(name, address()))
        .orElseGet(this::address);
  }

  /** @deprecated Use {@link #of(String)} instead */
  @Deprecated
  @InlineMe(replacement = "EmailAddress.of(address)", imports = "com.google.common.labs.email.EmailAddress")
  public static EmailAddress parse(String address) {
    return of(address);
  }

  /**
   * Parses {@code addressList} according to RFC 5322 and returns an immutable list of {@link
   * EmailAddress}.
   *
   * <p>Both comma ({@code ,}) and semicolon ({@code ;}) are supported as delimiters, with
   * whitespaces ignored. Trailing delimiters are allowed.
   *
   * <p>Empty input will result in an empty list being returned.
   *
   * <p>Note that if your address list may contain invalid entries, and you'd want to ignore them
   * instead of failing, use {@link #parseAddressList(String, Consumer)}.
   *
   * @throws Parser.ParseException if {@code addressList} is invalid
   */
  public static List<EmailAddress> parseAddressList(String addressList) {
    return PARSER
        .zeroOrMoreDelimitedBy(ADDRESS_LIST_DELIMITER, toUnmodifiableList())
        .followedBy(ADDRESS_LIST_DELIMITER.orElse(null))
        .parseSkipping(SAFE_WHITESPACE, addressList);
  }

  /**
   * Parses {@code addressList} according to RFC 5322 and returns an immutable list of {@link
   * EmailAddress}, with invalid entries passed to the {@code ifInvalid} consumer.
   *
   * <p>For example, <pre>{@code
   * List<EmailAddress> addresses = parseAddressList(inputAddressList, logger::log);
   * }</pre>
   *
   * <p>Both comma ({@code ,}) and semicolon ({@code ;}) are supported as delimiters, with
   * whitespaces ignored. Trailing delimiters are allowed.
   *
   * <p>Empty input will result in an empty list being returned.
   *
   * @since 10.3
   */
  public static List<EmailAddress> parseAddressList(
      String addressList, Consumer<? super String> ifInvalid) {
    return anyOf(
            PARSER.notFollowedBy(one("[^,;]"), "non-separator"),  // don't extract a@b from a@b@c
            consecutive("[^,;]").map(String::trim))
        .zeroOrMoreDelimitedBy(ADDRESS_LIST_DELIMITER, onlyEmailAddresses(ifInvalid))
        .followedBy(ADDRESS_LIST_DELIMITER.orElse(null))
        .parseSkipping(SAFE_WHITESPACE, addressList);
  }

  @Override public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    return obj instanceof EmailAddress that
        && localPart.equals(that.localPart)
        && domain.equals(that.domain)
        && displayName.equals(that.displayName);
  }

  @Override public int hashCode() {
    return Objects.hash(localPart, domain, displayName);
  }

  private static Parser<EmailAddress> makeParser() {
    var unquotedDisplayNameChars = DANGEROUS.or("<>;\\\"").not().precomputeForAscii();
    Parser<String> unquotedAtom =
        consecutive(unquotedDisplayNameChars, "unquoted display name")
            .suchThat(n -> !(n.contains(",") && n.contains("@")), "unambiguous display name");
    Parser<AddrSpecAlike> bracketedAddress = ADDR_SPEC_ALIKE.between("<", ">");
    Parser<String> displayName =
        anyOf(unquotedAtom.map(String::trim), QUOTED).atLeastOnce(joining(" "));
    // a standalone address not followed by a display name char.
    // If it's followed by a comma or semicolon, we still allow it because the address
    // may be in a list.
    // If it's not in a list, the left-over comma or semicolon won't match anything
    // because we don't allow both ',' and '@' co-existing in display name anyways.
    Parser<AddrSpecAlike> looksLikeAddrSpec =  ADDR_SPEC_ALIKE.notFollowedBy(
        one(unquotedDisplayNameChars.or('"').and(noneOf(",;")), "display name char"),
        "part of display name");
    return anyOf(
        sequence(
            // optimization so that for the common case of user@company.com, we don't have to
            // backtrack to the sequence(displayName, bracketedAddress) rule.
            looksLikeAddrSpec, bracketedAddress.orElse(null),
            (addrSpecOrDisplayName, bracketedOrNull) ->
                bracketedOrNull == null
                    ? addrSpecOrDisplayName.toEmailAddress()
                    : bracketedOrNull.toEmailAddressWithDisplayName(addrSpecOrDisplayName.toString())),
        sequence(
            displayName, bracketedAddress,
            (name, addr) -> addr.toEmailAddressWithDisplayName(name)),
        bracketedAddress.map(AddrSpecAlike::toEmailAddress),
        ADDR_SPEC_PARSER); // fall back when PARSER is combined with other parsers
  }

  private static String topLevelDomainOrThrow(String domain) {
    return TLD.from(domain).orElseThrow(() -> DOTLESS_DOMAIN_BANNED.with(domain));
  }

  private static Collector<Object, ?, List<EmailAddress>> onlyEmailAddresses(
      Consumer<? super String> ifInvalid) {
    requireNonNull(ifInvalid);
    return filtering(
        e -> {
          if (e instanceof String s) ifInvalid.accept(s);
          return e instanceof EmailAddress;
        },
        mapping(e -> (EmailAddress) e, toUnmodifiableList()));
  }

  private static String escape(String name) {
    return all(anyOf("\"\\")).replaceAllFrom(name, c -> "\\" + c);
  }

  private static boolean requiresQuoting(String name) {
    return INLINE_WHITESPACE.isPrefixOf(name)
        || INLINE_WHITESPACE.isSuffixOf(name)
        || !ATEXT.or(INLINE_WHITESPACE).matchesAllOf(name)
        || Substring.consecutive(INLINE_WHITESPACE)
            .repeatedly()
            .match(name)
            .anyMatch(ws -> ws.length() > 1);
  }

  private static String checkLocalPart(String localPart) {
    checkArgument(!localPart.isEmpty(), "local-part cannot be empty");
    checkArgument(
        !ENCODED_WORD.matches(localPart), "local-part doesn't allow encoded word (%s)", localPart);
    checkArgument(
        DANGEROUS.matchesNoneOf(localPart),
        "local-part must not contain control or formatting characters");
    return localPart;
  }

  private static String checkDisplayName(String displayName) {
    checkArgument(
        DANGEROUS.matchesNoneOf(displayName),
        "display name must not contain control or formatting characters");
    return displayName;
  }

  private static String canonicalizeDomain(String domain) {
    return IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
  }

  @FormatMethod
  private static void checkArgument(
      boolean condition, @FormatString String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  private record AddrSpecAlike(String localPart, String domain) {
    EmailAddress toEmailAddress() {
      return new EmailAddress(localPart, canonicalizeDomain(domain), Optional.empty());
    }

    EmailAddress toEmailAddressWithDisplayName(String displayName) {
      return new EmailAddress(localPart, canonicalizeDomain(domain), Optional.of(displayName));
    }

    @Override
    public String toString() {
      return localPart + '@' + domain;
    }
  }
}
