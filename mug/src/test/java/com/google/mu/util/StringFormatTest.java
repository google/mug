package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.spanningInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.testing.ClassSanityTester;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringFormatTest {

  @Test public void parse_noPlaceholder() {
    StringFormat template = new StringFormat("this is literal");
    assertThat(template.parse("this is literal").toMap()).isEmpty();
  }

  @Test public void parse_onlyPlaceholder() {
    StringFormat template = new StringFormat("%s");
    assertThat(template.parse("Hello Tom!").toMap()).containsExactly("%s", "Hello Tom!");
  }

  @Test public void parse_singlePlaceholder() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("Hello Tom!").toMap()).containsExactly("%s", "Tom");
  }

  @Test public void parse_multiplePlaceholders() {
    StringFormat template =
        new StringFormat("Hello %s, welcome to %s!");
    assertThat(template.parse("Hello Gandolf, welcome to Isengard!").values())
        .containsExactly("Gandolf", "Isengard")
        .inOrder();
  }

  @Test public void parse_multiplePlaceholdersWithSameName() {
    StringFormat template =
        new StringFormat("Hello {name} and {name}!");
    ImmutableListMultimap<String, String> result =
        template.parse("Hello Gandolf and Aragon!")
            .collect(ImmutableListMultimap::toImmutableListMultimap);
    assertThat(result)
        .containsExactly("{name}", "Gandolf", "{name}", "Aragon")
        .inOrder();
  }

  @Test public void parse_emptyPlaceholderValue() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("Hello !").toMap()).containsExactly("%s", "");
  }

  @Test public void parse_preludeFailsToMatch() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hell Tom!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("elloh Tom!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse(" Hello Tom!"));
  }

  @Test public void parse_postludeFailsToMatch() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom?"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom! "));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom"));
  }

  @Test public void parse_nonEmptyTemplate_emptyInput() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThrows(IllegalArgumentException.class, () -> template.parse(""));
  }

  @Test public void parse_emptyTemplate_nonEmptyInput() {
    StringFormat template = new StringFormat("");
    assertThrows(IllegalArgumentException.class, () -> template.parse("."));
  }

  @Test public void parse_emptyTemplate_emptyInput() {
    StringFormat template = new StringFormat("");
    assertThat(template.parse("").toMap()).isEmpty();
  }

  @Test public void parse_withOneArgLambda() {
    assertThat(new StringFormat("1 is %s").parse("1 is one", Object::toString)).isEqualTo("one");
  }

  @Test public void parse_withTwoArgsLambda() {
    assertThat(new StringFormat("1 is %s, 2 is %s").parse("1 is one, 2 is two", String::concat))
        .isEqualTo("onetwo");
  }

  @Test public void parse_withThreeArgsLambda() {
    String result =
        new StringFormat("1 is %s, 2 is %s, 3 is %s")
            .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> x + "," + y + "," + z);
    assertThat(result).isEqualTo("one,two,three");
  }

  @Test public void parse_withFourArgsLambda() {
    String result =
        new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s")
            .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> a + b + c + d);
    assertThat(result).isEqualTo("onetwothreefour");
  }

  @Test public void parse_withFiveArgsLambda() {
    String result =
        new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s, 5 is %s")
            .parse("1 is one, 2 is two, 3 is three, 4 is four, 5 is five", (a, b, c, d, e) -> a + b + c + d + e);
    assertThat(result).isEqualTo("onetwothreefourfive");
  }

  @Test public void parse_withSixArgsLambda() {
    String result =
        new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s, 5 is %s, 6 is %s")
            .parse("1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six", (a, b, c, d, e, f) -> a + b + c + d + e + f);
    assertThat(result).isEqualTo("onetwothreefourfivesix");
  }

  @Test public void parse_customPlaceholder() {
    assertThat(
            new StringFormat("My name is [name]", spanningInOrder("[", "]").repeatedly())
                .parse("My name is one")
                .toMap())
        .containsExactly("[name]", "one");
  }

  @Test public void parse_ignoreTrailing() {
    StringFormat template = new StringFormat("Hello {name}!{*}");
    Map<String, String> result =
        template.parse("Hello Tom! whatever")
            .skipKeysIf("{*}"::equals)
            .toMap();
    assertThat(result).containsExactly("{name}", "Tom");
  }

  @Test public void testFormat() {
    assertThat(
            new StringFormat("Dear %s, next stop is %s!").format("passengers", "Seattle"))
        .isEqualTo("Dear passengers, next stop is Seattle!");
    assertThat(
            new StringFormat("Who is {person}").format("David"))
        .isEqualTo("Who is David");
  }

  @Test public void testFormat_noArg() {
    assertThat(new StringFormat("Hello world!").format()).isEqualTo("Hello world!");
  }

  @Test public void testFormat_nullArg() {
    assertThat(
            new StringFormat("Dear %s, next stop is %s!").format("passengers", null))
        .isEqualTo("Dear passengers, next stop is null!");
  }

  @Test public void testFormat_fewerArgs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("Dear %s, next stop is %s!").format("passengers"));
  }

  @Test public void testFormat_moreArgs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("Dear %s, next stop is %s!").format("passengers", "Seattle", 1));
  }

  @Test public void testFormat_duplicatePlaceholderVariableNames() {
    assertThat(new StringFormat("Hi {person} and {person}!").format("Tom", "Jerry"))
        .isEqualTo("Hi Tom and Jerry!");
  }

  @Test public void testFormat_roundtrip(
      @TestParameter({"", "k", ".", "foo"}) String key,
      @TestParameter({"", "v", ".", "bar"}) String value) {
    StringFormat template = new StringFormat("key : {key}, value : {value}");
    String formatted = template.format(key, value);
    assertThat( template.parse(formatted).toMap())
        .containsExactly("{key}", key, "{value}", value)
        .inOrder();
  }

  @Test public void twoPlaceholdersNextToEachOther() {
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("{a}{b}"));
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("%s%s"));
  }

  @Test public void parse_partiallyOverlappingTemplate() {
    assertThat(new StringFormat("xyz%sxzz").parse("xyzzxxzz").values())
        .containsExactly("zx");
  }

  @Test public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.RepeatingPattern.class, first("%s").repeatedly())
        .testNulls(StringFormat.class);
  }
}
