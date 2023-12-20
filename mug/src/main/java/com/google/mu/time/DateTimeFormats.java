/*****************************************************************************
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
package com.google.mu.time;

import static com.google.mu.util.CharPredicate.anyOf;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.Substring.consecutive;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.leading;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static com.google.mu.util.stream.BiCollectors.maxByKey;
import static com.google.mu.util.stream.BiStream.biStream;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.mu.collect.PrefixSearchTable;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;

/**
 * A facade class providing convenient {@link DateTimeFormatter} instances by inferring from an
 * example date/time/datetime string in the expected format.
 *
 * <p>For example:
 *
 * <pre>{@code
 * private static final DateTimeFormatter DATE_TIME_FORMATTER =
 *     DateTimeFormats.formatOf("2023-12-09 10:00:00.12345 America/Los_Angeles");
 * private static final DateTimeFormatter USING_ZONE_OFFSET =
 *     DateTimeFormats.formatOf("2023-12-09 10:00:00+08:00");
 * private static final DateTimeFormatter ISO_FORMATTER =
 *     DateTimeFormats.formatOf("2023-12-09T10:00:00.12345[Europe/Paris]");
 * private static final DateTimeFormatter WITH_DAY_OF_WEEK =
 *     DateTimeFormats.formatOf("2023/12/09 Sat 10:00+08:00");
 * }</pre>
 *
 * <p>Most ISO 8601 formats are supported, except BASIC_ISO_DATE, ISO_WEEK_DATE ('2012-W48-6')
 * and ISO_ORDINAL_DATE ('2012-337').
 *
 * <p>For the date part of custom patterns, {@code MM/dd/yyyy} or {@code dd/MM/yyyy} or any variant
 * where the {@code yyyy} is after the {@code MM} or {@code dd} are not supported. However if
 * localized month names such as {@code Jan} or {@code March} are used, all natural orders
 * ({@code year month day}, {@code month day year} or {@code day month year}) are supported.
 *
 * <p>For the time part of custom patterns, only {@code HH:mm}, {@code HH:mm:ss} and {@code
 * HH:mm:ss.S} variants are supported (the S can be 1 to 9 digits). AM/PM and 12-hour numbers are
 * not supported. Though you can explicitly specify them together with placeholders (see below).
 *
 * <p>If the variant of the date time pattern you need exceeds the out-of-box support, you can
 * explicitly mix the {@link DateTimeFormatter} specifiers with example placeholders
 * (between a pair of pointy brackets) to be translated.
 *
 * <p>For example the following code uses the {@code dd}, {@code MM} and {@code yyyy} specifiers as
 * is but translates the {@code Tue} and {@code America/New_York} example snippets into {@code E}
 * and {@code VV} specifiers respectively. It will then parse and format to datetime strings like
 * "Fri, 20 Oct 2023 10:30:59.123 Europe/Paris".
 *
 * <pre>{@code
 * private static final DateTimeFormatter FORMATTER =
 *     formatOf("<Tue>, dd MM yyyy HH:mm:ss.SSS <America/New_York>");
 * }</pre>
 *
 * @since 7.1
 */
public final class DateTimeFormats {
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').or(CharPredicate.range('A', 'Z').or(is('_')));

  /** delimiters don't have semantics and are ignored during parsing. */
  private static final CharPredicate DELIMITER = anyOf(" ,;");

  /** Punctuation chars, such as '/', ':', '-' are essential part of the pattern syntax. */
  private static final CharPredicate PUNCTUATION = DIGIT.or(ALPHA).or(DELIMITER).not();
  private static final Substring.RepeatingPattern TOKENIZER =
      Stream.of(consecutive(DIGIT), consecutive(ALPHA), first(PUNCTUATION))
          .collect(firstOccurrence())
          .repeatedly();
  private static final Substring.RepeatingPattern PLACEHOLDERS =
      consecutive(noneOf("<>")).immediatelyBetween("<", INCLUSIVE, ">", INCLUSIVE).repeatedly();
  private static final Map<List<?>, DateTimeFormatter> ISO_DATE_FORMATTERS =
      BiStream.of(
          forExample("2011-12-03"), DateTimeFormatter.ISO_LOCAL_DATE,
          forExample("2011-12-03+08:00"), DateTimeFormatter.ISO_DATE,
          forExample("2011-12-03-08:00"), DateTimeFormatter.ISO_DATE).toMap();

