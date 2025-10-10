package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.mu.util.Both;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.stream.BiStream;

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
  }

  @Test public void testWithNestedMap() {
    assertThat(parse("{k1 : v1, k2 : {k21 : v21, k22 : v22}}"))
        .isEqualTo(Map.of("k1", "v1", "k2", Map.of("k21", "v21", "k22", "v22")));
  }

  static Map<String, ?> parse(String input) {
    Parser<String> word = consecutive(CharPredicate.WORD, "key");
    Parser.Lazy<Map<String, ?>> lazy = new Parser.Lazy<>();
    Parser<List<String>> listParser = word.zeroOrMoreDelimitedBy(",").between("[", "]");
    Parser<Map<String, ?>> mapParser =
        sequence(word.followedBy(":"), anyOf(word, listParser, lazy), Both::of)
            .zeroOrMoreDelimitedBy(",")
            .between("{", "}")
            .map(entries -> BiStream.from(entries.stream()).toMap());
    return lazy.delegateTo(mapParser).parseSkipping(Character::isWhitespace, input);
  }
}
