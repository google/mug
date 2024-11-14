package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Optionals.ifPresent;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Optionals.optionally;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;

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
  @Mock private Consumer<Object> consumer;
  @Mock private Runnable otherwise;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void optionally_empty() {
    assertThat(
            optionally(
                false,
                () -> {
                  throw new AssertionError();
                }))
        .isEmpty();
  }

  @Test public void optionally_supplierReturnsNull() {
    assertThat(optionally(true, () -> null)).isEmpty();
  }

  @Test public void optionally_notEmpty() {
    assertThat(optionally(true, () -> "v")).hasValue("v");
  }

  @Test public void optional_empty() {
    assertThat(optional(false, "whatever")).isEmpty();
    assertThat(optional(false, null)).isEmpty();
  }

  @Test public void optional_nullValueIsTranslatedToEmpty() {
    assertThat(optional(true, null)).isEmpty();
  }

  @Test public void optional_notEmpty() {
    assertThat(optional(true, "v")).hasValue("v");
  }

  @Test public void asSet_empty() {
    assertThat(Optionals.asSet(Optional.empty())).isEmpty();
  }

  @Test public void asSet_notEmpty() {
    assertThat(Optionals.asSet(Optional.of(123))).containsExactly(123);
  }

  @Test public void nonEmpty_emptyCollection() {
    assertThat(Optionals.nonEmpty(asList())).isEmpty();
  }

  @Test public void nonEmpty_nonEmptyCollection() {
    assertThat(Optionals.nonEmpty(asList(1))).hasValue(asList(1));
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

  @Test public void ifPresent_optionalIntIsEmpty() {
    ifPresent(OptionalInt.empty(), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer, never()).accept(any());
    verify(otherwise).run();
  }

  @Test public void ifPresent_optionalIntIsPresent() {
    ifPresent(OptionalInt.of(123), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer).accept(123);
    verify(otherwise, never()).run();
  }

  @Test public void ifPresent_optionalLongIsEmpty() {
    ifPresent(OptionalLong.empty(), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer, never()).accept(any());
    verify(otherwise).run();
  }

  @Test public void ifPresent_optionalLongIsPresent() {
    ifPresent(OptionalLong.of(123), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer).accept(123L);
    verify(otherwise, never()).run();
  }

  @Test public void ifPresent_optionalDoubleIsEmpty() {
    ifPresent(OptionalDouble.empty(), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer, never()).accept(any());
    verify(otherwise).run();
  }

  @Test public void ifPresent_optionalDoubleIsPresent() {
    ifPresent(OptionalDouble.of(123), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer).accept(123D);
    verify(otherwise, never()).run();
  }

  @Test public void ifPresent_optionalIsEmpty() {
    ifPresent(Optional.empty(), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer, never()).accept(any());
    verify(otherwise).run();
  }

  @Test public void ifPresent_optionalIsPresent() {
    ifPresent(Optional.of("foo"), consumer::accept)
        .orElse(otherwise::run);
    verify(consumer).accept("foo");
    verify(otherwise, never()).run();
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

  @Test public void ifPresent_biOptional_empty() {
    assertThat(ifPresent(BiOptional.empty(), action::run)).isEqualTo(Conditional.FALSE);
    verify(action, never()).run(any(), any());
  }

  @Test public void ifPresent_biOptional_notEmpty() {
    assertThat(ifPresent(BiOptional.of("foo", "bar"), action::run)).isEqualTo(Conditional.TRUE);
    verify(action).run("foo", "bar");
  }

  @Test public void both_bothEmpty() {
    assertThat(Optionals.both(Optional.empty(), Optional.empty())).isEqualTo(BiOptional.empty());
  }

  @Test public void both_oneIsEmpty() {
    assertThat(Optionals.both(Optional.empty(), Optional.of("one"))).isEqualTo(BiOptional.empty());
    assertThat(Optionals.both(Optional.of(1), Optional.empty())).isEqualTo(BiOptional.empty());
  }

  @Test public void both_noneEmpty() {
    assertThat(Optionals.both(Optional.of(1), Optional.of("one")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void inOrder_firstStepIsEmpty_secondStepNotEvaluated() {
    assertThat(
            Optionals.inOrder(
                    Optional.empty(),
                    () -> {
                      throw new AssertionError();
                    })
                .isPresent())
        .isFalse();
    assertThat(
            Optionals.inOrder(
                    Optional.empty(),
                    x -> {
                      throw new AssertionError();
                    })
                .isPresent())
        .isFalse();
  }

  @Test
  public void inOrder_secondStepEvaluatesToEmpty() {
    assertThat(Optionals.inOrder(Optional.of(1), Optional::empty).isPresent()).isFalse();
    assertThat(Optionals.inOrder(Optional.empty(), x -> Optional.empty()).isPresent()).isFalse();
  }

  @Test
  public void inOrder_bothStepsArePresent() {
    assertThat(Optionals.inOrder(Optional.of(1), () -> Optional.of(2)).map((a, b) -> a + b))
        .hasValue(3);
    assertThat(Optionals.inOrder(Optional.of(10), a -> Optional.of(a * 20)).map((a, b) -> a + b))
        .hasValue(210);
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester()
        .setDefault(Optional.class, Optional.empty())
        .setDefault(OptionalInt.class, OptionalInt.empty())
        .setDefault(OptionalLong.class, OptionalLong.empty())
        .setDefault(OptionalDouble.class, OptionalDouble.empty())
        .setDefault(BiOptional.class, BiOptional.of(1, "one"))
        .ignore(Optionals.class.getMethod("optional", boolean.class, Object.class))
        .ignore(Optionals.class.getMethod("both", Optional.class, Optional.class))
        .testAllPublicStaticMethods(Optionals.class);
    new NullPointerTester()
        .setDefault(Optional.class, Optional.of("foo"))
        .setDefault(OptionalInt.class, OptionalInt.of(123))
        .setDefault(OptionalLong.class, OptionalLong.of(123))
        .setDefault(OptionalDouble.class, OptionalDouble.of(123))
        .setDefault(BiOptional.class, BiOptional.of(1, "one"))
        .ignore(Optionals.class.getMethod("optional", boolean.class, Object.class))
        .testAllPublicStaticMethods(Optionals.class);
  }

  private interface BiAction {
    Object run(Object left, Object right);
  }
}
