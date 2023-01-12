package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.ClassSanityTester;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringTemplateTest {

  @Test public void parse_noPlaceholder() {
    StringTemplate template = new StringTemplate("this is literal");
    assertThat(template.parse("this is literal").toMap()).isEmpty();
    assertThat(template.match("this isn't literal")).isEmpty();
  }

  @Test public void parse_onlyPlaceholder() {
    StringTemplate template = new StringTemplate("{body}");
    assertThat(template.parse("Hello Tom!").toMap()).containsExactly("{body}", "Hello Tom!");
  }

  @Test public void parse_singlePlaceholder() {
    StringTemplate template = new StringTemplate("Hello {name}!");
    assertThat(template.parse("Hello Tom!").toMap()).containsExactly("{name}", "Tom");
  }

  @Test public void parse_multiplePlaceholders() {
    StringTemplate template =
        new StringTemplate("Hello {name}, welcome to {where}!");
    assertThat(template.parse("Hello Gandolf, welcome to Isengard!").toMap())
        .containsExactly("{name}", "Gandolf", "{where}", "Isengard")
        .inOrder();
  }

  @Test public void parse_multiplePlaceholdersWithSameName() {
    StringTemplate template =
        new StringTemplate("Hello {name} and {name}!");
    ImmutableListMultimap<String, String> result =
        template.parse("Hello Gandolf and Aragon!")
            .collect(ImmutableListMultimap::toImmutableListMultimap);
    assertThat(result)
        .containsExactly("{name}", "Gandolf", "{name}", "Aragon")
        .inOrder();
  }

  @Test public void parse_emptyPlaceholderValue() {
    StringTemplate template =
        new StringTemplate("Hello {name}!");
    assertThat(template.parse("Hello !").toMap()).containsExactly("{name}", "");
  }

  @Test public void parse_preludeFailsToMatch() {
    StringTemplate template = new StringTemplate("Hello {name}!");
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hell Tom!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("elloh Tom!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse(" Hello Tom!"));
  }

  @Test public void parse_postludeFailsToMatch() {
    StringTemplate template = new StringTemplate("Hello {name}!");
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom?"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom! "));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom"));
  }

  @Test public void parse_nonEmptyTemplate_emptyInput() {
    StringTemplate template = new StringTemplate("Hello {name}!");
    assertThrows(IllegalArgumentException.class, () -> template.parse(""));
  }

  @Test public void parse_emptyTemplate_nonEmptyInput() {
    StringTemplate template = new StringTemplate("");
    assertThrows(IllegalArgumentException.class, () -> template.parse("."));
  }

  @Test public void parse_emptyTemplate_emptyInput() {
    StringTemplate template = new StringTemplate("");
    assertThat(template.parse("").toMap()).isEmpty();
  }

  @Test public void parse_withOneArgLambda() {
    assertThat(new StringTemplate("1 is {}").parse("1 is one", Object::toString)).isEqualTo("one");
  }

  @Test public void parse_withTwoArgsLambda() {
    assertThat(new StringTemplate("1 is {}, 2 is {}").parse("1 is one, 2 is two", String::concat))
        .isEqualTo("onetwo");
  }

  @Test public void parse_withThreeArgsLambda() {
    String result =
        new StringTemplate("1 is {}, 2 is {}, 3 is {}")
            .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> x + "," + y + "," + z);
    assertThat(result).isEqualTo("one,two,three");
  }

  @Test public void parse_withFourArgsLambda() {
    String result =
        new StringTemplate("1 is {}, 2 is {}, 3 is {}, 4 is {what}")
            .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> a + b + c + d);
    assertThat(result).isEqualTo("onetwothreefour");
  }

  @Test public void parse_withFiveArgsLambda() {
    String result =
        new StringTemplate("1 is {}, 2 is {}, 3 is {}, 4 is {what}, 5 is {}")
            .parse("1 is one, 2 is two, 3 is three, 4 is four, 5 is five", (a, b, c, d, e) -> a + b + c + d + e);
    assertThat(result).isEqualTo("onetwothreefourfive");
  }

  @Test public void parse_withSixArgsLambda() {
    String result =
        new StringTemplate("1 is {}, 2 is {}, 3 is {}, 4 is {what}, 5 is {}, 6 is {}")
            .parse("1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six", (a, b, c, d, e, f) -> a + b + c + d + e + f);
    assertThat(result).isEqualTo("onetwothreefourfivesix");
  }

  @Test public void testRender() {
    assertThat(new StringTemplate("Hi {name}!").render(v -> "Tom")).isEqualTo("Hi Tom!");
  }

  @Test public void testRenderRoundtrip(
      @TestParameter({"", "k", ".", "foo"}) String key,
      @TestParameter({"", "v", ".", "bar"}) String value) {
    ImmutableMap<String, String> mappings = ImmutableMap.of("{key}", key, "{value}", value);
    StringTemplate template = new StringTemplate("key : {key}, value : {value}");
    String rendered = template.render(v -> mappings.get(v.toString()));
    assertThat(template.parse(rendered).toMap()).isEqualTo(mappings);
  }

  @Test public void twoPlaceholdersNextToEachOther() {
    assertThrows(IllegalArgumentException.class, () -> new StringTemplate("{a}{b}"));
    assertThrows(IllegalArgumentException.class, () -> new StringTemplate("{}{}"));
  }

  @Test public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.Pattern.class, Substring.between("{", "}"))
        .testNulls(StringTemplate.class);
  }
}
