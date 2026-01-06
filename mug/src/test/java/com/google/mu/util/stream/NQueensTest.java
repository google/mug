package com.google.mu.util.stream;

import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;

@RunWith(JUnit4.class)
public final class NQueensTest {

  @Test
  public void nQueens_1() {
    assertThat(nQueens(1)).containsExactly(asList(0));
  }

  @Test
  public void nQueens_2() {
    assertThat(nQueens(2)).isEmpty();
  }

  @Test
  public void nQueens_3() {
    assertThat(nQueens(3)).isEmpty();
  }

  @Test
  public void nQueens_4() {
    assertThat(nQueens(4)).containsExactly(asList(1, 3, 0, 2), asList(2, 0, 3, 1));
  }

  @Test
  public void nQueens_5() {
    assertThat(nQueens(5))
        .containsExactly(
            asList(0, 2, 4, 1, 3),
            asList(0, 3, 1, 4, 2),
            asList(1, 3, 0, 2, 4),
            asList(1, 4, 2, 0, 3),
            asList(2, 0, 3, 1, 4),
            asList(2, 4, 1, 3, 0),
            asList(3, 0, 2, 4, 1),
            asList(3, 1, 4, 2, 0),
            asList(4, 1, 3, 0, 2),
            asList(4, 2, 0, 3, 1));
  }

  @Test
  public void nQueens_6() {
    assertThat(nQueens(6))
        .containsExactly(
            asList(1, 3, 5, 0, 2, 4),
            asList(2, 5, 1, 4, 0, 3),
            asList(3, 0, 4, 1, 5, 2),
            asList(4, 2, 0, 5, 3, 1));
  }

  @Test
  public void nQueens_8() {
    assertThat(nQueens(8)).hasSize(92);
  }

  @Test
  public void nQueens_9() {
    assertThat(nQueens(9)).hasSize(352);
  }

  @Test
  public void nQueens_10() {
    assertThat(nQueens(10)).hasSize(724);
  }

  static Stream<ImmutableList<Integer>> nQueens(int n) {
    class NQueen extends Iteration<ImmutableList<Integer>> {
      final List<Integer> queens = new ArrayList<>(nCopies(n, -1));
      final BitSet columns = new BitSet(n);
      final BitSet diagonals = new BitSet(n * 2);
      final BitSet antiDiagonals = new BitSet(n * 2);

      NQueen from(int r) {
        if (r == n) {
          emit(ImmutableList.copyOf(queens));
          return this;
        }
        forEachLazily(IntStream.range(0, n), c -> {
          int d = r + c;
          int ad = r - c + n;
          if (columns.get(c) || diagonals.get(d) || antiDiagonals.get(ad)) {
            return;
          }
          columns.set(c);
          diagonals.set(d);
          antiDiagonals.set(ad);
          queens.set(r, c);
          from(r + 1);
          lazily(() -> {
            columns.clear(c);
            diagonals.clear(d);
            antiDiagonals.clear(ad);
          });
        });
        return this;
      }
    }
    return new NQueen().from(0).iterate();
  }
}
