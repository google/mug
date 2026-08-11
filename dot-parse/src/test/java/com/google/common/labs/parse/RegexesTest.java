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
import static org.junit.Assert.assertThrows;

import com.google.common.labs.regex.RegexPattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RegexesTest {

  @Test public void validateRegex_valid() {
    Regexes.validate("a+");
    Regexes.validate("[0-9]");
    Regexes.validate("(?:foo|bar)+");
  }

  @Test public void validateRegex_empty_throws() {
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate(""));
  }

  @Test public void validateRegex_matchesEmpty_throws() {
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("a*"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("a?"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("(foo)?"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("foo|"));
  }

  @Test public void validateRegex_anchor_throws() {
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("^a"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("a$"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("\\ba"));
  }

  @Test public void validateRegex_lookaround_throws() {
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("a(?=b)"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("a(?!b)"));
  }

  @Test public void validateRegex_backreference_throws() {
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("(a)\\1"));
    assertThrows(IllegalArgumentException.class, () -> Regexes.validate("(?<foo>a)\\k<foo>"));
  }

  @Test public void prefixesOf_literal() {
    assertThat(prefixes("abc")).containsExactly("abc");
    assertThat(prefixes("")).containsExactly("");
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
  }

  @Test public void prefixesOf_unicodeCharacterClass() {
    assertThat(prefixes("(?U:\\d)")).containsExactly("");
  }

  @Test public void prefixesOf_anchorsAndLookaroundsAndBackreferences() {
    assertThat(prefixes("^")).containsExactly("");
    assertThat(prefixes("(?=a)")).containsExactly("");
    assertThat(prefixes("(a)\\1")).containsExactly("a");
  }

  private static java.util.Set<String> prefixes(String regex) {
    return Regexes.prefixesOf(RegexPattern.of(regex));
  }
}
