package org.mu.function;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FunctionsTest {

  @Test public void testCheckedSupplier_map_null() {
    CheckedSupplier<?, ?> supplier = () -> 1;
    assertThrows(NullPointerException.class, () -> supplier.map(null));
  }

  @Test public void testCheckedSupplier_map() throws Throwable {
    CheckedSupplier<?, ?> supplier = () -> 1;
    assertThat(supplier.map(Object::toString).get()).isEqualTo("1");
  }

  @Test public void testCheckedFunction_map_null() {
    CheckedFunction<?, ?, ?> function = x -> 1;
    assertThrows(NullPointerException.class, () -> function.map(null));
  }

  @Test public void testCheckedFunction_map() throws Throwable {
    CheckedFunction<Object, ?, ?> function = x -> 1;
    assertThat(function.map(Object::toString).apply("x")).isEqualTo("1");
  }

  @Test public void testCheckedBiFunction_map_null() {
    CheckedBiFunction<?, ?, ?, ?> function = (a, b) -> 1;
    assertThrows(NullPointerException.class, () -> function.map(null));
  }

  @Test public void testCheckedBiFunction_map() throws Throwable {
    CheckedBiFunction<Object, Object, ?, ?> function = (a, b) -> 1;
    assertThat(function.map(Object::toString).apply("x", "y")).isEqualTo("1");
  }
}
