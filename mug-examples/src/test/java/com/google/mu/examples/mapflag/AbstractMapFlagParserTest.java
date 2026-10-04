package com.google.mu.examples.mapflag;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public abstract class AbstractMapFlagParserTest {

  protected abstract Map<String, Object> parse(String input);

  @Test public void emptyMap() {
    assertThat(parse("{}")).isEmpty();
  }

  @Test public void whitespaceInEmptyMap() {
    assertThat(parse("  { \n\t }  ")).isEmpty();
  }

  @Test public void singleStringEntry() {
    assertThat(parse("{k1 = \"v1\"}")).containsExactly("k1", "v1");
  }

  @Test public void singleIntegerEntry() {
    assertThat(parse("{k2 = 3}")).containsExactly("k2", 3);
  }

  @Test public void singleNegativeIntegerEntry() {
    assertThat(parse("{k2 = -42}")).containsExactly("k2", -42);
  }

  @Test public void singleDecimalEntry() {
    assertThat(parse("{pi = 3.14}")).containsExactly("pi", 3.14);
  }

  @Test public void singleNegativeDecimalEntry() {
    assertThat(parse("{neg = -0.5}")).containsExactly("neg", -0.5);
  }

  @Test public void zeroValue() {
    assertThat(parse("{k = 0}")).containsExactly("k", 0);
  }

  @Test public void decimalWithZeroIntegerPart() {
    assertThat(parse("{k = 0.5}")).containsExactly("k", 0.5);
  }

  @Test public void decimalWithLeadingZeroInFraction() {
    assertThat(parse("{k = 1.05}")).containsExactly("k", 1.05);
  }

  @Test public void escapedCharactersInString() {
    assertThat(parse("{k = \"a\\\"b\\\\c\"}")).containsExactly("k", "a\"b\\c");
  }

  @Test public void escapedNewlineInString() {
    assertThat(parse("{k = \"a\\\nb\"}")).containsExactly("k", "a\nb");
  }

  @Test public void delimitersInsideQuotedString() {
    assertThat(parse("{k = \"a, b = [1, 2], }\"}")).containsExactly("k", "a, b = [1, 2], }");
  }

  @Test public void emptyListValue() {
    assertThat(parse("{k = []}")).containsExactly("k", List.of());
  }

  @Test public void whitespaceInEmptyListValue() {
    assertThat(parse("{k = [ \t\n ]}")).containsExactly("k", List.of());
  }

  @Test public void listOfNumbersAndStrings() {
    assertThat(parse("{k = [10, -20.5, \"thirty\"]}"))
        .containsExactly("k", List.of(10, -20.5, "thirty"));
  }

  @Test public void listWithTrailingComma() {
    assertThat(parse("{k = [10, 20.5, ]}")).containsExactly("k", List.of(10, 20.5));
  }

  @Test public void mapWithTrailingComma() {
    assertThat(parse("{k1 = \"v1\", k2 = 3, }")).containsExactly("k1", "v1", "k2", 3).inOrder();
  }

  @Test public void multipleEntriesWithMixedTypes() {
    assertThat(parse("{k1 = \"v1\", k2=3, k3=[10, 20.5], }"))
        .containsExactly("k1", "v1", "k2", 3, "k3", List.of(10, 20.5))
        .inOrder();
  }

  @Test public void keyWithHyphenAndUnderscore() {
    assertThat(parse("{my_key-1 = 100}")).containsExactly("my_key-1", 100);
  }

  @Test public void numericKey() {
    assertThat(parse("{123 = \"val\"}")).containsExactly("123", "val");
  }

  @Test public void numericKeyWithLeadingZero() {
    assertThat(parse("{05 = 1}")).containsExactly("05", 1);
  }

  @Test public void freeWhitespaceAroundTokens() {
    assertThat(parse("\n { \t k1 \n = \r \"v1\" , k2 = [ 1 , 2 , ] , } \t"))
        .containsExactly("k1", "v1", "k2", List.of(1, 2))
        .inOrder();
  }

  @Test public void formFeedAsWhitespace() {
    assertThat(parse("{\fk = 1}")).containsExactly("k", 1);
  }

  @Test public void unicodeSpaceAsWhitespace() {
    assertThat(parse("{k =\u2003 1}")).containsExactly("k", 1);
  }

  @Test public void emptyInput_fails() {
    assertThrows(RuntimeException.class, () -> parse(""));
  }

  @Test public void missingOpenBrace_fails() {
    assertThrows(RuntimeException.class, () -> parse("k1 = \"v1\"}"));
  }

  @Test public void missingCloseBrace_fails() {
    assertThrows(RuntimeException.class, () -> parse("{k1 = \"v1\""));
  }

  @Test public void missingEquals_fails() {
    assertThrows(RuntimeException.class, () -> parse("{k1 \"v1\"}"));
  }

  @Test public void missingValue_fails() {
    assertThrows(RuntimeException.class, () -> parse("{k1 = }"));
  }

  @Test public void missingKey_fails() {
    assertThrows(RuntimeException.class, () -> parse("{= 1}"));
  }

  @Test public void missingCommaBetweenEntries_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = 1 b = 2}"));
  }

  @Test public void missingCommaBetweenListElements_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = [1 2]}"));
  }

  @Test public void doubleCommaInMap_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = 1, , b = 2}"));
  }

  @Test public void doubleCommaInList_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = [1, , 2]}"));
  }

  @Test public void nestedList_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = [[1]]}"));
  }

  @Test public void nestedMap_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = {b = 1}}"));
  }

  @Test public void nestedMapInList_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = [{b = 1}]}"));
  }

  @Test public void unclosedString_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = \"hello}"));
  }

  @Test public void unquotedStringValue_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = hello}"));
  }

  @Test public void whitespaceAfterNegativeSign_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = - 5}"));
  }

  @Test public void leadingZero_fails() {
    assertThrows(RuntimeException.class, () -> parse("{k = 05}"));
  }

  @Test public void negativeLeadingZero_fails() {
    assertThrows(RuntimeException.class, () -> parse("{k = -05}"));
  }

  @Test public void leadingZeroInDecimal_fails() {
    assertThrows(RuntimeException.class, () -> parse("{k = 05.5}"));
  }

  @Test public void decimalAsKey_fails() {
    assertThrows(RuntimeException.class, () -> parse("{1.5 = 2}"));
  }

  @Test public void duplicateKey_fails() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> parse("{dup = 1, dup = 2}"));
    assertThat(thrown).hasMessageThat().contains("Duplicate key");
  }

  @Test public void trailingGarbageAfterCloseBrace_fails() {
    assertThrows(RuntimeException.class, () -> parse("{a = 1} extra"));
  }

  @Test public void leadingGarbageBeforeOpenBrace_fails() {
    assertThrows(RuntimeException.class, () -> parse("extra {a = 1}"));
  }

  @Test public void nullInput_fails() {
    assertThrows(NullPointerException.class, () -> parse(null));
  }
}
