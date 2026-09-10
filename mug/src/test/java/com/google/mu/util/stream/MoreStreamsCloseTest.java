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
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoreStreamsCloseTest {
  @Test public void withSideEffect_closesInputWithoutTraversal() {
    List<String> closed = new ArrayList<>();
    List<Integer> seen = new ArrayList<>();
    Stream<Integer> stream = MoreStreams.withSideEffect(
        Stream.of(1, 2).onClose(() -> closed.add("input")), seen::add);
    assertThat(closed).isEmpty();
    stream.close();
    stream.close();
    assertThat(closed).containsExactly("input");
    assertThat(seen).isEmpty();
  }

  @Test public void withSideEffect_closesInputAfterShortCircuit() {
    List<String> closed = new ArrayList<>();
    List<Integer> seen = new ArrayList<>();
    try (Stream<Integer> stream = MoreStreams.withSideEffect(
        Stream.of(1, 2).onClose(() -> closed.add("input")), seen::add)) {
      assertThat(stream.limit(1).collect(toList())).containsExactly(1);
      assertThat(closed).isEmpty();
    }
    assertThat(closed).containsExactly("input");
    assertThat(seen).containsExactly(1);
  }

  @Test public void generate_closesConsumedFanoutStreams() {
    List<String> closed = new ArrayList<>();
    assertThat(MoreStreams.generate(
            1,
            i -> i == 1
                ? Stream.of(2, 3).onClose(() -> closed.add("fanout"))
                : Stream.empty())
        .collect(toList()))
        .containsExactly(1, 2, 3).inOrder();
    assertThat(closed).containsExactly("fanout");
  }
}
