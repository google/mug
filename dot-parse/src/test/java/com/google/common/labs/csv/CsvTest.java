package com.google.common.labs.csv;

import static com.google.common.labs.csv.Csv.CSV;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.labs.parse.Parser;

@RunWith(JUnit4.class)
public final class CsvTest {
  @Test
  public void parse_empty() {
    assertThat(CSV.parse("", toUnmodifiableList())).isEmpty();
  }

  @Test
  public void parse_emptyLines() {
    assertThat(CSV.parse("\n", toUnmodifiableList())).containsExactly(ImmutableList.of());
    assertThat(CSV.parse("\n\r\n", toUnmodifiableList()))
        .containsExactly(ImmutableList.of(), ImmutableList.of());
    assertThat(CSV.parse("\r\r\n", toUnmodifiableList()))
        .containsExactly(ImmutableList.of(), ImmutableList.of());
  }

  @Test
  public void parse_emptyColumns() {
    assertThat(CSV.parse("\"\"\n", toUnmodifiableList())).containsExactly(ImmutableList.of(""));
    assertThat(CSV.parse("\n\"\"\r\n\"\"", toUnmodifiableList()))
        .containsExactly(ImmutableList.of(), ImmutableList.of(""), ImmutableList.of(""));
    assertThat(CSV.parse("\"\"\n\"\",\"\"\r\n\"\"", toUnmodifiableList()))
        .containsExactly(ImmutableList.of(""), ImmutableList.of("", ""), ImmutableList.of(""));
  }

  @Test
  public void parse_singleColumnSingleRow() {
    assertThat(CSV.parse("abc", toUnmodifiableList())).containsExactly(ImmutableList.of("abc"));
  }

