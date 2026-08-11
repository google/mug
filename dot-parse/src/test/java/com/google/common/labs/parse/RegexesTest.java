/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.parse.Regexes.PrefixAnalyzer;
import com.google.common.labs.regex.RegexPattern;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RegexesTest {

  @Test public void prefixesOf_literal() {
    assertThat(prefixes("abc")).containsExactly("abc");
    assertThat(prefixes("")).containsExactly("");
    assertThat(prefixes("a\uD83D\uDE00b")).containsExactly("a\uD83D\uDE00b");
  }

  @Test public void prefixesOf_sequence() {
    assertThat(prefixes("abc")).containsExactly("abc");
    assertThat(prefixes("a*b")).containsExactly("a", "b");
    assertThat(prefixes("(a|b)?c")).containsExactly("a", "b", "c");
    assertThat(prefixes("a*b*c")).containsExactly("a", "b", "c");
    assertThat(prefixes("a*b\\d")).containsExactly("a", "b");
    assertThat(prefixes("a\\d")).containsExactly("a");
  }

  @Test public void prefixesOf_alternation() {
    assertThat(prefixes("a|b")).containsExactly("a", "b");
    assertThat(prefixes("a|\\w")).containsExactly("");
  }

  @Test public void prefixesOf_group() {
    assertThat(prefixes("(abc)")).containsExactly("abc");
    assertThat(prefixes("(?:abc)")).containsExactly("abc");
    assertThat(prefixes("(?<name>abc)")).containsExactly("abc");
  }

  @Test public void prefixesOf_quantified() {
    assertThat(prefixes("a+")).containsExactly("a");
  }

  @Test public void prefixesOf_characterSet() {
    assertThat(prefixes("[abc]")).containsExactly("a", "b", "c");
    assertThat(prefixes("[a-c]")).containsExactly("a", "b", "c");
    assertThat(prefixes("[^abc]")).containsExactly("");
    assertThat(prefixes("[a-c\\d]")).containsExactly("");
  }

  @Test public void prefixesOf_largeCharRange_fallback() {
    assertThat(prefixes("[ -z]")).containsExactly(""); // size 91, > 30 limit
    assertThat(prefixes("[ -?]")).containsExactly(""); // size 32, > 30 limit
    assertThat(prefixes("[\u0080-\u00FF]")).containsExactly(""); // non-ASCII range
  }

  @Test public void prefixesOf_largeCharRange_boundary() {
    assertThat(prefixes("[a-z]")).hasSize(26); // size 26, under 30 limit
  }

  @Test public void prefixesOf_smallNonAsciiRange_fallback() {
    assertThat(prefixes("[\u0080-\u008A]")).containsExactly(""); // small non-ASCII range
  }

  @Test public void prefixesOf_predefinedCharClass() {
    assertThat(prefixes("\\d")).containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    assertThat(prefixes("\\w")).containsExactly("");
  }

  @Test public void prefixesOf_caseInsensitive() {
    assertThat(prefixes("(?i:b)")).containsExactly("b", "B");
    assertThat(prefixes("(?i:abc)"))
        .containsExactly("abc", "abC", "aBc", "aBC", "Abc", "AbC", "ABc", "ABC");
    assertThat(prefixes("(?i:abcd)"))
        .containsExactly("abc", "abC", "aBc", "aBC", "Abc", "AbC", "ABc", "ABC");
    assertThat(prefixes("(?i:a)b")).containsExactly("a", "A");
    assertThat(prefixes("(?i:[a])")).containsExactly("a", "A");
    assertThat(prefixes("(?i:[a-c])")).containsExactly("a", "A", "b", "B", "c", "C");
    assertThat(prefixes("(?i:a\uD83D\uDE00b)")).containsExactly("a\uD83D\uDE00", "A\uD83D\uDE00");
    assertThat(prefixes("(?i:\uD83D\uDE00b)")).containsExactly("\uD83D\uDE00b", "\uD83D\uDE00B");
    assertThat(prefixes("(?i:\uD83D\uDE00\uD83D\uDE01b)")).containsExactly("\uD83D\uDE00\uD83D");
  }

  @Test public void prefixesOf_disabledCaseInsensitive() {
    assertThat(prefixes("(?i:a?(?-i:b))")).containsExactly("a", "A", "b");
  }

  @Test public void prefixesOf_conflictingCaseInsensitive() {
    assertThat(prefixes("(?i-i:b)")).containsExactly("b");
  }

  @Test public void prefixesOf_unicodeCharacterClass() {
    assertThat(prefixes("(?U:\\d)")).containsExactly("");
  }

  @Test public void prefixesOf_disabledUnicodeCharacterClass() {
    assertThat(prefixes("(?U:(?-U:\\d))"))
        .containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
  }

  @Test public void prefixesOf_conflictingUnicodeCharacterClass() {
    assertThat(prefixes("(?U-U:\\d)"))
        .containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
  }

  @Test public void prefixesOf_anchorsAndLookaroundsAndBackreferences() {
    assertThat(prefixes("^")).containsExactly("");
    assertThat(prefixes("(?=a)")).containsExactly("");
    assertThat(prefixes("(a)\\1")).containsExactly("a");
  }

  @Test public void maxSize_literal() {
    assertThat(maxSize("abc")).isEqualTo(3);
  }

  @Test public void maxSize_optional() {
    assertThat(maxSize("a?")).isEqualTo(1);
  }

  @Test public void maxSize_infiniteQuantifier() {
    assertThat(maxSize("a*")).isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void maxSize_limitedQuantifier() {
    assertThat(maxSize("(abc){2,4}")).isEqualTo(12);
  }

  @Test public void maxSize_caseInsensitive() {
    assertThat(maxSize("(?i:abc)")).isEqualTo(3);
  }

  @Test public void maxSize_dot() {
    assertThat(maxSize(".")).isEqualTo(2);
  }

  @Test public void maxSize_characterClass() {
    assertThat(maxSize("[abc]")).isEqualTo(2);
  }

  @Test public void maxSize_predefinedCharacterClass() {
    assertThat(maxSize("\\d")).isEqualTo(2);
  }

  @Test public void maxSize_alternation() {
    assertThat(maxSize("a|bc")).isEqualTo(2);
  }

  private static Set<String> prefixes(String regex) {
    return new PrefixAnalyzer().prefixesOf(RegexPattern.of(regex));
  }

  private static int maxSize(String regex) {
    return RegexPattern.of(regex).metadata().maxSize();
  }
}
