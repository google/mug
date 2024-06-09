package com.google.mu.collect;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.collect.Chain.concat;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.MoreStreams;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class ChainTest {
  @Test public void singleElement() {
    assertThat(Chain.of("foo")).containsExactly("foo");
    assertThat(Chain.of("foo")).isNotEmpty();
  }

  @Test public void singleElement_varargs() {
    String[] noMore = {};
    assertThat(Chain.of("foo", noMore)).containsExactly("foo");
  }

  @Test public void singleElement_size() {
    assertThat(Chain.of("foo").size()).isEqualTo(1);
  }

  @Test public void multipleElements() {
    assertThat(Chain.of("foo", "bar", "baz")).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test public void multipleElements_get() {
    Chain<String> sequence = Chain.of("foo", "bar", "baz");
    assertThat(sequence.get(0)).isEqualTo("foo");
    assertThat(sequence.get(1)).isEqualTo("bar");
    assertThat(sequence.get(2)).isEqualTo("baz");
  }

  @Test public void concatSingleElement_afterSingleElement() {
    assertThat(Chain.of("foo").concat("bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test public void concatSingleElement_varargs() {
    String[] noMore = {};
    assertThat(Chain.of("foo", noMore).concat("bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test public void concatSingleElement_afterMultipleElements() {
    assertThat(Chain.of("foo", "bar").concat("baz")).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test public void concatSingleElementsize() {
    assertThat(Chain.of("foo").concat("bar").size()).isEqualTo(2);
  }

  @Test public void concatTwoSequences() {
    assertThat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")))
        .containsExactly("foo", "bar", "baz", "zoo")
        .inOrder();
    assertThat(concat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")), Chain.of("dash")))
        .containsExactly("foo", "bar", "baz", "zoo", "dash")
        .inOrder();
    assertThat(concat(Chain.of("zero"), concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo"))))
        .containsExactly("zero", "foo", "bar", "baz", "zoo")
        .inOrder();
  }

  @Test public void concatTwoSequences_size() {
    assertThat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")).size())
        .isEqualTo(4);
    assertThat(concat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")), Chain.of("dash")).size())
        .isEqualTo(5);
    assertThat(concat(Chain.of("zero", "one"), concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo"))).size())
        .isEqualTo(6);
  }

  @Test public void concatTwoSequences_collect() {
    assertThat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")).stream().collect(toImmutableSet()))
        .containsExactly("foo", "bar", "baz", "zoo")
        .inOrder();
    assertThat(concat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")), Chain.of("dash")).stream().collect(toImmutableSet()))
        .containsExactly("foo", "bar", "baz", "zoo", "dash")
        .inOrder();
    assertThat(concat(Chain.of("zero", "one"), concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo"))).stream().collect(toImmutableSet()))
        .containsExactly("zero", "one", "foo", "bar", "baz", "zoo")
        .inOrder();
  }

  @Test public void concatTwoSequences_subList() {
    assertThat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")).subList(1, 3))
        .containsExactly("bar", "baz")
        .inOrder();
  }

  @Test public void concatFromBeginning() {
    ImmutableList<Integer> list = MoreStreams.indexesFrom(1).limit(100).collect(toImmutableList());
    assertThat(list.stream().map(Chain::of).reduce(Chain::concat)).hasValue(list);
  }

  @Test public void concatFromEnd() {
    ImmutableList<Integer> list = MoreStreams.indexesFrom(1).limit(100).collect(toImmutableList());
    assertThat(list.stream().map(Chain::of).reduce((a, b) -> Chain.concat(b, a)))
        .hasValue(list.reverse());
  }

  @Test public void concatInTheMiddle() {
    ImmutableList<Integer> list = MoreStreams.indexesFrom(1).limit(100).collect(toImmutableList());
    assertThat(toSequence(list)).containsExactlyElementsIn(list).inOrder();
  }

  @Test public void elementsIsIdempotent() {
    Chain<?> sequence = concat(Chain.of("foo", "bar"), Chain.of("bar", "baz"));
    assertThat(sequence.elements()).isSameInstanceAs(sequence.elements());
  }

  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(Chain.of(1, 2), Chain.of(1).concat(2), concat(Chain.of(1), Chain.of(2)))
        .addEqualityGroup(Chain.of(1, 2, 3))
        .addEqualityGroup(Chain.of(1))
        .testEquals();
  }

  @Test public void testNulls() {
    NullPointerTester tester = new NullPointerTester().setDefault(Chain.class, Chain.of("dummy"));
    tester.testAllPublicStaticMethods(Chain.class);
    tester.testAllPublicInstanceMethods(Chain.of(1));
  }

  private static <T> Chain<T> toSequence(List<T> list) {
    assertThat(list).isNotEmpty();
    if (list.size() == 1) {
      return Chain.of(list.get(0));
    }
    int mid = list.size() / 2;
    return Chain.concat(toSequence(list.subList(0, mid)), toSequence(list.subList(mid, list.size())));
  }
}
