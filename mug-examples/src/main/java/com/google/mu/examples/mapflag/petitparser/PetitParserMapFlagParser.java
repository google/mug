package com.google.mu.examples.mapflag.petitparser;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.noneOf;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.pattern;

import com.google.mu.examples.mapflag.ParseUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.petitparser.parser.Parser;

public final class PetitParserMapFlagParser {
  private static final Parser COMMA = of(',').trim();

  // .trim() only skips whitespace outside the flattened number, so "- 5" is rejected.
  // PetitParser is untyped, so map() lambdas cast their input parameter types.
  private static final Parser SCALAR = of('-')
      .optional()
      .seq(
          of('0').or(pattern("1-9").seq(digit().star())),
          of('.').seq(digit().plus()).optional())
      .flatten()
      .trim()
      .map(ParseUtils::toNumber)
      .or(
          of('"')
              .seq(
                  of('\\')
                      .seq(any())
                      .pick(1)
                      .or(noneOf("\"\\"))
                      .star()
                      .map((List<Character> cs) -> cs.stream()
                          .map(String::valueOf)
                          .collect(joining())),
                  of('"'))
              .pick(1)
              .trim());

  private static final Parser MAP = of('{')
      .trim()
      .seq(
          pattern("a-zA-Z0-9_-")
              .plus()
              .flatten()
              .trim()
              .seq(
                  of('=').trim(),
                  SCALAR.or(
                      of('[')
                          .trim()
                          .seq(
                              // delimitedBy() allows a trailing comma but keeps the separators.
                              SCALAR
                                  .delimitedBy(COMMA)
                                  .map(PetitParserMapFlagParser::dropSeparators)
                                  .optional(List.of()),
                              of(']').trim())
                          .pick(1)))
              .map((List<Object> kv) -> Map.entry((String) kv.get(0), kv.get(2)))
              .delimitedBy(COMMA)
              .map(PetitParserMapFlagParser::dropSeparators)
              .optional(List.of()),
          of('}').trim())
      .pick(1)
      .map((List<Map.Entry<String, Object>> entries) -> entries.stream()
          .collect(
              toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)))
      .end();

  public static Map<String, Object> parse(String input) {
    return MAP.parse(requireNonNull(input)).get();
  }

  private static List<Object> dropSeparators(List<Object> items) {
    return IntStream.range(0, items.size())
        .filter(i -> i % 2 == 0)
        .mapToObj(items::get)
        .toList();
  }
}
