package com.google.mu.benchmarks;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.google.common.labs.parse.Parser;
/**
 * Benchmark comparing using {@link Parser} vs. regex to parse chess move notations.
 *
 * <p>The regex parser is looser than Parser because realistically you wouldn't bother creating a
 * strict regex. It's too complicated.
 *
 * <p>Results:
 *
 * <pre>
 * Benchmark                           Mode  Cnt     Score    Error  Units
 * ChessMoveParserBenchmark.dotParse  thrpt   10  1071.109 ± 10.138  ops/s
 * ChessMoveParserBenchmark.regex     thrpt   10  1129.392 ±  6.653  ops/s
 * </pre>
 */
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@RunWith(JUnit4.class)
public class ChessMoveBenchmark {
  private static final String INPUT =
      "e4 Nf3 exd5 O-O Qxd5+ e8=Q# a6 Nc6 d5 e5 Bd6 Nge7 O-O-O";
  private static final int SCALE = 100;

  private static final String LARGE_INPUT = Collections.nCopies(SCALE, INPUT).stream().collect(joining(" "));

  private static final Pattern MOVE_REGEX = Pattern.compile(
      "(O-O-O|O-O)|(?:([NBRQK])?([a-h]|[1-8]|[a-h][1-8])?(x)?([a-h][1-8])(?:=([NBRQ]))?([+#])?)");

  private static final Parser<Move> PARSER = moveParser();

  private static Parser<Move> moveParser() {
    var file = one(charsIn("[a-h]")).source();
    var rank = one(charsIn("[1-8]")).source();
    var piece = one(charsIn("[NBRQK]")).source();
    var destination = sequence(file, rank, String::concat);
    var promotion = string("=").then(one(charsIn("[NBRQ]")).source());
    var checkOrMate = one(charsIn("[+#]")).source();
    var disambiguation = anyOf(file.optionallyFollowedBy(rank, String::concat), rank);

    Parser<Castling> castling = string("O-O").thenReturn(Castling.kingside())
        .optionallyFollowedBy("-O", c -> Castling.queenside());

    Parser<PieceMove> pieceMove = anyOf(
        // Piece Disambiguation 'x' Destination
        sequence(
            piece, disambiguation.followedBy("x"), destination,
            PieceMove::captureWithDisambiguation),
        sequence(piece, disambiguation, destination, PieceMove::moveWithDisambiguation),
        sequence(piece.followedBy("x"), destination,  PieceMove::capture),
        sequence(piece, destination, PieceMove::move),
        sequence(file.followedBy("x"), destination, PieceMove::pawnCapture),
        destination.map(PieceMove::pawnMove));

    Parser<PieceMove> fullPieceMove = pieceMove
        .optionallyFollowedBy(promotion, PieceMove::withPromotion)
        .optionallyFollowedBy(checkOrMate, PieceMove::withCheckOrMate);
    Parser<Move> move = anyOf(castling, fullPieceMove);
    return move.followedBy(zeroOrMore(charsIn("[ \t\n\r]")));
  }

  private static Move mapMatch(java.util.regex.MatchResult mr) {
    String castling = mr.group(1);
    if (castling != null) {
      return new Castling(castling.equals("O-O"));
    }
    return new PieceMove(
        mr.group(2),
        mr.group(3),
        mr.group(4) != null,
        mr.group(5),
        mr.group(6),
        mr.group(7));
  }

  @Benchmark
  public long regex() {
    try (Scanner scanner = new Scanner(LARGE_INPUT)) {
      return scanner.findAll(MOVE_REGEX)
          .map(ChessMoveBenchmark::mapMatch)
          .count();
    }
  }

  @Benchmark
  public long dotParse() {
    return PARSER.parseToStream(LARGE_INPUT).count();
  }

  private void assertMove(String moveStr, Move expected) {
    assertThat(PARSER.parse(moveStr)).isEqualTo(expected);

    try (Scanner scanner = new Scanner(moveStr)) {
      List<Move> regexMatched = scanner.findAll(MOVE_REGEX)
          .map(ChessMoveBenchmark::mapMatch)
          .collect(Collectors.toList());
      assertThat(regexMatched).containsExactly(expected);
    }
  }

