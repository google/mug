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
package com.google.mu.collect;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PrefixSearchTableTest {

  @Test
  public void emptyTable() {
    PrefixSearchTable<Integer, String> table = PrefixSearchTable.<Integer, String>builder().build();
    assertThat(table.search(asList(1)).toMap()).isEmpty();
    assertThat(table.get(asList(1))).isEmpty();
  }

  @Test
  public void emptyKeyCannotBeSearched() {
    PrefixSearchTable<Integer, String> table = PrefixSearchTable.<Integer, String>builder().build();
    assertThrows(IllegalArgumentException.class, () -> table.search(asList()));
    assertThrows(IllegalArgumentException.class, () -> table.get(asList()));
  }

  @Test
  public void emptyKeyCannotBeAdded() {
    PrefixSearchTable.Builder<Integer, String> builder = PrefixSearchTable.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.add(asList(), "foo"));
  }

  @Test
  public void nullKeyElementCannotBeAdded() {
    PrefixSearchTable.Builder<Integer, String> builder = PrefixSearchTable.builder();
    assertThrows(NullPointerException.class, () -> builder.add(asList(1, null), "foo"));
  }

  @Test
  public void nullKeyCannotBeSearched() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1), "foo").build();
    assertThrows(NullPointerException.class, () -> table.get(asList(1, null)));
  }

  @Test
  public void singleKeyMatched() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1), "foo").build();
    assertThat(table.search(asList(1)).toMap()).containsExactly(asList(1), "foo");
    assertThat(table.get(asList(1))).hasValue("foo");
  }

  @Test
  public void singleKeyNotMatched() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1), "foo").build();
    assertThat(table.search(asList(2)).toMap()).isEmpty();
    assertThat(table.get(asList(2))).isEmpty();
  }

  @Test
  public void singleKeyMatchesPrefix() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1), "foo").build();
    assertThat(table.search(asList(1, 2, 3)).toMap()).containsExactly(asList(1), "foo");
    assertThat(table.get(asList(1, 2, 3))).hasValue("foo");
  }

  @Test
  public void multipleKeysExactMatch() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1, 2, 3), "foo").build();
    assertThat(table.search(asList(1, 2, 3)).toMap()).containsExactly(asList(1, 2, 3), "foo");
    assertThat(table.get(asList(1, 2, 3))).hasValue("foo");
  }

  @Test
  public void multipleKeysPrefixMatched() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1, 2, 3), "foo").build();
    assertThat(table.search(asList(1, 2, 3, 4, 5)).toMap()).containsExactly(asList(1, 2, 3), "foo");
    assertThat(table.get(asList(1, 2, 3, 4, 5))).hasValue("foo");
  }

  @Test
  public void multipleKeysLongerThanSearchKeySize() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder().add(asList(1, 2, 3), "foo").build();
    assertThat(table.search(asList(1, 2)).toMap()).isEmpty();
    assertThat(table.get(asList(1, 2))).isEmpty();
  }

  @Test
  public void multipleCandidates() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder()
            .add(asList(1, 2, 3), "foo")
            .add(asList(1, 2), "bar")
            .add(asList(1, 2, 4), "baz")
            .add(asList(2, 1, 3), "zoo")
            .build();
    assertThat(table.search(asList(1, 2, 3)).toMap())
        .containsExactly(asList(1, 2), "bar", asList(1, 2, 3), "foo")
        .inOrder();
    assertThat(table.get(asList(1, 2, 3))).hasValue("foo");
  }

  @Test
  public void conflictingMappingDisallowed() {
    PrefixSearchTable.Builder<Integer, String> builder = PrefixSearchTable.builder();
    builder.add(asList(1, 2, 3), "foo");
    assertThrows(IllegalArgumentException.class, () -> builder.add(asList(1, 2, 3), "bar"));
  }

  @Test
  public void redundantMappingAllowed() {
    PrefixSearchTable<Integer, String> table =
        PrefixSearchTable.<Integer, String>builder()
            .add(asList(1, 2, 3), "foo")
            .add(asList(1), "bar")
            .add(asList(1, 2, 3), "foo")
            .build();
    assertThat(table.search(asList(1, 2, 3)).toMap())
        .containsExactly(asList(1), "bar", asList(1, 2, 3), "foo")
        .inOrder();
    assertThat(table.get(asList(1, 2, 3))).hasValue("foo");
  }
}
