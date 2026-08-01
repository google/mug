package com.google.mu.benchmarks.parsers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public abstract class AbstractUsPhoneParserTest {

  protected abstract String parseSingle(String input) throws Throwable;

  protected abstract List<String> parseList(String input) throws Throwable;

  @Test
  public void testSingle_valid() throws Throwable {
    assertThat(parseSingle(BenchmarkInputs.US_PHONE)).isEqualTo(BenchmarkInputs.US_PHONE);
  }

  @Test
  public void testSingle_invalid_tooLong() {
    assertThrows(Throwable.class, () -> parseSingle(BenchmarkInputs.US_PHONE_INVALID));
  }

  @Test
  public void testSingle_invalid_tooShort() {
    assertThrows(Throwable.class, () -> parseSingle("(650)123-456"));
  }

  @Test
  public void testSingle_invalid_missingParens() {
    assertThrows(Throwable.class, () -> parseSingle("650-123-4567"));
  }

  @Test
  public void testSingle_invalid_internalWhitespace() {
    assertThrows(Throwable.class, () -> parseSingle("(650) 123-4567"));
  }

  @Test
  public void testSingle_invalid_wrongDashPattern() {
    assertThrows(Throwable.class, () -> parseSingle("1234-56-7890"));
  }

  @Test
  public void testList_emptyString() throws Throwable {
    assertThat(parseList("")).isEmpty();
  }

  @Test
  public void testList_whitespaceOnly() throws Throwable {
    assertThat(parseList("   \n\t  ")).isEmpty();
  }

  @Test
  public void testList_singleElement() throws Throwable {
    assertThat(parseList(BenchmarkInputs.US_PHONE)).containsExactly(BenchmarkInputs.US_PHONE);
  }

  @Test
  public void testList_singleElementWithSurroundingWhitespace() throws Throwable {
    assertThat(parseList("  " + BenchmarkInputs.US_PHONE + " \n "))
        .containsExactly(BenchmarkInputs.US_PHONE);
  }

  @Test
  public void testList_multipleElements() throws Throwable {
    List<String> result = parseList(BenchmarkInputs.US_PHONE_LIST);
    assertThat(result).hasSize(1000);
    assertThat(result.get(0)).isEqualTo("(000)000-0000");
    assertThat(result.get(999)).isEqualTo("(999)993-2987");
  }
}
