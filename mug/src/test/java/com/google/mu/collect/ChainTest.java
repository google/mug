package com.google.mu.collect;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.collect.Chain.concat;
import static java.util.Arrays.asList;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.testing.IteratorFeature;
import com.google.common.collect.testing.IteratorTester;
import com.google.common.collect.testing.SpliteratorTester;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.MoreStreams;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class ChainTest {
  @Test public void singleElement() {
    assertChain(Chain.of("foo"), "foo");
  }

  @Test public void singleElement_varargs() {
    String[] noMore = {};
    assertChain(Chain.of("foo", noMore), "foo");
  }

  @Test public void multipleElements() {
    assertChain(Chain.of("foo", "bar", "baz"), "foo", "bar", "baz");
  }

  @Test public void multipleElements_get() {
    Chain<String> chain = Chain.of("foo", "bar", "baz");
    assertThat(chain.get(0)).isEqualTo("foo");
    assertThat(chain.get(1)).isEqualTo("bar");
    assertThat(chain.get(2)).isEqualTo("baz");
    assertChain(chain, "foo", "bar", "baz");
  }

  @Test public void concatSingleElement_afterSingleElement() {
    assertChain(Chain.of("foo").concat("bar"), "foo", "bar");
  }

  @Test public void concatSingleElement_varargs() {
    String[] noMore = {};
    assertChain(Chain.of("foo", noMore).concat("bar"), "foo", "bar");
  }

  @Test public void concatSingleElement_afterMultipleElements() {
    assertChain(Chain.of("foo", "bar").concat("baz"), "foo", "bar", "baz");
  }

  @Test public void concatTwoSequences() {
    assertChain(
        concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")),
        "foo", "bar", "baz", "zoo");
    assertChain(
        concat(concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo")), Chain.of("dash")),
        "foo", "bar", "baz", "zoo", "dash");
    assertChain(concat(
        Chain.of("zero"), concat(Chain.of("foo", "bar"), Chain.of("baz", "zoo"))),
        "zero", "foo", "bar", "baz", "zoo");
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
    assertThat(toChain(list)).containsExactlyElementsIn(list).inOrder();
  }

  @Test public void elementsIsIdempotent() {
    Chain<?> chain = concat(Chain.of("foo", "bar"), Chain.of("bar", "baz"));
    assertThat(chain.elements()).isSameInstanceAs(chain.elements());
  }

  private static <T> void assertChain(Chain<T> chain, T... expected) {
    // Before materialization
    assertThat(chain.stream()).containsExactlyElementsIn(asList(expected)).inOrder();
    assertThat(chain.size()).isEqualTo(expected.length);
    assertThat(chain).isNotEmpty();
    assertThat(chain.getFirst()).isEqualTo(expected[0]);
    assertThat(chain.get(0)).isEqualTo(expected[0]);
    assertThat(chain.get(expected.length - 1)).isEqualTo(expected[expected.length - 1]);

    // Materialize
    assertThat(chain).containsExactlyElementsIn(asList(expected)).inOrder();
    assertThat(chain.stream()).containsExactlyElementsIn(asList(expected)).inOrder();
    assertThat(chain.size()).isEqualTo(expected.length);
    assertThat(chain).isNotEmpty();
    assertThat(chain.getFirst()).isEqualTo(expected[0]);
    assertThat(chain.get(0)).isEqualTo(expected[0]);
    assertThat(chain.get(expected.length - 1)).isEqualTo(expected[expected.length - 1]);
    IteratorTester<T> tester =
         new IteratorTester<T>(
             6,
             IteratorFeature.UNMODIFIABLE,
             asList(expected),
             IteratorTester.KnownOrder.KNOWN_ORDER) {
           @Override protected Iterator<T> newTargetIterator() {
             return chain.iterator();
           }
         };
         tester.test();
         tester.testForEachRemaining();
     SpliteratorTester.of(chain::spliterator).expect(expected).inOrder();
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

  private static <T> Chain<T> toChain(List<T> list) {
    assertThat(list).isNotEmpty();
    if (list.size() == 1) {
      return Chain.of(list.get(0));
    }
    int mid = list.size() / 2;
    return Chain.concat(toChain(list.subList(0, mid)), toChain(list.subList(mid, list.size())));
  }
}
