/*****************************************************************************
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
package com.google.mu.util;

import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.MoreLists.findFirst;
import static com.google.mu.util.MoreLists.findOnly;
import static java.util.Arrays.asList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class MoreListsTest {
  @Test public void testFindOnlyTwoElements() {
    assertThat(findOnly(asList(1, 10), Integer::sum)).hasValue(11);
    assertThat(findOnly(asList(1), Integer::sum)).isEmpty();
    assertThat(findOnly(asList(1, 2, 3), Integer::sum)).isEmpty();
  }

  @Test public void testFindOnlyThreeElements() {
    assertThat(findOnly(asList(1, 3, 5), (a, b, c) -> a + b + c)).hasValue(9);
    assertThat(findOnly(asList(1, 2), (a, b, c) -> a + b + c)).isEmpty();
    assertThat(findOnly(asList(1, 2, 3, 4), (a, b, c) -> a + b + c)).isEmpty();
  }

  @Test public void testFindOnlyFourElements() {
    assertThat(findOnly(asList(1, 3, 5, 7), (a, b, c, d) -> a + b + c + d)).hasValue(16);
    assertThat(findOnly(asList(1, 2, 3), (a, b, c, d) -> a + b + c + d)).isEmpty();
    assertThat(findOnly(asList(1, 2, 3, 4, 5), (a, b, c, d) -> a + b + c)).isEmpty();
  }

  @Test public void testFindOnlyFiveElements() {
    assertThat(findOnly(asList(1, 3, 5, 7, 9), (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue(25);
    assertThat(findOnly(asList(1, 2, 3, 4), (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
    assertThat(findOnly(asList(1, 2, 3, 4, 5, 6), (a, b, c, d, e) -> a + b + c + e))
        .isEmpty();
  }

  @Test public void testFindOnlySixElements() {
    assertThat(findOnly(asList(1, 3, 5, 7, 9, 11), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue(36);
    assertThat(findOnly(asList(1, 2, 3, 4, 5), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
    assertThat(findOnly(asList(1, 2, 3, 4, 5, 6, 7), (a, b, c, d, e, f) -> a + b + c + e + f))
        .isEmpty();
  }

  @Test public void testFindFirstTwoElements() {
    assertThat(findFirst(asList(1, 2), (a, b) -> a + b)).hasValue(3);
    assertThat(findFirst(asList(1, 3, 5), (a, b) -> a + b)).hasValue(4);
    assertThat(findFirst(asList(1), (a, b) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstThreeElements() {
    assertThat(findFirst(asList(1, 3, 5), (a, b, c) -> a + b + c)).hasValue(9);
    assertThat(findFirst(asList(1, 3, 5, 7), (a, b, c) -> a + b + c)).hasValue(9);
    assertThat(findFirst(asList(1, 2), (a, b, c) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstFourElements() {
    assertThat(findFirst(asList(1, 3, 5, 7), (a, b, c, d) -> a + b + c + d))
        .hasValue(16);
    assertThat(findFirst(asList(1, 3, 5, 7, 9), (a, b, c, d) -> a + b + c + d))
        .hasValue(16);
    assertThat(findFirst(asList(1, 2, 3), (a, b, c, d) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstFiveElements() {
    assertThat(findFirst(asList(1, 3, 5, 7, 9), (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue(25);
    assertThat(findFirst(asList(1, 3, 5, 7, 9, 11), (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue(25);
    assertThat(findFirst(asList(1, 2, 3, 4), (a, b, c, d, e) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstSixElements() {
    assertThat(findFirst(asList(1, 3, 5, 7, 9, 11), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue(36);
    assertThat(findFirst(asList(1, 3, 5, 7, 9, 11, 13), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue(36);
    assertThat(findFirst(asList(1, 2, 3, 4, 5), (a, b, c, d, e, f) -> a + b)).isEmpty();
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(MoreLists.class);
  }
}
