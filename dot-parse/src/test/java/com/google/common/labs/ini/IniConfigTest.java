package com.google.common.labs.ini;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.util.Map;

import org.junit.Test;

import com.google.common.labs.ini.IniConfig.Section;
import com.google.mu.time.DateTimeFormats;

public class IniConfigTest {

  @Test public void getBoolean_true() {
    assertThat(IniConfig.parse("enabled = true").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = True").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = TRUE").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = yes").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = Yes").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = YES").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = on").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = On").defaultSection().is("enabled")).isTrue();
    assertThat(IniConfig.parse("enabled = ON").defaultSection().is("enabled")).isTrue();
  }

  @Test public void getBoolean_false() {
    assertThat(IniConfig.parse("enabled = false").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = False").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = FALSE").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = no").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = No").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = NO").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = off").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = Off").defaultSection().is("enabled")).isFalse();
    assertThat(IniConfig.parse("enabled = OFF").defaultSection().is("enabled")).isFalse();
  }

  @Test public void getBoolean_notFound() {
    var section = IniConfig.parse("enabled = false").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.is("what"));
    assertThat(thrown).hasMessageThat().contains("what");
    assertThat(thrown).hasMessageThat().contains("[enabled]");
  }

  @Test public void getInt_found() {
    assertThat(IniConfig.parse("max_size = 10").defaultSection().getInt("max_size")).isEqualTo(10);
    assertThat(IniConfig.parse("max_size = -10").defaultSection().getInt("max_size")).isEqualTo(-10);
  }

  @Test public void getInt_notFound() {
    var section = IniConfig.parse("max_size = 10").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInt("min_size"));
    assertThat(thrown).hasMessageThat().contains("min_size");
    assertThat(thrown).hasMessageThat().contains("[max_size]");
  }

  @Test public void getInt_invalid() {
    var section = IniConfig.parse("max_size = a0").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInt("max_size"));
    assertThat(thrown).hasMessageThat().contains("max_size = a0");
    assertThat(thrown).hasMessageThat().contains("isn't valid integer");
  }

  @Test public void getInts_found() {
    assertThat(IniConfig.parse("max_size = 10").defaultSection().getInts("max_size")).containsExactly(10);
    assertThat(IniConfig.parse("max_size = -10, 20").defaultSection().getInts("max_size")).containsExactly(-10, 20);
  }

  @Test public void getInts_notFound() {
    var section = IniConfig.parse("[size] max_size = 10").section("size");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInts("min_size"));
    assertThat(thrown).hasMessageThat().contains("section [size]");
    assertThat(thrown).hasMessageThat().contains("min_size");
    assertThat(thrown).hasMessageThat().contains("[max_size]");
  }

  @Test public void getInts_invalid() {
    var section = IniConfig.parse("max_size = 10,").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInts("max_size"));
    assertThat(thrown).hasMessageThat().contains("max_size = 10,");
    assertThat(thrown).hasMessageThat().contains("isn't valid integer list");
  }

  @Test public void getLong_found() {
    assertThat(IniConfig.parse("max_size = 10").defaultSection().getLong("max_size")).isEqualTo(10);
    assertThat(IniConfig.parse("max_size = -10").defaultSection().getLong("max_size")).isEqualTo(-10);
  }

  @Test public void getLong_notFound() {
    var section = IniConfig.parse("max_size = 10").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getLong("min_size"));
    assertThat(thrown).hasMessageThat().contains("min_size");
    assertThat(thrown).hasMessageThat().contains("[max_size]");
  }

  @Test public void getLong_invalid() {
    var section = IniConfig.parse("max_size = a0").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getLong("max_size"));
    assertThat(thrown).hasMessageThat().contains("max_size = a0");
    assertThat(thrown).hasMessageThat().contains("isn't valid long");
  }

  @Test public void getLongs_found() {
    assertThat(IniConfig.parse("max_size = 10, 20").defaultSection().getLongs("max_size")).containsExactly(10L, 20L);
    assertThat(IniConfig.parse("max_size = -10").defaultSection().getLongs("max_size")).containsExactly(-10L);
  }

  @Test public void getLongs_notFound() {
    var section = IniConfig.parse("max_size = 10").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getLongs("min_size"));
    assertThat(thrown).hasMessageThat().contains("min_size");
    assertThat(thrown).hasMessageThat().contains("[max_size]");
  }

  @Test public void getLongs_invalid() {
    var section = IniConfig.parse("max_size = a0").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getLongs("max_size"));
    assertThat(thrown).hasMessageThat().contains("max_size = a0");
    assertThat(thrown).hasMessageThat().contains("isn't valid long list");
  }

  @Test public void getString_found() {
    assertThat(IniConfig.parse("prefix = abc,def").defaultSection().getString("prefix")).isEqualTo("abc,def");
  }

  @Test public void getString_notFound() {
    var section = IniConfig.parse("prefix = abc").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getString("suffix"));
    assertThat(thrown).hasMessageThat().contains("suffix");
    assertThat(thrown).hasMessageThat().contains("[prefix]");
  }

  @Test public void getEnum_found() {
    assertThat(IniConfig.parse("status = ACTIVE").defaultSection().getEnum("status", Status.class)).isEqualTo(Status.ACTIVE);
  }

  @Test public void getEnum_notFound() {
    var section = IniConfig.parse("status = ACTIVE").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getEnum("suffix", Status.class));
    assertThat(thrown).hasMessageThat().contains("suffix");
    assertThat(thrown).hasMessageThat().contains("[status]");
  }

  @Test public void getEnum_invalid() {
    var section = IniConfig.parse("status = na").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getEnum("status", Status.class));
    assertThat(thrown).hasMessageThat().contains("status = na");
    assertThat(thrown).hasMessageThat().contains("isn't a valid enum");
    assertThat(thrown).hasMessageThat().contains("Status");
  }

  @Test public void getEnums_found() {
    assertThat(IniConfig.parse("status = ACTIVE, INACTIVE").defaultSection().getEnums("status", Status.class))
        .containsExactly(Status.ACTIVE, Status.INACTIVE)
        .inOrder();
  }

  @Test public void getEnums_notFound() {
    var section = IniConfig.parse("status = ACTIVE").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getEnums("suffix", Status.class));
    assertThat(thrown).hasMessageThat().contains("suffix");
    assertThat(thrown).hasMessageThat().contains("[status]");
  }

  @Test public void getEnums_invalid() {
    var section = IniConfig.parse("status = ACTIVE,inactive").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getEnums("status", Status.class));
    assertThat(thrown).hasMessageThat().contains("status = ACTIVE,inactive");
    assertThat(thrown).hasMessageThat().contains("isn't valid List");
    assertThat(thrown).hasMessageThat().contains("Status");
  }

  @Test public void getInstant_found() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    assertThat(section.getInstant("start_time"))
        .isEqualTo(DateTimeFormats.parseToInstant("2025-10-30 15:30:00 America/Los_Angeles"));
  }

  @Test public void getInstant_notFound() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInstant("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_time]");
  }

  @Test public void getInstant_invalid() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInstant("start_time"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_time =");
  }

  @Test public void getInstants_found() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles, 2025-04-01 10:00:00-07:00")
        .defaultSection();
    assertThat(section.getInstants("start_time"))
        .containsExactly(
            DateTimeFormats.parseToInstant("2025-10-30 15:30:00 America/Los_Angeles"),
            DateTimeFormats.parseToInstant("2025-04-01 10:00:00-07:00"))
        .inOrder();
  }

  @Test public void getInstants_notFound() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInstants("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_time]");
  }

  @Test public void getInstants_invalid() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getInstants("start_time"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_time =");
  }

  @Test public void getZonedDateTime_found() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    assertThat(section.getZonedDateTime("start_time"))
        .isEqualTo(DateTimeFormats.parseZonedDateTime("2025-10-30 15:30:00 America/Los_Angeles"));
  }

  @Test public void getZonedDateTime_notFound() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getZonedDateTime("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_time]");
  }

  @Test public void getZonedDateTime_invalid() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getZonedDateTime("start_time"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_time =");
  }

  @Test public void getZonedDateTimeList_found() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles, 2025-04-01 10:00:00-07:00")
        .defaultSection();
    assertThat(section.getZonedDateTimeList("start_time"))
        .containsExactly(
            DateTimeFormats.parseZonedDateTime("2025-10-30 15:30:00 America/Los_Angeles"),
            DateTimeFormats.parseZonedDateTime("2025-04-01 10:00:00-07:00"))
        .inOrder();
  }

  @Test public void getZonedDateTimeList_notFound() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getZonedDateTimeList("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_time]");
  }

  @Test public void getZonedDateTimeList_invalid() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getZonedDateTimeList("start_time"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_time =");
  }

  @Test public void getOffsetDateTime_found() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00+03:00").defaultSection();
    assertThat(section.getOffsetDateTime("start_time"))
        .isEqualTo(DateTimeFormats.parseOffsetDateTime("2025-10-30 15:30:00+03:00"));
  }

  @Test public void getOffsetDateTime_notFound() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getOffsetDateTime("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_time]");
  }

  @Test public void getOffsetDateTime_invalid() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getOffsetDateTime("start_time"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_time =");
  }

  @Test public void getOffsetDateTimeList_found() {
    var section = IniConfig.parse("start_time = 2025-01-01 10:00:00-07:00, 2025-04-01 10:00:00-07:00")
        .defaultSection();
    assertThat(section.getOffsetDateTimeList("start_time"))
        .containsExactly(
            DateTimeFormats.parseOffsetDateTime("2025-01-01 10:00:00-07:00"),
            DateTimeFormats.parseOffsetDateTime("2025-04-01 10:00:00-07:00"))
        .inOrder();
  }

  @Test public void getOffsetDateTimeList_notFound() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getOffsetDateTimeList("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_time]");
  }

  @Test public void getOffsetDateTimeList_invalid() {
    var section = IniConfig.parse("start_time = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getOffsetDateTimeList("start_time"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_time =");
  }

  @Test public void getDate_found() {
    var section = IniConfig.parse("start_date = 2025-10-30").defaultSection();
    assertThat(section.getDate("start_date")).isEqualTo(LocalDate.parse("2025-10-30"));
  }

  @Test public void getDate_notFound() {
    var section = IniConfig.parse("start_date = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getDate("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_date]");
  }

  @Test public void getDate_invalid() {
    var section = IniConfig.parse("start_date = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getDate("start_date"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_date =");
  }

  @Test public void getDates_found() {
    var section = IniConfig.parse("start_date = 2025-01-01,2025-04-01").defaultSection();
    assertThat(section.getDates("start_date"))
        .containsExactly(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-04-01"))
        .inOrder();
  }

  @Test public void getDates_notFound() {
    var section = IniConfig.parse("start_date = 2025-10-30 15:30:00 America/Los_Angeles").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getDates("end_date"));
    assertThat(thrown).hasMessageThat().contains("end_date");
    assertThat(thrown).hasMessageThat().contains("[start_date]");
  }

  @Test public void getDates_invalid() {
    var section = IniConfig.parse("start_date = 2025-10-30 15:30:00 Beijing").defaultSection();
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> section.getDates("start_date"));
    assertThat(thrown).hasMessageThat().contains("Beijing");
    assertThat(thrown).hasMessageThat().contains("start_date =");
  }

  @Test public void sections_duplicateSectionName() {
    var config = IniConfig.parse("[plugin]\n name = jing\n[plugin]\n name=yang");
    assertThat(config.sections("plugin"))
        .containsExactly(new Section("plugin", Map.of("name", "jing")), new Section("plugin", Map.of("name", "yang")))
        .inOrder();
    assertThat(config.sections("[plugin]"))
        .containsExactly(new Section("plugin", Map.of("name", "jing")), new Section("plugin", Map.of("name", "yang")))
        .inOrder();
  }

  @Test public void section_duplicateSectionName() {
    var config = IniConfig.parse("[plugin]\n name = jing\n[plugin]\n name=yang");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> config.section("plugin"));
    assertThat(thrown).hasMessageThat().contains("section [plugin] is ambiguous");
  }

  @Test public void sectionNameIncludesSquareBrackets_found() {
    var section = IniConfig.parse("[launch] start_time = 2025-10-30 10:00:00-07").section("[launch]");
    assertThat(section.getInstant("start_time")).isEqualTo(DateTimeFormats.parseToInstant("2025-10-30 10:00:00-07"));
  }

  @Test public void sectionNameIncludesSquareBrackets_notFound() {
    var config = IniConfig.parse("[launch] start_time = 2025-10-30 10:00:00-07");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> config.section("[not launch]"));
    assertThat(thrown).hasMessageThat().contains("section [not launch]");
    assertThat(thrown).hasMessageThat().contains("[launch]");
  }

  @Test
  public void parse_emptyInput() {
    assertThat(IniConfig.parse("")).isEqualTo(new IniConfig(Map.of()));
  }

  @Test
  public void parse_commentOnly() {
    assertThat(IniConfig.parse("# this is only a comment")).isEqualTo(new IniConfig(Map.of()));
  }

  @Test
  public void parse_sectionlessKeyValues() {
    assertThat(IniConfig.parse("key = value")).isEqualTo(IniConfig.of(Map.of("key", "value")));
    assertThat(IniConfig.parse("k1 = v1\nk2 = v2")).isEqualTo(IniConfig.of(Map.of("k1", "v1", "k2", "v2")));
  }

  @Test
  public void parse_duplicateKeyValues() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> IniConfig.parse("k = v1\nk= v2"));
    assertThat(thrown).hasMessageThat().contains("[k]");
  }

  @Test
  public void parse_spacesInKeyValues() {
    assertThat(IniConfig.parse("my key = my value")).isEqualTo(IniConfig.of(Map.of("my key", "my value")));
  }

  @Test
  public void parse_keyValueFollowedByComment() {
    assertThat(IniConfig.parse("key = value # and comment")).isEqualTo(IniConfig.of(Map.of("key", "value")));
  }

  @Test
  public void parse_valueIsEmpty() {
    assertThat(IniConfig.parse("key =")).isEqualTo(IniConfig.of(Map.of("key", "")));
    assertThat(IniConfig.parse("key = # nothing")).isEqualTo(IniConfig.of(Map.of("key", "")));
  }

  @Test
  public void parse_emptySection() {
    String input = "[profile]";
    assertThat(IniConfig.parse(input)).isEqualTo(IniConfig.of(new Section("profile", Map.of())));
  }

  @Test
  public void parse_keyValuesInSection() {
    String input =
        """
        [profile] # it's a profile
        name = John Doe #it's not me
        email = jdoe@example.com
        """;
    assertThat(IniConfig.parse(input))
        .isEqualTo(IniConfig.of(new Section("profile", Map.of("name", "John Doe", "email", "jdoe@example.com"))));
  }

  @Test
  public void parse_spaceInSectionName() {
    String input =
        """
        [ my profile ] # can include space
        name = John Doe
        email = jdoe@example.com
        """;
    assertThat(IniConfig.parse(input))
        .isEqualTo(IniConfig.of(new Section("my profile", Map.of("name", "John Doe", "email", "jdoe@example.com"))));
  }

  @Test
  public void parse_multipleSections() {
    String input =
        """
        [profile]
        name = John Doe #it's not me
        email = jdoe@example.com
        [credentials]
        password = abcdef
        """;
    assertThat(IniConfig.parse(input))
        .isEqualTo(
            IniConfig.of(
                new Section(
                "profile",
                Map.of("name", "John Doe", "email", "jdoe@example.com")),
                new Section(
                "credentials",
                Map.of("password", "abcdef"))));
  }

  @Test
  public void parse_sectionsOfSameName() {
    String input =
        """
        [profile]
        name = John Doe #it's not me
        [profile]
        email = jdoe@example.com
        """;
    var result = IniConfig.parse(input);
    assertThat(result)
        .isEqualTo(
            IniConfig.of(
                new Section("profile", Map.of("name", "John Doe")),
                new Section("profile", Map.of("email", "jdoe@example.com"))));
  }

  private enum Status {ACTIVE, INACTIVE}
}