  @Test
  public void testMoveVariations() {
    assertMove("e4", PieceMove.pawnMove("e4"));
    assertMove("Nf3", PieceMove.move("N", "f3"));
    assertMove("exd5", PieceMove.pawnCapture("e", "d5"));
    assertMove("Nxd5", PieceMove.capture("N", "d5"));
    assertMove("Nge7", PieceMove.moveWithDisambiguation("N", "g", "e7"));
    assertMove("N1e7", PieceMove.moveWithDisambiguation("N", "1", "e7"));
    assertMove("Ngxd5", PieceMove.captureWithDisambiguation("N", "g", "d5"));
    assertMove("e8=Q", PieceMove.pawnMove("e8").withPromotion("Q"));
    assertMove("Qxd5+", PieceMove.capture("Q", "d5").withCheckOrMate("+"));
    assertMove("Qxd5#", PieceMove.capture("Q", "d5").withCheckOrMate("#"));
    assertMove("O-O", Castling.kingside());
    assertMove("O-O-O", Castling.queenside());
  }

  @Test
  public void testDotParseBenchmark() {
    assertThat(dotParse()).isEqualTo(13 * SCALE);
  }

  @Test
  public void testRegexBenchmark() {
    assertThat(regex()).isEqualTo(13 * SCALE);
  }

  @Test
  public void testDotParse() {
    assertThat(PARSER.parseToStream(INPUT))
        .containsExactly(
            PieceMove.pawnMove("e4"),
            PieceMove.move("N", "f3"),
            PieceMove.pawnCapture("e", "d5"),
            Castling.kingside(),
            PieceMove.capture("Q", "d5").withCheckOrMate("+"),
            PieceMove.pawnMove("e8").withPromotion("Q").withCheckOrMate("#"),
            PieceMove.pawnMove("a6"),
            PieceMove.move("N", "c6"),
            PieceMove.pawnMove("d5"),
            PieceMove.pawnMove("e5"),
            PieceMove.move("B", "d6"),
            PieceMove.moveWithDisambiguation("N", "g", "e7"),
            Castling.queenside()
        );
  }

  @Test
  public void testRegex() {
    try (Scanner scanner = new Scanner(INPUT)) {
      assertThat(scanner.findAll(MOVE_REGEX).map(ChessMoveBenchmark::mapMatch))
          .containsExactly(
              PieceMove.pawnMove("e4"),
              PieceMove.move("N", "f3"),
              PieceMove.pawnCapture("e", "d5"),
              Castling.kingside(),
              PieceMove.capture("Q", "d5").withCheckOrMate("+"),
              PieceMove.pawnMove("e8").withPromotion("Q").withCheckOrMate("#"),
              PieceMove.pawnMove("a6"),
              PieceMove.move("N", "c6"),
              PieceMove.pawnMove("d5"),
              PieceMove.pawnMove("e5"),
              PieceMove.move("B", "d6"),
              PieceMove.moveWithDisambiguation("N", "g", "e7"),
              Castling.queenside()
          );
    }
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  public sealed interface Move permits Castling, PieceMove {}

  record Castling(boolean isKingside) implements Move {
    public static Castling kingside() {
      return new Castling(true);
    }

    public static Castling queenside() {
      return new Castling(false);
    }
  }

  record PieceMove(
      String piece,
      String disambiguation,
      boolean isCapture,
      String destination,
      String promotion,
      String checkOrMate
  ) implements Move {

    public static PieceMove move(String piece, String destination) {
      return new PieceMove(piece, null, false, destination, null, null);
    }

    public static PieceMove capture(String piece, String destination) {
      return new PieceMove(piece, null, true, destination, null, null);
    }

    public static PieceMove moveWithDisambiguation(String piece, String disambiguation, String destination) {
      return new PieceMove(piece, disambiguation, false, destination, null, null);
    }

    public static PieceMove captureWithDisambiguation(String piece, String disambiguation, String destination) {
      return new PieceMove(piece, disambiguation, true, destination, null, null);
    }

    public static PieceMove pawnMove(String destination) {
      return new PieceMove(null, null, false, destination, null, null);
    }

    public static PieceMove pawnCapture(String file, String destination) {
      return new PieceMove(null, file, true, destination, null, null);
    }

    public PieceMove withPromotion(String promotion) {
      return new PieceMove(piece, disambiguation, isCapture, destination, promotion, checkOrMate);
    }

    public PieceMove withCheckOrMate(String checkOrMate) {
      return new PieceMove(piece, disambiguation, isCapture, destination, promotion, checkOrMate);
    }
  }
}
