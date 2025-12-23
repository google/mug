package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SqlTextOutlineTest {

  @Test
  public void getEnclosedBy_noCommentOrLiteral() {
    SqlTextOutline outline = new SqlTextOutline("select 1");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(8)).isEmpty();
  }

  @Test
  public void getEnclosedBy_lineComment_noTrailingNewline() {
    SqlTextOutline outline = new SqlTextOutline("select 1 -- line comment");
    assertThat(outline.getEnclosedBy(9)).isEmpty(); // space before --
    assertThat(outline.getEnclosedBy(10)).isEqualTo("--"); // -
    assertThat(outline.getEnclosedBy(11)).isEqualTo("--"); // -
    assertThat(outline.getEnclosedBy(12)).isEqualTo("--"); // space
    assertThat(outline.getEnclosedBy(23)).isEqualTo("--"); // t
  }

  @Test
  public void getEnclosedBy_lineComment_withTrailingNewline() {
    SqlTextOutline outline = new SqlTextOutline("-- line comment\nselect 1");
    assertThat(outline.getEnclosedBy(1)).isEqualTo("--");
    assertThat(outline.getEnclosedBy(15)).isEqualTo("--"); // t
    assertThat(outline.getEnclosedBy(16)).isEmpty(); // \n
    assertThat(outline.getEnclosedBy(17)).isEmpty(); // s in select
  }

  @Test
  public void getEnclosedBy_blockComment_terminated() {
    SqlTextOutline outline = new SqlTextOutline("select 1 /* block comment */");
    assertThat(outline.getEnclosedBy(9)).isEmpty(); // space before /*
    assertThat(outline.getEnclosedBy(10)).isEqualTo("/*"); // /
    assertThat(outline.getEnclosedBy(11)).isEqualTo("/*"); // *
    assertThat(outline.getEnclosedBy(26)).isEqualTo("/*"); // /
    assertThat(outline.getEnclosedBy(28)).isEmpty(); // after /
  }

  @Test
  public void getEnclosedBy_blockComment_unterminated() {
    SqlTextOutline outline = new SqlTextOutline("s /* ");
    assertThat(outline.getEnclosedBy(2)).isEmpty();
    assertThat(outline.getEnclosedBy(4)).isEqualTo("/*");
    assertThat(outline.getEnclosedBy(5)).isEmpty();
  }

  @Test
  public void getEnclosedBy_stringLiteral_terminated() {
    SqlTextOutline outline = new SqlTextOutline("select 'string literal'");
    assertThat(outline.getEnclosedBy(7)).isEmpty(); // space before '
    assertThat(outline.getEnclosedBy(8)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(9)).isEqualTo("'"); // s
    assertThat(outline.getEnclosedBy(22)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(23)).isEmpty(); // after '
  }

  @Test
  public void getEnclosedBy_stringLiteral_unterminated() {
    SqlTextOutline outline = new SqlTextOutline("s 'x''yz");
    assertThat(outline.getEnclosedBy(2)).isEmpty();
    assertThat(outline.getEnclosedBy(5)).isEqualTo("'");
    assertThat(outline.getEnclosedBy(6)).isEqualTo("'");
  }

  @Test
  public void getEnclosedBy_stringLiteral_withEscapedQuote() {
    SqlTextOutline outline = new SqlTextOutline("select 'string '' literal'");
    assertThat(outline.getEnclosedBy(8)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(15)).isEqualTo("'"); // first ' in ''
    assertThat(outline.getEnclosedBy(16)).isEqualTo("'"); // second ' in ''
    assertThat(outline.getEnclosedBy(25)).isEqualTo("'"); // last '
    assertThat(outline.getEnclosedBy(26)).isEmpty();
  }

  @Test
  public void getEnclosedBy_stringLiteral_withEscapedQuoteAtEnd() {
    SqlTextOutline outline = new SqlTextOutline(" 's '''");
    assertThat(outline.getEnclosedBy(1)).isEmpty();
    assertThat(outline.getEnclosedBy(2)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(5)).isEqualTo("'"); // first ' in ''
    assertThat(outline.getEnclosedBy(6)).isEqualTo("'"); // second ' in ''
    assertThat(outline.getEnclosedBy(7)).isEmpty();
  }

  @Test
  public void getEnclosedBy_lineComment_containingBlockCommentStart() {
    SqlTextOutline outline = new SqlTextOutline("-- commented /* not count */");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("--");
    assertThat(outline.getEnclosedBy(13)).isEqualTo("--"); // '/' in /*
    assertThat(outline.getEnclosedBy(14)).isEqualTo("--"); // '*' in /*
  }

  @Test
  public void getEnclosedBy_blockComment_containingLineCommentStart() {
    SqlTextOutline outline = new SqlTextOutline("/* --no line comment */");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("/*");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("/*"); // '-' in --
    assertThat(outline.getEnclosedBy(4)).isEqualTo("/*"); // '-' in --
    assertThat(outline.getEnclosedBy(21)).isEqualTo("/*"); // / in */
    assertThat(outline.getEnclosedBy(22)).isEqualTo("/*");
    assertThat(outline.getEnclosedBy(23)).isEmpty();
  }

  @Test
  public void getEnclosedBy_lineComment_containingQuote() {
    SqlTextOutline outline = new SqlTextOutline("-- 'not quoted'");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("--");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("--"); // '
  }

  @Test
  public void getEnclosedBy_blockComment_containingQuote() {
    SqlTextOutline outline = new SqlTextOutline("/* 'not quoted' */");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("/*");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("/*"); // '
    assertThat(outline.getEnclosedBy(14)).isEqualTo("/*"); // '
    assertThat(outline.getEnclosedBy(18)).isEmpty(); // / in */
  }

  @Test
  public void getEnclosedBy_stringLiteral_containingLineComment() {
    SqlTextOutline outline = new SqlTextOutline("'--not line comment'");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("'"); // '-'
    assertThat(outline.getEnclosedBy(2)).isEqualTo("'"); // '-'
    assertThat(outline.getEnclosedBy(19)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(20)).isEmpty();
  }

  @Test
  public void getEnclosedBy_stringLiteral_containingBlockComment() {
    SqlTextOutline outline = new SqlTextOutline("'/*not block comment */'");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("'"); // '/'
    assertThat(outline.getEnclosedBy(2)).isEqualTo("'"); // '*'
    assertThat(outline.getEnclosedBy(22)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(23)).isEqualTo("'");
    assertThat(outline.getEnclosedBy(24)).isEmpty();
  }

  @Test
  public void getEnclosedBy_backtickQuote_terminated() {
    SqlTextOutline outline = new SqlTextOutline("select `backtick`");
    assertThat(outline.getEnclosedBy(7)).isEmpty(); // space before `
    assertThat(outline.getEnclosedBy(8)).isEqualTo("`"); // `
    assertThat(outline.getEnclosedBy(9)).isEqualTo("`"); // b
    assertThat(outline.getEnclosedBy(16)).isEqualTo("`"); // `
    assertThat(outline.getEnclosedBy(17)).isEmpty(); // after `
  }

  @Test
  public void getEnclosedBy_backtickQuote_unterminated() {
    SqlTextOutline outline = new SqlTextOutline("s `xy");
    assertThat(outline.getEnclosedBy(2)).isEmpty();
    assertThat(outline.getEnclosedBy(3)).isEqualTo("`");
    assertThat(outline.getEnclosedBy(4)).isEqualTo("`");
  }

  @Test
  public void getEnclosedBy_doubleQuote_terminated() {
    SqlTextOutline outline = new SqlTextOutline("select \"double\"");
    assertThat(outline.getEnclosedBy(7)).isEmpty(); // space before "
    assertThat(outline.getEnclosedBy(8)).isEqualTo("\""); // "
    assertThat(outline.getEnclosedBy(9)).isEqualTo("\""); // d
    assertThat(outline.getEnclosedBy(14)).isEqualTo("\""); // "
    assertThat(outline.getEnclosedBy(15)).isEmpty(); // after "
  }

  @Test
  public void getEnclosedBy_doubleQuote_unterminated() {
    SqlTextOutline outline = new SqlTextOutline("s \"xy");
    assertThat(outline.getEnclosedBy(2)).isEmpty();
    assertThat(outline.getEnclosedBy(3)).isEqualTo("\"");
    assertThat(outline.getEnclosedBy(4)).isEqualTo("\"");
  }

  @Test
  public void getEnclosedBy_lineComment_containingBacktick() {
    SqlTextOutline outline = new SqlTextOutline("-- `not quoted`");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("--");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("--"); // `
  }

  @Test
  public void getEnclosedBy_lineComment_containingDoubleQuote() {
    SqlTextOutline outline = new SqlTextOutline("-- \"not quoted\"");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("--");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("--"); // "
  }

  @Test
  public void getEnclosedBy_blockComment_containingBacktick() {
    SqlTextOutline outline = new SqlTextOutline("/* `not quoted` */");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("/*");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("/*"); // `
    assertThat(outline.getEnclosedBy(14)).isEqualTo("/*"); // `
    assertThat(outline.getEnclosedBy(18)).isEmpty(); // / in */
  }

  @Test
  public void getEnclosedBy_blockComment_containingDoubleQuote() {
    SqlTextOutline outline = new SqlTextOutline("/* \"not quoted\" */");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("/*");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("/*"); // "
    assertThat(outline.getEnclosedBy(14)).isEqualTo("/*"); // "
    assertThat(outline.getEnclosedBy(18)).isEmpty(); // / in */
  }

  @Test
  public void getEnclosedBy_singleQuote_containingBacktick() {
    SqlTextOutline outline = new SqlTextOutline("' `not quoted` '");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("'");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("'"); // `
  }

  @Test
  public void getEnclosedBy_singleQuote_containingDoubleQuote() {
    SqlTextOutline outline = new SqlTextOutline("' \"not quoted\" '");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("'");
    assertThat(outline.getEnclosedBy(3)).isEqualTo("'"); // "
  }

  @Test
  public void getEnclosedBy_backtickQuote_containingLineComment() {
    SqlTextOutline outline = new SqlTextOutline("`--not line comment`");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("`"); // '-'
    assertThat(outline.getEnclosedBy(2)).isEqualTo("`"); // '-'
    assertThat(outline.getEnclosedBy(19)).isEqualTo("`"); // `
    assertThat(outline.getEnclosedBy(20)).isEmpty();
  }

  @Test
  public void getEnclosedBy_backtickQuote_containingBlockComment() {
    SqlTextOutline outline = new SqlTextOutline("`/*not block comment */`");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("`"); // '/'
    assertThat(outline.getEnclosedBy(2)).isEqualTo("`"); // '*'
    assertThat(outline.getEnclosedBy(22)).isEqualTo("`"); // `
    assertThat(outline.getEnclosedBy(23)).isEqualTo("`");
    assertThat(outline.getEnclosedBy(24)).isEmpty();
  }

  @Test
  public void getEnclosedBy_doubleQuote_containingLineComment() {
    SqlTextOutline outline = new SqlTextOutline("\"--not line comment\"");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("\""); // '-'
    assertThat(outline.getEnclosedBy(2)).isEqualTo("\""); // '-'
    assertThat(outline.getEnclosedBy(19)).isEqualTo("\""); // "
    assertThat(outline.getEnclosedBy(20)).isEmpty();
  }

  @Test
  public void getEnclosedBy_doubleQuote_containingBlockComment() {
    SqlTextOutline outline = new SqlTextOutline("\"/*not block comment */\"");
    assertThat(outline.getEnclosedBy(0)).isEmpty();
    assertThat(outline.getEnclosedBy(1)).isEqualTo("\""); // '/'
    assertThat(outline.getEnclosedBy(2)).isEqualTo("\""); // '*'
    assertThat(outline.getEnclosedBy(22)).isEqualTo("\""); // "
    assertThat(outline.getEnclosedBy(23)).isEqualTo("\"");
    assertThat(outline.getEnclosedBy(24)).isEmpty();
  }

  @Test
  public void getEnclosedBy_twoSingleQuotedStrings() {
    SqlTextOutline outline = new SqlTextOutline("'string1' and 'string2'");
    assertThat(outline.getEnclosedBy(0)).isEmpty(); // '
    assertThat(outline.getEnclosedBy(1)).isEqualTo("'"); // s
    assertThat(outline.getEnclosedBy(8)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(9)).isEmpty(); // space
    assertThat(outline.getEnclosedBy(10)).isEmpty(); // a
    assertThat(outline.getEnclosedBy(11)).isEmpty(); // n
    assertThat(outline.getEnclosedBy(12)).isEmpty(); // d
    assertThat(outline.getEnclosedBy(13)).isEmpty(); // space
    assertThat(outline.getEnclosedBy(14)).isEmpty(); // '
    assertThat(outline.getEnclosedBy(15)).isEqualTo("'"); // s
    assertThat(outline.getEnclosedBy(22)).isEqualTo("'"); // '
    assertThat(outline.getEnclosedBy(23)).isEmpty();
  }
}
