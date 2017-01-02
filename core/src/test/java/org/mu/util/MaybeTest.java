package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mu.function.CheckedBiFunction;
import org.mu.function.CheckedFunction;
import org.mu.function.CheckedSupplier;

import com.google.common.truth.IterableSubject;
import com.google.common.truth.ThrowableSubject;

@RunWith(JUnit4.class)
public class MaybeTest {

  @Test public void testOfNull() throws Throwable {
    assertThat(Maybe.of(null).get()).isEqualTo(null);
    assertThat(Maybe.of(null).toString()).isEqualTo("null");
  }

  @Test public void testGet_success() throws Throwable {
    assertThat(Maybe.of("test").get()).isEqualTo("test");
  }

  @Test public void testGet_failure() throws Throwable {
    MyException exception = new MyException("test");
    Maybe<?, MyException> maybe = Maybe.except(exception);
    assertSame(exception, assertThrows(MyException.class, maybe::get));
  }

  @Test public void testMap_success() {
    Maybe<Integer, MyException> maybe = Maybe.of(1);
    assertThat(maybe.map(Object::toString)).isEqualTo(Maybe.of("1"));
    assertThrows(NullPointerException.class, () -> maybe.map(null));
  }

  @Test public void testMap_failure() {
    MyException exception = new MyException("test");
    Maybe<?, MyException> maybe = Maybe.except(exception).map(Object::toString);
    assertSame(exception, assertThrows(MyException.class, maybe::get));
    assertThrows(NullPointerException.class, () -> maybe.map(null));
  }

  @Test public void testFlatMap_success() {
    Maybe<Integer, MyException> maybe = Maybe.of(1);
    assertThat(maybe.flatMap(o -> Maybe.of(o.toString()))).isEqualTo(Maybe.of("1"));
    assertThrows(NullPointerException.class, () -> maybe.flatMap(null));
  }

  @Test public void testFlatMap_failure() {
    MyException exception = new MyException("test");
    Maybe<?, MyException> maybe = Maybe.except(exception).flatMap(o -> Maybe.of(o.toString()));
    assertSame(exception, assertThrows(MyException.class, maybe::get));
    assertThrows(NullPointerException.class, () -> maybe.flatMap(null));
  }

  @Test public void testIsPresent() {
    assertThat(Maybe.of(1).isPresent()).isTrue();
    assertThat(Maybe.except(new Exception()).isPresent()).isFalse();
  }

  @Test public void testIfPresent_success() {
    AtomicInteger succeeded = new AtomicInteger();
    Maybe.of(100).ifPresent(i -> succeeded.set(i));
    assertThat(succeeded.get()).isEqualTo(100);
    assertThrows(NullPointerException.class, () -> Maybe.of(0).ifPresent(null));
  }

  @Test public void testIfPresent_failure() {
    AtomicBoolean succeeded = new AtomicBoolean();
    Maybe.except(new Exception()).ifPresent(i -> succeeded.set(true));
    assertThat(succeeded.get()).isFalse();
    assertThrows(NullPointerException.class, () -> Maybe.except(new Exception()).ifPresent(null));
  }

  @Test public void testOrElse() {
    assertThat(Maybe.of("good").orElse(Throwable::getMessage)).isEqualTo("good");
    assertThat(Maybe.except(new Exception("bad")).orElse(Throwable::getMessage)).isEqualTo("bad");
    assertThrows(NullPointerException.class, () -> Maybe.of("good").orElse(null));
    assertThrows(NullPointerException.class, () -> Maybe.except(new Exception()).orElse(null));
  }

  @Test public void testCatching_success() {
    AtomicReference<Throwable> failed = new AtomicReference<>();
    Maybe.of(100).catching(e -> {failed.set(e);});
    assertThat(failed.get()).isNull();
    assertThrows(NullPointerException.class, () -> Maybe.of(0).catching(null));
  }

  @Test public void testCatching_failure() {
    MyException exception = new MyException("test");
    AtomicReference<Throwable> failed = new AtomicReference<>();
    Maybe.except(exception).catching(e -> {failed.set(e);});
    assertThat(failed.get()).isSameAs(exception);
    assertThrows(NullPointerException.class, () -> Maybe.except(exception).catching(null));
  }

