package com.google.mu.util.concurrent24;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.concurrent24.BoundedConcurrency.withMaxConcurrency;

import com.google.mu.testing.concurrent.Happenstance;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Repro for the short-circuit masking failure mode: when the downstream short-circuits, pending
 * mappers are interrupted and joined; a mapper that <em>propagates</em> the interruption (instead
 * of swallowing it, as every mapper in {@code BoundedConcurrencyTest} does) queues an exception
 * during that join, and the gatherer's finisher reports it in place of the caller's result.
 *
 * <p>Determinism notes:
 *
 * <ul>
 *   <li>{@link Happenstance#join} makes the winner finish only after the loser is confirmed
 *       running, so the cancellation is guaranteed to hit a live mapper. No sleep-based racing.
 *   <li>The input has elements remaining after the short-circuit, so the short-circuit happens
 *       inside {@code integrate} rather than inside {@code finish}. This matters: if the downstream
 *       rejects during {@code finish}'s own flush, {@code finish} returns before re-polling the
 *       exception queue and the bug does not manifest.
 *   <li>The loser blocks for a day; it can only end via interruption. Checking in "loser running"
 *       just before the sleep is sufficient — if the interrupt lands before the sleep starts, the
 *       sleep throws immediately on the already-set interrupt status.
 * </ul>
 */
@RunWith(JUnit4.class)
public class BoundedConcurrencyShortCircuitTest {

  @Test public void
      mapConcurrently_findFirst_cancelledMapperPropagatesInterruption_winnerReturned() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    Happenstance<String> happens =
        Happenstance.<String>builder().sequence("2 running", "1 returns").build();
    assertThat(
            Stream.of(1, 2, 3, 4)
                .gather(
                    withMaxConcurrency(2)
                        .mapConcurrently(n -> {
                          started.add(n);
                          if (n == 1) { // the winner returns only after the loser is confirmed
                            // running
                            happens.join("1 returns");
                            return n;
                          }
                          happens.join(n + " running");
                          try {
                            Thread.sleep(
                                Duration.ofSeconds(30)); // interrupted long before this elapses
                            return n;
                          } catch (InterruptedException e) {
                            interrupted.add(n);
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("cancelled " + n, e);
                          }
                        }))
                .findFirst())
        .hasValue(1);
    assertThat(started).containsExactly(1, 2);
    assertThat(interrupted).containsExactly(2);
  }

  @Test public void concurrently_findFirst_cancelledMapperPropagatesInterruption_winnerReturned() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    Happenstance<String> happens =
        Happenstance.<String>builder().sequence("2 running", "1 returns").build();
    assertThat(
            Stream.of(1, 2, 3, 4)
                .collect(
                    withMaxConcurrency(2)
                        .concurrently(n -> {
                          started.add(n);
                          if (n == 1) {
                            happens.join("1 returns");
                            return n;
                          }
                          happens.join(n + " running");
                          try {
                            Thread.sleep(
                                Duration.ofSeconds(30)); // interrupted long before this elapses
                            return n;
                          } catch (InterruptedException e) {
                            interrupted.add(n);
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("cancelled " + n, e);
                          }
                        }))
                .values()
                .findFirst())
        .hasValue(1);
    assertThat(started).containsExactly(1, 2);
    assertThat(interrupted).containsExactly(2);
  }

  @Test public void mapConcurrently_limit_cancelledMapperPropagatesInterruption_resultsReturned() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    Happenstance<String> happens = Happenstance.<String>builder()
        .sequence("3 running", "1 returns")
        .sequence("3 running", "2 returns")
        .build();
    assertThat(
            Stream.of(1, 2, 3, 4)
                .gather(
                    withMaxConcurrency(3)
                        .mapConcurrently(n -> {
                          started.add(n);
                          if (n <= 2) { // both winners return only after the loser is confirmed
                            // running
                            happens.join(n + " returns");
                            return n;
                          }
                          if (n
                              > 3) { // 4 only keeps input in reserve; whether it starts is a race,
                            // see below
                            try {
                              Thread.sleep(
                                  Duration.ofSeconds(30)); // interrupted long before this elapses
                            } catch (InterruptedException e) {
                              Thread.currentThread()
                                  .interrupt(); // swallowed: 4 plays no part in the scenario
                            }
                            return n;
                          }
                          happens.join("3 running");
                          try {
                            Thread.sleep(
                                Duration.ofSeconds(30)); // interrupted long before this elapses
                            return n;
                          } catch (InterruptedException e) {
                            interrupted.add(n);
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("cancelled " + n, e);
                          }
                        }))
                .limit(2)
                .toList())
        .containsExactly(1, 2);
    // Whether 4 is started is a genuine race: it depends on whether 2's result has landed by the
    // time the window re-checks the downstream after acquiring the permit 1 released.
    assertThat(started).containsAtLeast(1, 2, 3);
    assertThat(interrupted).containsExactly(3);
  }

  /**
   * Control: the same scenario with a mapper that swallows the interruption, which is what every
   * existing test does. This one passes today and must keep passing.
   */
  @Test public void mapConcurrently_findFirst_cancelledMapperSwallowsInterruption_winnerReturned() {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    Happenstance<String> happens =
        Happenstance.<String>builder().sequence("2 running", "1 returns").build();
    assertThat(
            Stream.of(1, 2, 3, 4)
                .gather(
                    withMaxConcurrency(2)
                        .mapConcurrently(n -> {
                          if (n == 1) {
                            happens.join("1 returns");
                            return n;
                          }
                          happens.join(n + " running");
                          try {
                            Thread.sleep(
                                Duration.ofSeconds(30)); // interrupted long before this elapses
                          } catch (InterruptedException e) {
                            interrupted.add(n);
                          }
                          return n;
                        }))
                .findFirst())
        .hasValue(1);
    assertThat(interrupted).containsExactly(2);
  }
}
