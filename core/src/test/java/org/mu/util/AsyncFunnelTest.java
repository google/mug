package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.google.common.truth.Subject;
import com.google.common.truth.ThrowableSubject;

public class AsyncFunnelTest {

  @Spy private FakeExecutor executor;
  @Mock private Translator translator;
  @Mock private Renderer renderer;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void passthrough() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.add("hi");
    funnel.add("world");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    assertCompleted(futures.get(0)).isEqualTo("hi");
    assertCompleted(futures.get(1)).isEqualTo("world");
    verify(executor, never()).execute(any(Runnable.class));
  }

  @Test public void unchecked() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    funnel.through(translator::translate).accept("aloha");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isEqualTo("one");
    assertCompleted(futures.get(1)).isEqualTo("hi");
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void unchecked_withPostConversion() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1, v -> v + v);
    funnel.through(translator::translate).accept("aloha");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isEqualTo("oneone");
    assertCompleted(futures.get(1)).isEqualTo("hi");
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void unchecked_postConversionReturnsNull() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1, v -> null);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    assertNotCompleted(futures.get(0));
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isNull();
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void unchecked_postConversionThrows() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    AsyncFunnel.Batch<Integer, String> render = funnel.through(renderer::render);
    render.accept(1, v -> {throw new MyUncheckedException();});
    funnel.through(translator::translate).accept("aloha");
    render.accept(2);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(3);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    assertNotCompleted(futures.get(2));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1, 2))).thenReturn(asList("one", "two"));
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0))
        .isInstanceOf(MyUncheckedException.class);
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCompleted(futures.get(2)).isEqualTo("two");
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void unchecked_nullOutputIsFine() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    assertNotCompleted(futures.get(0));
    when(renderer.render(asList(1))).thenReturn(Collections.singletonList(null));
    executor.run();
    assertCompleted(futures.get(0)).isNull();
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void unchecked_exceptional() throws Exception {
    MyUncheckedException exception = new MyUncheckedException();
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    when(renderer.render(asList(1))).thenThrow(exception);
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0)).isSameAs(exception);
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void batchOutputsLessThanInputs() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    when(renderer.render(asList(1))).thenReturn(asList());
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0))
        .isInstanceOf(IllegalStateException.class);
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void batchOutputsMoreThanInputs() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    when(renderer.render(asList(1))).thenReturn(asList("bad", "bad"));
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0))
        .isInstanceOf(IllegalStateException.class);
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void unchecked_error() throws Exception {
    MyError error = new MyError();
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    when(renderer.render(asList(1))).thenThrow(error);
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0)).isSameAs(error);
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void checked() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    CompletionStage<Maybe<String, IOException>> one =
        funnel.through(renderer::render, IOException.class).accept(1);
    CompletionStage<Maybe<String, IOException>> aloha =
        funnel.through(translator::translate, IOException.class).accept("aloha");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    assertNotCompleted(one);
    assertNotCompleted(aloha);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isEqualTo("one");
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCompleted(one).isEqualTo(Maybe.of("one"));
    assertCompleted(aloha).isEqualTo(Maybe.of("hi"));
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void checked_withPostConversion() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    CompletionStage<Maybe<String, IOException>> one =
        funnel.through(renderer::render, IOException.class).accept(1, v -> v + v);
    CompletionStage<Maybe<String, IOException>> aloha =
        funnel.through(translator::translate, IOException.class).accept("aloha");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    assertNotCompleted(one);
    assertNotCompleted(aloha);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isEqualTo("oneone");
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCompleted(one).isEqualTo(Maybe.of("oneone"));
    assertCompleted(aloha).isEqualTo(Maybe.of("hi"));
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void checked_postConversionReturnsNull() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    CompletionStage<Maybe<String, IOException>> one =
        funnel.through(renderer::render, IOException.class).accept(1, v -> null);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    assertNotCompleted(one);
    assertNotCompleted(futures.get(0));
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isNull();
    assertCompleted(one).isEqualTo(Maybe.of(null));
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void checked_postConversionThrowsUnexpected() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    AsyncFunnel.CheckedBatch<Integer, String, IOException> render =
        funnel.through(renderer::render, IOException.class);
    CompletionStage<Maybe<String, IOException>> one =
        render.accept(1, v -> {throw new MyUncheckedException();});
    CompletionStage<Maybe<String, IOException>> aloha =
        funnel.through(translator::translate, IOException.class).accept("aloha");
    CompletionStage<Maybe<String, IOException>> two = render.accept(2);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(3);
    assertNotCompleted(one);
    assertNotCompleted(two);
    assertNotCompleted(aloha);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    assertNotCompleted(futures.get(2));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1, 2))).thenReturn(asList("one", "two"));
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0))
        .isInstanceOf(MyUncheckedException.class);
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCompleted(futures.get(2)).isEqualTo("two");
    assertCauseOf(ExecutionException.class, one)
        .isInstanceOf(MyUncheckedException.class);
    assertCompleted(aloha).isEqualTo(Maybe.of("hi"));
    assertCompleted(two).isEqualTo(Maybe.of("two"));
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void checked_postConversionThrowsExpected() throws Exception {
    IOException exception = new IOException();
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    AsyncFunnel.CheckedBatch<Integer, String, IOException> render =
        funnel.through(renderer::render, IOException.class);
    CompletionStage<Maybe<String, IOException>> one =
        render.accept(1, v -> {throw exception;});
    CompletionStage<Maybe<String, IOException>> aloha =
        funnel.through(translator::translate, IOException.class).accept("aloha");
    CompletionStage<Maybe<String, IOException>> two = render.accept(2);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(3);
    assertNotCompleted(one);
    assertNotCompleted(two);
    assertNotCompleted(aloha);
    assertNotCompleted(futures.get(0));
    assertNotCompleted(futures.get(1));
    assertNotCompleted(futures.get(2));
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    when(renderer.render(asList(1, 2))).thenReturn(asList("one", "two"));
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0)).isSameAs(exception);
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCompleted(futures.get(2)).isEqualTo("two");
    assertCompleted(one).isEqualTo(Maybe.except(exception));
    assertCompleted(aloha).isEqualTo(Maybe.of("hi"));
    assertCompleted(two).isEqualTo(Maybe.of("two"));
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void checked_nullOutputIsFine() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    CompletionStage<Maybe<String, IOException>> one =
        funnel.through(renderer::render, IOException.class).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(1);
    assertNotCompleted(one);
    assertNotCompleted(futures.get(0));
    when(renderer.render(asList(1))).thenReturn(Collections.singletonList(null));
    executor.run();
    assertCompleted(futures.get(0)).isNull();
    assertCompleted(one).isEqualTo(Maybe.of(null));
    verify(executor).execute(any(Runnable.class));
  }

  @Test public void checked_expectedException() throws Exception {
    IOException exception = new IOException();
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    CompletionStage<Maybe<String, IOException>> one =
        funnel.through(renderer::render, IOException.class).accept(1);
    CompletionStage<Maybe<String, IOException>> aloha =
        funnel.through(translator::translate, IOException.class).accept("aloha");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    when(renderer.render(asList(1))).thenThrow(exception);
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0)).isSameAs(exception);
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCompleted(one).isEqualTo(Maybe.except(exception));
    assertCompleted(aloha).isEqualTo(Maybe.of("hi"));
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void checked_unexpectedException() throws Exception {
    MyUncheckedException exception = new MyUncheckedException();
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    CompletionStage<Maybe<String, IOException>> one =
        funnel.through(renderer::render, IOException.class).accept(1);
    CompletionStage<Maybe<String, IOException>> aloha =
        funnel.through(translator::translate, IOException.class).accept("aloha");
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).hasSize(2);
    when(renderer.render(asList(1))).thenThrow(exception);
    when(translator.translate(asList("aloha"))).thenReturn(asList("hi"));
    executor.run();
    assertCauseOf(ExecutionException.class, futures.get(0)).isSameAs(exception);
    assertCompleted(futures.get(1)).isEqualTo("hi");
    assertCauseOf(ExecutionException.class, one).isSameAs(exception);
    assertCompleted(aloha).isEqualTo(Maybe.of("hi"));
    verify(executor, times(2)).execute(any(Runnable.class));
  }

  @Test public void emptyFunnel() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    List<CompletionStage<String>> futures = funnel.run(executor);
    assertThat(futures).isEmpty();
    verify(executor, never()).execute(any(Runnable.class));
  }

  @Test public void noReruns() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    funnel.through(renderer::render).accept(1);
    List<CompletionStage<String>> futures = funnel.run(executor);
    when(renderer.render(asList(1))).thenReturn(asList("one"));
    executor.run();
    assertCompleted(futures.get(0)).isEqualTo("one");
    verify(executor).execute(any(Runnable.class));

    // Should not execute anything any more.
    assertThat(funnel.run(executor)).isEmpty();
    executor.run();
    Mockito.verifyNoMoreInteractions(executor);
  }

  @Test public void testNulls() throws Exception {
    AsyncFunnel<String> funnel = new AsyncFunnel<>();
    assertThrows(NullPointerException.class, () -> funnel.add(null));
    assertThrows(NullPointerException.class, () -> funnel.through(null));
    assertThrows(NullPointerException.class, () -> funnel.through(null, Exception.class));
    assertThrows(NullPointerException.class, () -> funnel.through(c -> asList(), null));
    assertThrows(NullPointerException.class, () -> funnel.through(c -> asList()).accept(null));
    assertThrows(
        NullPointerException.class,
        () -> funnel.through(c -> asList(), Exception.class).accept(null));
    assertThrows(
        NullPointerException.class,
        () -> funnel.through(c -> asList(), Exception.class).accept(null, v -> v));
    assertThrows(
        NullPointerException.class,
        () -> funnel.through(c -> asList(), Exception.class).accept("", null));
    assertThrows(NullPointerException.class, () -> funnel.run(null));
  }

  private static Subject<?, Object> assertCompleted(CompletionStage<?> stage)
      throws InterruptedException, ExecutionException {
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    return assertThat(stage.toCompletableFuture().get());
  }

  private static void assertNotCompleted(CompletionStage<?> stage) {
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
  }

  private static ThrowableSubject assertCauseOf(
      Class<? extends Throwable> exceptionType, CompletionStage<?> stage) {
    return assertThat(
        Assertions.assertThrows(exceptionType, stage.toCompletableFuture()::get).getCause());
  }

  static class FakeExecutor implements Executor {
    private final List<Runnable> commands = new ArrayList<>();

    @Override public void execute(Runnable command) {
      assertThat(command).isNotNull();
      commands.add(command);
    }

    final void run() {
      for (Runnable command : new ArrayList<>(commands)) {
        command.run();
      }
      commands.clear();
    }
  }

  private interface Translator {
    List<String> translate(List<String> messages) throws IOException;
  }

  private interface Renderer {
    List<String> render(List<Integer> numbers) throws IOException;
  }

  @SuppressWarnings("serial")
  private static class MyUncheckedException extends RuntimeException {
    MyUncheckedException() {
      super("test");
    }
  }

  @SuppressWarnings("serial")
  private static final class MyError extends Error {
    MyError() {
      super("test");
    }
  }
}
