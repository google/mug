package com.google.mu.examples.mapflag.catsparse;

import static scala.jdk.javaapi.CollectionConverters.asJava;

import com.google.common.collect.Maps;
import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import scala.collection.Seq;

@RunWith(JUnit4.class)
public class CatsParseMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    return CatsParseMapFlagParser.parse(input)
        .fold(
            error -> {
              throw new IllegalArgumentException(error.toString());
            },
            map ->
                Maps.transformValues(asJava(map), v -> v instanceof Seq<?> seq ? asJava(seq) : v));
  }
}
