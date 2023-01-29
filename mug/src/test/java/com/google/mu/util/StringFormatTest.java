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
import com.google.mu.util.stream.Joiner;
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

  @Test public void twoPlaceholdersNextToEachOther() {
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("{a}{b}"));
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("%s%s"));
  }

  @Test public void parse_partiallyOverlappingTemplate() {
    assertThat(new StringFormat("xyz%sxzz").parse("xyzzxxzz").values())
        .containsExactly("zx");
  }

  @Test public void scan_singlePlaceholder() {
    assertThat(new StringFormat("[id=%s]").scan("id=1", v -> v)).isEmpty();
    assertThat(new StringFormat("[id=%s]").scan("[id=foo]", v -> v)).containsExactly("foo");
    assertThat(new StringFormat("[id=%s]").scan("[id=foo][id=bar]", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test public void scan_singlePlaceholder_emptyInput() {
    assertThat(new StringFormat("[id=%s]").scan("", v -> v)).isEmpty();
  }

  @Test public void scan_twoPlaceholders() {
    assertThat(new StringFormat("[id=%s, name=%s]").scan("id=1", (id, name) -> id + "," + name)).isEmpty();
    assertThat(new StringFormat("[id=%s, name=%s]").scan("[id=foo, name=bar]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar");
    assertThat(
            new StringFormat("[id=%s, name=%s]")
                .scan("[id=foo, name=bar][id=zoo, name=boo]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar", "zoo,boo")
        .inOrder();
  }

  @Test public void scan_twoPlaceholders_emptyInput() {
    assertThat(new StringFormat("[id=%s, name=%s]").scan("", (id, name) -> id + "," + name)).isEmpty();
  }

  @Test public void scan_threePlaceholders() {
    assertThat(new StringFormat("[a=%s, b=%s, c=%s]").scan("a=1,b=2,c", (a, b, c) -> a + b + c)).isEmpty();
    assertThat(new StringFormat("[a=%s, b=%s, c=%s]").scan("[a=1, b=2, c=3]", (a, b, c) -> a + b + c))
        .containsExactly("123");
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s]")
                .scan("[a=1, b=2, c=3] [a=x, b=y, c=z]", (a, b, c) -> a + b + c))
        .containsExactly("123", "xyz")
        .inOrder();
  }

  @Test public void scan_threePlaceholders_emptyInput() {
    assertThat(new StringFormat("[a=%s, b=%s, c=%s]").scan("", (a, b, c) -> a + b + c)).isEmpty();
  }

  @Test public void scan_fourPlaceholders() {
    assertThat(new StringFormat("[a=%s, b=%s, c=%s, d=%s]").scan("a=1,b=2,c=3,d", (a, b, c, d) -> a + b + c + d))
        .isEmpty();
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s]")
                .scan("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> a + b + c + d))
        .containsExactly("1234");
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s]")
                .scan("[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=w]", (a, b, c, d) -> a + b + c + d))
        .containsExactly("1234", "zyxw")
        .inOrder();
  }

  @Test public void scan_fourPlaceholders_emptyInput() {
    assertThat(new StringFormat("[a=%s, b=%s, c=%s, d=%s]").scan("", (a, b, c, d) -> a + b + c + d)).isEmpty();
  }

  @Test public void scan_fivePlaceholders_emptyInput() {
    assertThat(new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s]").scan("", (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
  }

  @Test public void scan_fivePlaceholders() {
    assertThat(new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s]").scan("a=1,b=2,c=3,d", (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s]")
                .scan("[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> a + b + c + d + e))
        .containsExactly("12345");
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s]")
                .scan("[a=1, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]", (a, b, c, d, e) -> a + b + c + d + e))
        .containsExactly("12345", "zyxwv")
        .inOrder();
  }

  @Test public void scan_sixPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s, f=%s]")
                .scan("", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
  }

  @Test public void scan_sixPlaceholders() {
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s, f=%s]")
                .scan("a=1, b=2, c=3, d=4, e=5, f", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s, f=%s]")
                .scan("[a=1, b=2, c=3, d=4, e=5, f=6]", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .containsExactly("123456");
    assertThat(
            new StringFormat("[a=%s, b=%s, c=%s, d=%s, e=%s, f=%s]")
                .scan("[a=1, b=2, c=3, d=4, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .containsExactly("123456", "zyxwvu")
        .inOrder();
  }

  @Test public void scan_skipsNonMatchingCharsFromBeginning() {
    assertThat(new StringFormat("[id=%s]").scan("whatever [id=foo] [id=bar]", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k=%s, v=%s]")
                .scan("whatever [k=one, v=1] [k=two, v=2]")
                .map(Joiner.on(":")::join))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test public void scan_skipsNonMatchingCharsFromMiddle() {
    assertThat(new StringFormat("[id=%s]").scan("[id=foo] [id=bar]", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k=%s, v=%s]")
                .scan("[k=one, v=1] and then [k=two, v=2]")
                .map(Joiner.on(":")::join))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test public void scan_skipsNonMatchingCharsFromEnd() {
    assertThat(new StringFormat("[id=%s]").scan("[id=foo] [id=bar];[id=baz", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k=%s, v=%s]")
                .scan("[k=one, v=1][k=two, v=2];[k=three,v]")
                .map(Joiner.on(":")::join))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.RepeatingPattern.class, first("%s").repeatedly())
        .testNulls(StringFormat.class);
  }
}
