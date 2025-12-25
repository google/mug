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
package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.mu.util.stream.Joiner.FasterStringJoiner;

/**
 * A joiner (and {@code Collector}) that joins strings.
 *
 * <p>When used to join a pair of objects (for example in a BiStream), it can be used for BiFunction:
 * <pre>{@code
 *   BiStream.zip(userIds, names)
 *       .mapToObj(Joiner.on('=')::join)  // (id, name) -> id + "=" + name
 *       ....;
 * }</pre>
 *
 * <p>Alternatively, it can be used as a Collector. For example,
 * {@code names.collect(Joiner.on(','))} is equivalent to {@code names.collect(Collectors.joining(","))}.
 *
 * Except that JDK {@code joining()} requires the inputs to be strings; while Joiner can join any input,
 * e.g. numbers.
 *
 * <p>Starting from v9.6, {@code Joiner.on()} is also more efficient than {@code Collectors.joining()}
 * when the input has only one string element because it will return the string element as is, whereas
 * JDK {@code Collectors.joining()} delegates to {@link StringJoiner}, which performs a deep copy
 * even when there is only one string with no prefix and suffix.
 *
 * <p>You can also chain {@link #between} to further enclose the joined result between a pair of strings.
 * The following code joins a list of ids and their corresponding names in the format of
 * {@code "[id1=name1, id2=name2, id3=name3, ...]"} by first joining each pair with {@code '='} and then
 * joining the {@code "id=name"} entries with {@code ','}, finally enclosing them between
 * {@code '['} and {@code ']'}:
 * <pre>{@code
 *   BiStream.zip(userIds, names)
 *       .mapToObj(Joiner.on('=')::join)
 *       .collect(Joiner.on(", ").between('[', ']'));
 * }</pre>
 * Which reads clearer than using JDK {@link Collectors#joining}:
 * <pre>{@code
 *   BiStream.zip(userIds, names)
 *       .mapToObj(id, name) -> id + "=" + name)
 *       .collect(Collectors.joining(", ", "[", "]"));
 * }</pre>
 *
 * <p>If you need to skip nulls and/or empty strings while joining, use {@code
 * collect(Joiner.on(...).skipNulls())} or {@code collect(Joiner.on(...).skipEmpties())} respectively.
 *
 * <p>Unlike Guava {@code com.google.common.base.Joiner}, nulls don't cause NullPointerException,
 * instead, they are stringified using {@link String#valueOf}.
 *
 * @since 5.6
 */
public final class Joiner implements Collector<Object, FasterStringJoiner, String> {
  private final String prefix;
  private final String delimiter;
  private final String suffix;

  private Joiner(String prefix, String delimiter, String suffix) {
    this.prefix = prefix;
    this.delimiter = delimiter;
    this.suffix = suffix;
  }

  /** Joining the inputs on the {@code delimiter} character */
  public static Joiner on(char delimiter) {
    return on(Character.toString(delimiter));
  }

  /** Joining the inputs on the {@code delimiter} string */
  public static Joiner on(CharSequence delimiter) {
    return new Joiner("", delimiter.toString(), "");
  }

  /** Joins {@code l} and {@code r} together. */
  public String join(Object l, Object r) {
    return prefix + l + delimiter + r + suffix;
  }

  /**
   * Joins elements from {@code collection}.
   *
   * <p>{@code joiner.join(list)} is equivalent to {@code list.stream().collect(joiner)}.
   *
   * @since 5.7
   */
  public String join(Collection<?> collection) {
    return collection.stream().collect(this);
  }

  /**
   * Returns an instance that wraps the join result between {@code before} and {@code after}.
   *
   * <p>For example both {@code Joiner.on(',').between('[', ']').join(List.of(1, 2))} and
   * {@code Joiner.on(',').between('[', ']').join(1, 2)} return {@code "[1,2]"}.
   */
  public Joiner between(char before, char after) {
    return new Joiner(before + prefix, delimiter, suffix + after);
  }

  /*
   * Returns an instance that wraps the join result between {@code before} and {@code after}.
   *
   * <p>For example both {@code Joiner.on(',').between("[", "]").join([1, 2])} and
   * {@code Joiner.on(',').between("[", "]").join(1, 2)} result in {@code "[1,2]"}.
   */
  public Joiner between(CharSequence before, CharSequence after) {
    return new Joiner(requireNonNull(before) + prefix, delimiter, suffix + requireNonNull(after));
  }

  /** Returns a Collector that skips null inputs and joins the remaining using this Joiner. */
  public Collector<Object, ?, String> skipNulls() {
    return Java9Collectors.filtering(v -> v != null, this);
  }

  /**
   * Returns a Collector that skips null and empty string inputs and joins the remaining using
   * this Joiner.
   */
  public Collector<CharSequence, ?, String> skipEmpties() {
    return Java9Collectors.filtering(s -> s != null && s.length() > 0, this);
  }

  @Override public Supplier<FasterStringJoiner> supplier() {
    return () -> new FasterStringJoiner(prefix, delimiter, suffix);
  }

  @Override public BiConsumer<FasterStringJoiner, Object> accumulator() {
    return (joiner, obj) -> joiner.add(String.valueOf(obj));
  }

  @Override public BinaryOperator<FasterStringJoiner> combiner() {
    return FasterStringJoiner::merge;
  }

  @Override public Function<FasterStringJoiner, String> finisher() {
    return FasterStringJoiner::toString;
  }

  @Override public Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }

  /** Faster than StringJoiner when there is only one string to join. */
  static final class FasterStringJoiner {
    private final StringJoiner buffer;
    private final String prefix;
    private final String suffix;
    private int count;
    private String last;

    FasterStringJoiner(String prefix, String delim, String suffix) {
      this.buffer = new StringJoiner(delim, prefix, suffix);
      this.prefix = prefix;
      this.suffix = suffix;
    }

    FasterStringJoiner add(String str) {
      buffer.add(str);
      last = str;
      count++;
      return this;
    }

    FasterStringJoiner merge(FasterStringJoiner that) {
      this.buffer.merge(that.buffer);
      this.count += that.count;
      if (that.last != null) {
        this.last = that.last;
      }
      return this;
    }

    @Override public String toString() {
      return count == 1 && prefix.isEmpty() && suffix.isEmpty() ? last : buffer.toString();
    }
  }
}
