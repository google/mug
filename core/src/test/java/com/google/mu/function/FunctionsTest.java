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
package com.google.mu.function;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;
import com.google.mu.function.CheckedBiFunction;
import com.google.mu.function.CheckedFunction;
import com.google.mu.function.CheckedSupplier;

@RunWith(JUnit4.class)
public class FunctionsTest {

  @Test public void testCheckedSupplier_andThen() throws Throwable {
    CheckedSupplier<?, ?> supplier = () -> 1;
    assertThat(supplier.andThen(Object::toString).get()).isEqualTo("1");
  }

  @Test public void testCheckedSupplier_nulls() throws Throwable {
    CheckedSupplier<?, ?> supplier = () -> 1;
    new NullPointerTester().testAllPublicInstanceMethods(supplier);
  }

  @Test public void testCheckedFunction_andThen() throws Throwable {
    CheckedFunction<Object, ?, IOException> function = x -> 1;
    assertThat(function.andThen(Object::toString).apply("x")).isEqualTo("1");
  }

  @Test public void testCheckedFunction_nulls() throws Throwable {
    CheckedFunction<Object, ?, ?> function = x -> {
      requireNonNull(x);
      return 1;
    };
    new NullPointerTester().testAllPublicInstanceMethods(function);
  }

  @Test public void testCheckedBiFunction_andThen() throws Throwable {
    CheckedBiFunction<Object, Object, ?, ?> function = (a, b) -> 1;
    assertThat(function.andThen(Object::toString).apply("x", "y")).isEqualTo("1");
  }

  @Test public void testCheckedBiFunction_nulls() throws Throwable {
    CheckedBiFunction<Object, Object, ?, ?> function = (a, b) -> {
      requireNonNull(a);
      requireNonNull(b);
      return 1;
    };
    new NullPointerTester().testAllPublicInstanceMethods(function);
  }

  @Test public void testCheckedConsumer_andThen() throws Throwable {
    List<String> outputs = new ArrayList<>();
    CheckedConsumer<Object, IOException> consumer = i -> outputs.add("1: " + i);
    consumer.andThen(i -> outputs.add("2: " + i)).accept("x");
    assertThat(outputs).containsExactly("1: x", "2: x");
  }

  @Test public void testCheckedConsumer_nulls() throws Throwable {
    CheckedConsumer<Object, IOException> consumer = Objects::requireNonNull;
    new NullPointerTester().testAllPublicInstanceMethods(consumer);
  }

  @Test public void testCheckedIntConsumer_andThen() throws Throwable {
    List<String> outputs = new ArrayList<>();
    CheckedIntConsumer<IOException> consumer = i -> outputs.add("a: " + i);
    consumer.andThen(i -> outputs.add("b: " + i)).accept(1);
    assertThat(outputs).containsExactly("a: 1", "b: 1");
  }

  @Test public void testCheckedIntConsumer_nulls() throws Throwable {
    CheckedIntConsumer<IOException> consumer = i -> {};
    new NullPointerTester().testAllPublicInstanceMethods(consumer);
  }

  @Test public void testCheckedLongConsumer_andThen() throws Throwable {
    List<String> outputs = new ArrayList<>();
    CheckedLongConsumer<IOException> consumer = i -> outputs.add("a: " + i);
    consumer.andThen(i -> outputs.add("b: " + i)).accept(1);
    assertThat(outputs).containsExactly("a: 1", "b: 1");
  }

  @Test public void testCheckedLongConsumer_nulls() throws Throwable {
    CheckedLongConsumer<IOException> consumer = i -> {};
    new NullPointerTester().testAllPublicInstanceMethods(consumer);
  }

  @Test public void testCheckedDoubleConsumer_andThen() throws Throwable {
    List<String> outputs = new ArrayList<>();
    CheckedDoubleConsumer<IOException> consumer = i -> outputs.add("a: " + i);
    consumer.andThen(i -> outputs.add("b: " + i)).accept(1);
    assertThat(outputs).containsExactly("a: 1.0", "b: 1.0");
  }

  @Test public void testCheckedDoubleConsumer_nulls() throws Throwable {
    CheckedDoubleConsumer<IOException> consumer = i -> {};
    new NullPointerTester().testAllPublicInstanceMethods(consumer);
  }

  @Test public void testCheckedBiConsumer_andThen() throws Throwable {
    AtomicInteger sum = new AtomicInteger();
    CheckedBiConsumer<Integer, Integer, Throwable> consumer = (a, b) -> sum.addAndGet(a + b);
    consumer.andThen(consumer).accept(1, 2);
    assertThat(sum.get()).isEqualTo(6);
  }

  @Test public void testCheckedBiConsumer_nulls() throws Throwable {
    CheckedBiConsumer<Object, Object, ?> consumer = (a, b) -> {
      requireNonNull(a);
      requireNonNull(b);
    };
    new NullPointerTester().testAllPublicInstanceMethods(consumer);
  }
}
