package com.google.common.labs.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertThrows;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class EmailAddressTest {
  private static final Pattern HTML5_EMAIL_PATTERN =
      Pattern.compile(
          """
          (?:                                # --- OPTION A: Named Address (Name <email@domain>) ---
           (?:                                                     # Start of Name portion
            (                                                      # Group 1: Display Name
             "(?:\\\\[\\x20-\\x7E]|[^"\\\\])*"                     # Quoted name
             |                                                     # OR
             [a-zA-Z0-9!\\#$%&'*+/=?^_`{|}~.\\-]+                  # Unquoted atom (# escaped)
              (?:\\s+[a-zA-Z0-9!\\#$%&'*+/=?^_`{|}~\\-]+)*         # Optional additional atoms
            )
            \\s+                                                   # Mandatory space before brackets
           )?                                                      # Name is optional
           <                                                       # Opening bracket
           ([a-zA-Z0-9.!\\#$%&'*+/=?^_`{|}~\\-]+)                  # Group 2: Local-part
           @
           (                                                       # Group 3: Domain
            [a-zA-Z0-9]
             (?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?                    # Subdomain label
             (?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)* # TLD/Additional labels
           )
           >                                                       # Closing bracket
          |                                           # --- OPTION B: Raw Address (email@domain) ---
           ([a-zA-Z0-9.!\\#$%&'*+/=?^_`{|}~\\-]+)                  # Group 4: Local-part
           @
           (                                                       # Group 5: Domain
            [a-zA-Z0-9]
             (?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?                    # Subdomain label
             (?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)* # TLD/Additional labels
           )
          )
          """,
          Pattern.COMMENTS);

  @Test
  public void testEmailAddressParsing_simple(@TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("test@example.com");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_complex(@TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("someone.else+and-another@some.sub-domain.example.co.uk");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("someone.else+and-another");
    assertThat(email.domain()).isEqualTo("some.sub-domain.example.co.uk");
  }

  @Test
  public void testEmailAddressParsing_singleLetterLocalPartAndDomain(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("a@b");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("a");
    assertThat(email.domain()).isEqualTo("b");
  }

  @Test
  public void testEmailAddressParsing_domainWithoutTld(@TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("test@example");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example");
  }

  @Test
  public void testEmailAddressParsing_allAllowedCharactersInLocalPart(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("a.b!#$%&'*+/=?^_`{|}~-c@example.com");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("a.b!#$%&'*+/=?^_`{|}~-c");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_invalidEmail_noLocalPart(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("@example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalidEmail_noDomain(@TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@"));
  }

  @Test
  public void testEmailAddressParsing_invalidEmail_noAtSign(@TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("testexample.com"));
  }

  @Test
  public void testEmailAddressParsing_invalidEmail_multipleAtSigns(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example@com"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName(@TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("John Doe <test@example.com>");
    assertThat(email.displayName()).hasValue("John Doe");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName(@TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("\"John Doe\" <test@example.com>");
    assertThat(email.displayName()).hasValue("John Doe");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_angleBracketEmailOnly(@TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("<test@example.com>");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_displayNameAndNoAngleBrackets(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("John Doe test@example.com"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName_multipleSpacesInName(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("John  Doe <test@example.com>");
    assertThat(email.displayName()).hasValue("John  Doe");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_withDisplayName_multipleSpacesBeforeBracket(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("John Doe  <test@example.com>");
    assertThat(email.displayName()).hasValue("John Doe");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_withEscapedQuote(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("\"John \\\"Doe\\\"\" <test@example.com>");
    assertThat(email.displayName()).hasValue("John \"Doe\"");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_withEscapedBackslash(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("\"John \\\\ Doe\" <test@example.com>");
    assertThat(email.displayName()).hasValue("John \\ Doe");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_spacesInsideQuotesPreserved(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("\"  John Doe  \" <test@example.com>");
    assertThat(email.displayName()).hasValue("  John Doe  ");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_onlySpacesInsideQuotes(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("\"  \" <test@example.com>");
    assertThat(email.displayName()).hasValue("  ");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_invalid_unclosedQuoteInDisplayName(
      @TestParameter ParseStrategy parser) {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("\"John Doe <test@example.com>"));
  }

  @Test
  public void testEmailAddressParsing_invalid_specialCharsInDisplayName(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("John<Doe <test@example.com>"));
  }

  @Test
  public void testEmailAddressParsing_invalid_domainLabelTooLong(
      @TestParameter ParseStrategy parser) {
    String longLabel = "b".repeat(64);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@" + longLabel + ".com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_domainLabelStartsWithHyphen(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@-example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_domainLabelEndsWithHyphen(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example-.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_domainLabelWithIllegalChars(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@exam_ple.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceBeforeAtSign(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test @example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceAfterAtSign(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@ example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceInDomainBeforeDot(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example .com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceInDomainAfterDot(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example. com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_localPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("пеле@example.com");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("пеле");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_i18n_domainPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("test@bücher.de");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("bücher.de");
  }

  @Test
  public void testEmailAddressParsing_i18n_displayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("\"Жशिऐ\" <test@example.com>");
    assertThat(email.displayName()).hasValue("Жशिऐ");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_i18n_unquotedDisplayName(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("Жशिऐ <test@example.com>");
    assertThat(email.displayName()).hasValue("Жशिऐ");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_i18n_chineseLocalPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("中文@example.com");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("中文");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_i18n_chineseDomainPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("test@中文.com");
    assertThat(email.displayName()).isEmpty();
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("中文.com");
  }

  @Test
  public void testEmailAddressParsing_i18n_chineseDisplayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    EmailAddress email = parser.parse("\"中文名\" <test@example.com>");
    assertThat(email.displayName()).hasValue("中文名");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithAllWeirdChars(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("weird!#$%&'*+/=?^_`{|}~-name <test@example.com>");
    assertThat(email.displayName()).hasValue("weird!#$%&'*+/=?^_`{|}~-name");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithSpacesAndAllWeirdChars(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("weird !#$%&'*+/=?^_`{|}~- name <test@example.com>");
    assertThat(email.displayName()).hasValue("weird !#$%&'*+/=?^_`{|}~- name");
    assertThat(email.localPart()).isEqualTo("test");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  @Test
  public void testToString_withoutDisplayName() {
    assertThat(EmailAddress.of("user", "google.com").address()).isEqualTo("user@google.com");
    assertThat(EmailAddress.of("user", "google.com").toString()).isEqualTo("user@google.com");
  }

  @Test
  public void testToString_withDisplayName() {
    EmailAddress emailAddress = EmailAddress.of("user", "google.com").withDisplayName("it's me");
    assertThat(emailAddress.address()).isEqualTo("user@google.com");
    assertThat(emailAddress.toString()).isEqualTo("\"it's me\"<user@google.com>");
  }

  @Test
  public void testToString_withDisplayName_withEscapes() {
    EmailAddress emailAddress = EmailAddress.of("user", "google.com").withDisplayName("name:\"\\me\"");
    assertThat(emailAddress.address()).isEqualTo("user@google.com");
    assertThat(emailAddress.toString()).isEqualTo("\"name:\\\"\\\\me\\\"\"<user@google.com>");
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithDots(
      @TestParameter ParseStrategy parser) {
    EmailAddress email = parser.parse("J.R.R. Tolkien <tolkien@example.com>");
    assertThat(email.displayName()).hasValue("J.R.R. Tolkien");
    assertThat(email.localPart()).isEqualTo("tolkien");
    assertThat(email.domain()).isEqualTo("example.com");
  }

  private static String unescape(String text) {
    return Substring.first(Pattern.compile("\\\\."))
        .repeatedly()
        .replaceAllFrom(text, e -> e.subSequence(1, e.length()));
  }

  private static String unquoteAndUnescapeDisplayName(String displayName) {
    return new StringFormat("\"{quoted}\"")
        .parse(displayName, quoted -> unescape(quoted))
        .orElse(displayName);
  }

  private enum ParseStrategy {
    REGEX {
      @Override
      EmailAddress parse(String email) {
        Matcher matcher = HTML5_EMAIL_PATTERN.matcher(email);
        checkArgument(matcher.matches(), "Invalid email address: %s", email);
        Optional<String> displayName =
            ofNullable(matcher.group(1)).map(EmailAddressTest::unquoteAndUnescapeDisplayName);
        String localPart = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
        String domain = matcher.group(3) != null ? matcher.group(3) : matcher.group(5);
        EmailAddress emailAddress = EmailAddress.of(localPart, domain);
        return displayName.map(emailAddress::withDisplayName).orElse(emailAddress);
      }
    },
    COMBINATOR {
      @Override
      EmailAddress parse(String email) {
        return EmailAddress.parse(email);
      }
    };

    abstract EmailAddress parse(String email);
  }
}
