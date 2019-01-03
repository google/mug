package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.Optionals.ifPresent;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class OptionalsTest {
  @Mock private BiAction action;
  @Mock private Consumer<Object> consumer;
  @Mock private Runnable otherwise;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void ifPresent_or_firstIsAbsent_secondSupplierIsPresent() {
    ifPresent(Optional.empty(), consumer::accept)
        .or(() -> ifPresent(Optional.of("left"), Optional.of("right"), action::run))
        .orElse(otherwise::run);
    verify(consumer, never()).accept(any());
    verify(action).run("left", "right");
    verify(otherwise, never()).run();
  }

  @Test public void ifPresent_or_firstIsAbsent_secondSupplierIsAbsent() {
    ifPresent(Optional.empty(), consumer::accept)
        .or(() -> ifPresent(Optional.empty(), Optional.of("right"), action::run))
        .orElse(otherwise::run);
    verify(consumer, never()).accept(any());
    verify(action, never()).run(any(), any());
    verify(otherwise).run();
  }

  @Test public void ifPresent_or_firstIsPresent_secondSupplierIsAbsent() {
    ifPresent(Optional.of("foo"), consumer::accept)
        .or(() -> ifPresent(Optional.empty(), Optional.of("right"), action::run))
        .orElse(otherwise::run);
    verify(consumer).accept("foo");
    verify(action, never()).run(any(), any());
    verify(otherwise, never()).run();
  }

  @Test public void ifPresent_or_firstIsPresent_secondSupplierIsPresent() {
    ifPresent(Optional.of("foo"), consumer::accept)
        .or(() -> ifPresent(Optional.of("left"), Optional.of("right"), action::run))
        .orElse(otherwise::run);
    verify(consumer).accept("foo");
    verify(action, never()).run(any(), any());
    verify(otherwise, never()).run();
  }

  @Test public void ifPresent_optionalIsEmpty() {
    ifPresent(Optional.empty(), consumer::accept);
    verify(consumer, never()).accept(any());
  }

  @Test public void ifPresent_optionalIsPresent() {
    ifPresent(Optional.of("foo"), consumer::accept);
    verify(consumer).accept("foo");
  }

  @Test public void ifPresent_leftIsEmpty() {
    ifPresent(Optional.empty(), Optional.of("bar"), action::run);
    verify(action, never()).run(any(), any());
  }

  @Test public void ifPresent_rightIsEmpty() {
    ifPresent(Optional.of("foo"), Optional.empty(), action::run);
    verify(action, never()).run(any(), any());
  }

  @Test public void ifPresent_bothPresent() {
    ifPresent(Optional.of("foo"), Optional.of("bar"), action::run);
    verify(action).run("foo", "bar");
  }

  @Test public void map_leftIsEmpty() {
    assertThat(Optionals.map(Optional.empty(), Optional.of("bar"), action::run)).isEqualTo(Optional.empty());
    verify(action, never()).run(any(), any());
  }

  @Test public void map_rightIsEmpty() {
    assertThat(Optionals.map(Optional.of("foo"), Optional.empty(), action::run)).isEqualTo(Optional.empty());
    verify(action, never()).run(any(), any());
  }

  @Test public void map_bothArePresent_mapperReturnsNull() {
    assertThat(Optionals.map(Optional.of("foo"), Optional.empty(), (a, b) -> null))
        .isEqualTo(Optional.empty());
  }

  @Test public void map_bothArePresent_mapperReturnsNonNull() {
    assertThat(Optionals.map(Optional.of("foo"), Optional.of("bar"), (a, b) -> a + b))
        .isEqualTo(Optional.of("foobar"));
  }

  @Test public void flatMap_leftIsEmpty() {
    assertThat(Optionals.flatMap(
             Optional.empty(), Optional.of("bar"),(a, b) -> Optional.of(action.run(a, b))))
        .isEqualTo(Optional.empty());
    verify(action, never()).run(any(), any());
  }

  @Test public void flatMap_rightIsEmpty() {
    assertThat(Optionals.flatMap(
             Optional.of("foo"), Optional.empty(),(a, b) -> Optional.of(action.run(a, b))))
        .isEqualTo(Optional.empty());
    verify(action, never()).run(any(), any());
  }

  @Test public void flatMap_bothPresent_mapperReturnsNull() {
    assertThrows(
        NullPointerException.class,
        () -> Optionals.flatMap(Optional.of("foo"), Optional.of("bar"), (a, b) -> null));
  }

  @Test public void flatMap_bothPresent_mapperReturnsNonNull() {
    assertThat(Optionals.flatMap(Optional.of("foo"), Optional.of("bar"), (a, b) -> Optional.of(a + b)))
        .isEqualTo(Optional.of("foobar"));
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester()
        .setDefault(Optional.class, Optional.empty())
        .testAllPublicStaticMethods(Optionals.class);
    new NullPointerTester()
        .setDefault(Optional.class, Optional.of("foo"))
        .testAllPublicStaticMethods(Optionals.class);
    new ClassSanityTester()
        .setDefault(Optional.class, Optional.empty())
        .forAllPublicStaticMethods(Optionals.class).testNulls();
    new ClassSanityTester()
        .setDefault(Optional.class, Optional.of("foo"))
        .forAllPublicStaticMethods(Optionals.class).testNulls();
  }
  
  private interface BiAction {
    Object run(Object left, Object right);
  }
}
