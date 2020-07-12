/*****************************************************************************
 * Copyright (C) google.com                                                *
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

import static com.google.mu.util.concurrent.Utils.typed;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.mu.util.Maybe;
import com.google.mu.util.concurrent.Retryer.Delay;

/**
 * Configures an abstract plan based on exceptions.
 *
 * This class is immutable.
 *
 * Each exceptional rule is configured with a list of abstract strategies.
 * Users are expected to use the continuation returned by {@link #execute} upon any exception.
 *
 * The returned {@link Execution} includes both the current strategy for the exception in
 * question, and a new {@code ExeceptionPlan} object for upcoming exceptions in the same logical
 * group (such as exceptions thrown by the same method invocation in a retry).
 *
 * <p>For {@link Retryer}, {@code T} can be {@link Delay} between retries. But any strategy types
 * work too.
 *
 * <p>Strategies specified through {@link #upon} are picked with
 * respect to the order they are added. Think of them as a bunch of
 * {@code if ... else if ...}. That is, <em>always specify the more specific exception first</em>.
 * The following code is wrong because the {@code IOException} strategies will always be
 * overshadowed by the {@code Exception} strategies.
 * <pre>{@code
 * ExceptionPlan<Duration> = new ExceptionPlan<Duration>()
 *     .upon(Exception.class, exponentialBackoff(...))
 *     .upon(IOException.class, uniformDelay(...));
 * }</pre>
 */
final class ExceptionPlan<T> {

  private final List<Rule<T>> rules;

  public ExceptionPlan() {
    this.rules = Collections.emptyList();
  }

  private ExceptionPlan(List<Rule<T>> rules) {
    this.rules = rules;
  }

  /**
   * Returns a new {@link ExceptionPlan} that uses {@code strategies} when an exception satisfies
   * {@code condition}.
   */
  public ExceptionPlan<T> upon(
      Predicate<? super Throwable> condition, List<? extends T> strategies) {
    List<Rule<T>> newRules = new ArrayList<>(rules);
    newRules.add(new Rule<T>(condition, strategies));
    return new ExceptionPlan<>(newRules);
  }

  /**
   * Returns a new {@link ExceptionPlan} that uses {@code strategies} when an exception is
   * instance of {@code exceptionType}.
   */
  public ExceptionPlan<T> upon(
      Class<? extends Throwable> exceptionType, List<? extends T> strategies) {
    return upon(exceptionType::isInstance, strategies);
  }

  /**
   * Returns a new {@link ExceptionPlan} that uses {@code strategies} when an exception is instance
   * of {@code exceptionType} and satisfies {@code condition}.
   */
  public <E extends Throwable> ExceptionPlan<T> upon(
      Class<E> exceptionType, Predicate<? super E> condition, List<? extends T> strategies) {
    return upon(typed(exceptionType, condition) , strategies);
  }

  /**
   * Executes the plan and either returns an {@link Execution} or throws {@code E} if the exception
   * isn't covered by the plan or the plan decides to propagate.
   */
  public <E extends Throwable> Maybe<Execution<T>, E> execute(E exception) {
    requireNonNull(exception);
    List<Rule<T>> remainingRules = new ArrayList<>();
    Rule<T> applicable = null;
    for (Rule<T> rule : rules) {
      if (applicable == null && rule.appliesTo(exception)) {
        applicable = rule;
        remainingRules.add(rule.remaining());
      } else {
        remainingRules.add(rule);
      }
    }
    if (applicable == null) return Maybe.except(exception);
    return applicable.currentStrategy()
        .map(s -> new Execution<>(s, new ExceptionPlan<>(remainingRules)))
        .map(Maybe::<Execution<T>, E>of)
        .orElse(Maybe.except(exception));  // The rule refuses to handle it.
  }

  /** Returns {@code true} if {@code exception} is covered in this plan. */
  public boolean covers(Throwable exception) {
    requireNonNull(exception);
    return rules.stream().anyMatch(rule -> rule.appliesTo(exception));
  }

  /** Describes what to do for the given exception. */
  public static final class Execution<T> {
    private final T strategy;
    private final ExceptionPlan<T> exceptionPlan;

    Execution(T stragety, ExceptionPlan<T> exceptionPlan) {
      this.strategy = requireNonNull(stragety);
      this.exceptionPlan = requireNonNull(exceptionPlan);
    }

    /** Returns the strategy to handle the current exception. Up to caller to interpret. */
    public T strategy() {
      return strategy;
    }

    /** Returns the exception plan for remaining exceptions. */
    public ExceptionPlan<T> remainingExceptionPlan() {
      return exceptionPlan;
    }
  }

  private static final class Rule<T> {
    private final Predicate<? super Throwable> condition;
    private final List<? extends T> strategies;
    private final int index;

    private Rule(Predicate<? super Throwable> condition, List<? extends T> strategies, int index) {
      this.condition = requireNonNull(condition);
      this.strategies = requireNonNull(strategies);
      this.index = index;
    }

    Rule(Predicate<? super Throwable> condition, List<? extends T> strategies) {
      this(condition, strategies, 0);
    }

    boolean appliesTo(Throwable exception) {
      return condition.test(exception);
    }

    Rule<T> remaining() {
      return new Rule<>(condition, strategies, index + 1);
    }

    Optional<T> currentStrategy() {
      if (index >= strategies.size()) return Optional.empty();
      try {
        return Optional.of(strategies.get(index));
      } catch (IndexOutOfBoundsException e) {
        // In case the list just changed due to race condition or side-effects.
        return Optional.empty();
      }
    }
  }
}