  @Test
  public void parse_singleColumnMultipleRows() {
    assertThat(CSV.parse("abc\ndef\nghi", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc"), ImmutableList.of("def"), ImmutableList.of("ghi"));
  }

  @Test
  public void parse_emptyColumn() {
    assertThat(CSV.parse("abc,,xyz", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "", "xyz"));
    assertThat(CSV.parse(",,xyz", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("", "", "xyz"));
    assertThat(CSV.parse(",,", toUnmodifiableList())).containsExactly(ImmutableList.of("", "", ""));
    assertThat(CSV.parse(",", toUnmodifiableList())).containsExactly(ImmutableList.of("", ""));
  }

  @Test
  public void parse_multipleColumnsSingleRow() {
    assertThat(CSV.parse("abc,1234,5678", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234", "5678"));
    assertThat(CSV.parse("abc,1234,5678\n", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234", "5678"));
    assertThat(CSV.parse("abc,1234,5678\r\n", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234", "5678"));
  }

  @Test
  public void parse_leadingSpaceRetained() {
    assertThat(CSV.parse("abc, 1234,5678", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", " 1234", "5678"));
  }

  @Test
  public void parse_trailingSpaceRetained() {
    assertThat(CSV.parse("abc,1234,5678 ", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234", "5678 "));
  }

  @Test
  public void parse_quotedColumnWithComma() {
    assertThat(CSV.parse("abc,\"1234,5678 \"", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234,5678 "));
  }

  @Test
  public void parse_quotedColumnWithNewline() {
    assertThat(CSV.parse("abc,\"1234\n5678\"", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234\n5678"));
  }

  @Test
  public void parse_quotedColumnWithNewline_multipleRows() {
    assertThat(CSV.parse("abc,\"1234\n5678\"\nxyz,\"9876\n5432\"\n", toUnmodifiableList()))
        .containsExactly(
            ImmutableList.of("abc", "1234\n5678"), ImmutableList.of("xyz", "9876\n5432"));
  }

  @Test
  public void parse_quotedColumnWithEscapedQuote() {
    assertThat(CSV.parse("abc,\"1234\"\"5678\"", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234\"5678"));
  }

  @Test
  public void parse_multipleColumnsMultipleRows() {
    assertThat(CSV.parse("abc,1234,5678\nxyz,9876,5432", toUnmodifiableList()))
        .containsExactly(
            ImmutableList.of("abc", "1234", "5678"), ImmutableList.of("xyz", "9876", "5432"));
  }

  @Test
  public void parse_lazy_laterInvalidRowsNotScanned() {
    String secondRowInvalid = "abc,1234,5678\n\"invalid";
    assertThat(CSV.parse(secondRowInvalid, toUnmodifiableList()).limit(1))
        .containsExactly(ImmutableList.of("abc", "1234", "5678"));
    Parser.ParseException thrown =
        assertThrows(
            Parser.ParseException.class,
            () -> CSV.parse(secondRowInvalid, toUnmodifiableList()).toList());
    assertThat(thrown).hasMessageThat().contains("expecting `\"`");
  }

  @Test
  public void parse_emptyCommentRow() {
    assertThat(CSV.withComments().parse("#", toUnmodifiableList())).isEmpty();
    assertThat(CSV.withComments().parse("#\n", toUnmodifiableList())).isEmpty();
  }

  @Test
  public void parse_singleCommentRow() {
    assertThat(CSV.withComments().parse("# this is a comment", toUnmodifiableList())).isEmpty();
    assertThat(CSV.withComments().parse("#this is a comment\n", toUnmodifiableList())).isEmpty();
  }

  @Test
  public void parse_multipleCommentRows() {
    assertThat(
            CSV.withComments().parse("# comment 1\n# comment 2\r\n# comment 3", toUnmodifiableList()))
        .isEmpty();
  }

  @Test
  public void parse_withCommentAndDataRows() {
    assertThat(CSV.withComments().parse("# header\nabc,123\n# comment\nxyz,987", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "123"), ImmutableList.of("xyz", "987"));
  }

  @Test
  public void parse_withCustomDelimiter() {
    assertThat(CSV.withDelimiter('|').parse("abc|1234|5678", toUnmodifiableList()))
        .containsExactly(ImmutableList.of("abc", "1234", "5678"));
  }

  @Test
  public void parseToMaps_emptyRowsAreIgnored() {
    assertThat(Csv.CSV.parseToMaps("h1,h2\n\nv1,v2"))
        .containsExactly(ImmutableMap.of("h1", "v1", "h2", "v2"));
  }

  @Test
  public void parseToMaps_oneDataRow() {
    assertThat(Csv.CSV.parseToMaps("h1,h2\nv1,v2"))
        .containsExactly(ImmutableMap.of("h1", "v1", "h2", "v2"));
  }

  @Test
  public void parseToMaps_twoDataRows() {
    assertThat(Csv.CSV.parseToMaps("h1,h2\nv1,v2\nv3,v4"))
        .containsExactly(
            ImmutableMap.of("h1", "v1", "h2", "v2"), ImmutableMap.of("h1", "v3", "h2", "v4"));
  }

  @Test
  public void parseToMaps_fewerFieldsInDataRow() {
    assertThat(Csv.CSV.parseToMaps("h1,h2\nv1")).containsExactly(ImmutableMap.of("h1", "v1"));
  }

  @Test
  public void parseToMaps_moreFieldsInDataRow() {
    assertThat(Csv.CSV.parseToMaps("h1,h2\nv1,v2,v3"))
        .containsExactly(ImmutableMap.of("h1", "v1", "h2", "v2"));
  }

  @Test
  public void parseToMaps_mixedDataRows() {
    String input = "h1,h2,h3\nv1\nv4,v5,v6,v7\nv8,v9";
    assertThat(Csv.CSV.parseToMaps(input))
        .containsExactly(
            ImmutableMap.of("h1", "v1"),
            ImmutableMap.of("h1", "v4", "h2", "v5", "h3", "v6"),
            ImmutableMap.of("h1", "v8", "h2", "v9"));
  }

  @Test
  public void invalidDelimiter() {
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('\r'));
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('\n'));
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('"'));
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('#'));
  }
}
