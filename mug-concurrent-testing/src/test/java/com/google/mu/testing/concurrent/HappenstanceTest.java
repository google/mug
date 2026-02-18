package com.google.mu.testing.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class HappenstanceTest {

  @Test
  public void checkpoint_undefinedPoint_throwsIllegalArgumentException() {
    Happenstance<String> happens = Happenstance.<String>builder().build();
    assertThrows(IllegalArgumentException.class, () -> happens.checkpoint("undefined"));
  }

  @Test
  public void join_undefinedPoint_throwsIllegalArgumentException() {
    Happenstance<String> happens = Happenstance.<String>builder().build();
    assertThrows(IllegalArgumentException.class, () -> happens.join("undefined"));
  }

  @Test
  public void inOrder_singlePoint() {
    Happenstance<String> happens = Happenstance.<String>builder().happenInOrder("A").build();
    happens.checkpoint("A");
  }

  @Test
  public void inOrder_oneCall() {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A", "B", "C").build();
    happens.checkpoint("A");
    happens.checkpoint("B");
    happens.checkpoint("C");
  }

  @Test
  public void inOrder_multipleCalls() {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A", "B").happenInOrder("B", "C").build();
    happens.checkpoint("A");
    happens.checkpoint("B");
    happens.checkpoint("C");
  }

  @Test
  public void inOrder_redundantEdge() {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A", "B", "C").happenInOrder("A", "B").build();
    happens.checkpoint("A");
    happens.checkpoint("B");
    happens.checkpoint("C");
  }

  @Test
  public void inOrder_diamond() {
    Happenstance<String> happens =
        Happenstance.<String>builder()
            .happenInOrder("A", "B1", "C")
            .happenInOrder("A", "B2", "C")
            .build();
    happens.checkpoint("A");
    happens.checkpoint("B1");
    happens.checkpoint("B2");
    happens.checkpoint("C");
  }

  @Test
  public void inOrder_cycle_throwsIllegalArgumentException() {
    Happenstance.Builder<String> builder = Happenstance.<String>builder().happenInOrder("A", "B");
    assertThrows(IllegalArgumentException.class, () -> builder.happenInOrder("B", "A"));
  }

  @Test
  public void inOrder_longCycle_throwsIllegalArgumentException() {
    Happenstance.Builder<String> builder =
        Happenstance.<String>builder().happenInOrder("A", "B", "C");
    assertThrows(IllegalArgumentException.class, () -> builder.happenInOrder("C", "A"));
  }

  @Test
  public void inOrder_selfCycle_isIgnored() {
    Happenstance<String> happens = Happenstance.<String>builder().happenInOrder("A", "A").build();
    happens.checkpoint("A");
  }

  @Test
  public void inOrder_noParameters() {
    assertThat(Happenstance.<String>builder().happenInOrder().build()).isNotNull();
  }

  @Test
  public void checkpoint_alreadyCompleted_throwsIllegalStateException() {
    Happenstance<String> happens = Happenstance.<String>builder().happenInOrder("A").build();
    happens.checkpoint("A");
    assertThrows(IllegalStateException.class, () -> happens.checkpoint("A"));
  }

  @Test
  public void join_alreadyCompleted_throwsIllegalStateException() {
    Happenstance<String> happens = Happenstance.<String>builder().happenInOrder("A").build();
    happens.join("A");
    assertThrows(IllegalStateException.class, () -> happens.join("A"));
  }

  @Test
  public void builder_initialPoints_noOrder() throws Exception {
    Happenstance<String> happens = Happenstance.<String>builder("A", "B").build();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> b = executor.submit(() -> happens.join("B"));
      b.get(1, TimeUnit.SECONDS); // B is not blocked by A
      Future<?> a = executor.submit(() -> happens.join("A"));
      a.get(1, TimeUnit.SECONDS); // A is not blocked by B
    }
  }

  @Test
  public void builder_points_then_happenInOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder("A done", "B start")
            .happenInOrder("A done", "B start")
            .build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> futureB =
          executor.submit(
              () -> {
                happens.join("B start");
                completed.add("B");
              });
      Thread.sleep(100);
      Future<?> futureA =
          executor.submit(
              () -> {
                completed.add("A");
                happens.join("A done");
              });
      futureA.get(5, TimeUnit.SECONDS);
      futureB.get(5, TimeUnit.SECONDS);
    }
    assertThat(completed).containsExactly("A", "B").inOrder();
  }

  @Test
  public void checkpoint_singleThread_respectsOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("1", "2", "3").build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    Thread t =
        new Thread(
            () -> {
              happens.checkpoint("1");
              completed.add("1");
              happens.checkpoint("2");
              completed.add("2");
              happens.checkpoint("3");
              completed.add("3");
            });
    t.start();
    t.join();
    assertThat(completed).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void join_singleThread_respectsOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("1", "2", "3").build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    Thread t =
        new Thread(
            () -> {
              happens.join("1");
              completed.add("1");
              happens.join("2");
              completed.add("2");
              happens.join("3");
              completed.add("3");
            });
    t.start();
    t.join();
    assertThat(completed).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void checkpoint_twoThreads_enforcedOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A done", "B start").build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> futureB =
          executor.submit(
              () -> {
                happens.checkpoint("B start");
                completed.add("B");
              });
      // Give B a chance to start and block
      Thread.sleep(100);

      Future<?> futureA =
          executor.submit(
              () -> {
                completed.add("A");
                happens.checkpoint("A done");
              });

      futureA.get(5, TimeUnit.SECONDS);
      futureB.get(5, TimeUnit.SECONDS);
    }
    assertThat(completed).containsExactly("A", "B").inOrder();
  }

  @Test
  public void join_twoThreads_enforcedOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A done", "B start").build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> futureB =
          executor.submit(
              () -> {
                happens.join("B start");
                completed.add("B");
              });
      // Give B a chance to start and block
      Thread.sleep(100);

      Future<?> futureA =
          executor.submit(
              () -> {
                completed.add("A");
                happens.join("A done");
              });

      futureA.get(5, TimeUnit.SECONDS);
      futureB.get(5, TimeUnit.SECONDS);
    }
    assertThat(completed).containsExactly("A", "B").inOrder();
  }

  @Test
  public void builder_happenInOrderAddsNewPoint() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder("A done").happenInOrder("A done", "B start").build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> futureB =
          executor.submit(
              () -> {
                happens.join("B start");
                completed.add("B");
              });
      Thread.sleep(100);
      Future<?> futureA =
          executor.submit(
              () -> {
                completed.add("A");
                happens.join("A done");
              });
      futureA.get(5, TimeUnit.SECONDS);
      futureB.get(5, TimeUnit.SECONDS);
    }
    assertThat(completed).containsExactly("A", "B").inOrder();
  }

  @Test
  public void checkpoint_threeThreads_enforcedOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder()
            .happenInOrder("A done", "B start")
            .happenInOrder("B done", "C start")
            .build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
      Future<?> futureC =
          executor.submit(
              () -> {
                happens.checkpoint("C start");
                completed.add("C");
              });
      Future<?> futureB =
          executor.submit(
              () -> {
                happens.checkpoint("B start");
                completed.add("B");
                happens.checkpoint("B done");
              });
      Thread.sleep(100); // Give B & C chance to block

      Future<?> futureA =
          executor.submit(
              () -> {
                completed.add("A");
                happens.checkpoint("A done");
              });

      futureA.get(5, TimeUnit.SECONDS);
      futureB.get(5, TimeUnit.SECONDS);
      futureC.get(5, TimeUnit.SECONDS);
    }
    assertThat(completed).containsExactly("A", "B", "C").inOrder();
  }

  @Test
  public void join_threeThreads_enforcedOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder()
            .happenInOrder("A done", "B start")
            .happenInOrder("B done", "C start")
            .build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
      Future<?> futureC =
          executor.submit(
              () -> {
                happens.join("C start");
                completed.add("C");
              });
      Future<?> futureB =
          executor.submit(
              () -> {
                happens.join("B start");
                completed.add("B");
                happens.join("B done");
              });
      Thread.sleep(100); // Give B & C chance to block

      Future<?> futureA =
          executor.submit(
              () -> {
                completed.add("A");
                happens.join("A done");
              });

      futureA.get(5, TimeUnit.SECONDS);
      futureB.get(5, TimeUnit.SECONDS);
      futureC.get(5, TimeUnit.SECONDS);
    }
    assertThat(completed).containsExactly("A", "B", "C").inOrder();
  }

  @Test
  public void checkpoint_diamondGraph_enforcedOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder()
            .happenInOrder("A", "B1", "C")
            .happenInOrder("A", "B2", "C")
            .build();
    List<String> completed = Collections.synchronizedList(new ArrayList<>());
    try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
      Future<?> futureC =
          executor.submit(
              () -> {
                happens.checkpoint("C");
                completed.add("C");
              });
      Future<?> futureB1 =
          executor.submit(
              () -> {
                happens.checkpoint("B1");
                completed.add("B1");
                return null;
              });
      Future<?> futureB2 =
          executor.submit(
              () -> {
                happens.checkpoint("B2");
                completed.add("B2");
                return null;
              });
      Future<?> futureA =
          executor.submit(
              () -> {
                happens.checkpoint("A");
                completed.add("A");
                return null;
              });

      futureA.get(5, TimeUnit.SECONDS);
      futureB1.get(5, TimeUnit.SECONDS);
      futureB2.get(5, TimeUnit.SECONDS);
      futureC.get(5, TimeUnit.SECONDS);
    }

    assertThat(completed).contains("A");
    assertThat(completed).contains("B1");
    assertThat(completed).contains("B2");
    assertThat(completed).contains("C");
  }

  @Test
  public void join_diamondGraph_enforcedOrder() throws Exception {
    Happenstance<String> happens =
        Happenstance.<String>builder()
            .happenInOrder("A", "B1", "C")
            .happenInOrder("A", "B2", "C")
            .build();
    AtomicLong stateA = new AtomicLong();
    AtomicLong stateB1 = new AtomicLong();
    AtomicLong stateB2 = new AtomicLong();
    try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
      Future<?> futureC =
          executor.submit(
              () -> {
                happens.join("C");
                assertThat(stateB1.get()).isEqualTo(1);
                assertThat(stateB2.get()).isEqualTo(1);
              });
      Future<?> futureB1 =
          executor.submit(
              () -> {
                stateB1.set(1);
                happens.join("B1");
                assertThat(stateA.get()).isEqualTo(1);
                return null;
              });
      Future<?> futureB2 =
          executor.submit(
              () -> {
                stateB2.set(1);
                happens.join("B2");
                assertThat(stateA.get()).isEqualTo(1);
                return null;
              });
      Future<?> futureA =
          executor.submit(
              () -> {
                stateA.set(1);
                happens.join("A");
                return null;
              });

      futureA.get(5, TimeUnit.SECONDS);
      futureB1.get(5, TimeUnit.SECONDS);
      futureB2.get(5, TimeUnit.SECONDS);
      futureC.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void stressTest() throws Exception {
    int numThreads = 10;
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A", "B", "C", "D", "E").build();
    try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
      List<Future<?>> futures = new ArrayList<>();
      List<String> points = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
      Collections.shuffle(points);

      for (String point : points) {
        futures.add(
            executor.submit(
                () -> {
                  happens.checkpoint(point);
                }));
      }

      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  public void join_stressTest() throws Exception {
    int numThreads = 10;
    Happenstance<String> happens =
        Happenstance.<String>builder().happenInOrder("A", "B", "C", "D", "E").build();
    try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
      List<Future<?>> futures = new ArrayList<>();
      List<String> points = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
      Collections.shuffle(points);

      for (String point : points) {
        futures.add(
            executor.submit(
                () -> {
                  happens.join(point);
                }));
      }

      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  public void myListToString_concurrentCalls_mayReturnDifferentInstances_noSequencer()
      throws Exception {
    ConcurrentMap<Integer, Throwable> races = new ConcurrentHashMap<>();
    Integer[] elements = IntStream.range(0, 100).boxed().toArray(Integer[]::new);
    String listString = Arrays.toString(elements);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 100000; i++) {
        MyList<Integer> list = MyList.of(elements);
        Future<String> f1 = executor.submit(list::toString);
        Future<String> f2 = executor.submit(list::toString);
        String s1 = f1.get();
        String s2 = f2.get();
        assertThat(s1).isEqualTo(listString);
        assertThat(s2).isEqualTo(listString);
        try {
          assertThat(s1).isSameInstanceAs(s2);
        } catch (AssertionError e) {
          races.put(i, e);
        }
      }
    }
    assertThat(races).isNotEmpty();
  }

  @Ignore
  @Test
  public void myListToString_concurrentCalls_mayReturnDifferentInstances_withSequencer()
      throws Exception {
    ConcurrentMap<Integer, Throwable> races = new ConcurrentHashMap<>();
    Integer[] elements = IntStream.range(0, 100).boxed().toArray(Integer[]::new);
    String listString = Arrays.toString(elements);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 100000; i++) {
        MyList<Integer> list = MyList.of(elements);
        Happenstance<String> sequencer =
            Happenstance.<String>builder()
                .happenInOrder("a2", "b")
                .happenInOrder("b", "b2")
                .happenInOrder("a", "a2")
                .build();
        Future<String> f1 =
            executor.submit(
                () -> {
                  sequencer.checkpoint("a");
                  String s = list.toString();
                  sequencer.checkpoint("a2");
                  return s;
                });
        Future<String> f2 =
            executor.submit(
                () -> {
                  sequencer.checkpoint("b");
                  String s = list.toString();
                  sequencer.checkpoint("b2");
                  return s;
                });
        String s1 = f1.get();
        String s2 = f2.get();
        assertThat(s1).isEqualTo(listString);
        assertThat(s2).isEqualTo(listString);
        try {
          assertThat(s1).isSameInstanceAs(s2);
        } catch (AssertionError e) {
          races.put(i, e);
        }
      }
    }
    assertThat(races).isNotEmpty();
  }

  private static class MyList<T> {
    private final T[] elements;
    private String string;

    private MyList(T[] elements) {
      this.elements = elements.clone();
    }

    static <T> MyList<T> of(T... elements) {
      return new MyList<>(elements);
    }

    @Override
    public String toString() {
      String s = string;
      if (s == null) {
        string = s = Arrays.toString(elements);
      }
      return s;
    }
  }

  @Test
  public void join_providesMemoryBarrier() throws Exception {
    ConcurrentMap<Integer, Throwable> races = new ConcurrentHashMap<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 100000; i++) {
        BuggySut sut = new BuggySut();
        Happenstance<String> sequencer =
            Happenstance.<String>builder().happenInOrder("W", "R").build();
        AtomicLong result = new AtomicLong();
        Future<?> readFuture =
            executor.submit(
                () -> {
                  sequencer.join("R");
                  result.set(sut.read());
                });
        Future<?> writeFuture =
            executor.submit(
                () -> {
                  sut.write(Long.MAX_VALUE);
                  sequencer.join("W");
                });
        writeFuture.get();
        readFuture.get();
        try {
          assertThat(result.get()).isEqualTo(Long.MAX_VALUE);
        } catch (AssertionError e) {
          races.put(i, e);
        }
      }
    }
    assertThat(races).isEmpty();
  }

  @Ignore
  @Test
  public void checkpoint_noUnintendedMemoryBarrier() throws Exception {
    ConcurrentMap<Integer, Throwable> races = new ConcurrentHashMap<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 100000; i++) {
        BuggySut sut = new BuggySut();
        Happenstance<String> sequencer =
            Happenstance.<String>builder()
                .happenInOrder("W", "R")
                .happenInOrder("Done Writing", "Done Reading")
                .build();
        AtomicLong result = new AtomicLong();
        Future<?> readFuture =
            executor.submit(
                () -> {
                  sequencer.checkpoint("R");
                  result.set(sut.read());
                });
        Future<?> writeFuture =
            executor.submit(
                () -> {
                  sut.write(Long.MAX_VALUE);
                  sequencer.checkpoint("W");
                });
        writeFuture.get();
        readFuture.get();
        try {
          assertThat(result.get()).isEqualTo(Long.MAX_VALUE);
        } catch (AssertionError e) {
          races.put(i, e);
        }
      }
    }
    assertThat(races).isNotEmpty();
  }
  
  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(Happenstance.class);
    new NullPointerTester().testAllPublicInstanceMethods(Happenstance.builder());
  }

  private static class BuggySut {
    boolean ready = false;
    long data = 0;

    void write(long val) {
      data = val;
      ready = true;
    }

    long read() {
      return ready ? data : -1;
    }
  }
}
