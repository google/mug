package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertAbout;
import static org.junit.Assert.assertThrows;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.StringReader;
import java.util.stream.Stream;

/** Custom Truth Subject to test {@link Parser} implementations. */
final class ParserSubject extends Subject {
  private final Parser<?> parser;

  static ParserSubject assertThat(Parser<?> parser) {
    return assertAbout(ParserSubject::new).that(parser);
  }

  private ParserSubject(FailureMetadata metadata, Parser<?> parser) {
    super(metadata, parser);
    this.parser = parser;
  }

  ParserInputSubject fromStringOrReader(String input) {
    return new StringAndReaderInputSubject(parser, input);
  }

  ParserInputSubject fromString(String input) {
    return new StringOnlyInputSubject(parser, input);
  }

  /** Holds assertion methods for a specific parser input. */
  abstract static class ParserInputSubject {
    final Parser<?> parser;
    final String input;

    ParserInputSubject(Parser<?> parser, String input) {
      this.parser = parser;
      this.input = input;
    }

    abstract void parsesTo(Object expected);
    abstract void failsToParse();
    abstract void failsToParse(String expectedMessage);
    abstract void failsToParse(int expectedIndex, String expectedMessage);
  }

  private static final class StringOnlyInputSubject extends ParserInputSubject {
    StringOnlyInputSubject(Parser<?> parser, String input) {
      super(parser, input);
    }

    @Override void parsesTo(Object expected) {
      Truth.assertThat(parser.parse(input)).isEqualTo(expected);
    }

    @Override void failsToParse() {
      assertThrows(Parser.ParseException.class, () -> parser.parse(input));
    }

    @Override void failsToParse(String expectedMessage) {
      Parser.ParseException ex =
          assertThrows(Parser.ParseException.class, () -> parser.parse(input));
      Truth.assertThat(ex).hasMessageThat().contains(expectedMessage);
    }

    @Override void failsToParse(int expectedIndex, String expectedMessage) {
      Parser.ParseException ex =
          assertThrows(Parser.ParseException.class, () -> parser.parse(input));
      Truth.assertThat(ex.getSourceIndex()).isEqualTo(expectedIndex);
      Truth.assertThat(ex).hasMessageThat().contains(expectedMessage);
    }
  }

  private static final class StringAndReaderInputSubject extends ParserInputSubject {
    StringAndReaderInputSubject(Parser<?> parser, String input) {
      super(parser, input);
    }

    @Override void parsesTo(Object expected) {
      Truth.assertThat(parser.parse(input)).isEqualTo(expected);
      Stream<?> stream = parser.parseToStream(new StringReader(input));
      Truth.assertThat(stream.findFirst().get()).isEqualTo(expected);
    }

    @Override void failsToParse() {
      assertThrows(Parser.ParseException.class, () -> parser.parse(input));
      Stream<?> stream = parser.parseToStream(new StringReader(input));
      assertThrows(Parser.ParseException.class, () -> stream.findFirst());
    }

    @Override void failsToParse(String expectedMessage) {
      Parser.ParseException stringParseException =
          assertThrows(Parser.ParseException.class, () -> parser.parse(input));
      Truth.assertThat(stringParseException).hasMessageThat().contains(expectedMessage);

      Stream<?> stream = parser.parseToStream(new StringReader(input));
      Parser.ParseException readerParseException =
          assertThrows(Parser.ParseException.class, () -> stream.findFirst());
      Truth.assertThat(readerParseException).hasMessageThat().contains(expectedMessage);
    }

    @Override void failsToParse(int expectedIndex, String expectedMessage) {
      Parser.ParseException stringParseException =
          assertThrows(Parser.ParseException.class, () -> parser.parse(input));
      Truth.assertThat(stringParseException.getSourceIndex()).isEqualTo(expectedIndex);
      Truth.assertThat(stringParseException).hasMessageThat().contains(expectedMessage);

      Stream<?> stream = parser.parseToStream(new StringReader(input));
      Parser.ParseException readerParseException =
          assertThrows(Parser.ParseException.class, () -> stream.findFirst());
      Truth.assertThat(readerParseException.getSourceIndex()).isEqualTo(expectedIndex);
      Truth.assertThat(readerParseException).hasMessageThat().contains(expectedMessage);
    }
  }
}
