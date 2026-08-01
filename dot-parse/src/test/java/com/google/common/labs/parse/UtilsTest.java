package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UtilsTest {

  @Test public void caseInsensitivePrefixes_emptyString() {
    assertThat(Utils.caseInsensitivePrefixes("", 1)).containsExactly("");
  }

  @Test public void caseInsensitivePrefixes_maxLengthIsZero() {
    assertThat(Utils.caseInsensitivePrefixes("foo", 0)).containsExactly("");
  }

  @Test public void caseInsensitivePrefixes_maxLengthIsOne() {
    assertThat(Utils.caseInsensitivePrefixes("foo", 1)).containsExactly("f", "F").inOrder();
  }

  @Test public void caseInsensitivePrefixes_maxLengthIsTwo() {
    assertThat(Utils.caseInsensitivePrefixes("foo", 2)).containsExactly("fo", "fO", "Fo", "FO")
        .inOrder();
  }

  @Test public void caseInsensitivePrefixes_maxLengthIsThree() {
    assertThat(Utils.caseInsensitivePrefixes("jing", 3))
        .containsExactly("jin", "jiN", "jIn", "jIN", "Jin", "JiN", "JIn", "JIN")
        .inOrder();
  }

  @Test public void caseInsensitivePrefixes_lowerAndUpperCaseTheSame() {
    assertThat(Utils.caseInsensitivePrefixes("a12", 2)).containsExactly("a1", "A1")
        .inOrder();
  }

  @Test public void caseInsensitivePrefixes_maxLengthIsGreatherThanStringLength() {
    assertThat(Utils.caseInsensitivePrefixes("ab", 20)).containsExactly("ab", "aB", "Ab", "AB")
        .inOrder();
  }

  @Test public void caseInsensitivePrefixes_maxLengthIsNegative() {
    assertThrows(IllegalArgumentException.class, () -> Utils.caseInsensitivePrefixes("ab", -1));
  }
}