  @Test public void testEqualsAndHashCode() {
    Maybe<?, ?> fail1 = Maybe.except(new MyException("bad"));
    Maybe<?, ?> fail2 = Maybe.except(new Exception());
    Maybe<?, ?> nil = Maybe.of(null);
    assertThat(Maybe.of("hello")).isEqualTo(Maybe.of("hello"));
    assertThat(Maybe.of("hello").hashCode()).isEqualTo(Maybe.of("hello").hashCode());
    assertThat(Maybe.of("hello")).isNotEqualTo(Maybe.of("world"));
    assertThat(Maybe.of("hello")).isNotEqualTo(fail1);
    assertThat(Maybe.of("hello")).isNotEqualTo(null);
    assertThat(Maybe.of("hello")).isNotEqualTo(nil);
    assertThat(nil).isEqualTo(nil);
    assertThat(nil.hashCode()).isEqualTo(Maybe.of(null).hashCode());
    assertThat(nil).isNotEqualTo(Maybe.of("hello"));
    assertThat(nil).isNotEqualTo(fail1);
    assertThat(fail1).isNotEqualTo(nil);
    assertThat(fail1).isEqualTo(fail1);
    assertThat(fail1.hashCode()).isEqualTo(fail1.hashCode());
    assertThat(fail1).isNotEqualTo(fail2);
    assertThat(fail1).isNotEqualTo(Maybe.of("hello"));
    assertThat(fail1).isNotEqualTo(null);
  }

  @Test public void testNulls() {
    assertThrows(NullPointerException.class, () -> Maybe.except(null));
    assertThrows(NullPointerException.class, () -> Maybe.wrap((CheckedSupplier<?, ?>) null));
    assertThrows(
        NullPointerException.class,
        () -> Maybe.wrap((CheckedSupplier<?, RuntimeException>) null, RuntimeException.class));
    assertThrows(
        NullPointerException.class, () -> Maybe.wrap(() -> justReturn("good"), null));
    assertThrows(NullPointerException.class, () -> Maybe.wrap((CheckedFunction<?, ?, ?>) null));
    assertThrows(
        NullPointerException.class,
        () -> Maybe.wrap((CheckedFunction<?, ?, RuntimeException>) null, RuntimeException.class));
    assertThrows(NullPointerException.class, () -> Maybe.wrap(this::justReturn, null));
    assertThrows(
        NullPointerException.class, () -> Maybe.wrap((CheckedBiFunction<?, ?, ?, ?>) null));
    assertThrows(
        NullPointerException.class,
        () -> Maybe.wrap((String a, String b) -> justReturn(a + b), null));
    assertThrows(
        NullPointerException.class,
        () -> Maybe.wrap((CheckedBiFunction<?, ?, ?, Exception>) null, Exception.class));
    assertThrows(NullPointerException.class, () -> Maybe.byValue(null));
  }

  @Test public void testStream_success() throws MyException {
    assertStream(Stream.of("hello", "friend").map(Maybe.wrap(this::justReturn)))
        .containsExactly("hello", "friend").inOrder();
  }

  @Test public void testStream_exception() {
    Stream<Maybe<String, MyException>> stream = 
        Stream.of("hello", "friend").map(Maybe.wrap(this::raise));
    assertThrows(MyException.class, () -> Maybe.collect(stream));
  }

  @Test public void testStream_uncheckedExceptionNotCaptured() {
    Stream<String> stream = Stream.of("hello", "friend")
          .map(Maybe.wrap(this::raiseUnchecked))
          .flatMap(m -> m.catching(e -> {}));
    assertThrows(RuntimeException.class, () -> stream.collect(toList()));
  }

  @Test public void testStream_swallowException() {
    assertThat(Stream.of("hello", "friend")
            .map(Maybe.wrap(this::raise))
            .flatMap(m -> m.catching(e -> {}))
            .collect(toList()))
        .isEmpty();
  }

  @Test public void testStream_generateSuccess() {
    assertThat(Stream.generate(Maybe.wrap(() -> justReturn("good"))).findFirst().get())
        .isEqualTo(Maybe.of("good"));
  }

  @Test public void testStream_generateFailure() {
    Maybe<String, MyException> maybe =
        Stream.generate(Maybe.wrap(() -> raise("bad"))).findFirst().get();
    assertThat(assertThrows(MyException.class, maybe::get).getMessage()).isEqualTo("bad");
  }

