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
package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.stream.BiStream.concatenating;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.testing.NullPointerTester;

public class LookupTest {
  @Test public void notFoundInMap() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    assertThat(Stream.of("two").flatMap(Lookup.in(map)::findOrEmpty)).isEmpty();
  }

  @Test public void foundInMap() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    assertThat(Stream.of("one").flatMap(Lookup.in(map)::findOrEmpty)).containsExactly(1);
  }

  @Test public void by_keyNotFoundInMap() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    BiStream<Entry<String, String>, Integer> concatenated =
        Stream.of(Maps.immutableEntry("two", "dos"))
            .collect(concatenating(Lookup.in(map).findOrEmpty(Map.Entry::getKey)));
    assertThat(concatenated.toMap()).isEmpty();
  }

  @Test public void by_keyFoundInMap() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    BiStream<Map.Entry<String, String>, Integer> concatenated = BiStream.concat(
        Stream.of(Maps.immutableEntry("two", "dos"))
            .map(Lookup.in(map).findOrEmpty(Map.Entry::getKey)));
    assertThat(concatenated.toMap()).isEmpty();
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(Lookup.class);
    new NullPointerTester()
        .ignore(Lookup.class.getMethod("findOrEmpty", Object.class))
        .testAllPublicInstanceMethods(Lookup.in(ImmutableMap.of()));
  }
}
