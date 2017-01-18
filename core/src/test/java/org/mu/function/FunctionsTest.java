package org.mu.function;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class FunctionsTest {

  @Test public void testCheckedSupplier_map() throws Throwable {
    CheckedSupplier<?, ?> supplier = () -> 1;
    assertThat(supplier.map(Object::toString).get()).isEqualTo("1");
  }

  @Test public void testCheckedSupplier_nulls() throws Throwable {
    CheckedSupplier<?, ?> supplier = () -> 1;
    new NullPointerTester().testAllPublicInstanceMethods(supplier);
  }

  @Test public void testCheckedFunction_map() throws Throwable {
    CheckedFunction<Object, ?, ?> function = x -> 1;
    assertThat(function.map(Object::toString).apply("x")).isEqualTo("1");
  }

  @Test public void testCheckedFunction_nulls() throws Throwable {
    CheckedFunction<Object, ?, ?> function = x -> {
      requireNonNull(x);
      return 1;
    };
    new NullPointerTester().testAllPublicInstanceMethods(function);
  }

  @Test public void testCheckedBiFunction_map() throws Throwable {
    CheckedBiFunction<Object, Object, ?, ?> function = (a, b) -> 1;
    assertThat(function.map(Object::toString).apply("x", "y")).isEqualTo("1");
  }

  @Test public void testCheckedBiFunction_nulls() throws Throwable {
    CheckedBiFunction<Object, Object, ?, ?> function = (a, b) -> {
      requireNonNull(a);
      requireNonNull(b);
      return 1;
    };
    new NullPointerTester().testAllPublicInstanceMethods(function);
  }
}
