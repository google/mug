package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExceptionPlanTest {
  @Test public void emptyPlan_errorPropagation() {
    ExceptionPlan<String> plan = new ExceptionPlan<>();
    MyError error = new MyError();
    Maybe<ExceptionPlan.Execution<String>, MyError> maybe = plan.execute(error);
    assertSame(error, assertThrows(MyError.class, maybe::get));
  }

  @Test public void emptyPlan_uncheckedPropagation() {
    ExceptionPlan<String> plan = new ExceptionPlan<>();
    RuntimeException exception = new RuntimeException("test");
    Maybe<ExceptionPlan.Execution<String>, RuntimeException> maybe = plan.execute(exception);
    assertSame(exception, assertThrows(RuntimeException.class, maybe::get));
  }

  @Test public void emptyPlan_checkedPropagation() {
    ExceptionPlan<String> plan = new ExceptionPlan<>();
    Exception exception = new Exception("test");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe = plan.execute(exception);
    assertSame(exception, assertThrows(Exception.class, maybe::get));
  }

  @Test public void planWithZeroStrategies() {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(Exception.class, asList());
    Exception exception = new Exception("test");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe = plan.execute(exception);
    assertSame(exception, assertThrows(Exception.class, maybe::get));
  }

  @Test public void planWithStrategies() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(Exception.class, asList("retry", "report"));
    Exception exception = new Exception("test");
    ExceptionPlan.Execution<String> execution = plan.execute(exception).get();
    assertThat(execution.strategy()).isEqualTo("retry");
    execution = execution.remainingExceptionPlan().execute(exception).get();
    assertThat(execution.strategy()).isEqualTo("report");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe =
        execution.remainingExceptionPlan().execute(exception);
    assertSame(exception, assertThrows(Exception.class, maybe::get));
  }

  @Test public void planWithPartiallyOverlappingRules() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(MyException.class, asList("report"))
        .upon(Exception.class, asList("report", "report"));
    MyException myException = new MyException();
    ExceptionPlan.Execution<String> execution = plan.execute(myException).get();
    assertThat(execution.strategy()).isEqualTo("report");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe =
        execution.remainingExceptionPlan().execute(myException);
    assertSame(myException, assertThrows(MyException.class, maybe::get));
    assertThat(plan.execute(new Exception("test")).get().strategy()).isEqualTo("report");
  }

  @Test public void withTypedPredicate() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(Exception.class, e -> "recoverable".equals(e.getMessage()), asList("recover"))
        .upon(Exception.class, asList("report"));
    MyError error = new MyError();
    assertSame(error, assertThrows(MyError.class, () -> plan.execute(error).get()));
    
    ExceptionPlan.Execution<String> execution = plan.execute(new Exception("recoverable")).get();
    assertThat(execution.strategy()).isEqualTo("recover");
    execution = plan.execute(new Exception("bad")).get();
    assertThat(execution.strategy()).isEqualTo("report");
  }

  @Test public void withPredicate() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(e -> "recoverable".equals(e.getMessage()), asList("recover"))
        .upon(Exception.class, asList("report"));
    MyError error = new MyError();
    assertSame(error, assertThrows(MyError.class, () -> plan.execute(error).get()));
    ExceptionPlan.Execution<String> execution = plan.execute(new Exception("recoverable")).get();
    assertThat(execution.strategy()).isEqualTo("recover");
    execution = plan.execute(new Exception("bad")).get();
    assertThat(execution.strategy()).isEqualTo("report");
  }

  @Test public void testNulls() {
    Class<Throwable> nullType = null;
    Predicate<Object> nullCondition = null;
    assertThrows(NullPointerException.class, () -> new ExceptionPlan<>().upon(nullType, asList()));
    assertThrows(
        NullPointerException.class, () -> new ExceptionPlan<>().upon(Exception.class, null));
    assertThrows(
        NullPointerException.class,
        () -> new ExceptionPlan<>().upon(nullType, x -> true, asList()));
    assertThrows(
        NullPointerException.class,
        () -> new ExceptionPlan<>().upon(Exception.class, nullCondition, asList()));
    assertThrows(
        NullPointerException.class,
        () -> new ExceptionPlan<>().upon(nullCondition, asList()));
    assertThrows(
        NullPointerException.class, () -> new ExceptionPlan<>().upon(x -> true, null));
    assertThrows(
        NullPointerException.class,
        () -> new ExceptionPlan<>().upon(Exception.class, x -> true, null));
  }

  @SuppressWarnings("serial")
  private static class MyException extends Exception {}

  @SuppressWarnings("serial")
  private static class MyError extends Error {}
}
