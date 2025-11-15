package com.google.common.labs.csv;

import static com.google.common.labs.csv.Csv.CSV;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.labs.parse.Parser;

@RunWith(JUnit4.class)
public final class CsvTest {
  @Test
  public void parseToLists_empty() {
    assertThat(CSV.parseToLists("")).isEmpty();
  }

  @Test
  public void parseToLists_emptyLines() {
    assertThat(CSV.parseToLists("\n")).containsExactly(List.of());
    assertThat(CSV.parseToLists("\n\n")).containsExactly(List.of(), List.of());
    assertThat(CSV.parseToLists("\n\r\n"))
        .containsExactly(List.of(), List.of());
    assertThat(CSV.parseToLists("\r\r\n"))
        .containsExactly(List.of(), List.of());
  }

  @Test
  public void parseToLists_emptyColumns() {
    assertThat(CSV.parseToLists("\"\"\n")).containsExactly(List.of(""));
    assertThat(CSV.parseToLists("\n\"\"\r\n\"\""))
        .containsExactly(List.of(), List.of(""), List.of(""));
    assertThat(CSV.parseToLists("\"\"\n\"\",\"\"\r\n\"\""))
        .containsExactly(List.of(""), List.of("", ""), List.of(""));
  }

  @Test
  public void parseToLists_singleColumnSingleRow() {
    assertThat(CSV.parseToLists("abc")).containsExactly(List.of("abc"));
  }

  @Test
  public void parseToLists_singleColumnMultipleRows() {
    assertThat(CSV.parseToLists("abc\ndef\nghi"))
        .containsExactly(List.of("abc"), List.of("def"), List.of("ghi"));
  }

  @Test
  public void parseToLists_emptyColumn() {
    assertThat(CSV.parseToLists("abc,,xyz"))
        .containsExactly(List.of("abc", "", "xyz"));
    assertThat(CSV.parseToLists(",,xyz"))
        .containsExactly(List.of("", "", "xyz"));
    assertThat(CSV.parseToLists(",,")).containsExactly(List.of("", "", ""));
    assertThat(CSV.parseToLists(",")).containsExactly(List.of("", ""));
  }

  @Test
  public void parseToLists_multipleColumnsSingleRow() {
    assertThat(CSV.parseToLists("abc,1234,5678"))
        .containsExactly(List.of("abc", "1234", "5678"));
    assertThat(CSV.parseToLists("abc,1234,5678\n"))
        .containsExactly(List.of("abc", "1234", "5678"));
    assertThat(CSV.parseToLists("abc,1234,5678\r\n"))
        .containsExactly(List.of("abc", "1234", "5678"));
  }

  @Test
  public void parseToLists_leadingSpaceRetained() {
    assertThat(CSV.parseToLists("abc, 1234,5678"))
        .containsExactly(List.of("abc", " 1234", "5678"));
  }

  @Test
  public void parseToLists_trailingSpaceRetained() {
    assertThat(CSV.parseToLists("abc,1234,5678 "))
        .containsExactly(List.of("abc", "1234", "5678 "));
  }

  @Test
  public void parseToLists_spaceAroundQuotesIgnored() {
    assertThat(CSV.parseToLists("\"abc\", \"1234\" ,\"5678\""))
        .containsExactly(List.of("abc", "1234", "5678"));
  }

  @Test
  public void parseToLists_quotedColumnWithComma() {
    assertThat(CSV.parseToLists("abc,\"1234,5678 \""))
        .containsExactly(List.of("abc", "1234,5678 "));
  }

  @Test
  public void parseToLists_quotedColumnWithNewline() {
    assertThat(CSV.parseToLists("abc,\"1234\n5678\""))
        .containsExactly(List.of("abc", "1234\n5678"));
  }

  @Test
  public void parseToLists_quotedColumnWithNewline_multipleRows() {
    assertThat(CSV.parseToLists("abc,\"1234\n5678\"\nxyz,\"9876\n5432\"\n"))
        .containsExactly(
            List.of("abc", "1234\n5678"), List.of("xyz", "9876\n5432"));
  }

  @Test
  public void parseToLists_quotedColumnWithEscapedQuote() {
    assertThat(CSV.parseToLists("abc,\"1234\"\"5678\""))
        .containsExactly(List.of("abc", "1234\"5678"));
  }

  @Test
  public void parseToLists_multipleColumnsMultipleRows() {
    assertThat(CSV.parseToLists("abc,1234,5678\nxyz,9876,5432"))
        .containsExactly(
            List.of("abc", "1234", "5678"), List.of("xyz", "9876", "5432"));
  }

  @Test
  public void parseToLists_lazy_laterInvalidRowsNotScanned() {
    String secondRowInvalid = "abc,1234,5678\n\"invalid";
    assertThat(CSV.parseToLists(secondRowInvalid).limit(1))
        .containsExactly(List.of("abc", "1234", "5678"));
    Parser.ParseException thrown =
        assertThrows(
            Parser.ParseException.class,
            () -> CSV.parseToLists(secondRowInvalid).toList());
    assertThat(thrown).hasMessageThat().contains("expecting <\">, encountered <EOF>");
  }

  @Test
  public void parseToLists_emptyCommentRow() {
    assertThat(CSV.withComments().parseToLists("#")).isEmpty();
    assertThat(CSV.withComments().parseToLists("#\n")).isEmpty();
  }

  @Test
  public void parseToLists_singleCommentRow() {
    assertThat(CSV.withComments().parseToLists("# this is a comment")).isEmpty();
    assertThat(CSV.withComments().parseToLists("#this is a comment\n")).isEmpty();
  }

