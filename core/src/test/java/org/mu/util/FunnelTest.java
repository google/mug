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
package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.testing.ClassSanityTester;

@RunWith(JUnit4.class)
public final class FunnelTest {
  private final Funnel<String> funnel = new Funnel<>();
  @Mock private Batch batch;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void emptyFunnel() {
    assertThat(funnel.run()).isEmpty();
  }

  @Test public void testNulls() {
    new ClassSanityTester().testNulls(Funnel.class);
  }

  @Test public void singleElementFunnel() {
    funnel.add("hello");
    assertThat(funnel.run()).containsExactly("hello");
  }

  @Test public void twoElementsFunnel() {
    funnel.add("hello");
    funnel.add("world");
    assertThat(funnel.run()).containsExactly("hello", "world").inOrder();
  }

  @Test public void batchFunctionNotCalledIfNothingAdded() {
    funnel.through(batch::send);
    assertThat(funnel.run()).isEmpty();
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithTwoElements() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThat(funnel.run()).containsExactly("one", "two").inOrder();
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithPostConversion() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    toSpell.accept(1, s -> s + s);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThat(funnel.run()).containsExactly("oneone", "two").inOrder();
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithPostConversionThatReturnsNull() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    toSpell.accept(1, s -> null);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThat(funnel.run()).containsExactly(null, "two").inOrder();
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithPostConversionThatThrows() {
    MyUncheckedException exception = new MyUncheckedException();
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    Function<String, String> throwingFunction = s -> {throw exception;};
    toSpell.accept(1, throwingFunction);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThrows(MyUncheckedException.class, funnel::run);
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithAftereffect() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    AtomicReference<String> spelled = new AtomicReference<>();
    toSpell.accept(1, spelled::set);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThat(funnel.run()).containsExactly("one", "two").inOrder();
    assertThat(spelled.get()).isEqualTo("one");
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithAftereffectThatThrows() {
    MyUncheckedException exception = new MyUncheckedException();
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    Consumer<String> throwingEffect = s -> {throw exception;};
    toSpell.accept(1, throwingEffect);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThrows(MyUncheckedException.class, funnel::run);
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void interleavedButRespectsOrder() {
    Batch batch2 = Mockito.mock(Batch.class);
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    Funnel.Batch<String, String> toLowerCase = funnel.through(batch2::send);
    funnel.add("zero");
    toSpell.accept(1);
    funnel.add("two");
    toLowerCase.accept("THREE");
    toSpell.accept(4);
    when(batch.send(asList(1, 4))).thenReturn(asList("one", "four"));
    when(batch2.send(asList("THREE"))).thenReturn(asList("three"));
    assertThat(funnel.run()).containsExactly("zero", "one", "two", "three", "four").inOrder();
    Mockito.verify(batch).send(asList(1, 4));
    Mockito.verify(batch2).send(asList("THREE"));
    Mockito.verifyNoMoreInteractions(batch);
    Mockito.verifyNoMoreInteractions(batch2);
  }

  @Test public void batchReturnsEmpty() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    when(batch.send(asList(1))).thenReturn(asList());
    assertThrows(IllegalStateException.class, funnel::run);
  }

  @Test public void batchReturnsLessThanInput() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one"));
    assertThrows(IllegalStateException.class, funnel::run);
  }

  @Test public void batchReturnsMoreThanInput() {
    Funnel.Batch<Integer, String> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    when(batch.send(asList(1))).thenReturn(asList("one", "two"));
    assertThrows(IllegalStateException.class, funnel::run);
  }

  @SuppressWarnings("serial")
  private static class MyUncheckedException extends RuntimeException {
    MyUncheckedException() {
      super("test");
    }
  }

  private interface Batch {
    <F, T> List<T> send(List<F> input);
  }
}