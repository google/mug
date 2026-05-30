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
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.mu.util.CharPredicate.anyOf;
import static com.google.mu.util.Substring.all;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.filtering;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.net.IDN;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collector;

import com.google.common.labs.parse.Parser;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.StringFormat;

/**
 * Represents an email address according to RFC 5322, designed as a modern,
 * light-weight alternative API to {@code javax.mail.InternetAddress}.
 *
 * <p>For example: <pre>{@code
 * EmailAddress address = EmailAddress.parse("J.R.R. Tolkien <tolkien@lotr.org>");
 * // address.displayName() => "J.R.R. Tolkien"
 * // address.localPart()) => "tolkien"
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
 *       display names, forbidding "specials", i.e. the {@code ()<>[]:;@\,"} characters,
 *       while allowing periods for real-world usability (e.g., "J.R.R. Tolkien").</li>
 *   <li><b>Folding White Space (FWS):</b> Supports optional whitespace
 *       between the display name and the angle-bracketed address.</li>
 *   <li><b>Address-List:</b> Supports semicolon as separators; allows real-world
 *       variations like trailing commas, two-commas-in-a-row etc.</li>
 * </ul>
 *
 * <h3>Comparison with {@code javax.mail.InternetAddress}</h3>
 * <table border="1">
 *   <tr><th>Feature</th><th>{@code InternetAddress}</th><th>{@code EmailAddress}</th></tr>
 *   <tr><td><b>Immutability</b></td><td>Mutable pojo</td><td>Immutable {@code record}</td></tr>
 *   <tr><td><b>DNS labels</b></td><td>Permissive (allows illegal hyphens)</td><td>Rejects {@code wrong-@.com}</td></tr>
 *   <tr><td><b>I18n</b></td><td>Often requires Encoded-Words</td><td>Native BMP support</td></tr>
 * </table>
 *
 * <h3>Intentionally Omitted Legacy Features</h3>
 * <p>To maintain compatibility with modern MTAs (Gmail, Outlook) and mitigate
 * header injection risks, the following RFC 5322 edge cases are excluded:</p>
 *
 * <ul>
 *   <li><b>Comments (CFWS):</b> (e.g., {@code name(comment) <addr>}) - De facto obsolete.
 *   <li><b>Domain Literals:</b> (e.g., {@code user@[192.168.1.1]}) - IP routing is rarely supported.
 * </ul>
 *
 * @param displayName the {@code "J.R.R. Tolkien"} from {@code J.R.R. Tolkien <tolkien@lotr.org>}
 * @param localPart the {@code "tolkien"} from {@code J.R.R. Tolkien <tolkien@lotr.org>}
 * @param domain the {@code "lotr.org"} from {@code J.R.R. Tolkien <tolkien@lotr.org>}
 * @since 9.9.4
 */
@Immutable
public record EmailAddress(Optional<String> displayName, String localPart, String domain) {
  private static final StringFormat WITH_DISPLAY_NAME = new StringFormat("\"{name}\" <{address}>");
  private static final CharPredicate ISO_CONTROL = Character::isISOControl;
  private static final CharPredicate LETTER_OR_DIGIT = Character::isLetterOrDigit;
  private static final CharPredicate ATEXT =
      LETTER_OR_DIGIT.or("!#$%&'*+-/=?^_`{|}~").precomputeForAscii();
  private static final CharPredicate DOMAIN_CHARS = LETTER_OR_DIGIT.or("-.").precomputeForAscii();
  private static final CharPredicate ADDRESS_LIST_SEPARATOR_CHAR = anyOf(",;").precomputeForAscii();
  private static final Parser<?> ADDRESS_LIST_DELIMITER =
      Parser.one(ADDRESS_LIST_SEPARATOR_CHAR, "delimiter").atLeastOnce(counting());

  /**
   * The parser for email address, according to RFC 5322, and supporting BMP characters.
   *
   * <p>Prefer using the {@link #parse} convenience method. This constant is to be used for
   * composition, for example to parse a list of email addresses: <pre>{@code
   * EmailAddress.PARSER.skipping(whitespace()).parseToStream(emailAddresses).toList();
   * }</pre>
   */
  public static final Parser<EmailAddress> PARSER = makeParser();