  @Test
  public void parseToLists_multipleCommentRows() {
    assertThat(
            CSV.withComments().parseToLists("# comment 1\n# comment 2\r\n# comment 3"))
        .isEmpty();
  }

  @Test
  public void parseToLists_withCommentAndDataRows() {
    assertThat(CSV.withComments().parseToLists("# header\nabc,123\n# comment\nxyz,987"))
        .containsExactly(List.of("abc", "123"), List.of("xyz", "987"));
  }

  @Test
  public void parseToLists_withCustomDelimiter() {
    assertThat(CSV.withDelimiter('|').parseToLists("abc|1234|5678"))
        .containsExactly(List.of("abc", "1234", "5678"));
  }

  @Test
  public void parseToMaps_emptyInput() {
    assertThat(CSV.parseToMaps("")) .isEmpty();
  }

  @Test
  public void parseToMaps_withOnlyBlankLines() {
    assertThat(CSV.parseToMaps("\n")) .isEmpty();
    assertThat(CSV.parseToMaps("\n\n")).isEmpty();
    assertThat(CSV.parseToMaps("\n \n")).isEmpty();
  }

  @Test
  public void parseToMaps_withOnlyHeaderRow() {
    assertThat(CSV.parseToMaps("name,publisher\n")).isEmpty();
  }

  @Test
  public void parseToMaps_emptyRowsAreIgnored() {
    assertThat(CSV.parseToMaps("h1,h2\n\nv1,v2"))
        .containsExactly(ImmutableMap.of("h1", "v1", "h2", "v2"));
  }

  @Test
  public void parseToMaps_oneDataRow() {
    assertThat(CSV.parseToMaps("h1,h2\nv1,v2"))
        .containsExactly(ImmutableMap.of("h1", "v1", "h2", "v2"));
  }

  @Test
  public void parseToMaps_withEmptyHeaderName() {
    assertThat(CSV.parseToMaps("h1,\nv1,v2"))
        .containsExactly(ImmutableMap.of("h1", "v1", "", "v2"));
  }

  @Test
  public void parseToMaps_twoDataRows() {
    assertThat(CSV.parseToMaps("h1,h2\nv1,v2\nv3,v4"))
        .containsExactly(
            ImmutableMap.of("h1", "v1", "h2", "v2"), ImmutableMap.of("h1", "v3", "h2", "v4"));
  }

  @Test
  public void parseToMaps_fewerFieldsInDataRow() {
    assertThat(CSV.parseToMaps("h1,h2\nv1")).containsExactly(ImmutableMap.of("h1", "v1"));
  }

  @Test
  public void parseToMaps_moreFieldsInDataRow() {
    assertThat(CSV.parseToMaps("h1,h2\nv1,v2,v3"))
        .containsExactly(ImmutableMap.of("h1", "v1", "h2", "v2"));
  }

  @Test
  public void parseToMaps_mixedDataRows() {
    String input = "h1,h2,h3\nv1\nv4,v5,v6,v7\nv8,v9";
    assertThat(CSV.parseToMaps(input))
        .containsExactly(
            ImmutableMap.of("h1", "v1"),
            ImmutableMap.of("h1", "v4", "h2", "v5", "h3", "v6"),
            ImmutableMap.of("h1", "v8", "h2", "v9"));
  }

  @Test
  public void parseToMaps_duplicateColumnName_lastWins() {
    assertThat(CSV.parseToMaps("name,name,age\nYang,Jing,28"))
        .containsExactly(ImmutableMap.of("name", "Jing", "age", "28"));
  }

  @Test
  public void parseToMaps_withComments_commentRowsSkipped() {
    assertThat(CSV.withComments().parseToMaps("#c1\n#c2\nh1,h2\n#c3\nv1,v2\n#c4\nv3,v4\n#c5"))
        .containsExactly(
            ImmutableMap.of("h1", "v1", "h2", "v2"), ImmutableMap.of("h1", "v3", "h2", "v4"));
  }

  @Test
  public void parseToMaps_withComments_onlyCommentRows() {
    assertThat(CSV.withComments().parseToMaps("#c1\n#c2")).isEmpty();
  }

  @Test
  public void parseToMaps_withComments_headerAndCommentRows() {
    assertThat(CSV.withComments().parseToMaps("#c1\nh1,h2\n#c2")).isEmpty();
  }

  @Test
  public void parseWithHeaderFields_duplicateColumnName_keepBoth() {
    assertThat(CSV.parseWithHeaderFields("name,name,age\nYang,Jing,28", ImmutableListMultimap::toImmutableListMultimap))
        .containsExactly(ImmutableListMultimap.of("name", "Yang", "name", "Jing", "age", "28"));
  }

  @Test public void quoteNotBeforeComma() {
    var thrown = assertThrows(Parser.ParseException.class, () -> CSV.parseToLists("\"a\"b, c").toList());
    assertThat(thrown).hasMessageThat().contains("1:4");
  }

  @Test public void unescapedQuote() {
    var thrown = assertThrows(Parser.ParseException.class, () -> CSV.parseToLists("\"foo\"bar\"").toList());
    assertThat(thrown).hasMessageThat().contains("1:6");
  }

  @Test
  public void invalidDelimiter() {
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('\r'));
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('\n'));
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('"'));
    assertThrows(IllegalArgumentException.class, () -> CSV.withDelimiter('#'));
  }
}