  /** These ISO formats all support optional nanoseconds in the format of ".nnnnnnnnn". */
  private static final Map<List<?>, DateTimeFormatter> ISO_DATE_TIME_FORMATTERS =
      BiStream.of(
          forExample("10:00:00"), DateTimeFormatter.ISO_LOCAL_TIME,
          forExample("10:00:00+00:00"), DateTimeFormatter.ISO_TIME,
          forExample("2011-12-03T10:15:30"), DateTimeFormatter.ISO_LOCAL_DATE_TIME,
          forExample("2011-12-03T10:15:30+01:00"), DateTimeFormatter.ISO_DATE_TIME,
          forExample("2011-12-03T10:15:30-01:00"), DateTimeFormatter.ISO_DATE_TIME,
          forExample("2011-12-03T10:15:30+01:00[Europe/Paris]"), DateTimeFormatter.ISO_DATE_TIME,
          forExample("2011-12-03T10:15:30-01:00[Europe/Paris]"), DateTimeFormatter.ISO_DATE_TIME,
          forExample("2011-12-03T10:15:30Z"), DateTimeFormatter.ISO_INSTANT).toMap();

  /** The day-of-week part is optional; the day-of-month can be 1 or 2 digits. */
  private static final Map<List<?>, DateTimeFormatter> RFC_1123_FORMATTERS =
      Stream.of(
              "Tue, 1 Jun 2008 11:05:30 GMT",
              "Tue, 10 Jun 2008 11:05:30 GMT",
              "1 Jun 2008 11:05:30 GMT",
              "10 Jun 2008 11:05:30 GMT",
              "Tue, 1 Jun 2008 11:05:30 +0800",
              "Tue, 1 Jun 2008 11:05:30 -0800",
              "Tue, 10 Jun 2008 11:05:30 +0800",
              "Tue, 10 Jun 2008 11:05:30 -0800",
              "1 Jun 2008 11:05:30 +0800",
              "10 Jun 2008 11:05:30 +0800")
          .collect(
              toMap(
                  DateTimeFormats::forExample, ex -> DateTimeFormatter.RFC_1123_DATE_TIME));

