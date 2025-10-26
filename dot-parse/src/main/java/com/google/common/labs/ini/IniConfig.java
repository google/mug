package com.google.common.labs.ini;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.BiCollectors.groupingBy;
import static com.google.mu.util.stream.BiStream.toBiStream;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.labs.parse.Parser;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.time.DateTimeFormats;
import com.google.mu.util.Both;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;

/**
 * A light-weight INI syntax parser, supporting key-values and sections.
 *
 * <p>The syntax looks like TOML (with sections, comments and key-value pairs). But no quoting or
 * escaping, and whitespaces around keys and values are ignored. For example:
 *
 * <pre>
 * # optional global key values
 * enabled = true
 * active_time = 2025-10-30 15:30:00 America/Los_Angeles
 * denied_statuses = DELETED, BLOCKED
 *
 * [user profile]  # line end comment
 * name = John Doe
 * home address = 123 Main St, Anytown, CA 98765
 *
 * [credentials]
 * username = john # comment not included in value
 * password = abcdef
 * </pre>
 *
 * <p>A typical usage pattern: <pre>{@code
 * var config = IniConfig.parse(input);
 *
 * boolean enabled = config.defaultSection().is("enabled");
 * Instant activeTime = config.defaultSection().getInstant("start_time");
 * List<Status> deniedStatuses =
 *     config.defaultSection().getEnumList("denied_statuses", Status.class);
 * String userName = config.section("credentials").getString("username");
 * }</pre>
 *
 * <p>TOML list of tables is supported by using multiple sections of the same name
 * (i.e. {@code [plugin]}, not {@code [[plugins]]}), and you can access these sections using
 * the {@link #sections(String)} method.
 *
 * @since 9.4
 */
@Immutable
@SuppressWarnings("Immutable") // Map is immutable
public record IniConfig(Map<String, List<Section>> sections) {
  public IniConfig {
    requireNonNull(sections);
  }

  static IniConfig of(Map<String, String> keyValues) {
    return of(new Section("", keyValues));
  }

  static IniConfig of(Section... sections) {
    return new IniConfig(
        stream(sections)
            .collect(BiStream.groupingBy(Section::name, toUnmodifiableList()))
            .toMap());
  }

