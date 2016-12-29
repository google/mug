package org.mu.util;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A funnel dispatches a sequence of inputs through arbitrary batch conversions while maintaining
 * first-in-first-out order. For example, the following code can either batch load users from a
 * user store, or batch load from third party user store, or else create a dummy user immediately
 * without conversion:
 *
 * <pre>{@code
 * Funnel<User> funnel = new Funnel<>();
 * Consumer<Long> userStoreBatch = funnel.through(userStore::loadUsers);
 * Consumer<ThirdPartyUser> thirdPartyBatch = funnel.through(thirdPartyClient::loadUsers);
 * for (UserDto dto : users) {
 *   if (dto.hasUserId()) {
 *     userStoreBatch.accept(dto.getUserId());
 *   } else if (dto.hasThirdParty()) {
 *     thirdPartyBatch.accept(dto.getThirdParty());
 *   } else {
 *     funnel.add(createDummyUser(dto));
 *   }
 * }
 * List<User> users = funnel.run();
 * }</pre>
 *
 * <p>Elements flow out of the funnel in the same order as they enter, regardless of which {@link
 * Consumer} instance admitted them, or if they were directly {@link #add added} into the
 * funnel without conversion.
 */
public final class Funnel<T> {

  private int size = 0;
  private final List<Batch<?>> batches = new ArrayList<>();
  private final Consumer<T> passthrough = through(x -> x);

  /** Holds the elements to be converted through a batch conversion. */
  private final class Batch<F> implements Consumer<F> {
    private final Function<? super List<F>, ? extends Collection<? extends T>> converter;
    private final List<Indexed<F>> indexedSources = new ArrayList<>();

    private Batch(Function<? super List<F>, ? extends Collection<? extends T>> converter) {
      this.converter = requireNonNull(converter);
    }

    /** Adds {@code source} to be converted. */
    @Override public void accept(F source) {
      indexedSources.add(new Indexed<>(size++, source));
    }

    private void convertInto(ArrayList<T> output) {
      if (indexedSources.isEmpty()) {
        return;
      }
      List<F> params = indexedSources.stream()
          .map(i -> i.value)
          .collect(toList());
      List<T> results = new ArrayList<T>(converter.apply(params));
      if (params.size() != results.size()) {
        throw new IllegalStateException(
            converter + " expected to return " + params.size() + " elements for input "
                + params + ", but got " + results + " of size " + results.size() + ".");
      }
      for (int i = 0; i < indexedSources.size(); i++) {
        output.set(indexedSources.get(i).index, results.get(i));
      }
    }
  }

  /**
   * Returns a {@link Consumer} instance accepting elements that, when {@link #run} is called,
   * will be converted together in a batch through {@code converter}.
   */
  public <F> Consumer<F> through(
      Function<? super List<F>, ? extends Collection<? extends T>> converter) {
    Batch<F> batch = new Batch<>(converter);
    batches.add(batch);
    return batch;
  }

  /** Adds {@code element} to the funnel. */
  public void add(T element) {
    passthrough.accept(element);
  }

  /**
   * Runs all batch conversions and returns conversion results together with elements {@link #add
   * added} as is, in encounter order.
   */
  public List<T> run() {
    ArrayList<T> output = new ArrayList<>(Collections.nCopies(size, null));
    for (Batch<?> batch : batches) {
      batch.convertInto(output);
    }
    return output;
  }

  private static final class Indexed<T> {
    final int index;
    final T value;

    Indexed(int index, T value) {
      this.index = index;
      this.value = requireNonNull(value);
    }
  }
}