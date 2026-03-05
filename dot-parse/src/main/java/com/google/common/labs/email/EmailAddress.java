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
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.mu.util.Substring.all;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.util.Optional;

import com.google.common.labs.parse.Parser;
import com.google.mu.util.CharPredicate;

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
 *   <li><b>Name-Addr:</b> Fully supports {@code [display-name] <addr-spec>}
 *       syntax (RFC 5322 §3.4).</li>
 *   <li><b>Quoted-Strings:</b> Complies with RFC 5322 §3.2.4, supporting
 *       backslash-escaped characters within double-quoted display names.</li>
 *   <li><b>Phrases (Unquoted Names):</b> Supports RFC 5322 "atoms" in
 *       display names, correctly forbidding "specials" {@code ()<>[]:;@\,."}
 *       while allowing periods for real-world usability (e.g., "J.R.R. Tolkien").</li>
 *   <li><b>Folding White Space (FWS):</b> Supports optional whitespace
 *       between the display name and the angle-bracketed address.</li>
 * </ul>
 *
 * <h3>Comparison with {@code javax.mail.InternetAddress}</h3>
 * <table border="1">
 *   <tr><th>Feature</th><th>{@code InternetAddress}</th><th>{@code EmailAddress}</th></tr>
 *   <tr><td><b>Immutability</b></td><td>Mutable POJO</td><td>Immutable {@code record}</td></tr>
 *   <tr><td><b>DNS labels</b></td><td>Permissive (allows illegal hyphens)</td><td>Strict (RFC 1035/1123 63-char limit)</td></tr>
 *   <tr><td><b>Null safety</b></td><td>{@code getPersonal()} returns null</td><td>{@code Optional<String> displayName}</td></tr>
 *   <tr><td><b>Validation</b></td><td>Lazy/Permissive</td><td>Fail-fast via {@code Parser.ParseException}</td></tr>
 *   <tr><td><b>I18n</b></td><td>Often requires Encoded-Words</td><td>Native BMP support in names</td></tr>
 * </table>
 *
 * <h3>Intentionally Omitted Legacy Features</h3>
 * <p>To maintain compatibility with modern MTAs (Gmail, Outlook) and mitigate
 * header injection risks, the following RFC 5322 edge cases are excluded:</p>
 * <ul>
 *   <li><b>Quoted Local-Parts:</b> (e.g., {@code "john doe"@domain}) - Deprecated in practice.</li>
 *   <li><b>Comments (CFWS):</b> (e.g., {@code name(comment) <addr>}) - Parenthetical comments are ignored.</li>
 *   <li><b>Domain Literals:</b> (e.g., {@code user@[192.168.1.1]}) - IP routing is rarely supported.</li>
 *   <li><b>Obsolete Syntax:</b> (RFC 5322 §4) - Legacy syntax like "quoted-pairs" in unquoted names.</li>
 * </ul>
 *
 * @since 9.9.4
 */
public record EmailAddress(Optional<String> displayName, String localPart, String domain) {
  public EmailAddress{
    requireNonNull(displayName);
    requireNonNull(localPart);
    requireNonNull(domain);
  }

  /** Returns an otherwise equivalent {@link EmailAddress} but with {@code displayName}. */
  public EmailAddress withDisplayName(String displayName) {
    return new EmailAddress(Optional.ofNullable(displayName), localPart, domain);
  }

  /** For example: {@code EmailAddress.of("user", "mycompany.com")}. */
  public static EmailAddress of(String localPart, String domain) {
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
    return displayName.map(n ->
            "\"" + all(CharPredicate.anyOf("\"\\")).replaceAllFrom(n, c -> "\\" + c)
            + "\" <" + address() + ">")
        .orElseGet(this::address);
  }

  /** Parses {@code address} and throws {@link Parser.ParseException} if failed. */
  public static EmailAddress parse(String address) {
    return parser().parse(address);
  }

  /** Returns a parser for email address, according to RFC 5322, supporting BMP characters. */
  public static Parser<EmailAddress> parser() {
    Parser<String> domain =
        consecutive(CharPredicate.is('-').or(Character::isLetterOrDigit), "domain label chars")
            .suchThat(
                s -> s.length() <= 63 && s.charAt(0) != '-' && s.charAt(s.length() - 1) != '-',
                "{1,63} chars domain label")
            .atLeastOnceDelimitedBy(".", joining("."));
    CharPredicate isIsoControl = Character::isISOControl;
    Parser<String> localPart =
        consecutive(
            CharPredicate.anyOf("@<>(),;:\\\"[]").or(isIsoControl).or(Character::isWhitespace).not(),
            "local part");
    Parser<EmailAddress> emailAddr =
        sequence(localPart, Parser.string("@").then(domain), EmailAddress::of);
    Parser<String> quotedDisplayName =
        Parser.quotedByWithEscapes(
            '"', '"', chars(1).suchThat(c -> isIsoControl.matchesNoneOf(c), "quoted"));
    Parser<String> unquotedDisplayName =
        consecutive(
                CharPredicate.anyOf("()<>[]:;@\\,\"").or(isIsoControl).not(),
                "unquoted display name")
            .map(String::trim);
    Parser<EmailAddress> emailPartWithBrackets = emailAddr.between("<", ">");
    return Parser.anyOf(
        emailPartWithBrackets,
        emailAddr,
        sequence( // or with display name
            Parser.anyOf(quotedDisplayName, unquotedDisplayName)
                .followedBy(zeroOrMore(Character::isWhitespace, "whitespaces")),
            emailPartWithBrackets,
            (d, e) -> e.withDisplayName(d)));
  }
}
