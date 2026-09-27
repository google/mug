package com.google.mu.examples.mapflag.antlr;

import static com.google.mu.examples.mapflag.ParseUtils.toNumber;
import static java.util.stream.Collectors.toMap;

import com.google.mu.examples.mapflag.antlr.FlagMapParser.ListContext;
import com.google.mu.examples.mapflag.antlr.FlagMapParser.ScalarContext;
import com.google.mu.examples.mapflag.antlr.FlagMapParser.SingleContext;
import com.google.mu.examples.mapflag.antlr.FlagMapParser.ValueContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public final class AntlrMapFlagParser {
  // ANTLR logs errors to stderr and recovers by default; install THROWER on both lexer and parser.
  private static final BaseErrorListener THROWER = new BaseErrorListener() {
    @Override public void syntaxError(
        Recognizer<?, ?> r, Object sym, int line, int col, String msg, RecognitionException e) {
      throw new IllegalArgumentException(line + ":" + (col + 1) + " " + msg);
    }
  };

  public static Map<String, Object> parse(String input) {
    FlagMapLexer lexer = new FlagMapLexer(CharStreams.fromString(input));
    lexer.removeErrorListeners();
    lexer.addErrorListener(THROWER);
    FlagMapParser parser = new FlagMapParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(THROWER);
    return parser.map().entry().stream()
        .collect(
            toMap(
                e -> e.key().getText(),
                e -> toValue(e.value()),
                (a, b) -> {
                  throw new IllegalArgumentException("Duplicate key");
                },
                LinkedHashMap::new));
  }

  private static Object toValue(ValueContext v) {
    return switch (v) {
      case SingleContext s -> toScalar(s.scalar());
      case ListContext l -> l.scalar().stream()
          .map(AntlrMapFlagParser::toScalar)
          .toList();
      default -> throw new AssertionError(v);
    };
  }

  private static Object toScalar(ScalarContext s) {
    if (s.STRING() != null) {
      String raw = s.STRING().getText();
      return raw.substring(1, raw.length() - 1)
          .replaceAll("(?s)\\\\(.)", "$1");
    }
    return toNumber(s.getText());
  }
}