  private static final PrefixSearchTable<Object, String> PREFIX_TABLE =
      PrefixSearchTable.<Object, String>builder()
          .add(forExample("2011-12-03"), "yyyy-MM-dd")
          .add(forExample("2011-12-3"), "yyyy-MM-d")
          .add(forExample("2011/12/03"), "yyyy/MM/dd")
          .add(forExample("2011/12/3"), "yyyy/MM/d")
          .add(forExample("Jan 11 2011"), "LLL dd yyyy")
          .add(forExample("Jan 1 2011"), "LLL d yyyy")
          .add(forExample("11 Jan 2011"), "dd LLL yyyy")
          .add(forExample("1 Jan 2011"), "d LLL yyyy")
          .add(forExample("2011 Jan 1"), "yyyy LLL d")
          .add(forExample("2011 Jan 11"), "yyyy LLL dd")
          .add(forExample("January 11 2011"), "LLLL dd yyyy")
          .add(forExample("January 1 2011"), "LLLL d yyyy")
          .add(forExample("11 January 2011"), "dd LLLL yyyy")
          .add(forExample("1 January 2011"), "d LLLL yyyy")
          .add(forExample("2011 January 1"), "yyyy LLLL d")
          .add(forExample("2011 January 11"), "yyyy LLLL dd")
          .add(forExample("T"), "'T'")
          .add(forExample("10:15"), "HH:mm")
          .add(forExample("10:15:30"), "HH:mm:ss")
          .add(forExample("10:15:30.1"), "HH:mm:ss.S")
          .add(forExample("10:15:30.12"), "HH:mm:ss.SS")
          .add(forExample("10:15:30.123"), "HH:mm:ss.SSS")
          .add(forExample("10:15:30.1234"), "HH:mm:ss.SSSS")
          .add(forExample("10:15:30.12345"), "HH:mm:ss.SSSSS")
          .add(forExample("10:15:30.123456"), "HH:mm:ss.SSSSSS")
          .add(forExample("10:15:30.1234567"), "HH:mm:ss.SSSSSSS")
          .add(forExample("10:15:30.12345678"), "HH:mm:ss.SSSSSSSS")
          .add(forExample("10:15:30.123456789"), "HH:mm:ss.SSSSSSSSS")
          .add(forExample("America/Los_Angeles"), "VV")
          .add(forExample("PST"), "zzz")
          .add(forExample("PT"), "zzz") // In Java 21 it can be "v"
          .add(forExample("Z"), "X")
          .add(forExample("+08"), "x")
          .add(forExample("-08"), "x")
          .add(forExample("+080000"), "xxxx")
          .add(forExample("-080000"), "xxxx")
          .add(forExample("+08:00:00"), "xxxxx")
          .add(forExample("-08:00:00"), "xxxxx")
          .add(forExample("+0800"), "ZZ")
          .add(forExample("-0800"), "ZZ")
          .add(forExample("+08:00"), "ZZZZZ")
          .add(forExample("-08:00"), "ZZZZZ")
          .add(forExample("GMT+8"), "O")
          .add(forExample("GMT-8"), "O")
          .add(forExample("GMT+12"), "O")
          .add(forExample("GMT-12"), "O")
          .add(forExample("GMT+08:00"), "OOOO")
          .add(forExample("GMT-08:00"), "OOOO")
          .add(forExample("Fri"), "E")
          .add(forExample("Friday"), "EEEE")
          .add(forExample("Jan"), "LLL")
          .add(forExample("January"), "LLLL")
          .add(forExample("PM"), "a")
          .add(forExample("AD"), "G")
          .build();

  /**
   * Infers and returns the {@link DateTimeFormatter} based on {@code example}.
   *
   * @throws IllegalArgumentException if {@code example} is invalid or the pattern isn't supported.
   */
  public static DateTimeFormatter formatOf(String example) {
    return inferFromExample(example);
  }