  /**
   * Prefer using the {@link #of} factory method. You can call {@link #withDisplayName}
   * to optionally attach a display name.
   */
  public EmailAddress {
    requireNonNull(displayName);
    checkArgument(!localPart.isEmpty(), "local-part cannot be empty");
    checkArgument(!domain.isEmpty(), "domain cannot be empty");
    checkArgument(
        DOMAIN_CHARS.matchesAllOf(domain), "domain '%s' contains invalid characters", domain);
    all('.').split(domain).forEach(label ->
        checkArgument(
            !label.startsWith("-") && !label.endsWith("-"),
            "domain label '%s' must not start or end with a hyphen",
            label));
    IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED);
  }

  /** Returns an otherwise equivalent {@link EmailAddress} but with {@code displayName}. */
  public EmailAddress withDisplayName(String displayName) {
    return new EmailAddress(Optional.ofNullable(displayName), localPart, domain);
  }

  /** For example: {@code EmailAddress.of("user", "mycompany.com")}. */
  public static EmailAddress of(String localPart, String domain) {
    checkArgument(
        localPart.length() + domain.length() + 1 <= 254,
        "<%s@%s> must be <= 254 chars", localPart, domain);
    return new EmailAddress(Optional.empty(), localPart, domain);
  }

  /**
   * Parses {@code address} and throws {@link Parser.ParseException} if failed.
   *
   * @since 9.9.8
   */
  public static EmailAddress of(String address) {
    return PARSER.parseSkipping(Character::isWhitespace, address);
  }

  /** Returns the {@code addr-spec}, in the form of {@code user@mycompany.com}. */
  public String address() {
    return showLocalPart() + '@' + domain;
  }

  private String showLocalPart() {
    return localPart.isEmpty()
        || localPart.startsWith(".")
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
        .map(name -> WITH_DISPLAY_NAME.format(escape(name), address()))
        .orElseGet(this::address);
  }

  /** @deprecated Use {@link #of(String)} instead */
  @Deprecated
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
        .parseSkipping(Character::isWhitespace, addressList);
  }

  /**
   * Parsers {@code addressList} according to RFC 5322 and returns an immutable list of {@link
   * EmailAddress}, with invalid entries passed to the {@code invalid} consumer.
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
      String addressList, Consumer<? super String> invalid) {
    Parser<?> significant = Parser.one(ADDRESS_LIST_SEPARATOR_CHAR.not(), "significant char");
    return anyOf(
            PARSER.notFollowedBy(significant, "non-separator"),  // don't extract a@b from a@b@c
            consecutive(ADDRESS_LIST_SEPARATOR_CHAR.or(Character::isWhitespace).not(), "invalid"))
        .zeroOrMoreDelimitedBy(ADDRESS_LIST_DELIMITER, onlyEmailAddresses(invalid))
        .followedBy(ADDRESS_LIST_DELIMITER.orElse(null))
        .parseSkipping(Character::isWhitespace, addressList);
  }

  private static Parser<EmailAddress> makeParser() {
    Parser<String> quoted = quotedByWithEscapes(
        '"', '"', chars(1).suchThat(ISO_CONTROL::matchesNoneOf, "escapable char"));
    Parser<String> localPart = anyOf(
        quoted,
        consecutive(ATEXT, "local part").atLeastOnceDelimitedBy(".", joining(".")));
    Parser<String> domain = consecutive(DOMAIN_CHARS, "domain label chars");
    Parser<EmailAddress> address =
        literally(sequence(localPart, string("@").then(domain), EmailAddress::of));
    Parser<String> unquotedDisplayName = consecutive(
        ISO_CONTROL.or("()<>[]:;@\\,\"").not().precomputeForAscii(), "unquoted display name");
    Parser<EmailAddress> bracketedAddress = address.between("<", ">");
    Parser<String> displayName = anyOf(quoted, unquotedDisplayName.map(String::trim));
    return anyOf(
        bracketedAddress,
        address,
        sequence(displayName, bracketedAddress, (name, addr) -> addr.withDisplayName(name)));
  }

  private static Collector<Object, ?, List<EmailAddress>> onlyEmailAddresses(
      Consumer<? super String> invalid) {
    requireNonNull(invalid);
    return filtering(
        e -> {
          if (e instanceof String s) invalid.accept(s);
          return e instanceof EmailAddress;
        },
        mapping(e -> (EmailAddress) e, toUnmodifiableList()));

  }

  private static String escape(String name) {
    return all(anyOf("\"\\")).replaceAllFrom(name, c -> "\\" + c);
  }

  @FormatMethod
  private static void checkArgument(
      boolean condition, @FormatString String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }
}
