package com.google.common.labs.email;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.or;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.mu.util.CharPredicate.anyOf;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.CharPredicate.range;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.joining;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.labs.parse.Parser;
import com.google.mu.util.CaseBreaker;

/**
 * Representation of an RFC 2047 encoded-word (e.g. {@code =?UTF-8?Q?Admin?=}).
 * Parses and decodes standard MIME encoding without using regular expressions.
 */
record EncodedWord(Charset charset, Encoding encoding, String encodedText) {
  /** Parser that matches a valid RFC 2047 encoded-word. */
  private static final Parser<EncodedWord> ENCODED =
      sequence(
          oneOf(US_ASCII, ISO_8859_1, UTF_8).followedBy("?"),
          caseInsensitiveBy(Encoding::name, Encoding.values()).followedBy("?"),
          zeroOrMore(range('!', '~').and(anyOf("?").not()), "encoded text"),
          EncodedWord::new);

  private static final Parser<Object> SEGMENT =
      anyOf(
          ENCODED.between("=?", "?="),
          consecutive(noneOf("= \t\r\n"), "literal chars"),
          string("="),
          consecutive(anyOf(" \t\r\n"), "linear whitespaces").map(Lws::new));

  static String decode(String input) {
    List<?> segments = SEGMENT.zeroOrMore().parse(input);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      Object segment = segments.get(i);
      if (i > 0 && i < segments.size() - 1
          && segment instanceof Lws
          && segments.get(i - 1) instanceof EncodedWord
          && segments.get(i + 1) instanceof EncodedWord) {
        continue;
      }
      builder.append(segment);
    }
    return builder.toString();
  }

  @Override public String toString() {
    try {
      return new String(encoding.decodeBytes(encodedText), charset);
    } catch (Exception e) { // Fallback to the raw format if decoding fails
      return "=?" + charset.name() + "?" + encoding + "?" + encodedText + "?=";
    }
  }

  @SafeVarargs
  private static <T> Parser<T> caseInsensitiveBy(Function<? super T, String> getName, T... values) {
    return stream(values).map(c -> caseInsensitive(getName.apply(c)).thenReturn(c)).collect(or());
  }

  private static Parser<Charset> oneOf(Charset... charsets) {
    return stream(charsets).map(EncodedWord::charset).collect(or());
  }

  private static Parser<Charset> charset(Charset charset) {
    return Stream.concat(Stream.of(charset.name()), charset.aliases().stream())
        .flatMap(EncodedWord::variationsOf)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .distinct()
        .sorted(reverseOrder())
        .map(Parser::caseInsensitive)
        .collect(or())
        .thenReturn(charset);
  }

  private static Stream<String> variationsOf(String name) {
    var tokens = new CaseBreaker()
        .withLowerCaseChars(Character::isLowerCase)  // number and letters should separate
        .breakCase(name)
        .toList();
    return Stream.of(
        name,
        tokens.stream().collect(joining("-")),
        tokens.stream().collect(joining("_")),
        tokens.stream().collect(joining()));
  }

  enum Encoding {
    Q {
      @Override byte[] decodeBytes(String raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length());
        for (int i = 0; i < raw.length(); i++) {
          char c = raw.charAt(i);
          if (c == '=' && i + 2 < raw.length()) { // is it like =E9?
            int high = Character.digit(raw.charAt(i + 1), 16);
            int low = Character.digit(raw.charAt(i + 2), 16);
            // Character.digit returns a non-negative value if valid, or -1 if invalid
            if (high >= 0 && low >= 0) {
              // Combine the two hex digits (high and low nibbles) into a single byte
              out.write((high << 4) | low);
              i += 2; // Skip the two consumed hex digits
              continue;
            }
          }
          out.write(c == '_' ? ' ' : c);
        }
        return out.toByteArray();
      }
    },
    B {
      @Override byte[] decodeBytes(String raw) {
        return Base64.getDecoder().decode(raw);
      }
    }
    ;

    abstract byte[] decodeBytes(String raw);
  }

  private record Lws(String whitespaces) {
    @Override public String toString() {
      return whitespaces;
    }
  }
}
