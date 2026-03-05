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
            \\s*                                                   # Optional space before brackets
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
    parser.assertParsesTo("test@example.com", EmailAddress.of("test", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_complex(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "someone.else+and-another@some.sub-domain.example.co.uk",
        EmailAddress.of("someone.else+and-another", "some.sub-domain.example.co.uk"));
  }

  @Test
  public void testEmailAddressParsing_singleLetterLocalPartAndDomain(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo("a@b", EmailAddress.of("a", "b"));
  }

  @Test
  public void testEmailAddressParsing_domainWithoutTld(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo("test@example", EmailAddress.of("test", "example"));
  }

  @Test
  public void testEmailAddressParsing_allAllowedCharactersInLocalPart(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "a.b!#$%&'*+/=?^_`{|}~-c@example.com",
        EmailAddress.of("a.b!#$%&'*+/=?^_`{|}~-c", "example.com"));
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
    parser.assertParsesTo(
        "John Doe <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"John Doe\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_angleBracketEmailOnly(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo("<test@example.com>", EmailAddress.of("test", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_displayNameAndNoAngleBrackets(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("John Doe test@example.com"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName_multipleSpacesInName(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "John  Doe <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John  Doe"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName_multipleSpacesBeforeBracket(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "John Doe  <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_withEscapedQuote(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"John \\\"Doe\\\"\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John \"Doe\""));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_withEscapedBackslash(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"John \\\\ Doe\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John \\ Doe"));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_spacesInsideQuotesPreserved(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"  John Doe  \" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("  John Doe  "));
  }

  @Test
  public void testEmailAddressParsing_withEmptyQuotedDisplayName(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"\"<test@example.com>", EmailAddress.of("test", "example.com").withDisplayName(""));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_onlySpacesInsideQuotes(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"  \" <test@example.com>", EmailAddress.of("test", "example.com").withDisplayName("  "));
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
  public void testEmailAddressParsing_invalid_domainTooLong(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    String domain254 =
        "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62);
    assertThat(domain254.length()).isEqualTo(254);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@" + domain254));
  }

  @Test
  public void testEmailAddressParsing_invalid_addressTooLong(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    String local = "l".repeat(127);
    String domain = "a".repeat(63) + "." + "b".repeat(63);
    assertThat(local.length()).isEqualTo(127);
    assertThat(domain.length()).isEqualTo(127);
    assertThrows(IllegalArgumentException.class, () -> parser.parse(local + "@" + domain));
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
  public void testEmailAddressParsing_invalid_localPartStartsWithDot(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    assertThrows(IllegalArgumentException.class, () -> parser.parse(".test@example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_localPartEndsWithDot(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test.@example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_localPartWithDoubleDot(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test..foo@example.com"));
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
    parser.assertParsesTo("пеле@example.com", EmailAddress.of("пеле", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_domainPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo("test@bücher.de", EmailAddress.of("test", "bücher.de"));
  }

  @Test
  public void testEmailAddressParsing_i18n_displayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo(
        "\"Жशिऐ\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("Жशिऐ"));
  }

  @Test
  public void testEmailAddressParsing_i18n_unquotedDisplayName(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo(
        "Жशिऐ <test@example.com>", EmailAddress.of("test", "example.com").withDisplayName("Жशिऐ"));
  }

  @Test
  public void testEmailAddressParsing_i18n_chineseLocalPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo("中文@example.com", EmailAddress.of("中文", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_chineseDomainPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo("test@中文.com", EmailAddress.of("test", "中文.com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_chineseDisplayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo(
        "\"中文名\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("中文名"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithAllWeirdChars(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "weird!#$%&'*+/=?^_`{|}~-name <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("weird!#$%&'*+/=?^_`{|}~-name"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithSpacesAndAllWeirdChars(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "weird !#$%&'*+/=?^_`{|}~- name<test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("weird !#$%&'*+/=?^_`{|}~- name"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithDots(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "J.R.R. Tolkien <tolkien@example.com>",
        EmailAddress.of("tolkien", "example.com").withDisplayName("J.R.R. Tolkien"));
  }

  @Test
  public void testEmailAddressParsing_aliasLookingLikeAddress(
      @TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "\"john.smith@example.com\" <real@example.com>",
        EmailAddress.of("real", "example.com").withDisplayName("john.smith@example.com"));
  }

  @Test
  public void testParserComposition() {
    assertThat(EmailAddress.PARSER.atLeastOnceDelimitedBy(",").parse("a@example.com,b@foo.com"))
        .containsExactly(EmailAddress.of("a", "example.com"), EmailAddress.of("b", "foo.com"));
    assertThat(EmailAddress.PARSER.skipping(Character::isWhitespace).parseToStream("a@example.com\nb@foo.com"))
        .containsExactly(EmailAddress.of("a", "example.com"), EmailAddress.of("b", "foo.com"));
    assertThat(EmailAddress.PARSER.skipping(Character::isWhitespace).parseToStream("a@example.com b@foo.com"))
        .containsExactly(EmailAddress.of("a", "example.com"), EmailAddress.of("b", "foo.com"));
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

    final void assertParsesTo(String email, EmailAddress result) {
      assertThat(parse(email)).isEqualTo(result);
      assertThat(parse(email.toString())).isEqualTo(result);
    }
  }
}
