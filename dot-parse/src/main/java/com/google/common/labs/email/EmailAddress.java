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

import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.mu.util.Substring.all;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.net.IDN;
import java.util.List;
import java.util.Optional;

import com.google.common.labs.parse.Parser;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.StringFormat;

/**
 * Represents an email address according to RFC 5322, designed as a modern,
 * type-safe replacement for {@code javax.mail.InternetAddress}.
 *
 * <p>For example: <pre>{@code
 * EmailAddress emailAddress = EmailAddress.parse("john-doe@gmail.com");
 * }</pre>
 *
 * <h3>RFC 5322 Compliance Profile</h3>
 * <ul>
 *   <li><b>Address Specification (addr-spec):</b> Supports the standard
 *       {@code local-part@domain} format (RFC 5322 §3.4.1).</li>
 *   <li><b>Name-Addr:</b> Fully supports {@code "display-name" <addr-spec>}
 *       syntax (RFC 5322 §3.4).</li>
 *   <li><b>Quoted-Strings:</b> Complies with RFC 5322 §3.2.4, supporting
 *       backslash-escaped characters within double-quoted display names.</li>
 *   <li><b>Phrases (unquoted names):</b> Supports RFC 5322 "atoms" in
 *       display names, forbidding "specials", i.e. the {@code ()<>[]:;@\,"} characters,
 *       while allowing periods for real-world usability (e.g., "J.R.R. Tolkien").</li>
 *   <li><b>Folding White Space (FWS):</b> Supports optional whitespace
 *       between the display name and the angle-bracketed address.</li>
 * </ul>
 *
 * <h3>Comparison with {@code javax.mail.InternetAddress}</h3>
 * <table border="1">
 *   <tr><th>Feature</th><th>{@code InternetAddress}</th><th>{@code EmailAddress}</th></tr>
 *   <tr><td><b>Immutability</b></td><td>Mutable pojo</td><td>Immutable {@code record}</td></tr>
 *   <tr><td><b>DNS labels</b></td><td>Permissive (allows illegal hyphens)</td><td>Strict (RFC 1035/1123 63-char limit)</td></tr>
 *   <tr><td><b>Validation</b></td><td>Lazy – need to call {@code validate()}</td><td>Eager – {@code parse()} throws if invalid</td></tr>
 *   <tr><td><b>I18n</b></td><td>Often requires Encoded-Words</td><td>Native BMP support</td></tr>
 * </table>
 *
 * <h3>Intentionally Omitted Legacy Features</h3>
 * <p>To maintain compatibility with modern MTAs (Gmail, Outlook) and mitigate
 * header injection risks, the following RFC 5322 edge cases are excluded:</p>
 *
 * <ul>
 *   <li><b>Quoted Local-Parts:</b> (e.g., {@code "john doe"@domain}) - Deprecated in practice.
 *   <li><b>Comments (CFWS):</b> (e.g., {@code name(comment) <addr>}) - De facto obsolete.
 *   <li><b>Domain Literals:</b> (e.g., {@code user@[192.168.1.1]}) - IP routing is rarely supported.
 *   <li><b>Obsolete Syntax:</b> (RFC 5322 §4) - Legacy syntax like "quoted-pairs" in unquoted names.
 * </ul>
 *
 * @since 9.9.4
 */
public record EmailAddress(Optional<String> displayName, String localPart, String domain) {
  private static final StringFormat WITH_DISPLAY_NAME = new StringFormat("\"{name}\" <{address}>");
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
  public EmailAddress{
    checkArgument(!localPart.isEmpty(), "local-part cannot be empty");
    checkArgument(!domain.isEmpty(), "domain cannot be empty");
    requireNonNull(displayName);
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
    String idnValidated = IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED);
    return new EmailAddress(Optional.empty(), localPart, domain);
  }

  /** Returns the {@code addr-spec}, in the form of {@code user@mycompany.com}. */
  public String address() {
    return localPart + '@' + domain;
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

  /** Parses {@code address} and throws {@link Parser.ParseException} if failed. */
  public static EmailAddress parse(String address) {
    return PARSER.parseSkipping(Character::isWhitespace, address);
  }

  /**
   * Parses {@code addressList} according to RFC 5322 and returns an immutable list of {@link
   * EmailAddress}.
   *
   * <p>Both colon ({@code ,}) and semicolon ({@code ;}) are supported as delimiters, with
   * whitespaces ignored. Trailing delimiters are allowed.
   *
   * <p>Empty input will result in an empty list being returned.
   */
  public static List<EmailAddress> parseAddressList(String addressList) {
    Parser<?> delimiter = Parser.one(CharPredicate.anyOf(",;"), "delimiter").atLeastOnce(counting());
    return PARSER
        .zeroOrMoreDelimitedBy(delimiter, toUnmodifiableList())
        .followedBy(delimiter.orElse(null))
        .parseSkipping(Character::isWhitespace, addressList);
  }

  private static Parser<EmailAddress> makeParser() {
    CharPredicate letterOrDigit = Character::isLetterOrDigit;
    CharPredicate isoControl = Character::isISOControl;
    Parser<String> localPart = consecutive(
        letterOrDigit.or(CharPredicate.anyOf("!#$%&'*+-/=?^_`{|}~.")).precomputeForAscii(),
        "local part");
    Parser<String> domain =
        consecutive(letterOrDigit.or('-').precomputeForAscii(), "domain label chars")
            .suchThat(s -> s.charAt(0) != '-' && s.charAt(s.length() - 1) != '-', "domain label")
            .atLeastOnceDelimitedBy(".", counting())  // counting is the cheapest collector
            .source();                                // source() is faster than using joining(".")
    Parser<EmailAddress> address =
        sequence(localPart, literally(string("@").then(domain)), EmailAddress::of);
    Parser<String> quotedDisplayName = Parser.quotedByWithEscapes(
        '"', '"', chars(1).suchThat(isoControl::matchesNoneOf, "escapable char"));
    Parser<String> unquotedDisplayName = consecutive(
        CharPredicate.anyOf("()<>[]:;@\\,\"").or(isoControl).not().precomputeForAscii(),
        "unquoted display name");
    Parser<EmailAddress> bracketedAddress = address.between("<", ">");
    Parser<String> displayName =
        Parser.anyOf(quotedDisplayName, unquotedDisplayName.map(String::trim));
    return Parser.anyOf(
        address,
        bracketedAddress,
        sequence(displayName, bracketedAddress, (name, addr) -> addr.withDisplayName(name)));
  }

  private static String escape(String name) {
    return all(CharPredicate.anyOf("\"\\")).replaceAllFrom(name, c -> "\\" + c);
  }

  @FormatMethod
  private static void checkArgument(
      boolean condition, @FormatString String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }
}
