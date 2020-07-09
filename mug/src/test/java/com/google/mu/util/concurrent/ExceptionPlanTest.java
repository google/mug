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
package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.ClassSanityTester;
import com.google.mu.util.Maybe;

@RunWith(JUnit4.class)
public class ExceptionPlanTest {
  @Test public void emptyPlan_errorPropagation() {
    ExceptionPlan<String> plan = new ExceptionPlan<>();
    MyError error = new MyError();
    Maybe<ExceptionPlan.Execution<String>, MyError> maybe = plan.execute(error);
    assertSame(error, assertThrows(MyError.class, () -> maybe.orElseThrow(e -> e)));
  }

  @Test public void emptyPlan_uncheckedPropagation() {
    ExceptionPlan<String> plan = new ExceptionPlan<>();
    RuntimeException exception = new RuntimeException("test");
    Maybe<ExceptionPlan.Execution<String>, RuntimeException> maybe = plan.execute(exception);
    assertSame(exception, assertThrows(RuntimeException.class, () -> maybe.orElseThrow(e -> e)));
  }

  @Test public void emptyPlan_checkedPropagation() {
    ExceptionPlan<String> plan = new ExceptionPlan<>();
    Exception exception = new Exception("test");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe = plan.execute(exception);
    assertSame(exception, assertThrows(Exception.class, () -> maybe.orElseThrow(e -> e)));
  }

  @Test public void planWithZeroStrategies() {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(Exception.class, asList());
    Exception exception = new Exception("test");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe = plan.execute(exception);
    assertSame(exception, assertThrows(Exception.class, () -> maybe.orElseThrow(e -> e)));
  }

  @Test public void planWithStrategies() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(Exception.class, asList("retry", "report"));
    Exception exception = new Exception("test");
    ExceptionPlan.Execution<String> execution = plan.execute(exception).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("retry");
    execution = execution.remainingExceptionPlan().execute(exception).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("report");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe =
        execution.remainingExceptionPlan().execute(exception);
    assertSame(exception, assertThrows(Exception.class, () -> maybe.orElseThrow(e -> e)));
  }

  @Test public void planWithPartiallyOverlappingRules() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(MyException.class, asList("report"))
        .upon(Exception.class, asList("report", "report"));
    MyException myException = new MyException();
    ExceptionPlan.Execution<String> execution = plan.execute(myException).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("report");
    Maybe<ExceptionPlan.Execution<String>, Exception> maybe =
        execution.remainingExceptionPlan().execute(myException);
    assertSame(myException, assertThrows(MyException.class, () -> maybe.orElseThrow(e -> e)));
    assertThat(plan.execute(new Exception("test")).orElseThrow().strategy()).isEqualTo("report");
  }

  @Test public void withTypedPredicate() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(Exception.class, e -> "recoverable".equals(e.getMessage()), asList("recover"))
        .upon(Exception.class, asList("report"));
    MyError error = new MyError();
    assertSame(error, assertThrows(MyError.class, () -> plan.execute(error).orElseThrow(e -> e)));

    ExceptionPlan.Execution<String> execution =
        plan.execute(new Exception("recoverable")).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("recover");
    execution = plan.execute(new Exception("bad")).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("report");
  }

  @Test public void withPredicate() throws Exception {
    ExceptionPlan<String> plan = new ExceptionPlan<String>()
        .upon(e -> "recoverable".equals(e.getMessage()), asList("recover"))
        .upon(Exception.class, asList("report"));
    MyError error = new MyError();
    assertSame(error, assertThrows(MyError.class, () -> plan.execute(error).orElseThrow(e -> e)));
    ExceptionPlan.Execution<String> execution =
        plan.execute(new Exception("recoverable")).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("recover");
    execution = plan.execute(new Exception("bad")).orElseThrow();
    assertThat(execution.strategy()).isEqualTo("report");
  }

  @Test public void testNulls() {
    new ClassSanityTester().testNulls(ExceptionPlan.class);
  }

  @SuppressWarnings("serial")
  private static class MyException extends Exception {}

  @SuppressWarnings("serial")
  private static class MyError extends Error {}
}
