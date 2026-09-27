package com.google.mu.examples.mapflag.parsecj;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.javafp.parsecj.Combinators.eof;
import static org.javafp.parsecj.Combinators.or;
import static org.javafp.parsecj.Combinators.retn;
import static org.javafp.parsecj.Combinators.satisfy;
import static org.javafp.parsecj.Text.chr;
import static org.javafp.parsecj.Text.regex;
import static org.javafp.parsecj.Text.wspaces;

import com.google.mu.examples.mapflag.ParseUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.javafp.data.IList;
import org.javafp.parsecj.Parser;
import org.javafp.parsecj.Reply;
import org.javafp.parsecj.input.Input;

public final class ParsecjMapFlagParser {
  private static final Parser<Character, Character> COMMA = tok(chr(','));

  private static final Parser<Character, Object> SCALAR = or(
      tok(regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))
          .map(ParseUtils::toNumber),
      tok(chr('\\')
              .then(satisfy((Character c) -> true))
              .or(satisfy((Character c) -> c != '"' && c != '\\'))
              .many()
              .between(chr('"'), chr('"')))
          .map(IList::listToString));

  // Entries are built with bind() over the key and map() over the value;
  // sepEndBy(COMMA) parses 0+ items separated and optionally terminated by COMMA.
  private static final Parser<Character, Map<String, Object>> MAP = tok(regex("[a-zA-Z0-9_-]+"))
      .bind(k -> tok(chr('='))
          .then(
              or(
                  SCALAR,
                  SCALAR.sepEndBy(COMMA)
                      .between(tok(chr('[')), tok(chr(']')))
                      .map(IList::toList)))
          .map(v -> Map.entry(k, v)))
      .sepEndBy(COMMA)
      .between(tok(chr('{')), tok(chr('}')))
      .between(wspaces, eof())
      .map(entries -> entries.stream()
          .collect(
              toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)));

  public static Map<String, Object> parse(String input) {
    return MAP.parse(Input.of(requireNonNull(input)))
        .match(
            Reply.Ok::getResult,
            err -> {
              throw new IllegalArgumentException(err.getMsg());
            });
  }

  // ParsecJ has no global whitespace skipping; each terminal is wrapped in tok().
  private static <T> Parser<Character, T> tok(Parser<Character, T> p) {
    return p.bind(x -> wspaces.then(retn(x)));
  }
}
