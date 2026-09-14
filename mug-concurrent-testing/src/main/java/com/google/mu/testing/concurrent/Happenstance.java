package com.google.mu.testing.concurrent;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.mu.util.graph.Walker;
import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.Joiner;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * A utility to manipulate temporal ordering (via {@link #checkpoint}) or happens-before (via {@link
 * #join}) relationships between events in concurrent operations. This is useful for testing, where
 * you want to ensure that certain actions are executed in a specific order.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class MyConcurrentTest {
 *   @Test
 *   public void testConcurrent() {
 *     var happens =
 *         Happenstance.<String>builder()
 *             .sequence("writtenB", "readingA", "writtenA")
 *             .sequence("readingB", "writtenB")
 *             .build();
 *     Stream.of("A", "B")
 *         .parallel()
 *         .forEach(
 *             input -> {
 *               happens.checkpoint("reading" + input);
 *               sut.read(input);
 *               sut.write(input);
 *               happens.checkpoint("written" + input);
 *               sut.finish(input);
 *             });
 *   }
 * }
 * }</pre>
 *
 * <p>The example uses {@link #checkpoint}, which is the default choice because it doesn't add
 * memory barriers that could hide the SUT bug you are trying to reproduce. Switch to {@link #join}
 * when the test needs the sequence points to establish happens-before, and the extra barriers don't
 * defeat the test.
 *
 * <p>Implementation note: this class uses VarHandle instead of high-level synchronization
 * primitives to avoid introducing unintended memory barrier that may result in false negative tests
 * (the test would have failed without the sequence points). When waiting for predecessors, a
 * three-stage back-off strategy is employed: {@link Thread#onSpinWait} is called up to 1000 times
 * to catch tight visibility races in CPU-bound tests without triggering a context switch; if the
 * predecessor is still not ready, {@link Thread#yield} is called up to 100 times to prevent
 * deadlocks or extreme performance degradation in heavily over-provisioned environments; beyond
 * that the thread parks for 50µs at a time, so that an I/O-bound SUT operation taking hundreds of
 * milliseconds doesn't burn a core per waiter.
 *
 * <p><em>Limits of the no-barrier property:</em> it only holds while a thread stays on the {@code
 * onSpinWait} path. Both {@link Thread#yield} and the parking stage are native calls that the JIT
 * cannot optimize across and that the scheduler typically implements with fences, so a thread that
 * exhausts the spin budget gets a de-facto barrier: plain SUT writes made before the checkpoint can
 * no longer be sunk past the status store, and plain SUT reads made after it can no longer be
 * served from a register. Such a checkpoint degrades toward {@link #join} semantics and a genuine
 * visibility bug may fail to reproduce. In practice the property is preserved for sequence points
 * with no predecessors, or whose predecessors check in within the spin budget (which is
 * microarchitecture-dependent).
 *
 * <p>Even on the spin path, what {@code checkpoint} preserves is mainly the compiler's freedom to
 * reorder plain accesses around the opaque status access; a thousand spin hints leave enough time
 * for store buffers to drain, so hardware-level store-store windows are narrow regardless. On x86
 * (TSO) the hardware never reorders store-store, so the JIT is the only thing that can surface such
 * a bug there. On AArch64 (Apple Silicon, Graviton CI) the hardware-level property is real, and the
 * yield caveat applies equally.
 *
 * <p>The {@link Builder#sequence} method is intended to be called from the main thread to set up
 * the DAG of relationships between sequence points before the {@code checkpoint()} or {@code
 * join()} method is called from any threads.
 *
 * @param <K> the type of the sequence points
 * @since 9.9.3
 */
@ThreadSafe
public final class Happenstance<K> {
  private static final VarHandle CHECKIN_STATUS_HANDLE =
      MethodHandles.arrayElementVarHandle(int[].class);
  private static final int SPIN_THRESHOLD = 1000;
  private static final int YIELD_THRESHOLD = SPIN_THRESHOLD + 100;
  private static final long PARK_NANOS = 50_000;
  private final Map<K, Integer> pointToIndex;
  private final int[][] predecessors;
  private final int[] checkInStatus; // 1 if checked in.

  private Happenstance(Builder<K> builder) {
    this.pointToIndex = BiStream.from(builder.pointToIndex).toMap();
    this.predecessors = builder.predecessors.stream()
        .map(list -> list.stream().mapToInt(Integer::intValue).toArray())
        .toArray(int[][]::new);
    this.checkInStatus = new int[builder.pointToIndex.size()];
  }

  /**
   * Returns a new {@link Builder} initialized with {@code sequencePoints}. No order is defined
   * among these sequence points, until you explicitly call {@link Builder#sequence}.
   */
  @SafeVarargs
  public static <K> Builder<K> builder(K... sequencePoints) {
    return builder(asList(sequencePoints));
  }

  /**
   * Returns a new {@link Builder} initialized with {@code sequencePoints}. No order is defined
   * among these sequence points, until you explicitly call {@link Builder#sequence}.
   */
  public static <K> Builder<K> builder(Iterable<? extends K> sequencePoints) {
    Builder<K> builder = new Builder<>();
    for (K point : sequencePoints) {
      builder.declareSequencePoint(point);
    }
    return builder;
  }

  /**
   * Builder for {@link Happenstance}.
   *
   * @param <K> the type of the sequence points
   */
  public static final class Builder<K> {
    private final Map<K, Integer> pointToIndex = new HashMap<>();
    private final List<K> indexToPoint = new ArrayList<>();
    private final List<List<Integer>> predecessors = new ArrayList<>();
    private final List<Set<Integer>> successors = new ArrayList<>();

    Builder() {}

    /**
     * Defines a ordering between consecutive {@code sequencePoints}. For example, {@code
     * sequence("A", "B", "C")} specifies that sequence points "A", "B", and "C" must be completed
     * in that order ("A" before "B", and "B" before "C").
     *
     * <p>NOTE that no order is implied across subsequent {@code sequence()} calls.
     *
     * <p>This method should be called to define all sequence point orders before {@link #build} is
     * called.
     *
     * @since 9.9.7
     */
    @CanIgnoreReturnValue
    @SafeVarargs
    public final Builder<K> sequence(K... sequencePoints) {
      for (K point : sequencePoints) {
        declareSequencePoint(point);
      }
      BiStream.adjacentPairsFrom(sequencePoints)
          .forEach((predecessor, successor) -> {
            int u = pointToIndex.get(predecessor);
            int v = pointToIndex.get(successor);
            if (u != v && successors.get(u).add(v)) {
              predecessors.get(v).add(u);
            }
          });
      return this;
    }

    /**
     * Builds a {@link Happenstance} instance respecting the sequence points ordering.
     *
     * @throws IllegalArgumentException if the ordering contains a cycle
     */
    public Happenstance<K> build() {
      Walker.inGraph((Integer index) -> successors.get(index).stream())
          .detectCycleFrom(pointToIndex.values())
          .ifPresent(cycle -> {
            throw new IllegalArgumentException(
                "cycle detected: " + cycle.map(indexToPoint::get).collect(Joiner.on(" -> ")));
          });
      return new Happenstance<>(this);
    }

    @CanIgnoreReturnValue
    private int declareSequencePoint(K id) {
      requireNonNull(id, "sequencePoint cannot be null");
      return pointToIndex.computeIfAbsent(
          id,
          k -> {
            int index = indexToPoint.size();
            indexToPoint.add(k);
            predecessors.add(new ArrayList<>());
            successors.add(new LinkedHashSet<>());
            return index;
          });
    }
  }

  /**
   * Joins until all predecessors of {@code sequencePoint} have checked in, then marks {@code
   * sequencePoint} as checked-in and returns.
   *
   * <p>This method differs from {@link #checkpoint} in that it establishes happens-before
   * relationship between sequence points, which means writes happening before {@code join(A)} are
   * visible to code after {@code join(B)} as long as {@code sequence(A, B)} is specified.
   *
   * <p><em>Warning:</em>Using {@code join()} inappropriately may result in false negative tests if
   * the SUT has a bug that writes to non-volatile state, because the {@code join()} call will
   * accidentally "fix" the bug by making the write visible to other threads.
   *
   * <p>If the calling thread is interrupted while waiting, this method throws {@link
   * AssertionError} without checking in, and leaves the thread's interrupt status set. An {@code
   * Error} is used so that the bail-out isn't swallowed by a {@code catch (Exception)} in the code
   * under test.
   *
   * @param sequencePoint the sequence point to wait for and mark as completed.
   * @throws IllegalArgumentException if {@code sequencePoint} wasn't defined via {@link
   *     Builder#sequence}.
   * @throws IllegalStateException if {@code sequencePoint} has already been marked as completed.
   * @throws AssertionError if the calling thread is interrupted while waiting for predecessors.
   */
  public void join(K sequencePoint) {
    checkIn(sequencePoint, Ordering.HAPPENS_BEFORE);
  }

  /**
   * Waits for all predecessors of {@code sequencePoint} to have checked in, then marks {@code
   * sequencePoint} as checked-in and returns.
   *
   * <p>To avoid introducing unintended memory barriers, this method only establishes temporal
   * ordering; no additional happens-before relationship between sequence points is established,
   * which means writes before the checkpoint A may still be invisible to reads after checkpoint B
   * even with {@code sequence(A, B)}. The SUT itself should establish happens-before relationship
   * if necessary.
   *
   * <p>The temporal ordering applies to the checkpoint calls themselves, not to the SUT code around
   * them. Opaque accesses order the check-in status updates, but they don't stop the JIT or the CPU
   * from moving the surrounding plain reads and writes across a checkpoint. So {@code
   * checkpoint("a"); sut.foo(); checkpoint("a2")} makes it likely, not certain, that {@code foo()}
   * runs between "a" and "a2". Treat {@code checkpoint} as a best-effort interleaving tool for
   * making a race likely to reproduce; only {@link #join} pins the surrounding code, because its
   * release store keeps earlier accesses from sinking past it and its acquire load keeps later
   * accesses from being hoisted before it.
   *
   * <p>If extra memory barrier doesn't defeat your concurrency tests, and you need to establish
   * happens-before relationships, use {@link #join} instead.
   *
   * <p>Note that the absence of memory barriers is best-effort: if the predecessors don't check in
   * within the spin budget, this method falls back to {@link Thread#yield} and then to parking,
   * both of which act as barriers. See the class Javadoc for details.
   *
   * <p>If the calling thread is interrupted while waiting, this method throws {@link
   * AssertionError} without checking in, and leaves the thread's interrupt status set. An {@code
   * Error} is used so that the bail-out isn't swallowed by a {@code catch (Exception)} in the code
   * under test. The interrupt status is only consulted while actually waiting, so a sequence point
   * with no predecessors, or whose predecessors have already checked in, checks in normally even on
   * an interrupted thread.
   *
   * @param sequencePoint the sequence point to wait for and mark as completed.
   * @throws IllegalArgumentException if {@code sequencePoint} wasn't defined via {@link
   *     Builder#sequence}.
   * @throws IllegalStateException if {@code sequencePoint} has already been marked as completed.
   * @throws AssertionError if the calling thread is interrupted while waiting for predecessors.
   */
  public void checkpoint(K sequencePoint) {
    checkIn(sequencePoint, Ordering.TEMPORAL);
  }

  private void checkIn(K sequencePoint, Ordering ordering) {
    int index = uponSequencePoint(sequencePoint);
    int[] statuses = this.checkInStatus;
    for (int predecessor : predecessors[index]) {
      for (int spins = 0; ordering.read(statuses, predecessor) == 0; spins++) {
        if (spins < SPIN_THRESHOLD) {
          Thread.onSpinWait();
        } else if (Thread.currentThread().isInterrupted()) {
          // Leave the interrupt bit alone so the caller can still observe it.
          throw new AssertionError(
              String.format(
                  "Interrupted while waiting for the predecessors of sequencePoint '%s'.",
                  sequencePoint));
        } else if (spins < YIELD_THRESHOLD) {
          Thread.yield();
        } else {
          LockSupport.parkNanos(PARK_NANOS);
        }
      }
    }
    checkState(
        Ordering.TEMPORAL.read(statuses, index) == 0,
        "sequencePoint '%s' has already been checked in or joined.", sequencePoint);
    ordering.write(statuses, index, 1);
  }

  private int uponSequencePoint(K sequencePoint) {
    requireNonNull(sequencePoint, "sequencePoint cannot be null");
    Integer index = pointToIndex.get(sequencePoint);
    checkArgument(index != null, "sequencePoint '%s' not defined in sequence()", sequencePoint);
    return index;
  }

  private enum Ordering {
    HAPPENS_BEFORE {
      @Override int read(int[] statuses, int index) {
        return (int) CHECKIN_STATUS_HANDLE.getAcquire(statuses, index);
      }

      @Override void write(int[] statuses, int index, int value) {
        CHECKIN_STATUS_HANDLE.setRelease(statuses, index, value);
      }
    },
    TEMPORAL {
      @Override int read(int[] statuses, int index) {
        return (int) CHECKIN_STATUS_HANDLE.getOpaque(statuses, index);
      }

      @Override void write(int[] statuses, int index, int value) {
        CHECKIN_STATUS_HANDLE.setOpaque(statuses, index, value);
      }
    };

    abstract int read(int[] statuses, int index);
    abstract void write(int[] statuses, int index, int value);
  }

  @FormatMethod
  private static void checkArgument(boolean condition, String message, Object arg) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, arg));
    }
  }

  @FormatMethod
  private static void checkState(boolean condition, String message, Object arg) {
    if (!condition) {
      throw new IllegalStateException(String.format(message, arg));
    }
  }
}
