package com.google.mu.benchmarks.parsers.javacc;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

import java.io.Reader;
import java.util.stream.Stream;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;

/** Streaming JSON parser implemented using JavaCC. */
public final class JavaccStreamingParser implements StreamingJsonParser {

  @Override
  public Stream<JsonValue> parse(Reader reader) {
    JavaccJsonParser parser = new JavaccJsonParser(reader);

    return whileNotNull(() -> {
      try {
        if (parser.getToken(1).kind == 0) { // EOF
          return null;
        }
        return parser.jsonValue();
      } catch (ParseException e) {
        throw new RuntimeException(e);
      } catch (TokenMgrError e) {
        throw new RuntimeException(e);
      }
    });
  }
}
