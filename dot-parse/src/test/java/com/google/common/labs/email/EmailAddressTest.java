package com.google.common.labs.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.labs.email.EmailAddress.parseAddressList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.sanctionco.jmail.JMail;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Benchmark for email parsing.
 *
 * <p>Result:
 *
 * <pre>
 * Benchmark                                            Mode  Cnt     Score      Error  Units
 * EmailParserTest.combinatorParseEmailWithDisplayName  avgt    5  1800.977 ±   20.637  ns/op
 * EmailParserTest.combinatorParseSimpleEmail           avgt    5  1007.580 ±   28.874  ns/op
 * EmailParserTest.javaMailParseEmailWithDisplayName    avgt    5  1724.123 ±   47.234  ns/op
 * EmailParserTest.javaMailParseSimpleEmail             avgt    5  1678.353 ± 1087.262  ns/op
 * EmailParserTest.regexParseEmailWithDisplayName       avgt    5  3011.731 ±   99.699  ns/op
 * EmailParserTest.regexParseSimpleEmail                avgt    5  2611.682 ±  263.736  ns/op
 * </pre>
 */
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

  private static final StringFormat QUOTED = new StringFormat("\"{quoted}\"");
  private static final Substring.RepeatingPattern ESCAPED_CHARS =
      Substring.first(Pattern.compile("\\\\.")).repeatedly();
  private static final StringFormat ADDR_SPEC = new StringFormat("{localPart}@{domain}");

  @Test
  public void testEmailAddressParsing_simple(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo("test@example.com", EmailAddress.of("test", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_simple(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress address = parser.parse("\"john doe\"@example.com");
    assertThat(address.localPart()).isEqualTo("john doe");
    assertThat(address.address()).isEqualTo("\"john doe\"@example.com");
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_withEscapedQuotes(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress address = parser.parse("\"john\\\"doe\"@example.com");
    assertThat(address.localPart()).isEqualTo("john\"doe");
    assertThat(address.address()).isEqualTo("\"john\\\"doe\"@example.com");
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_withEscapedBackslash(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress address = parser.parse("\"john\\\\doe\"@example.com");
    assertThat(address.localPart()).isEqualTo("john\\doe");
    assertThat(address.address()).isEqualTo("\"john\\\\doe\"@example.com");
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_withOtherEscapedChars(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress address = parser.parse("\"john\\=doe\"@example.com");
    assertThat(address.localPart()).isEqualTo("john=doe");
    assertThat(address.address()).isEqualTo("john=doe@example.com");
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_withDisplayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress address = parser.parse("John Doe <\"john doe\"@example.com>");
    assertThat(address.localPart()).isEqualTo("john doe");
    assertThat(address.address()).isEqualTo("\"john doe\"@example.com");
    assertThat(address.displayName()).hasValue("John Doe");
  }

  @Test
  public void testEmailAddressParsing_bothQuotedDisplayNameAndQuotedLocalPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress address = parser.parse("\"John Doe\" <\"john doe\"@example.com>");
    assertThat(address.localPart()).isEqualTo("john doe");
    assertThat(address.address()).isEqualTo("\"john doe\"@example.com");
    assertThat(address.displayName()).hasValue("John Doe");
    assertThat(address.toString()).isEqualTo("\"John Doe\" <\"john doe\"@example.com>");
  }

  @Test
  public void testEmailAddressOf_localPartNeedsQuoting() {
    EmailAddress address = EmailAddress.of("john\"doe", "example.com");
    assertThat(address.localPart()).isEqualTo("john\"doe");
    assertThat(address.toString()).isEqualTo("\"john\\\"doe\"@example.com");
  }

  @Test
  public void testEmailAddressOf_localPartNeedsQuoting_withDisplayName() {
    EmailAddress address = EmailAddress.of("john doe", "example.com").withDisplayName("John Doe");
    assertThat(address.localPart()).isEqualTo("john doe");
    assertThat(address.toString()).isEqualTo("\"John Doe\" <\"john doe\"@example.com>");
  }

  @Test
  public void testEmailAddressWithDisplayName_null() {
    EmailAddress address = EmailAddress.of("john.doe", "example.com").withDisplayName("John Doe");
    EmailAddress cleared = address.withDisplayName(null);
    assertThat(cleared.displayName()).isEmpty();
    assertThat(cleared.toString()).isEqualTo("john.doe@example.com");
  }

  @Test
  public void testEmailAddressWithDisplayName_needsEscaping() {
    EmailAddress address = EmailAddress.of("test", "example.com").withDisplayName("A \"B\" \\ C");
    assertThat(address.toString()).isEqualTo("\"A \\\"B\\\" \\\\ C\" <test@example.com>");
    EmailAddress parsed = EmailAddress.of(address.toString());
    assertThat(parsed.displayName()).hasValue("A \"B\" \\ C");
  }

  @Test
  public void testEmailAddressOf_roundtrip_localPartNeedsQuoting_withDisplayName() {
    EmailAddress address =
        EmailAddress.of("john,doe;part", "example.com")
            .withDisplayName("John [Doe] (Name)");
    String serialized = address.toString();
    assertThat(serialized)
        .isEqualTo("\"John [Doe] (Name)\" <\"john,doe;part\"@example.com>");
    EmailAddress parsed = EmailAddress.of(serialized);
    assertThat(parsed.displayName()).hasValue("John [Doe] (Name)");
    assertThat(parsed.localPart()).isEqualTo("john,doe;part");
    assertThat(parsed.domain()).isEqualTo("example.com");
    assertThat(parsed.toString()).isEqualTo(serialized);
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_invalid_escapedControlChar(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("\"john\\\ndoe\"@example.com"));
  }

  @Test
  public void testEmailAddressParsing_quotedLocalPart_invalid_rawControlChar(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("\"john\ndoe\"@example.com"));
  }

  @Test
  public void testEmailAddress_dangerousUnicodeRejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    
    // Line/Paragraph separators inside quoted local part
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("\"john\u2028doe\"@example.com"));
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("\"john\u2029doe\"@example.com"));

    // BiDi control chars inside quoted local part
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("\"john\u202Edoe\"@example.com"));
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("\"john\u202Adoe\"@example.com"));

    // BiDi control chars inside display name (quoted and unquoted)
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("\"John\u202EDoe\" <john@example.com>"));
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("John\u202EDoe <john@example.com>"));

    // Constructor validation checks
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("john\u202Edoe", "example.com"));
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("john", "example.com").withDisplayName("John\u202EDoe"));
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("john\u2028doe", "example.com"));
  }

  @Test
  public void testEmailAddressOf_localPartStartsWithDot() {
    EmailAddress address = EmailAddress.of(".john.doe", "example.com");
    assertThat(address.localPart()).isEqualTo(".john.doe");
    assertThat(address.toString()).isEqualTo("\".john.doe\"@example.com");
  }

  @Test
  public void testEmailAddressOf_localPartEndsWithDot() {
    EmailAddress address = EmailAddress.of("john.doe.", "example.com");
    assertThat(address.localPart()).isEqualTo("john.doe.");
    assertThat(address.toString()).isEqualTo("\"john.doe.\"@example.com");
  }

  @Test
  public void testEmailAddressOf_localPartContainsDoubleDot() {
    EmailAddress address = EmailAddress.of("john..doe", "example.com");
    assertThat(address.localPart()).isEqualTo("john..doe");
    assertThat(address.toString()).isEqualTo("\"john..doe\"@example.com");
  }

  @Test
  public void testEmailAddressOf_localPartContainsSingleDotInMiddle() {
    EmailAddress address = EmailAddress.of("john.doe", "example.com");
    assertThat(address.localPart()).isEqualTo("john.doe");
    assertThat(address.toString()).isEqualTo("john.doe@example.com");
  }

  @Test
  public void testEmailAddressOf_validDomainChars() {
    EmailAddress address = EmailAddress.of("test", "bücher.de");
    assertThat(address.domain()).isEqualTo("xn--bcher-kva.de");
    assertThat(address.unicodeDomain()).isEqualTo("bücher.de");

    // Standard ASCII valid domain characters: letters, digits, and hyphens in the middle
    EmailAddress asciiAddress = EmailAddress.of("test", "abcdefghijklmnopqrstuvwxyz-1234567890.com");
    assertThat(asciiAddress.domain()).isEqualTo("abcdefghijklmnopqrstuvwxyz-1234567890.com");
  }

  @Test
  public void testEmailAddressOf_domainCaseFolding() {
    // Standard ASCII case folding
    EmailAddress address1 = EmailAddress.of("test", "Example.Com");
    assertThat(address1.domain()).isEqualTo("example.com");
    assertThat(address1.unicodeDomain()).isEqualTo("example.com");

    // IDN case folding (converts capital B to lowercase b and maps to punycode)
    EmailAddress address2 = EmailAddress.of("test", "Bücher.De");
    assertThat(address2.domain()).isEqualTo("xn--bcher-kva.de");
    assertThat(address2.unicodeDomain()).isEqualTo("bücher.de");

    // Parse case folding
    EmailAddress address3 = EmailAddress.of("test@Example.Com");
    assertThat(address3.domain()).isEqualTo("example.com");
    assertThat(address3.unicodeDomain()).isEqualTo("example.com");

    // Raw Punycode case folding (converts uppercase XN-- to lowercase xn--)
    EmailAddress address4 = EmailAddress.of("test", "XN--bcher-kva.de");
    assertThat(address4.domain()).isEqualTo("xn--bcher-kva.de");
    assertThat(address4.unicodeDomain()).isEqualTo("bücher.de");
  }

  @Test
  public void testHasI18nDomain() {
    // Standard ASCII domain
    assertThat(EmailAddress.of("test", "example.com").hasI18nDomain()).isFalse();

    // IDN domain (Punycode in second-level domain)
    assertThat(EmailAddress.of("test", "bücher.de").hasI18nDomain()).isTrue();

    // IDN subdomain (Punycode in subdomain)
    assertThat(EmailAddress.of("test", "bücher.google.com").hasI18nDomain()).isTrue();

    // IDN TLD (Punycode in TLD)
    assertThat(EmailAddress.of("test", "google.рус").hasI18nDomain()).isTrue();

    // Raw Punycode input (lowercase)
    assertThat(EmailAddress.of("test", "xn--bcher-kva.de").hasI18nDomain()).isTrue();

    // Raw Punycode input (uppercase/mixed-case)
    assertThat(EmailAddress.of("test", "Xn--bcher-kva.de").hasI18nDomain()).isTrue();
    assertThat(EmailAddress.of("test", "XN--bcher-kva.de").hasI18nDomain()).isTrue();
  }

  @Test
  public void testEmailAddressOf_dotlessDomain_throws() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "localhost"));
  }

  @Test
  public void testEmailAddressOf_invalidDomainChars_space() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "example .com"));
  }

  @Test
  public void testEmailAddressOf_invalidDomainChars_atSign() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "example@com"));
  }

  @Test
  public void testEmailAddressOf_invalidDomainChars_underscore() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "exam_ple.com"));
  }

  @Test
  public void testEmailAddressOf_emptyDomainLabel_startsWithDot() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", ".example.com"));
  }

  @Test
  public void testEmailAddressOf_emptyDomainLabel_endsWithDot() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "example.com."));
  }

  @Test
  public void testEmailAddressOf_emptyDomainLabel_consecutiveDots() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "example..com"));
  }

  @Test
  public void testConstructor_localPartContainsControlChar() {
    assertThrows(
        IllegalArgumentException.class, () -> EmailAddress.of("local\npart", "example.com"));
  }

  @Test
  public void testConstructor_displayNameContainsControlChar() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EmailAddress("local", "example.com", Optional.of("John\nDoe")));
  }

  @Test
  public void testConstructor_domainContainsUppercaseChar_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EmailAddress("local", "Example.com", Optional.empty()));
  }

  @Test
  public void testConstructor_domainContainsControlChar() {
    assertThrows(
        IllegalArgumentException.class, () -> EmailAddress.of("local", "example\r.com"));
  }

  @Test
  public void testConstructor_domainLabelTooLong() {
    assertThrows(
        IllegalArgumentException.class,
        () -> EmailAddress.of("test", "1234567890123456789012345678901234567890123456789012345678901234.com"));
  }

  @Test
  public void testConstructor_allNumericTld() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> EmailAddress.of("test", "example.123"));
    assertThat(thrown).hasMessageThat().contains("numeric (123)");
  }

  @Test
  public void testConstructor_notAllNumericTld() {
    assertThat(EmailAddress.of("test", "123.com").address()).isEqualTo("test@123.com");
    assertThat(EmailAddress.of("test", "123.c1").address()).isEqualTo("test@123.c1");
  }

  @Test
  public void testEmailAddressParsing_unseparatedQuotedStrings(
      @TestParameter ParseStrategy parser) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("\"first\"last\"@test.org"));
  }

  @Test
  public void testEmailAddressParsing_unquotedSpaceInLocalPart(
      @TestParameter ParseStrategy parser) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("hello world@test.org"));
  }

  @Test
  public void testEmailAddressParsing_unquotedSpecialsInLocalPart(
      @TestParameter ParseStrategy parser) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("()[]\\;:,><@test.org"));
  }

  @Test
  public void testEmailAddressOf_idnToAsciiThrows() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test", "aא.com"));
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test@aא.com"));
    assertThat(thrown).hasMessageThat().contains("aא");
    assertThat(thrown).hasMessageThat().ignoringCase().contains("BiDi");
  }

  @Test
  public void testEmailAddressParsing_invalidEmail_domainLiterals() {
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test@[192.168.1.1]"));
    assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("test@192.168.1.1"));
  }

  @Test
  public void testEmailAddressParsing_complex(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "someone.else+and-another@some.sub-domain.example.co.uk",
        EmailAddress.of("someone.else+and-another", "some.sub-domain.example.co.uk"));
  }

  @Test
  public void testEmailAddressParsing_dotAfterPlus(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo(
        "someone+else.and-another@example.com",
        EmailAddress.of("someone+else.and-another", "example.com"));
    parser.assertParsesTo(
        "someone+.else@example.com",
        EmailAddress.of("someone+.else", "example.com"));
  }

  @Test
  public void testUserAndAlias() {
    EmailAddress addr1 = EmailAddress.of("someone.else+and-another", "example.com");
    assertThat(addr1.user()).isEqualTo("someone.else");
    assertThat(addr1.alias()).hasValue("and-another");

    EmailAddress addr2 = EmailAddress.of("john.doe", "example.com");
    assertThat(addr2.user()).isEqualTo("john.doe");
    assertThat(addr2.alias()).isEmpty();

    EmailAddress addr3 = EmailAddress.of("+tag", "example.com");
    assertThat(addr3.user()).isEmpty();
    assertThat(addr3.alias()).hasValue("tag");

    EmailAddress addr4 = EmailAddress.of("user+", "example.com");
    assertThat(addr4.user()).isEqualTo("user");
    assertThat(addr4.alias()).isEmpty();

    EmailAddress addr5 = EmailAddress.of("someone+else+another", "example.com");
    assertThat(addr5.user()).isEqualTo("someone");
    assertThat(addr5.alias()).hasValue("else+another");

    EmailAddress addr6 = EmailAddress.of("+", "example.com");
    assertThat(addr6.user()).isEmpty();
    assertThat(addr6.alias()).isEmpty();

    EmailAddress addr7 = EmailAddress.of("++", "example.com");
    assertThat(addr7.user()).isEmpty();
    assertThat(addr7.alias()).hasValue("+");
  }

  @Test
  public void testEmailAddressParsing_singleLetterLocalPartAndDomain(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("a@b"));
  }

  @Test
  public void testEmailAddressParsing_domainWithoutTld(@TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example"));
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
    assertThrows(IllegalArgumentException.class, () -> parser.parse("tester@protonmail.com@presidence@elysee.fr"));
  }

  @Test
  public void testEmailAddressParsing_localhost(@TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@localhost"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "John Doe <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "\"John Doe\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_angleBracketEmailOnly(@TestParameter ParseStrategy parser) {
    parser.assertParsesTo("<test@example.com>", EmailAddress.of("test", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_whitespace(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    // Spaces, tabs, and newlines are skipped
    assertThat(EmailAddress.of(" test@example.com ")).isEqualTo(EmailAddress.of("test", "example.com"));
    assertThat(EmailAddress.of("\ttest@example.com\t")).isEqualTo(EmailAddress.of("test", "example.com"));
    assertThat(EmailAddress.of("\ntest@example.com\n")).isEqualTo(EmailAddress.of("test", "example.com"));
    assertThat(EmailAddress.of("\r\ntest@example.com\r\n")).isEqualTo(EmailAddress.of("test", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_displayNameAndNoAngleBrackets(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("John Doe test@example.com"));
  }

  @Test
  public void testEmailAddressParsing_whitespaceAfterOpeningBracket(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNoneOf(ParseStrategy.REGEX, ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "John Doe < test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_whitespaceBeforeClosingBracket(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNoneOf(ParseStrategy.REGEX, ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "John Doe <test@example.com >",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName_multipleSpacesInName(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "John  Doe <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John  Doe"));
  }

  @Test
  public void testEmailAddressParsing_withDisplayName_multipleSpacesBeforeBracket(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "John Doe  <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John Doe"));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_withEscapedQuote(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "\"John \\\"Doe\\\"\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John \"Doe\""));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_withEscapedBackslash(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "\"John \\\\ Doe\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("John \\ Doe"));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_spacesInsideQuotesPreserved(
      @TestParameter ParseStrategy parser) {
    // JMAIL doesn't remove the trailing blank
    assume().that(parser).isNotEqualTo( ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "\"  John Doe  \" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("  John Doe  "));
  }

  @Test
  public void testEmailAddressParsing_withEmptyQuotedDisplayName(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JAKARTA);
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "\"\"<test@example.com>", EmailAddress.of("test", "example.com").withDisplayName(""));
  }

  @Test
  public void testEmailAddressParsing_withQuotedDisplayName_onlySpacesInsideQuotes(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNoneOf(ParseStrategy.JAKARTA, ParseStrategy.JMAIL);
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
  public void testEmailAddressParsing_localPartStartsWithDot(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    assertThrows(IllegalArgumentException.class, () -> parser.parse(".test@example.com"));
  }

  @Test
  public void testEmailAddressParsing_localPartEndsWithDot(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test.@example.com"));
  }

  @Test
  public void testEmailAddressParsing_localPartWithDoubleDot(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test..foo@example.com"));
  }

  @Test
  public void testEmailAddressParsing_domainWithDoubleDot(@TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("foo@example..com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceBeforeAtSign(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not check space
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test @example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceAfterAtSign(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not check space
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@ example.com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceInDomainBeforeDot(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example .com"));
  }

  @Test
  public void testEmailAddressParsing_invalid_spaceInDomainAfterDot(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not check space in domain
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@example. com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_localPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo("пеле@example.com", EmailAddress.of("пеле", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_rtlDomainPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo("test@גוגל.com", EmailAddress.of("test", "גוגל.com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_domainPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.REGEX);
    parser.assertParsesTo("test@bücher.de", EmailAddress.of("test", "bücher.de"));
  }

  @Test
  public void testEmailAddressParsing_i18n_displayName(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNoneOf(ParseStrategy.REGEX, ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "\"Жशिऐ\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("Жशिऐ"));
  }

  @Test
  public void testEmailAddressParsing_i18n_unquotedDisplayName(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNoneOf(ParseStrategy.REGEX, ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "Жशिऐ <test@example.com>", EmailAddress.of("test", "example.com").withDisplayName("Жशिऐ"));
  }

  @Test
  public void testEmailAddressParsing_underscoreInDomainNameIsInvalid(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@under_score.com"));
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
    assume().that(parser).isNoneOf(ParseStrategy.REGEX, ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "\"中文名\" <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("中文名"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithAllWeirdChars(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "weird!#$%&'*+/=?^_`{|}~-name <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("weird!#$%&'*+/=?^_`{|}~-name"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithSpacesAndAllWeirdChars(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "weird !#$%&'*+/=?^_`{|}~- name<test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("weird !#$%&'*+/=?^_`{|}~- name"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithDots(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);  // does not remove trailing blank
    parser.assertParsesTo(
        "J.R.R. Tolkien <tolkien@example.com>",
        EmailAddress.of("tolkien", "example.com").withDisplayName("J.R.R. Tolkien"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithComma(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    parser.assertParsesTo(
        "Doe, John <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("Doe, John"));
  }

  @Test
  public void testEmailAddressParsing_unquotedDisplayNameWithColon(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    parser.assertParsesTo(
        "Support: Admin <test@example.com>",
        EmailAddress.of("test", "example.com").withDisplayName("Support: Admin"));
  }

  @Test
  public void testParseAddressList_withUnquotedDisplayNameDelimiters() {
    List<EmailAddress> list = EmailAddress.parseAddressList(
        "Doe, John <john@example.com>, Smith, Jane <jane@example.com>");
    assertThat(list).containsExactly(
        EmailAddress.of("john", "example.com").withDisplayName("Doe, John"),
        EmailAddress.of("jane", "example.com").withDisplayName("Smith, Jane"));
  }

  @Test
  public void testParseAddressList_withUnquotedDisplayNameColons() {
    List<EmailAddress> list = EmailAddress.parseAddressList(
        "Support: Admin <admin@example.com>, Billing: System <billing@example.com>");
    assertThat(list).containsExactly(
        EmailAddress.of("admin", "example.com").withDisplayName("Support: Admin"),
        EmailAddress.of("billing", "example.com").withDisplayName("Billing: System"));
  }

  @Test
  public void testEmailAddressParsing_aliasLookingLikeAddress(
      @TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JMAIL);
    parser.assertParsesTo(
        "\"john.smith@example.com\" <real@example.com>",
        EmailAddress.of("real", "example.com").withDisplayName("john.smith@example.com"));
  }

  @Test
  public void testEmailAddressParsing_i18n_mixedLtrRtlIdnDomainIsIllegal(
      @TestParameter ParseStrategy parser) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("test@aא.com"));
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

  @Test
  public void testParseAddressList_emptyString() {
    assertThat(parseAddressList("")).isEmpty();
    assertThat(parseAddressList("  ")).isEmpty();
    assertThat(parseAddressList(" , ;\r\n, ")).isEmpty();
  }

  @Test
  public void testParseAddressList_singleAddress() {
    assertThat(parseAddressList("a@b.com")).containsExactly(EmailAddress.of("a", "b.com"));
  }

  @Test
  public void testParseAddressList_twoAddressesNoWhitespace() {
    assertThat(parseAddressList("a@b.com,c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
  }

  @Test
  public void testParseAddressList_twoAddressesWithWhitespaces() {
    assertThat(parseAddressList(" a@b.com , c@d.com "))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
  }

  @Test
  public void testParseAddressList_withNewlines() {
    assertThat(parseAddressList("a@b.com,\nc@d.com\r\n, e@f.com"))
        .containsExactly(
            EmailAddress.of("a", "b.com"),
            EmailAddress.of("c", "d.com"),
            EmailAddress.of("e", "f.com"));
  }

  @Test
  public void testParseAddressList_withTrailingComma() {
    assertThat(parseAddressList("a@b.com,")).containsExactly(EmailAddress.of("a", "b.com"));
    assertThat(parseAddressList("a@b.com , ")).containsExactly(EmailAddress.of("a", "b.com"));
    assertThat(parseAddressList("a@b.com, c@d.com,"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com,c@d.com , "))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
  }

  @Test
  public void testParseAddressList_semicolonDelimiter() {
    assertThat(parseAddressList("a@b.com;c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com ; c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com;\nc@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com\n;\nc@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com\n;\n c@d.com "))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
  }

  @Test
  public void testParseAddressList_consecutiveDelimiters() {
    assertThat(parseAddressList("a@b.com,,c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com;;c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com,;c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com;,c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com ,, c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com ; ; c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com,\n;c@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
    assertThat(parseAddressList("a@b.com,\n;\r\nc@d.com"))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"));
  }

  @Test
  public void testParseAddressList_withConsumer_emptyString() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("", invalid::add)).isEmpty();
    assertThat(invalid).isEmpty();
  }

  @Test
  public void testParseAddressList_withConsumer_whitespaceOnlyString() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("   ", invalid::add)).isEmpty();
    assertThat(invalid).isEmpty();
  }

  @Test
  public void testParseAddressList_withConsumer_delimitersOnlyString() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList(",,  ;; , ", invalid::add)).isEmpty();
    assertThat(invalid).isEmpty();
  }

  @Test
  public void testParseAddressList_withConsumer_allValid() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("a@b.com, c@d.com; e@f.com", invalid::add))
        .containsExactly(
            EmailAddress.of("a", "b.com"),
            EmailAddress.of("c", "d.com"),
            EmailAddress.of("e", "f.com"))
        .inOrder();
    assertThat(invalid).isEmpty();
  }

  @Test
  public void testParseAddressList_withConsumer_withInvalidEntries_middle() {
    List<String> invalid = new ArrayList<>();
    assertThat(
            EmailAddress.parseAddressList(
                "a@b.com, invalid-address, c@d.com; wrong@@domain.com , e@f.com", invalid::add))
        .containsExactly(
            EmailAddress.of("a", "b.com"),
            EmailAddress.of("c", "d.com"),
            EmailAddress.of("e", "f.com"))
        .inOrder();
    assertThat(invalid).containsExactly("invalid-address", "wrong@@domain.com").inOrder();
  }

  @Test
  public void testParseAddressList_withConsumer_withInvalidEntries_scattered() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("invalid1, invalid2; goo.d@address ; @com", invalid::add))
        .isEmpty();
    assertThat(invalid).containsExactly("invalid1", "invalid2", "goo.d@address", "@com").inOrder();
  }

  @Test
  public void testParseAddressList_withConsumer_withInvalidEntries_beginning() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("invalid-address, a@b.com", invalid::add))
        .containsExactly(EmailAddress.of("a", "b.com"));
    assertThat(invalid).containsExactly("invalid-address");
  }

  @Test
  public void testParseAddressList_withConsumer_withInvalidEntries_end() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("a@b.com, invalid-address", invalid::add))
        .containsExactly(EmailAddress.of("a", "b.com"));
    assertThat(invalid).containsExactly("invalid-address");
  }

  @Test
  public void testParseAddressList_withConsumer_multipleConsecutiveInvalidEntries() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("a@b.com, invalid1; invalid2, c@d.com", invalid::add))
        .containsExactly(EmailAddress.of("a", "b.com"), EmailAddress.of("c", "d.com"))
        .inOrder();
    assertThat(invalid).containsExactly("invalid1", "invalid2").inOrder();
  }

  @Test
  public void testParseAddressList_withConsumer_invalidDomainHyphen() {
    List<String> invalid = new ArrayList<>();
    assertThat(
            EmailAddress.parseAddressList(
                "a@b.com, c@-d.com, e@f.com; g@h-.com, i@j.com", invalid::add))
        .containsExactly(
            EmailAddress.of("a", "b.com"),
            EmailAddress.of("e", "f.com"),
            EmailAddress.of("i", "j.com"))
        .inOrder();
    assertThat(invalid).containsExactly("c@-d.com", "g@h-.com").inOrder();
  }

  @Test
  public void testParseAddressList_withConsumer_invalidDomainWithSpacesAroundDot() {
    List<String> invalid = new ArrayList<>();
    EmailAddress.parseAddressList("a@b.com, c@d . com, e@f.com", invalid::add);
    assertThat(invalid).containsExactly("c@d . com").inOrder();
  }

  @Test
  public void testParseAddressList_withConsumer_allInvalid() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("invalid1, invalid2; wrong@address@com", invalid::add)).isEmpty();
    assertThat(invalid).containsExactly("invalid1", "invalid2", "wrong@address@com").inOrder();
  }

  @Test
  public void testParseAddressList_withConsumer_delimitersAndWhitespace() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("  a@b.com  ,,;  c@d.com ; ; invalid , e@f.com , ", invalid::add))
        .containsExactly(
            EmailAddress.of("a", "b.com"),
            EmailAddress.of("c", "d.com"),
            EmailAddress.of("e", "f.com"))
        .inOrder();
    assertThat(invalid).containsExactly("invalid");
  }

  @Test
  public void testParseAddressList_withConsumer_delimitersInQuotedDisplayName() {
    List<String> invalid = new ArrayList<>();
    assertThat(
            EmailAddress.parseAddressList(
                "\"John, Doe\" <john@example.com>, \"Smith; Jane\" <jane@example.com>", invalid::add))
        .containsExactly(
            EmailAddress.of("john", "example.com").withDisplayName("John, Doe"),
            EmailAddress.of("jane", "example.com").withDisplayName("Smith; Jane"))
        .inOrder();
    assertThat(invalid).isEmpty();
  }

  @Test
  public void testParseAddressList_withConsumer_delimitersInQuotedLocalPart() {
    List<String> invalid = new ArrayList<>();
    assertThat(
            EmailAddress.parseAddressList(
                "\"john,doe\"@example.com; \"jane;smith\"@example.com", invalid::add))
        .containsExactly(
            EmailAddress.of("john,doe", "example.com"),
            EmailAddress.of("jane;smith", "example.com"))
        .inOrder();
    assertThat(invalid).isEmpty();
  }

  @Test
  public void testParseAddressList_withTrailingJunk_rejected() {
    List<String> invalid = new ArrayList<>();
    assertThat(EmailAddress.parseAddressList("a@b.com junk", invalid::add)).isEmpty();
    assertThat(invalid).containsExactly("a@b.com junk");
  }

  @Test
  public void testParseAddressList_withConsumer_allNumericTld_rejected() {
    List<String> invalid = new ArrayList<>();
    assertThat(
            EmailAddress.parseAddressList(
                "good@example.com, bad@example.123, fine@example.org", invalid::add))
        .containsExactly(
            EmailAddress.of("good", "example.com"),
            EmailAddress.of("fine", "example.org"))
        .inOrder();
    assertThat(invalid).containsExactly("bad@example.123");
  }

  @Test
  public void testEmailAddressParsing_jakartaDifferential_rejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JAKARTA);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("<aaa@bbb.com>ccc@ddd.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("<legitimate@trusted.com>attacker@evil.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("<attacker@evil.com>@trusted.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("<aaa@aaa.com> (bbb@bbb.com) ccc@ccc.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("<aaa@aaa.com> bbb@bbb.com"));
    // Single quotes are NOT valid quote boundaries under RFC 5322; this is a Hibernate validator regex vulnerability
    assertThrows(IllegalArgumentException.class, () -> parser.parse("'foo@bar.com'@example.com"));
  }

  @Test
  public void testEmailAddressParsing_jakartaDifferential_acceptedByJakarta_bad(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.JAKARTA);
    assertThat(parser.parse("<aaa@bbb.com>ccc@ddd.com").address()).isEqualTo("aaa@bbb.com");
    assertThat(parser.parse("<legitimate@trusted.com>attacker@evil.com").address()).isEqualTo("legitimate@trusted.com");
    assertThat(parser.parse("<attacker@evil.com>@trusted.com").address()).isEqualTo("attacker@evil.com");
    assertThat(parser.parse("<aaa@aaa.com> (bbb@bbb.com) ccc@ccc.com").address()).isEqualTo("aaa@aaa.com");
    assertThat(parser.parse("<aaa@aaa.com> bbb@bbb.com").address()).isEqualTo("aaa@aaa.com");
  }

  @Test
  public void testParseAddressList_withJakartaDifferential() {
    List<String> invalid = new ArrayList<>();
    List<EmailAddress> parsed = EmailAddress.parseAddressList(
        "<aaa@bbb.com>ccc@ddd.com, legitimate@trusted.com, <attacker@evil.com>@trusted.com",
        invalid::add);
    assertThat(parsed).containsExactly(EmailAddress.of("legitimate", "trusted.com"));
    assertThat(invalid).containsExactly("<aaa@bbb.com>ccc@ddd.com", "<attacker@evil.com>@trusted.com");
  }

  @Test
  public void testEmailAddressParsing_groupAddress_rejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isNotEqualTo(ParseStrategy.JAKARTA);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("group-name:addr1@b.com,addr2@c.com;"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("group-name:addr1@b.com;"));
  }

  @Test
  public void testParseAddressList_withGroupAddress() {
    List<String> invalid = new ArrayList<>();
    List<EmailAddress> parsed = EmailAddress.parseAddressList(
        "group-name:addr1@b.com,addr2@c.com;",
        invalid::add);
    assertThat(parsed).containsExactly(EmailAddress.of("addr2", "c.com"));
    assertThat(invalid).containsExactly("group-name:addr1@b.com");
  }

  @Test
  public void testParseAddressList_withGroupAddressAndComments() {
    List<String> invalid = new ArrayList<>();
    List<EmailAddress> parsed = EmailAddress.parseAddressList(
        "a:(aaa@bbb.com)ccc@ddd.com,eee@fff.com,ggg@hhh.com;",
        invalid::add);
    assertThat(parsed).containsExactly(
        EmailAddress.of("eee", "fff.com"),
        EmailAddress.of("ggg", "hhh.com"));
    assertThat(invalid).containsExactly("a:(aaa@bbb.com)ccc@ddd.com");
  }

  @Test
  public void testEmailAddressParsing_multiAtLocalPart(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    // Double quoted local part is allowed to contain '@' under RFC 5322.
    EmailAddress parsed = parser.parse("\"attacker@evil.com\"@trusted.com");
    assertThat(parsed.localPart()).isEqualTo("attacker@evil.com");
    assertThat(parsed.domain()).isEqualTo("trusted.com");

    // Unquoted local part cannot contain multiple '@' signs.
    assertThrows(IllegalArgumentException.class, () -> parser.parse("attacker@evil.com@trusted.com"));
  }

  @Test
  public void testParseAddressList_withMultiAtLocalPart() {
    List<String> invalid = new ArrayList<>();
    List<EmailAddress> parsed = EmailAddress.parseAddressList(
        "\"attacker@evil.com\"@trusted.com, attacker@evil.com@trusted.com",
        invalid::add);
    assertThat(parsed).containsExactly(EmailAddress.of("attacker@evil.com", "trusted.com"));
    assertThat(invalid).containsExactly("attacker@evil.com@trusted.com");
  }

  @Test
  public void testEmailAddressParsing_rfc2047EncodedWord_withAt_rejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("=?UTF-8?Q?Administrator_=3Cadmin@example.com=3E?= <attacker@evil.com>"));
  }

  @Test
  public void testEmailAddressParsing_rfc2047EncodedWord_withAt_acceptedByJakarta_bad(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.JAKARTA);
    EmailAddress parsed = parser.parse("=?UTF-8?Q?Administrator_=3Cadmin@example.com=3E?= <attacker@evil.com>");
    assertThat(parsed.displayName()).hasValue("Administrator <admin@example.com>");
    assertThat(parsed.address()).isEqualTo("attacker@evil.com");
  }

  @Test
  public void testEmailAddressParsing_rfc2047EncodedWord_withoutAt_parsedByCombinator(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    EmailAddress parsed = parser.parse("=?UTF-8?Q?Administrator?= <attacker@evil.com>");
    assertThat(parsed.displayName()).hasValue("=?UTF-8?Q?Administrator?=");
    assertThat(parsed.address()).isEqualTo("attacker@evil.com");
  }

  @Test
  public void testEmailAddressParsing_rfc2047EncodedWord_withoutAt_decodedByJakarta_bad(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.JAKARTA);
    EmailAddress parsed = parser.parse("=?UTF-8?Q?Administrator?= <attacker@evil.com>");
    assertThat(parsed.displayName()).hasValue("Administrator");
    assertThat(parsed.address()).isEqualTo("attacker@evil.com");
  }

  @Test
  public void testParseAddressList_withRfc2047EncodedWord() {
    List<String> invalid = new ArrayList<>();
    List<EmailAddress> parsed = EmailAddress.parseAddressList(
        "=?UTF-8?Q?Administrator_=3Cadmin@example.com=3E?= <attacker@evil.com>, "
            + "=?UTF-8?Q?Administrator?= <attacker@evil.com>",
        invalid::add);
    // The first one has an '@' in the unquoted display name, so it's treated as invalid.
    // The second one is valid, parsing literally without decoding.
    assertThat(parsed).containsExactly(
        EmailAddress.of("attacker", "evil.com").withDisplayName("=?UTF-8?Q?Administrator?="));
    assertThat(invalid).containsExactly(
        "=?UTF-8?Q?Administrator_=3Cadmin@example.com=3E?= <attacker@evil.com>");
  }

  @Test
  public void testParseAddressList_withEncodedWordInLocalPart_doesNotCrash() {
    List<String> invalid = new ArrayList<>();
    List<EmailAddress> parsed = EmailAddress.parseAddressList(
        "=?x?q?=41=42=43collab=40psres.net=3e=20?=@psres.net, safe@example.com",
        invalid::add);
    assertThat(parsed).containsExactly(EmailAddress.of("safe", "example.com"));
    assertThat(invalid).containsExactly("=?x?q?=41=42=43collab=40psres.net=3e=20?=@psres.net");
  }

  @Test
  public void testJMailValidation_acceptsEncodedWordPhishing_bad() {
    // Proves JMail fails to reject RFC 2047 display name address injection,
    // accepting a display name containing an unquoted '@' character.
    assertThat(JMail.tryParse("=?UTF-8?Q?Administrator_=3Cadmin@example.com=3E?= <attacker@evil.com>").isPresent())
        .isTrue();
  }

  @Test
  public void testJMailValidation_doesNotStripQuotes_bad() {
    // Proves JMail fails to strip or unescape double quotes from the local part.
    var parsed = JMail.tryParse("\"attacker@evil.com\"@trusted.com");
    assertThat(parsed.isPresent()).isTrue();
    assertThat(parsed.get().localPart()).isEqualTo("\"attacker@evil.com\"");
  }

  @Test
  public void testJMailValidation_rejectsGroupAddress_good() {
    // Proves JMail correctly rejects group address formats.
    assertThat(JMail.tryParse("group-name:addr1@b.com,addr2@c.com;").isPresent()).isFalse();
    assertThat(JMail.tryParse("group-name:addr1@b.com;").isPresent()).isFalse();
  }

  @Test
  public void testJMailValidation_rejectsJakartaDifferential_good() {
    // Proves JMail correctly rejects Jakarta parsing differential injections.
    assertThat(JMail.tryParse("<aaa@bbb.com>ccc@ddd.com").isPresent()).isFalse();
    assertThat(JMail.tryParse("<legitimate@trusted.com>attacker@evil.com").isPresent()).isFalse();
    assertThat(JMail.tryParse("<attacker@evil.com>@trusted.com").isPresent()).isFalse();
  }

  @Test
  public void testJakartaValidation_acceptsEncodedWordPhishing_bad() throws Exception {
    // Proves Jakarta Mail accepts RFC 2047 display name address injection, decoding it.
    InternetAddress address =
        new InternetAddress(
            "=?UTF-8?Q?Administrator_=3Cadmin@example.com=3E?= <attacker@evil.com>",
            /* strict= */ true);
    assertThat(address.getPersonal()).isEqualTo("Administrator <admin@example.com>");
    assertThat(address.getAddress()).isEqualTo("attacker@evil.com");
  }

  @Test
  public void testJakartaValidation_decodesEncodedWordWithoutAt_bad() throws Exception {
    // Proves Jakarta Mail automatically decodes RFC 2047 encoded display names.
    InternetAddress address =
        new InternetAddress("=?UTF-8?Q?Administrator?= <attacker@evil.com>", /* strict= */ true);
    assertThat(address.getPersonal()).isEqualTo("Administrator");
    assertThat(address.getAddress()).isEqualTo("attacker@evil.com");
  }

  @Test
  public void testJakartaValidation_acceptsDifferential_bad() throws Exception {
    // Proves Jakarta Mail parses/accepts differential injection, returning only nested address.
    InternetAddress address =
        new InternetAddress("<aaa@bbb.com>ccc@ddd.com", /* strict= */ true);
    assertThat(address.getAddress()).isEqualTo("aaa@bbb.com");
  }

  @Test
  public void testJakartaValidation_acceptsGroupAddress_bad() throws Exception {
    // Proves Jakarta Mail accepts group address formats.
    InternetAddress address =
        new InternetAddress("group-name:addr1@b.com,addr2@c.com;", /* strict= */ true);
    assertThat(address.isGroup()).isTrue();
  }

  @Test
  public void testJakartaValidation_rejectsInvalidAddresses_good() {
    // Proves Jakarta Mail correctly rejects basic invalid formats under strict validation.
    assertThrows(AddressException.class, () -> new InternetAddress("testexample.com", true));
    assertThrows(AddressException.class, () -> new InternetAddress("test@", true));
    assertThrows(AddressException.class, () -> new InternetAddress("@example.com", true));
    assertThrows(AddressException.class, () -> new InternetAddress("'foo@bar.com'@example.com", true));
  }

  @Test
  public void testEmailAddressParsing_quotedSpecialsAllowed(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    parser.assertParsesTo("\"@\"@example.com", EmailAddress.of("@", "example.com"));
    parser.assertParsesTo("\"\\\"\"@example.com", EmailAddress.of("\"", "example.com"));
  }

  @Test
  public void testEmailAddressParsing_commentsRejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("(foo)user@example.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("user@(bar)example.com"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("collab%psres.net(@example.com"));
  }

  @Test
  public void testEmailAddressParsing_uucpRejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("!#$%&'*+\\/=?^_`{|}~-collab\\@psres.net"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("oastify.com!collab\\@example.com"));
  }

  @Test
  public void testEmailAddressParsing_unicodeOverflowsRejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    // ŀ (U+0140) must NOT overflow to @ (U+0040)
    assertThrows(IllegalArgumentException.class, () -> parser.parse("foo\u0140example.com"));
    // ❀ (U+2740) must NOT overflow to @ (U+0040)
    assertThrows(IllegalArgumentException.class, () -> parser.parse("foo\u2740example.com"));
  }

  @Test
  public void testEmailAddressParsing_encodedWordInLocalPartRejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("=?x?q?=41=42=43collab=40psres.net=3e=20?=@psres.net"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("prefix=?x?q?=41=42=43collab=40psres.net=3e=20?=@psres.net"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("=?x?q?=41=42=43collab=40psres.net=3e=20?=suffix@psres.net"));
  }

  @Test
  public void testEmailAddressParsing_smtpParameterSmugglingRejected(@TestParameter ParseStrategy parser) {
    assume().that(parser).isEqualTo(ParseStrategy.COMBINATOR);
    assertThrows(IllegalArgumentException.class, () -> parser.parse("\"foo\\\\\"@psres.net> ORCPT=test;admin\"@example.com"));
  }

  private static String unescape(String text) {
    return ESCAPED_CHARS.replaceAllFrom(text, e -> e.subSequence(1, e.length()));
  }

  private static String unquoteAndUnescapeDisplayName(String displayName) {
    return QUOTED
        .parse(displayName, quoted -> unescape(quoted))
        .orElse(displayName);
  }

  @Test
  public void testUnicodeDisplayName_withEncodedWord() {
    EmailAddress address = EmailAddress.of("=?UTF-8?Q?John_Doe?= <test@example.com>");
    assertThat(address.displayName()).hasValue("=?UTF-8?Q?John_Doe?=");
    assertThat(address.unicodeDisplayName()).hasValue("John Doe");
  }

  @Test
  public void testUnicodeDisplayName_withPlainDisplayName() {
    EmailAddress plain = EmailAddress.of("John Doe <test@example.com>");
    assertThat(plain.displayName()).hasValue("John Doe");
    assertThat(plain.unicodeDisplayName()).hasValue("John Doe");

    // Quoted display name containing raw non-ASCII characters (not RFC 2047 encoded)
    EmailAddress rawUnicode = EmailAddress.of("\"René\" <test@example.com>");
    assertThat(rawUnicode.displayName()).hasValue("René");
    assertThat(rawUnicode.unicodeDisplayName()).hasValue("René");

    // Mixed display name (contains both plain text and RFC 2047 encoded word)
    EmailAddress mixed = EmailAddress.of("Hello =?UTF-8?Q?John?= <test@example.com>");
    assertThat(mixed.displayName()).hasValue("Hello =?UTF-8?Q?John?=");
    assertThat(mixed.unicodeDisplayName()).hasValue("Hello John");
  }

  @Test
  public void testUnicodeDisplayName_withoutDisplayName() {
    EmailAddress noName = EmailAddress.of("test@example.com");
    assertThat(noName.displayName()).isEmpty();
    assertThat(noName.unicodeDisplayName()).isEmpty();
  }

  @Test
  public void testUnicodeDisplayName_withUnknownCharset() {
    // Unknown/unsupported charset -> fallback to raw
    EmailAddress unknownCharset = EmailAddress.of("=?INVALID?Q?John?= <test@example.com>");
    assertThat(unknownCharset.displayName()).hasValue("=?INVALID?Q?John?=");
    assertThat(unknownCharset.unicodeDisplayName()).hasValue("=?INVALID?Q?John?=");
  }

  @Test
  public void testUnicodeDisplayName_withMalformedEncodingStructure() {
    // Malformed encoding structure (missing trailing =) -> fallback to raw
    EmailAddress malformedStructure = EmailAddress.of("=?UTF-8?Q?John? <test@example.com>");
    assertThat(malformedStructure.displayName()).hasValue("=?UTF-8?Q?John?");
    assertThat(malformedStructure.unicodeDisplayName()).hasValue("=?UTF-8?Q?John?");
  }

  @Test
  public void testUnicodeDisplayName_withDecodingFailure() {
    // Valid charset but bad base64 payload -> fallback to raw
    EmailAddress badBase64 = EmailAddress.of("=?UTF-8?B?invalid_base64?= <test@example.com>");
    assertThat(badBase64.displayName()).hasValue("=?UTF-8?B?invalid_base64?=");
    assertThat(badBase64.unicodeDisplayName()).hasValue("=?UTF-8?B?invalid_base64?=");
  }

  private enum ParseStrategy {
    REGEX {
      @Override EmailAddress parse(String email) {
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
      @Override EmailAddress parse(String email) {
        return EmailAddress.of(email);
      }
    },
    JAKARTA {
      @Override EmailAddress parse(String email) {
        try {
          InternetAddress internetAddress = new InternetAddress(email, /* strict= */ true);
          EmailAddress emailAddress = ADDR_SPEC.parseOrThrow(
              internetAddress.getAddress(),
              (localPart, domain) -> EmailAddress.of(localPart, domain));
          return ofNullable(internetAddress.getPersonal())
              .map(emailAddress::withDisplayName)
              .orElse(emailAddress);
        } catch (AddressException e) {
          throw new IllegalArgumentException(e);
        }
      }
    },
    JMAIL {
      @Override EmailAddress parse(String email) {
        JMail.validate(email);
        return JMail.tryParse(email)
            .map(result -> {
              EmailAddress address = EmailAddress.of(result.localPart(), result.domain());
              return result.hasIdentifier()
                  ? address.withDisplayName(
                      // INCOMPATIBILITY: JMAIL does not remove the double quotes
                      QUOTED.parse(result.identifier(), identity()).orElse(result.identifier()))
                  : address;
            })
            .orElseThrow(() -> new IllegalArgumentException("failed to parse " + email));
      }
    };

    abstract EmailAddress parse(String email);

    final void assertParsesTo(String email, EmailAddress result) {
      assertThat(parse(email)).isEqualTo(result);
      assertThat(parse(result.toString())).isEqualTo(result);
    }
  }
}
