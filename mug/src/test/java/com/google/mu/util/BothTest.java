package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class BothTest {
  @Test public void match() {
    assertThat(both(1, 2).matches(Object::equals)).isFalse();
    assertThat(both(1, 1).matches(Object::equals)).isTrue();
  }

  @Test public void filter() {
    assertThat(both(1, 2).filter(Object::equals)).isEqualTo(BiOptional.empty());
    assertThat(both(1, 1).filter(Object::equals)).isEqualTo(BiOptional.of(1, 1));
  }

  @Test public void peek() {
    AtomicInteger effect = new AtomicInteger();
    Both<Integer, Integer> b = both(1, 2);
    assertThat(b.peek((x, y) -> effect.set(x + y))).isSameAs(b);
    assertThat(effect.get()).isEqualTo(3);
  }

  @Test
  public void testToEntry() {
    assertThat(Both.of(1, "foo").toEntry())
        .isEqualTo(new AbstractMap.SimpleImmutableEntry<>(1, "foo"));
  }

  @Test public void nulls() {
    new NullPointerTester().testAllPublicInstanceMethods(both(1, 2));
  }

  private static <A, B> Both<A, B> both(A a, B b) {
    return new Object() {
      <T> T map(BiFunction<? super A, ? super B, T> f) {
        return f.apply(a, b);
      }
    }::map;
  }
}
