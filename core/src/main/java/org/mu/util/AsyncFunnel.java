package org.mu.util;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mu.function.CheckedFunction;

/**
 * A funnel that dispatches a sequence of inputs through arbitrary batch conversions asynchronously
 * while maintaining first-in-first-out order. For example, the following code can either batch
 * load users from a user store, or batch load from third party user store, or else create a dummy
 * user immediately without conversion:
 *
 * <pre>{@code
 * AsyncFunnel<User> funnel = new AsyncFunnel<>();
 * Batch<Long, User> userStoreBatch = funnel.through(userStore::loadUsers);
 * Batch<ThirdPartyUser> thirdPartyBatch = funnel.through(thirdPartyClient::loadUsers);
 * for (UserDto dto : users) {
 *   if (dto.hasUserId()) {
 *     userStoreBatch.accept(dto.getUserId());
 *   } else if (dto.hasThirdParty()) {
 *     thirdPartyBatch.accept(dto.getThirdParty());
 *   } else {
 *     funnel.add(createDummyUser(dto));
 *   }
 * }
 * List<CompletionStage<User>> users = funnel.run(executor);
 * }</pre>
 *
 * <p>All conversions are executed by the {@link Executor} instance passed to {@link #run}.
 *
 * <p>Elements flow out of the funnel in the same order as they enter, regardless of which {@link
 * Consumer} instance admitted them, or if they were directly {@link #add added} into the
 * funnel without conversion.
 */
public final class AsyncFunnel<T> {
  private final List<Task<?, T>> tasks = new ArrayList<>();
  private final List<AbstractBatch<?, T>> batches = new ArrayList<>();
  private final AbstractBatch<T, T> passthrough = new AbstractBatch<T, T>(this) {
    @Override void submit(List<Task<T, T>> trivialTasks, Executor executor) {
      trivialTasks.forEach(t -> t.future.complete(t.input));
    }
  };

  public AsyncFunnel() {
    batches.add(passthrough);
  }

  /** Adds {@code value} directly without conversion. */
  public void add(T value) {
    passthrough.accept(value);
  }

  /**
   * Returns a batch that converts inputs through {@code batchConverter}.
   * {@link CheckedBatch} allows callers to access exceptions through {@code Maybe<T, E>} type
   * safely.
   */
  public <F, E extends Throwable> CheckedBatch<F, T, E> through(
      CheckedFunction<? super List<F>, ? extends Collection<T>, E> batchConverter,
      Class<E> exceptionType) {
    return new CheckedBatch<>(through(batchConverter), exceptionType);
  }

  /** Returns a batch that converts inputs through {@code batchConverter}. */
  public <F> Batch<F, T> through(
      CheckedFunction<? super List<F>, ? extends Collection<T>, ?> batchConverter) {
    Batch<F, T> batch = new Batch<>(this, batchConverter);
    batches.add(batch);
    return batch;
  }

  /** Runs all batches in {@code executor} and returns the result futures in encounter order. */
  public List<CompletionStage<T>> run(Executor executor) {
    requireNonNull(executor);
    Funnel<CompletionStage<T>> funnel = new Funnel<>();
    // We need a Batch<F, T> -> Consumer<F> mapping. But that's much ado to create a type-safe
    // version. Just so unchecked.
    IdentityHashMap<AbstractBatch<?, T>, Consumer<Task<?, T>>> batchMap = new IdentityHashMap<>();
    for (AbstractBatch<?, T> batch : batches) {
      @SuppressWarnings({ "rawtypes", "unchecked" })  // batch comes from miscellaneous list.
      Consumer<Task<?, T>> consumer = (Consumer) funnel.through(batch.asConverter(executor));
      batchMap.put(batch, consumer);
    }
    for (Task<?, T> task : tasks) {
      batchMap.get(task.batch).accept(task);
    }
    funnel.run();
    try {
      return unmodifiableList(copyOf(tasks.stream().map(t -> t.future)));
    } finally {
      // It makes no sense to change the future objects stored in the Task objects, if run()
      // ever gets called again.
      tasks.clear();
      batches.clear();
      batches.add(passthrough);
    }
  }

  /** Represents a single batch to be executed asynchronously. */
  public static final class Batch<F, T> extends AbstractBatch<F, T> {
    private final CheckedFunction<? super List<F>, ? extends Collection<T>, ?> batchConverter;

    Batch(
        AsyncFunnel<T> funnel,
        CheckedFunction<? super List<F>, ? extends Collection<T>, ?> batchConverter) {
      super(funnel);
      this.batchConverter = requireNonNull(batchConverter);
    }

    @Override void submit(List<Task<F, T>> tasks, Executor executor) {
      if (tasks.isEmpty()) return;
      List<F> inputs = copyOf(tasks.stream().map(t -> t.input));
      executor.execute(() -> {
        try {
          List<T> results = new ArrayList<>(batchConverter.apply(inputs));
          if (results.size() != inputs.size()) {
            throw new IllegalStateException(
                "Batch returned " + results.size() + " outputs for " + inputs.size() + " inputs.");
          }
          for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).future.complete(results.get(i));
          }
        } catch (Throwable e) {
          for (Task<?, ?> task : tasks) {
            task.future.completeExceptionally(e);
          }
        }
      });
    }
  }

  /**
   * Represents a single batch to be executed asynchronously.
   *
   * <p>Exceptions of type {@code E} will be caught and wrapped inside a {@link Maybe} object
   * so that clients can access or handle it type safely.
   */
  public static final class CheckedBatch<F, T, E extends Throwable> {
    private final AbstractBatch<F, T> wrapped;
    private final Class<E> exceptionType;

    public CheckedBatch(Batch<F, T> wrapped, Class<E> exceptionType) {
      this.wrapped = wrapped;
      this.exceptionType = requireNonNull(exceptionType);
    }

    /**
     * Accepts {@code input} into the batch and returns future of {@code Maybe<T, E>} to allow the
     * client to handle exception type safely.
     */
    public CompletionStage<Maybe<T, E>> accept(F input) {
      return Maybe.catchException(exceptionType, wrapped.accept(input));
    }
  }

  private static abstract class AbstractBatch<F, T> {

    private final AsyncFunnel<T> funnel;

    AbstractBatch(AsyncFunnel<T> funnel) {
      this.funnel = funnel;
    }

    /**
     * Accepts {@code input} to the batch and returns a future object that can be used to access
     * the conversion result after the batch conversion is done.
     */
    public final CompletionStage<T> accept(F input) {
      Task<F, T> task = new Task<>(this, input);
      funnel.tasks.add(task);
      return task.future;
    }

    final Function<List<Task<F, T>>, List<CompletionStage<T>>> asConverter(Executor executor) {
      return tasks -> {
        submit(tasks, executor);
        return copyOf(tasks.stream().map(t -> t.future));
      };
    }

    abstract void submit(List<Task<F, T>> tasks, Executor executor);
  }

  private static final class Task<F, T> {
    final AbstractBatch<F, T> batch;
    final F input;
    final CompletableFuture<T> future = new CompletableFuture<>();

    Task(AbstractBatch<F, T> batch, F input) {
      this.batch = batch;
      this.input = requireNonNull(input);
    }
  }

  private static <T> List<T> copyOf(Stream<? extends T> stream) {
    // Collectors.toList() doesn't guarantee thread-safety.
    Collector<T, ?, ArrayList<T>> collector = Collectors.toCollection(ArrayList::new);
    return stream.collect(collector);
  }
}