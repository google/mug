package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class OptionalsTest {
  @Mock private BiAction action;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void ifPresent_leftIsEmpty() {
    Optionals.ifPresent(Optional.empty(), Optional.of("bar"), action::run);
    verify(action, never()).run(any(), any());
  }

  @Test public void ifPresent_rightIsEmpty() {
    Optionals.ifPresent(Optional.of("foo"), Optional.empty(), action::run);
    verify(action, never()).run(any(), any());
  }

  @Test public void ifPresent_bothPresent() {
    Optionals.ifPresent(Optional.of("foo"), Optional.of("bar"), action::run);
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
        .isEqualTo(Optional.ofNullable("foobar"));
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester()
        .setDefault(Optional.class, Optional.empty())
        .testAllPublicStaticMethods(Optionals.class);
  }
  
  private interface BiAction {
    Object run(Object left, Object right);
  }
}
