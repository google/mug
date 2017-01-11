package org.mu.function;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.ClassSanityTester;

@RunWith(JUnit4.class)
public class FunctionsTest {

  @Test public void testCheckedSupplier_map() throws Throwable {
    CheckedSupplier<?, ?> supplier = () -> 1;
    assertThat(supplier.map(Object::toString).get()).isEqualTo("1");
  }

  @Test public void testCheckedFunction_map() throws Throwable {
    CheckedFunction<Object, ?, ?> function = x -> 1;
    assertThat(function.map(Object::toString).apply("x")).isEqualTo("1");
  }

  @Test public void testCheckedBiFunction_map() throws Throwable {
    CheckedBiFunction<Object, Object, ?, ?> function = (a, b) -> 1;
    assertThat(function.map(Object::toString).apply("x", "y")).isEqualTo("1");
  }

  @Test public void testNulls() {
    new ClassSanityTester().testNulls(CheckedSupplier.class);
    new ClassSanityTester().testNulls(CheckedFunction.class);
    new ClassSanityTester().testNulls(CheckedBiFunction.class);
  }
}