  @Test public void testFilterByValue_successValueFiltered() throws MyException {
    assertStream(Stream.of("hello", "friend")
            .map(Maybe.wrap(this::justReturn))
            .filter(Maybe.byValue("hello"::equals)))
        .containsExactly("hello");
  }

  @Test public void testFilterByValue_failuresNotFiltered() {
    List<Maybe<String, MyException>> maybes = Stream.of("hello", "friend")
        .map(Maybe.wrap(this::raise))
        .filter(Maybe.byValue(s -> false))
        .collect(toList());
    assertThat(maybes).hasSize(2);
    assertThat(assertThrows(MyException.class, () -> maybes.get(0).get()).getMessage())
        .isEqualTo("hello");
    assertThat(assertThrows(MyException.class, () -> maybes.get(1).get()).getMessage())
        .isEqualTo("friend");
  }

  @Test public void wrapFuture_futureIsSuccess() throws Exception {
    CompletionStage<Maybe<String, Exception>> stage =
        Maybe.catchException(Exception.class, completedFuture("good"));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get().get()).isEqualTo("good");
  }

  @Test public void wrapFuture_futureIsSuccessNull() throws Exception {
    CompletionStage<Maybe<String, Exception>> stage =
        Maybe.catchException(Exception.class, completedFuture(null));
    assertThat(completedFuture(null).isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get().get()).isNull();
  }

  @Test public void wrapFuture_futureIsExpectedFailure() throws Exception {
    MyException exception = new MyException("test");
    CompletionStage<Maybe<String, MyException>> stage =
        Maybe.catchException(MyException.class, exceptionally(exception));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get()).isEqualTo(Maybe.except(exception));
  }

  @Test public void wrapFuture_futureIsExpectedFailureNestedInExecutionException()
      throws Exception {
    MyUncheckedException exception = new MyUncheckedException("test");
    CompletionStage<Maybe<String, MyUncheckedException>> stage =
        Maybe.catchException(MyUncheckedException.class, executionExceptionally(exception));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().get()).isEqualTo(Maybe.except(exception));
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
  }

  @Test public void wrapFuture_futureIsUnexpectedFailure() throws Exception {
    RuntimeException exception = new RuntimeException("test");
    CompletionStage<Maybe<String, MyException>> stage =
        Maybe.catchException(MyException.class, exceptionally(exception));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, stage).isSameAs(exception);
  }

  @Test public void wrapFuture_futureIsUnexpectedCheckedException_idempotence() throws Exception {
    MyException exception = new MyException("test");
    CompletionStage<?> stage =
        Maybe.catchException(IOException.class, exceptionally(exception));
    stage = Maybe.catchException(IOException.class, stage);
    stage = Maybe.catchException(MyUncheckedException.class, stage);
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, stage).isSameAs(exception);
  }

  @Test public void wrapFuture_futureIsUnexpectedUncheckedException_idempotence() throws Exception {
    RuntimeException exception = new RuntimeException("test");
    CompletionStage<?> stage =
        Maybe.catchException(IOException.class, exceptionally(exception));
    stage = Maybe.catchException(IOException.class, stage);
    stage = Maybe.catchException(MyException.class, stage);
    stage = Maybe.catchException(Error.class, stage);
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, stage).isSameAs(exception);
  }

  @Test public void wrapFuture_futureIsUnexpectedError_idempotence() throws Exception {
    Error error = new Error("test");
    CompletionStage<?> stage =
        Maybe.catchException(IOException.class, exceptionally(error));
    stage = Maybe.catchException(IOException.class, stage);
    stage = Maybe.catchException(MyException.class, stage);
    stage = Maybe.catchException(MyUncheckedException.class, stage);
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, stage).isSameAs(error);
  }

  @Test public void wrapFuture_futureIsUnexpectedFailure_notApplied() throws Exception {
    RuntimeException exception = new RuntimeException("test");
    CompletionStage<?> stage = exceptionally(exception);
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, stage).isSameAs(exception);
  }

  @Test public void wrapFuture_futureBecomesSuccess() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletionStage<Maybe<String, Exception>> stage = Maybe.catchException(Exception.class, future);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    future.complete("good");
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().get().get()).isEqualTo("good");
  }

  @Test public void wrapFuture_futureBecomesExpectedFailure() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletionStage<Maybe<String, MyException>> stage =
        Maybe.catchException(MyException.class, future);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    MyException exception = new MyException("test");
    future.completeExceptionally(exception);
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().get()).isEqualTo(Maybe.except(exception));
  }

  @Test public void wrapFuture_transparentToHandle() throws Exception {
    assertThat(naiveExceptionHandlingCode(exceptionalUserCode()).toCompletableFuture().get())
        .isNull();
    assertThat(naiveExceptionHandlingCode(
        Maybe.catchException(MyUncheckedException.class, exceptionalUserCode()))
            .toCompletableFuture().get())
        .isNull();
  }

  @Test public void wrapFuture_transparentToExceptionally() throws Exception {
    assertThat(naiveExceptionallyCode(exceptionalUserCode()).toCompletableFuture().get())
        .isNull();
    assertThat(naiveExceptionallyCode(
        Maybe.catchException(MyUncheckedException.class, exceptionalUserCode()))
            .toCompletableFuture().get())
        .isNull();
  }

  private static CompletionStage<String> exceptionalUserCode() {
    CompletableFuture<String> future = new CompletableFuture<>();
    MyException exception = new MyException("test");
    future.completeExceptionally(exception);
    return future;
  }

  private static <T> CompletionStage<T> naiveExceptionHandlingCode(
      CompletionStage<T> stage) {
    return stage.handle((v, e) -> {
      assertThat(e).isInstanceOf(MyException.class);
      return null;
    });
  }

  private static <T> CompletionStage<T> naiveExceptionallyCode(
      CompletionStage<T> stage) {
    return stage.exceptionally(e -> {
      assertThat(e).isInstanceOf(MyException.class);
      return null;
    });
  }

  @Test public void testCompletionStage_handle_wraps() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    MyException exception = new MyException("test");
    future.completeExceptionally(exception);
    CompletionStage<String> stage = future.handle((v, e) -> {
      throw new CompletionException(e);
    });
    assertCauseOf(ExecutionException.class, stage)
        .isSameAs(exception);
  }

  @Test public void testCompletionStage_exceptionally_wraps() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    MyException exception = new MyException("test");
    future.completeExceptionally(exception);
    CompletionStage<String> stage = future.exceptionally(e -> {
      throw new CompletionException(e);
    });
    assertCauseOf(ExecutionException.class, stage)
        .isSameAs(exception);
  }

  @Test public void wrapFuture_futureBecomesUnexpectedFailure() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletionStage<Maybe<String, MyException>> stage = Maybe.catchException(MyException.class, future);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    RuntimeException exception = new RuntimeException("test");
    future.completeExceptionally(exception);
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, stage).isSameAs(exception);
  }

  @Test public void testExecutionExceptionally() {
    RuntimeException exception = new RuntimeException("test");
    assertCauseOf(ExecutionException.class, executionExceptionally(exception))
        .isSameAs(exception);
  }

  private static <T> CompletionStage<T> exceptionally(Throwable e) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(e);
    return future;
  }

  private static <T> CompletionStage<T> executionExceptionally(RuntimeException e) {
    return completedFuture((T) null).whenComplete((v, x) -> {throw e;});
  }

  private static ThrowableSubject assertCauseOf(
      Class<? extends Throwable> exceptionType, CompletionStage<?> stage) {
    return assertThat(
        Assertions.assertThrows(exceptionType, stage.toCompletableFuture()::get).getCause());
  }

  private String raise(String s) throws MyException {
    throw new MyException(s);
  }

  @SuppressWarnings("unused")  // Signature needed for Maybe.wrap()
  private String raiseUnchecked(String s) throws MyException {
    throw new RuntimeException(s);
  }

  @SuppressWarnings("unused")  // Signature needed for Maybe.wrap()
  private String justReturn(String s) throws MyException {
    return s;
  }

  private static <T, E extends Throwable> IterableSubject assertStream(
      Stream<Maybe<T, E>> stream) throws E {
    return assertThat(Maybe.collect(stream));
  }

  @SuppressWarnings("serial")
  private static class MyException extends Exception {
    MyException(String message) {
      super(message);
    }
  }

  @SuppressWarnings("serial")
  private static class MyUncheckedException extends RuntimeException {
    MyUncheckedException(String message) {
      super(message);
    }
  }
}