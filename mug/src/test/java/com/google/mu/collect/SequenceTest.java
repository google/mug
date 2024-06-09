package com.google.mu.collect;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.collect.Sequence.concat;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.MoreStreams;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class SequenceTest {
  @Test public void singleElement() {
    assertThat(Sequence.of("foo")).containsExactly("foo");
    assertThat(Sequence.of("foo")).isNotEmpty();
  }

  @Test public void singleElement_varargs() {
    String[] noMore = {};
    assertThat(Sequence.of("foo", noMore)).containsExactly("foo");
  }

  @Test public void singleElement_size() {
    assertThat(Sequence.of("foo").size()).isEqualTo(1);
  }

  @Test public void multipleElements() {
    assertThat(Sequence.of("foo", "bar", "baz")).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test public void multipleElements_get() {
    Sequence<String> sequence = Sequence.of("foo", "bar", "baz");
    assertThat(sequence.get(0)).isEqualTo("foo");
    assertThat(sequence.get(1)).isEqualTo("bar");
    assertThat(sequence.get(2)).isEqualTo("baz");
  }

  @Test public void concatSingleElement_afterSingleElement() {
    assertThat(Sequence.of("foo").concat("bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test public void concatSingleElement_varargs() {
    String[] noMore = {};
    assertThat(Sequence.of("foo", noMore).concat("bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test public void concatSingleElement_afterMultipleElements() {
    assertThat(Sequence.of("foo", "bar").concat("baz")).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test public void concatSingleElementsize() {
    assertThat(Sequence.of("foo").concat("bar").size()).isEqualTo(2);
  }

  @Test public void concatTwoSequences() {
    assertThat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")))
        .containsExactly("foo", "bar", "baz", "zoo")
        .inOrder();
    assertThat(concat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")), Sequence.of("dash")))
        .containsExactly("foo", "bar", "baz", "zoo", "dash")
        .inOrder();
    assertThat(concat(Sequence.of("zero"), concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo"))))
        .containsExactly("zero", "foo", "bar", "baz", "zoo")
        .inOrder();
  }

  @Test public void concatTwoSequences_size() {
    assertThat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")).size())
        .isEqualTo(4);
    assertThat(concat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")), Sequence.of("dash")).size())
        .isEqualTo(5);
    assertThat(concat(Sequence.of("zero", "one"), concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo"))).size())
        .isEqualTo(6);
  }

  @Test public void concatTwoSequences_collect() {
    assertThat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")).stream().collect(toImmutableSet()))
        .containsExactly("foo", "bar", "baz", "zoo")
        .inOrder();
    assertThat(concat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")), Sequence.of("dash")).stream().collect(toImmutableSet()))
        .containsExactly("foo", "bar", "baz", "zoo", "dash")
        .inOrder();
    assertThat(concat(Sequence.of("zero", "one"), concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo"))).stream().collect(toImmutableSet()))
        .containsExactly("zero", "one", "foo", "bar", "baz", "zoo")
        .inOrder();
  }

  @Test public void concatTwoSequences_subList() {
    assertThat(concat(Sequence.of("foo", "bar"), Sequence.of("baz", "zoo")).subList(1, 3))
        .containsExactly("bar", "baz")
        .inOrder();
  }

  @Test public void concatFromBeginning() {
    ImmutableList<Integer> list = MoreStreams.indexesFrom(1).limit(100).collect(toImmutableList());
    assertThat(list.stream().map(Sequence::of).reduce(Sequence::concat)).hasValue(list);
  }

  @Test public void concatInTheMiddle() {
    ImmutableList<Integer> list = MoreStreams.indexesFrom(1).limit(100).collect(toImmutableList());
    assertThat(toSequence(list)).containsExactlyElementsIn(list).inOrder();
  }

  @Test public void elementsIsIdempotent() {
    Sequence<?> sequence = concat(Sequence.of("foo", "bar"), Sequence.of("bar", "baz"));
    assertThat(sequence.elements()).isSameInstanceAs(sequence.elements());
  }

  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(Sequence.of(1, 2), Sequence.of(1).concat(2), concat(Sequence.of(1), Sequence.of(2)))
        .addEqualityGroup(Sequence.of(1, 2, 3))
        .addEqualityGroup(Sequence.of(1))
        .testEquals();
  }

  @Test public void testNulls() {
    NullPointerTester tester = new NullPointerTester().setDefault(Sequence.class, Sequence.of("dummy"));
    tester.testAllPublicStaticMethods(Sequence.class);
    tester.testAllPublicInstanceMethods(Sequence.of(1));
  }

  private static <T> Sequence<T> toSequence(List<T> list) {
    assertThat(list).isNotEmpty();
    if (list.size() == 1) {
      return Sequence.of(list.get(0));
    }
    int mid = list.size() / 2;
    return Sequence.concat(toSequence(list.subList(0, mid)), toSequence(list.subList(mid, list.size())));
  }
}
