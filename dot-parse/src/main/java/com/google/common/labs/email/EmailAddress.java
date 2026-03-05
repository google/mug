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
 * Represents an email address according to RFC 5322.
 *
 * <p>For example: <pre>{@code
 * EmailAddress emailAddress = EmailAddress.parse("john-doe@gmail.com");
 * }</pre>
 *
 * @since 9.9.4
 */
public record EmailAddress(Optional<String> displayName, String localPart, String domain) {
  public EmailAddress{
    requireNonNull(displayName);
    requireNonNull(localPart);
    requireNonNull(domain);
  }

  /** Returns an {@link EmailAddress} with {@code displayName}. */
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
   * {@code "display name"<local-part@domain>}. Backslashes and double quotes in
   * the display name are auto-escaped.
   */
  @Override public String toString() {
    return displayName.map(n ->
            "\"" + all(CharPredicate.anyOf("\"\\")).replaceAllFrom(n, c -> "\\" + c)
            + "\"<" + address() + ">")
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
                CharPredicate.anyOf("()<>[]:;@\\,.\"").or(isIsoControl).not(),
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
