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
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.mu.util.stream.MoreStreams;

@RunWith(Parameterized.class)
public class MoreStreamsParameterizedTest {

  private final StreamKind kind;

  public MoreStreamsParameterizedTest(StreamKind kind) {
    this.kind = kind;
  }

  @Parameters(name = "{index}: {0}")
  public static StreamKind[] params() {
    return StreamKind.values();
  }

  @Test public void streamSizeDivisible() {
    assertThat(MoreStreams.dice(kind.natural(6), 2).collect(toList()))
        .containsExactly(asList(1, 2), asList(3, 4), asList(5, 6)).inOrder();
  }

  @Test public void streamSizeNotDivisible() {
    assertThat(MoreStreams.dice(kind.natural(5), 2).collect(toList()))
        .containsExactly(asList(1, 2), asList(3, 4), asList(5)).inOrder();
  }

  @Test public void streamSizeIsOne() {
    assertThat(MoreStreams.dice(kind.natural(3), 1).collect(toList()))
        .containsExactly(asList(1), asList(2), asList(3)).inOrder();
  }

  @Test public void emptyStream() {
    assertThat(MoreStreams.dice(kind.natural(0), 1).collect(toList()))
        .isEmpty();
  }

  @Test public void spliteratorAndStreamHaveEqualCharacteristics() {
    assertThat(MoreStreams.dice(kind.natural(1), 2).spliterator().characteristics())
        .isEqualTo(MoreStreams.dice(kind.natural(1).spliterator(), 2).characteristics());
  }

  @Test public void intermediaryOperation() {
    assertThat(MoreStreams.dice(kind.natural(3), 2).flatMap(List::stream).collect(toList()))
        .containsExactly(1, 2, 3).inOrder();
  }

  @Test public void close() {
    Stream<?> stream = kind.natural(0);
    AtomicBoolean closed = new AtomicBoolean();
    stream.onClose(() -> closed.set(true));
    try (Stream<?> diced = MoreStreams.dice(stream, 1)) {}
    assertThat(closed.get()).isTrue();
  }

  private enum StreamKind {
    DEFAULT  {
      @Override Stream<Integer> natural(int numbers) {
        return IntStream.range(1, numbers + 1).boxed();
      }
    },
    ARRAY_BASED {
      @Override Stream<Integer> natural(int numbers) {
        return Arrays.asList(DEFAULT.natural(numbers).toArray(s -> new Integer[s]))
            .stream();
      }
    },
    TREE_SET_BASED {
      @Override Stream<Integer> natural(int numbers) {
        return new TreeSet<>(DEFAULT.natural(numbers).collect(toList())).stream();
      }
    },
    ;
    
    abstract Stream<Integer> natural(int numbers);
  }
}
