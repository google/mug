package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class FunnelTest {
  @Mock private Batch batch;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void emptyFunnel() {
    Funnel<String> funnel = new Funnel<>();
    assertThat(funnel.run()).isEmpty();
  }

  @Test public void rejectsNullElement() {
    Funnel<String> funnel = new Funnel<>();
    assertThrows(NullPointerException.class, () -> funnel.add(null));
  }

  @Test public void batchRejectsNullElement() {
    Funnel<String> funnel = new Funnel<>();
    assertThrows(NullPointerException.class, () -> funnel.through(batch::send).accept(null));
  }

  @Test public void rejectsNullBatchConverter() {
    Funnel<String> funnel = new Funnel<>();
    assertThrows(NullPointerException.class, () -> funnel.through(null));
  }

  @Test public void singleElementFunnel() {
    Funnel<String> funnel = new Funnel<>();
    funnel.add("hello");
    assertThat(funnel.run()).containsExactly("hello");
  }

  @Test public void twoElementsFunnel() {
    Funnel<String> funnel = new Funnel<>();
    funnel.add("hello");
    funnel.add("world");
    assertThat(funnel.run()).containsExactly("hello", "world").inOrder();
  }

  @Test public void batchFunctionNotCalledIfNothingAdded() {
    Funnel<String> funnel = new Funnel<>();
    funnel.through(batch::send);
    assertThat(funnel.run()).isEmpty();
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void batchInvokedWithTwoElements() {
    Funnel<String> funnel = new Funnel<>();
    Consumer<Integer> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one", "two"));
    assertThat(funnel.run()).containsExactly("one", "two").inOrder();
    Mockito.verify(batch).send(asList(1, 2));
    Mockito.verifyNoMoreInteractions(batch);
  }

  @Test public void interleavedButRespectsOrder() {
    Batch batch2 = Mockito.mock(Batch.class);
    Funnel<String> funnel = new Funnel<>();
    Consumer<Integer> toSpell = funnel.through(batch::send);
    Consumer<String> toLowerCase = funnel.through(batch2::send);
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
    Funnel<String> funnel = new Funnel<>();
    Consumer<Integer> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    when(batch.send(asList(1))).thenReturn(asList());
    assertThrows(IllegalStateException.class, funnel::run);
  }

  @Test public void batchReturnsLessThanInput() {
    Funnel<String> funnel = new Funnel<>();
    Consumer<Integer> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    toSpell.accept(2);
    when(batch.send(asList(1, 2))).thenReturn(asList("one"));
    assertThrows(IllegalStateException.class, funnel::run);
  }

  @Test public void batchReturnsMoreThanInput() {
    Funnel<String> funnel = new Funnel<>();
    Consumer<Integer> toSpell = funnel.through(batch::send);
    toSpell.accept(1);
    when(batch.send(asList(1))).thenReturn(asList("one", "two"));
    assertThrows(IllegalStateException.class, funnel::run);
  }

  private interface Batch {
    <F, T> List<T> send(List<F> input);
  }
}