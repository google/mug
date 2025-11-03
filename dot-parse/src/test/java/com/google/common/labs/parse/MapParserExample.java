package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parser.zeroOrMoreDelimited;
import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.stream.BiCollectors.toMap;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapParserExample {

  @Test public void testEmptyMap() {
    assertThat(parse("{}")).isEqualTo(Map.of());
  }

  @Test public void testSimpleKeyValues() {
    assertThat(parse("{k1 : v1, k2 : v2}")).isEqualTo(Map.of("k1", "v1", "k2", "v2"));
  }

  @Test public void testWithNestedList() {
    assertThat(parse("{k1 : v1, k2 : [v2, v3]}"))
        .isEqualTo(Map.of("k1", "v1", "k2", List.of("v2", "v3")));
    assertThat(parse("{k1 : v1, k2 : []}"))
        .isEqualTo(Map.of("k1", "v1", "k2", List.of()));
  }

  @Test public void testWithNestedMap() {
    assertThat(parse("{k1 : v1, k2 : {k21 : v21, k22 : v22}}"))
        .isEqualTo(Map.of("k1", "v1", "k2", Map.of("k21", "v21", "k22", "v22")));
    assertThat(parse("{k1 : v1, k2 : { }}"))
        .isEqualTo(Map.of("k1", "v1", "k2", Map.of()));
  }

  static Map<String, ?> parse(String input) {
    Parser.Rule<Map<String, ?>> lazy = new Parser.Rule<>();
    Parser<List<String>> listParser = word().zeroOrMoreDelimitedBy(",").between("[", "]");
    Parser<Map<String, Object>> mapParser =
        zeroOrMoreDelimited(word().followedBy(":"), anyOf(word(), listParser, lazy), ",", toMap())
            .between("{", "}");
    return lazy.definedAs(mapParser).parseSkipping(Character::isWhitespace, input);
  }
}