  /**
   * Just so that the parameterized tests can call it with test parameter strings that are
   * not @CompileTimeConstant.
   */
  static DateTimeFormatter inferFromExample(String example) {
    List<?> signature = forExample(example);
    DateTimeFormatter rfc = lookup(RFC_1123_FORMATTERS, signature).orElse(null);
    if (rfc != null) return rfc;
    DateTimeFormatter iso = lookup(ISO_DATE_FORMATTERS, signature).orElse(null);
    if (iso != null) return iso;
    // Ignore the ".nanosecond" part of the time in ISO examples because all ISO
    // time formats allow the nanosecond part optionally, with 1 to 9 digits.
    return lookup(ISO_DATE_TIME_FORMATTERS, forExample(removeNanosecondsPart(example)))
        .map(
            fmt -> {
              try {
                fmt.withResolverStyle(ResolverStyle.STRICT).parse(example);
              } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("invalid date time example: " + example, e);
              }
              return fmt;
            })
        .orElseGet(
            () -> {
              AtomicInteger placeholderCount = new AtomicInteger();
              String pattern =
                  PLACEHOLDERS.replaceAllFrom(
                      example,
                      placeholder -> {
                        placeholderCount.incrementAndGet();
                        return inferDateTimePattern(placeholder.skip(1, 1).toString());
                      });
              try {
                if (placeholderCount.get() > 0) {
                  // There is at least 1 placeholder. The input isn't a pure datetime "example".
                  // So we can't validate using parse().
                  return DateTimeFormatter.ofPattern(pattern);
                }
                pattern = inferDateTimePattern(example, signature);
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
                fmt.withResolverStyle(ResolverStyle.STRICT).parse(example);
                return fmt;
              } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                    "invalid date time example: " + example + " (" + pattern + ")", e);
              }
            });
  }

  static String inferDateTimePattern(String example) {
    return inferDateTimePattern(example, forExample(example));
  }

  private static String inferDateTimePattern(String example, List<?> signature) {
    boolean matched = false;
    int index = 0;
    StringBuilder builder = new StringBuilder();
    for (List<?> remaining = signature;
        remaining.size() > 0;
        remaining = signature.subList(index, signature.size())) {
      Object head = remaining.get(0);
      if (head instanceof String && DELIMITER.matchesAllOf((String) head)) {
        builder.append(head);
        index++;
        continue;
      }

      int consumed =
          PREFIX_TABLE
              .getAll(remaining)
              .collect(maxByKey(comparingInt(List::size)))
              .map(
                  (prefix, fmt) -> {
                    builder.append(fmt);
                    return prefix.size();
                  })
              .orElse(0);
      if (consumed <= 0) {
        throw new IllegalArgumentException("unsupported date time example: " + example);
      }
      index += consumed;
      matched = true;
    }
    if (!matched) {
      throw new IllegalArgumentException("unsupported date time example: " + example);
    }
    return builder.toString();
  }

  /**
   * Tokenizes {@code example} into a token list such that two examples of equal signatures are
   * considered equivalent.
   *
   * <p>An example string like {@code 2001-10-01 10:00:00 America/New_York} is considered to have
   * the same signature as {@code Tue 2023-01-20 05:09:00 Europe/Paris} because they have the same
   * number of digits for each part and the punctuations match, and their timezones are in the same
   * format. Both their signature lists will be: {@code [4, -, 2, -, 2, space, 2, : 2, :, 2, space,
   * word, /, word]}.
   *
   * <p>On the other hand {@code 10:01} and {@code 10:01:00} are considered to be different with
   * signature lists being: {@code [2, :, 2]} and {@code [2, :, 2, :, 2]} respectively.
   */
  private static List<?> forExample(String example) {
    return TOKENIZER
        .cut(example)
        .filter(Substring.Match::isNotEmpty)
        .map(
            match -> {
              if (DIGIT.matchesAnyOf(match)) {
                return match.length(); // the number of digits in the example matter
              }
              String name = match.toString();
              Token token = Token.ALL.get(name);
              if (token != null) {
                return token;
              }
              // Single-letter (including all punctuations) are reserved as format specifiers.
              // Spaces and delimiters are ignored during prefix matching and retained literally.
              // Unrecognized words are considered equivalent as they may be zone or geo names.
              return name.length() == 1 || DELIMITER.matchesAnyOf(name) ? name : Token.WORD;
            })
        .collect(toList());
  }

  private static String removeNanosecondsPart(String example) {
    return consecutive(DIGIT)
        .immediatelyBetween(":", INCLUSIVE, ".", INCLUSIVE) // the "":ss."" in "HH:mm:ss.nnnnn"
        .then(leading(DIGIT)) // the digits immediately after the ":ss." are the nanos
        .in(example)
        .map(nanos -> example.substring(0, nanos.index() - 1) + nanos.after())
        .orElse(example);
  }

  private enum Token {
    WEEKDAY_ABBREVIATION("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    WEEKDAY("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
    WEEKDAY_CODES("E", "EEEE"),
    MONTH_ABBREVIATION("Jan", "Feb", "Mar", "Apr", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    MONTH(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"),
    MONTH_CODES("L", "LLL", "LLLL"),
    YEAR_CODES("yyyy", "YYYY"),
    DAY_CODES("dd", "d"),
    HOUR_CODES("HH", "hh"),
    MINUTE_CODES("mm"),
    SECOND_CODES("ss"),
    AM_PM("am", "pm", "AM", "PM"),
    AD_BC("ad", "bc", "AD", "BC"),
    GENERIC_ZONE_NAME(
        "AT", "BT", "CT", "DT", "ET", "FT", "GT", "HT", "IT", "JT", "KT", "LT", "MT", "NT", "OT",
        "PT", "QT", "RT", "ST", "TT", "UT", "VT", "WT", "XT", "YT", "ZT"),
    ZONE_NAME(
        "ACDT", "ACST", "ACT", "ADT", "AEDT", "AEST", "AET", "AFT", "AKDT", "AKST", "AKT", "AMST",
        "AST", "AWDT", "AWST", "AWT", "AZOST", "AZT", "BDT", "BET", "BIOT", "BRT", "BST", "BTT",
        "CAST", "CAT", "CCT", "CDT", "CEDT", "CEST", "CET", "CHADT", "CHAST", "CHOST", "CHOT",
        "CHUT", "CIST", "CIT", "CKT", "CLST", "CLT", "CST", "CVT", "CWST", "CXT", "ChST", "DAVT",
        "DDUT", "DFT", "DUT", "EASST", "EAT", "ECT", "EDT", "EEDT", "EEST", "EET", "EGST", "EGT",
        "EIT", "EST", "FET", "FJT", "FKST", "FKT", "FNT", "GALT", "GAMT", "GFT", "GMT", "GST",
        "GYT", "HADT", "HAEC", "HAST", "HDT", "HKT", "HMT", "HOVT", "HST", "ICT", "IDT", "IOT",
        "IRDT", "IRKT", "IRST", "IST", "JST", "KGT", "KOST", "KRAT", "KST", "LHST", "LINT", "MAGT",
        "MAWT", "MDT", "MEST", "MET", "MHT", "MMT", "MSK", "MST", "MUT", "MVT", "MYT", "NCT", "NDT",
        "NFT", "NPT", "NST", "NUT", "NZDT", "NZST", "NZT", "OMST", "ORAT", "PDT", "PETT", "PGT",
        "PHOT", "PHT", "PKT", "PMDT", "PMST", "PONT", "PST", "RET", "ROTT", "SAKT", "SAMT", "SAST",
        "SBT", "SCT", "SGT", "SLT", "SRT", "SST", "SYOT", "TAHT", "TFT", "THA", "TJT", "TKT", "TLT",
        "TMT", "TVT", "UCT", "ULAT", "UTC", "UYST", "UYT", "UZT", "VLAT", "VOLT", "VOST", "VUT",
        "WAKT", "WAST", "WAT", "WEDT", "WEST", "WET", "WIB", "WIT", "WITA", "WST", "YAKT", "YEKT",
        "YET", "YKT", "YST"),
    ZONE_CODES("VV", "z", "zz", "zzz", "zzzz", "ZZ", "ZZZ", "ZZZZ", "ZZZZZ", "x", "X", "O", "OOOO"),
    REGION(
        "Africa",
        "America",
        "Antarctica",
        "Arctic",
        "Asia",
        "Atlantic",
        "Australia",
        "Brazil",
        "Canada",
        "Chile",
        "Cuba",
        "Egypt",
        "Eire",
        "Europe",
        "GB",
        "Greenwich",
        "Hongkong",
        "Iceland",
        "Indian",
        "Iran",
        "Israel",
        "Jamaica",
        "Japan",
        "Kwajalein",
        "Libya",
        "Mexico",
        "Mideast",
        "Navajo",
        "Pacific",
        "Poland",
        "Portugal",
        "Singapore",
        "SystemV",
        "Turkey",
        "US",
        "Universal",
        "Zulu"),
    WORD;

    static final Map<String, Token> ALL =
        biStream(Arrays.stream(Token.values()))
            .flatMapKeys(token -> token.names.stream())
            .toMap();

    private final Set<String> names;

    private Token(String... names) {
      this.names = new HashSet<String>(asList(names));
    }
  }

  private static <K, V> Optional<V> lookup(Map<K, ? extends V> map, Object key) {
    return Optional.ofNullable(map.get(key));
  }

  private DateTimeFormats() {}
}