  /**
   * Parses a simplified ini-style configuration from {@code file}.
   *
   * @throws UncheckedIOException if reading failed.
   */
  public static IniConfig parse(File file) {
    try {
      return parse(Files.readString(file.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }


  /** Parses a simplified ini-style configuration from the {@code input} string. */
  public static IniConfig parse(String input) {
    Parser<?> comment = string("#").followedBy(zeroOrMore(isNot('\n'), "commented"));
    Parser<String> sectionName =
        consecutive(noneOf("#[]\n\""), "section name").between("[", "]").map(String::trim);
    Parser<String> key = consecutive(noneOf("#=\n[]"), "key").map(String::trim);
    Parser<String> value = consecutive(noneOf("#\n"), "value").map(String::trim);
    Parser<Map<String, String>> sectionBody =
        sequence(key.followedBy("="), value.orElse(""), Both::of)
            .atLeastOnce(toBiStream(identity()))
            .map(BiStream::toMap);
    var parser =
        sequence(
            sectionBody
                .map(defaultKeyValues -> BiStream.of("", defaultKeyValues))
                .orElse(BiStream.empty()),
            sequence(sectionName, sectionBody.orElse(Map.of()), Both::of)
                .zeroOrMore(toBiStream(identity())),
            BiStream::concat);
    return new IniConfig(
        parser.parseSkipping(
                anyOf(comment, consecutive(Character::isWhitespace, "whitespace")), input)
            .mapValues(Section::new)
            .collect(groupingBy(name -> name, toUnmodifiableList()))
            .toMap());
  }

  /** Returns the default (section-less) key values, or an empty section is returned. */
  public Section defaultSection() {
    return sections.getOrDefault("", List.of(new Section("", Map.of()))).get(0);
  }

  /**
   * Returns the section identified by {@code sectionName}, case-sensitively.
   *
   * @param sectionName the section name as in {@code [profile]}
   * @throws IllegalArgumentException if {@code sectionName} isn't found in the config
   */
  public Section section(String sectionName) {
    sectionName = normalizeSectionName(sectionName);
    List<Section> candidates = sections(sectionName);
    if (candidates.size() > 1) {
      throw new IllegalArgumentException(
          "section [" + sectionName + "] is ambiguous. Consider using getSections() instead.");
    }
    return candidates.get(0);
  }

  /**
   * Returns the potentially multiple sections identified by {@code sectionName}, case-sensitively.
   * Useful for configuring what's known as "array of tables" in TOML, but instead of using the
   * {@code [[section]]} syntax, you use the same {@code [section]} syntax.
   *
   * @throws IllegalArgumentException if {@code sectionName} isn't found in the config
   */
  public List<Section> sections(String sectionName) {
    sectionName = normalizeSectionName(sectionName);
    List<Section> found = sections.get(sectionName);
    if (found == null) {
      throw new IllegalArgumentException(
          "section [" + sectionName + "] isn't among the known sections: " + sections.keySet());
    }
    return found;
  }

  /** A section is a collection of key value pairs. */
  @Immutable
  public record Section(String name, Map<String, String> keyValues) {
    private static final Set<String> TRUE = Set.of("TRUE", "True", "true", "YES", "Yes", "yes", "ON", "On", "on");
    private static final Set<String> FALSE = Set.of("FALSE", "False", "false", "NO", "No", "no", "OFF", "Off", "off");

    public Section {
      requireNonNull(name);
      requireNonNull(keyValues);
    }

    /**
     * Returns the boolean value configured by {@code key} in this section.
     *
     * <p>True, TRUE, true, Yes, YES, yes, On, ON are all considered true;
     * False, FALSE, false, No, NO, no, Off, OFF are considered false.
     */
    public boolean is(String key) {
      String value = getString(key);
      if (TRUE.contains(value)) {
        return true;
      }
      if (FALSE.contains(value)) {
        return false;
      }
      throw new IllegalArgumentException(
          key + " = " + value + " in section [" + name + "] isn't boolean");
    }

    /** Returns the int value configured by {@code key} in this section. */
    public int getInt(String key) {
      return getAndParse(key, Integer::parseInt ,"isn't valid integer");
    }

    /** Returns the long value configured by {@code key} in this section. */
    public long getLong(String key) {
      return getAndParse(key, Long::parseLong ,"isn't valid long");
    }

    /** Returns the double value configured by {@code key} in this section. */
    public double getDouble(String key) {
      return getAndParse(key, Double::parseDouble ,"isn't valid double");
    }

    /** Returns the enum value configured by {@code key} in this section. */
    public <E extends Enum<?>> E getEnum(String key, Class<E> enumType) {
      requireNonNull(enumType);
      String value = getString(key);
      return stream(enumType.getEnumConstants())
          .filter(e -> e.name().equals(value))
          .findFirst()
          .orElseThrow(
              () -> new IllegalArgumentException(
                  key + " = " + value + " in section [" + name + "] isn't a valid enum constant of " + enumType));
    }

    /** Returns the {@link LocalDate} value configured by {@code key} in this section. */
    public LocalDate getDate(String key) {
      return getAndParse(key, LocalDate::parse ,"isn't valid LocalDate");
    }

    /** Returns the {@link Instant} value configured by {@code key} in this section. */
    public Instant getInstant(String key) {
      return getAndParse(key, DateTimeFormats::parseToInstant ,"isn't valid time");
    }

    /** Returns the {@link ZonedDateTime} value configured by {@code key} in this section. */
    public ZonedDateTime getZonedDateTime(String key) {
      return getAndParse(key, DateTimeFormats::parseZonedDateTime ,"isn't valid ZonedDateTime");
    }

    /** Returns the {@link OffsetDateTime} value configured by {@code key} in this section. */
    public OffsetDateTime getOffsetDateTime(String key) {
      return getAndParse(key, DateTimeFormats::parseOffsetDateTime ,"isn't valid OffsetDateTime");
    }

    /** Returns the int values configured by {@code key} (separated by commas), in this section. */
    public List<Integer> getIntList(String key) {
      return getAndParse(
          key, value -> parseListValues(value, Integer::parseInt), "isn't valid integer list");
    }

    /** Returns the long values configured by {@code key} (separated by commas), in this section. */
    public List<Long> getLongList(String key) {
      return getAndParse(
          key, value -> parseListValues(value, Long::parseLong), "isn't valid long list");
    }

    /** Returns the double values configured by {@code key} (separated by commas), in this section. */
    public List<Double> getDoubleList(String key) {
      return getAndParse(
          key, value -> parseListValues(value, Double::parseDouble), "isn't valid double list");
    }

    /** Returns the enum values configured by {@code key} (separated by commas), in this section. */
    public <E extends Enum<?>> List<E> getEnumList(String key, Class<E> enumType) {
      Map<String, E> constants = stream(enumType.getEnumConstants()).
          collect(Collectors.toMap(Enum::name, identity()));
      return getAndParse(
          key,
          value -> parseListValues(value, n -> {
            E e = constants.get(n.toString());
            if (e == null) {
              throw new IllegalArgumentException(n + " isn't a valid constant of " + enumType);
            }
            return e;
          }),
          "isn't valid List<%s>", enumType.getName());
    }

    /** Returns the {#link LocalDate} values configured by {@code key} (separated by commas), in this section. */
    public List<LocalDate> getDateList(String key) {
      return getAndParse(
          key, value -> parseListValues(value, LocalDate::parse), "isn't valid List<LocalDate>");
    }

    /** Returns the {#link Instant} values configured by {@code key} (separated by commas), in this section. */
    public List<Instant> getInstantList(String key) {
      return getAndParse(
          key, value -> parseListValues(value, DateTimeFormats::parseToInstant), "isn't valid List<Instant>");
    }

    /** Returns the {#link ZonedDateTime} values configured by {@code key} (separated by commas), in this section. */
    public List<ZonedDateTime> getZonedDateTimeList(String key) {
      return getAndParse(
          key,
          value -> parseListValues(value, DateTimeFormats::parseZonedDateTime),
          "isn't valid List<ZonedDateTime>");
    }

    /** Returns the {#link OffsetDateTime} values configured by {@code key} (separated by commas), in this section. */
    public List<OffsetDateTime> getOffsetDateTimeList(String key) {
      return getAndParse(
          key,
          value -> parseListValues(value, DateTimeFormats::parseOffsetDateTime),
          "isn't valid List<OffsetDateTime>");
    }

    /** Returns the string value configured by {@code key} (leading and trailing spaces ignored), in this section. */
    public String getString(String key) {
      requireNonNull(key);
      String value = keyValues.get(key);
      if (value == null) {
        throw new IllegalArgumentException(
            "key `" + key + "` not among the keys in section [" + name + "]: "
                + keyValues.keySet());
      }
      return value;
    }

    private <T> T getAndParse(String key, Function<String, T> parse, String message, Object... args) {
      String value = getString(key);
      try {
        return parse.apply(value);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(
            key + " = " + value + " in section [" + name + "] " + String.format(message, args), e);
      }
    }

    private static <T> List<T> parseListValues(String value, Function<? super String, ? extends T> parse) {
      return Substring.all(',')
          .splitThenTrim(value)
          .filter(Substring.Match::isNotEmpty)
          .map(CharSequence::toString)
          .map(parse)
          .collect(toUnmodifiableList());
    }
  }

  private static String normalizeSectionName(String sectionName) {
    return Substring.between(prefix('['), suffix(']')).from(sectionName).orElse(sectionName);
  }
}
