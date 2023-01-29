package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.spanningInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.ClassSanityTester;
import com.google.mu.util.stream.Joiner;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringFormatTest {

  @Test public void parse_noPlaceholder() {
    StringFormat template = new StringFormat("this is literal");
    assertThat(template.parse("this is literal").get()).isEmpty();
  }

  @Test public void parse_onlyPlaceholder() {
    StringFormat template = new StringFormat("%s");
    assertThat(template.parse("Hello Tom!", v -> v)).hasValue("Hello Tom!");
  }

  @Test public void parse_singlePlaceholder() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("Hello Tom!", v -> v)).hasValue("Tom");
  }

  @Test public void parse_multiplePlaceholders() {
    StringFormat template =
        new StringFormat("Hello %s, welcome to %s!");
    assertThat(template.parse("Hello Gandolf, welcome to Isengard!").get().stream().map(Object::toString))
        .containsExactly("Gandolf", "Isengard")
        .inOrder();
  }

  @Test public void parse_multiplePlaceholdersWithSameName() {
    StringFormat template =
        new StringFormat("Hello {name} and {name}!");
    assertThat(template.parse("Hello Gandolf and Aragon!").get().stream().map(Object::toString))
        .containsExactly("Gandolf", "Aragon")
        .inOrder();
  }

  @Test public void parse_emptyPlaceholderValue() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("Hello !")).isEmpty();
  }

  @Test public void parse_preludeFailsToMatch() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("Hell Tom!")).isEmpty();
    assertThat(template.parse("elloh Tom!")).isEmpty();
    assertThat(template.parse(" Hello Tom!")).isEmpty();
  }

  @Test public void parse_postludeFailsToMatch() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("Hello Tom?")).isEmpty();
    assertThat(template.parse("Hello Tom! ")).isEmpty();
    assertThat(template.parse("Hello Tom")).isEmpty();
  }

  @Test public void parse_nonEmptyTemplate_emptyInput() {
    StringFormat template = new StringFormat("Hello %s!");
    assertThat(template.parse("")).isEmpty();
  }

  @Test public void parse_emptyTemplate_nonEmptyInput() {
    StringFormat template = new StringFormat("");
    assertThat(template.parse(".")).isEmpty();
  }

  @Test public void parse_emptyTemplate_emptyInput() {
    StringFormat template = new StringFormat("");
    assertThat(template.parse("")).hasValue(ImmutableList.of());
  }

  @Test public void parse_withOneArgLambda() {
    assertThat(new StringFormat("1 is %s").parse("1 is one", Object::toString)).hasValue("one");
  }

  @Test public void parse_withOneArgLambda_lambdaReturnsNull() {
    assertThat(new StringFormat("1 is %s").parse("1 is one", x -> null)).isEmpty();
  }

  @Test public void parse_withTwoArgsLambda() {
    assertThat(new StringFormat("1 is %s, 2 is %s").parse("1 is one, 2 is two", String::concat))
        .hasValue("onetwo");
  }

  @Test public void parse_withTwoArgsLambda_lambdaReturnsNull() {
    assertThat(new StringFormat("1 is %s, 2 is %s").parse("1 is one, 2 is two", (x, y) -> null))
        .isEmpty();
  }

  @Test public void parse_withThreeArgsLambda() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> x + "," + y + "," + z))
        .hasValue("one,two,three");
  }

  @Test public void parse_withThreeArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> null))
        .isEmpty();
  }

  @Test public void parse_withFourArgsLambda() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> a + b + c + d))
        .hasValue("onetwothreefour");
  }

  @Test public void parse_withFourArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> null))
        .isEmpty();
  }

  @Test public void parse_withFiveArgsLambda() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s, 5 is %s")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue("onetwothreefourfive");
  }

  @Test public void parse_withFiveArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s, 5 is %s")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> null))
        .isEmpty();
  }

  @Test public void parse_withSixArgsLambda() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s, 5 is %s, 6 is %s")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue("onetwothreefourfivesix");
  }

  @Test public void parse_withSixArgsLambd_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is %s, 2 is %s, 3 is %s, 4 is %s, 5 is %s, 6 is %s")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> null))
        .isEmpty();
  }

  @Test public void parse_customPlaceholder() {
    assertThat(
            new StringFormat("My name is [name]", spanningInOrder("[", "]").repeatedly())
                .parse("My name is one", name -> name))
        .hasValue("one");
  }

  @Test public void parseToMap_noPlaceholder() {
    assertThat(new StringFormat("Hello world").parseToMap("Hello world"))
        .hasValue(ImmutableMap.of());
  }

  @Test public void parseToMap_failsToMatch() {
    assertThat(new StringFormat("Hello {person}").parseToMap("Hi Tom")).isEmpty();
  }

  @Test public void parseToMap_matches() {
    assertThat(new StringFormat("key={key}, value={value}").parseToMap("key=1, value=one"))
        .hasValue(ImmutableMap.of("{key}", "1","{value}", "one"));
  }

  @Test public void parseToMap_duplicateKeyName() {
    StringFormat format = new StringFormat("key=%s, value=%s");
    assertThrows(IllegalArgumentException.class, () -> format.parseToMap("key=1, value=one"));
  }

  @Test public void twoPlaceholdersNextToEachOther() {
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("{a}{b}"));
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("%s%s"));
  }

  @Test public void parse_partiallyOverlappingTemplate() {
    assertThat(new StringFormat("xyz%sxzz").parse("xyzzxxzz", v -> v))
        .hasValue("zx");
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

  @Test public void scan_suffixConsumed() {
    assertThat(new StringFormat("/%s/%s/").scan("/foo/bar//zoo/boo/", (a, b) -> a + b))
        .containsExactly("foobar", "zooboo")
        .inOrder();
  }

  @Test public void scan_emptyPlaceholderValue() {
    assertThat(new StringFormat("/%s/%s/").scan("/foo/bar//zoo//", (a, b) -> a + b))
        .containsExactly("foobar");
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

  @Test public void testToString() {
    assertThat(new StringFormat("projects/{project}/locations/{location}").toString())
        .isEqualTo("projects/{project}/locations/{location}");
  }

  @Test public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.RepeatingPattern.class, first("%s").repeatedly())
        .testNulls(StringFormat.class);
  }
}
