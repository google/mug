package com.google.mu.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.ClassSanityTester;
import com.google.common.truth.OptionalSubject;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringFormatTest {
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');

  @Test
  public void parse_noPlaceholder(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("this is literal");
    assertThat(format.parse("this is literal").get()).isEmpty();
  }

  @Test
  public void parse_emptyCurlyBrace_doesNotCountAsPlaceholder(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("curly brace: {}");
    assertThat(format.parse("curly brace: {}").get()).isEmpty();
  }

  @Test
  public void parse_onlyPlaceholder(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("{v}");
    assertThat(format.parse("Hello Tom!", v -> v)).hasValue("Hello Tom!");
  }

  @Test
  public void parse_onlyEllipsis(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("{...}");
    assertThat(format.parse("Hello Tom!")).hasValue(ImmutableList.of());
  }

  @Test
  public void parse_singlePlaceholder(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {v}!");
    assertThat(format.parse("Hello Tom!", v -> v)).hasValue("Tom");
  }

  @Test
  public void parse_singlePlaceholder_withEllipsis(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {...}!");
    assertThat(format.parse("Hello Tom!")).hasValue(ImmutableList.of());
  }

  @Test
  public void parse_multiplePlaceholders(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {person}, welcome to {place}!");
    assertThat(
            format.parse("Hello Gandolf, welcome to Isengard!").get().stream()
                .map(Object::toString))
        .containsExactly("Gandolf", "Isengard")
        .inOrder();
  }

  @Test
  public void parse_multiplePlaceholders_withEllipsis(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {...}, welcome to {place}!");
    assertThat(
            format.parse("Hello Gandolf, welcome to Isengard!").get().stream()
                .map(Object::toString))
        .containsExactly("Isengard");
  }

  @Test
  public void parse_multiplePlaceholders_withEllipsis_usingLambda(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {...}, welcome to {place}!");
    assertThat(format.parse("Hello Gandolf, welcome to Isengard!", p -> p)).hasValue("Isengard");
  }

  @Test
  public void parse_multiplePlaceholdersWithSameName(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {name} and {name}!");
    assertThat(format.parse("Hello Gandolf and Aragon!").get().stream().map(Object::toString))
        .containsExactly("Gandolf", "Aragon")
        .inOrder();
  }

  @Test
  public void parse_emptyPlaceholderValue() {
    StringFormat format = new StringFormat("Hello {what}!");
    assertThat(format.parse("Hello !").get().stream().map(Substring.Match::toString))
        .containsExactly("");
  }

  @Test
  public void parse_strict_emptyPlaceholderValue() {
    StringFormat format = Mode.NO_EMPTY_MATCH.formatOf("Hello {what}!");
    assertThat(format.parse("Hello !")).isEmpty();
  }

  @Test
  public void parse_strict_placeholdeWithInvalidChar() {
    StringFormat format = StringFormat.strict("Hello {what}!", DIGIT);
    assertThat(format.parse("Hello x!")).isEmpty();
    assertThat(format.parse("Hello x1!")).isEmpty();
    assertThat(format.parse("Hello 1x!")).isEmpty();
  }

  @Test
  public void parse_preludeFailsToMatch(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {person}!");
    assertThat(format.parse("Hell Tom!")).isEmpty();
    assertThat(format.parse("elloh Tom!")).isEmpty();
    assertThat(format.parse(" Hello Tom!")).isEmpty();
  }

  @Test
  public void parse_postludeFailsToMatch(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {person}!");
    assertThat(format.parse("Hello Tom?")).isEmpty();
    assertThat(format.parse("Hello Tom! ")).isEmpty();
    assertThat(format.parse("Hello Tom")).isEmpty();
  }

  @Test
  public void parse_nonEmptyTemplate_emptyInput(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("Hello {person}!");
    assertThat(format.parse("")).isEmpty();
  }

  @Test
  public void parse_emptyTemplate_nonEmptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("").parse(".")).isEmpty();
  }

  @Test
  public void parse_emptyTemplate_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("").parse("")).hasValue(ImmutableList.of());
  }

  @Test
  public void parse_withOneArgLambda(@TestParameter Mode mode) {
    assertThat(mode.formatOf("1 is {what}").parse("1 is one", Object::toString)).hasValue("one");
  }

  @Test
  public void parse_withOneArgLambda_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("1 is {what}").parse("", Object::toString)).isEmpty();
  }

  @Test
  public void parse_withOneArgLambda_lambdaReturnsNull(@TestParameter Mode mode) {
    assertThat(mode.formatOf("1 is {what}").parse("1 is one", w -> null)).isEmpty();
  }

  @Test
  public void parse_withTwoArgsLambda(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {what}, 2 is {what}").parse("1 is one, 2 is two", String::concat))
        .hasValue("onetwo");
  }

  @Test
  public void parse_withTwoArgsLambda_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("1 is {x}, 2 is {y}").parse("", (x, y) -> null)).isEmpty();
  }

  @Test
  public void parse_withTwoArgsLambda_lambdaReturnsNull(@TestParameter Mode mode) {
    assertThat(mode.formatOf("1 is {x}, 2 is {y}").parse("1 is one, 2 is two", (x, y) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withThreeArgsLambda(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {x}, 2 is {y}, 3 is {z}")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> x + "," + y + "," + z))
        .hasValue("one,two,three");
  }

  @Test
  public void parse_withThreeArgsLambda_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("1 is {x}, 2 is {y}, 3 is {z}").parse("", (x, y, z) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withThreeArgsLambda_lambdaReturnsNull(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {x}, 2 is {y}, 3 is {z}")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withFourArgsLambda(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> a + b + c + d))
        .hasValue("onetwothreefour");
  }

  @Test
  public void parse_withFourArgsLambda_emptyInput(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}").parse("", (a, b, c, d) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withFourArgsLambda_lambdaReturnsNull(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withFiveArgsLambda(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue("onetwothreefourfive");
  }

  @Test
  public void parse_withFiveArgsLambda_emptyInput(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse("", (a, b, c, d, e) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withFiveArgsLambda_lambdaReturnsNull(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withSixArgsLambda(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue("onetwothreefourfivesix");
  }

  @Test
  public void parse_withSixArgsLambda_emptyInput(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse("", (a, b, c, d, e, f) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withSixArgsLambda_lambdaReturnsNull(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> null))
        .isEmpty();
  }

  @Test
  public void parse_placeholderInsideCurlyBraces(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("{key={key}, value={value}}");
    assertThat(format.parse("{key=one, value=1}", (key, value) -> key + ":" + value))
        .hasValue("one:1");
  }

  @Test
  public void parse_multipleCurlyBracedPlaceholderGroups(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("{key={key}}{value={value}}");
    assertThat(format.parse("{key=one}{value=1}", (key, value) -> key + ":" + value))
        .hasValue("one:1");
  }

  @Test
  public void parse_placeholderInsideMultipleCurlyBraces(@TestParameter Mode mode) {
    StringFormat format = mode.formatOf("{test: {{key={key}, value={value}}}}");
    assertThat(format.parse("{test: {{key=one, value=1}}}", (key, value) -> key + ":" + value))
        .hasValue("one:1");
  }

  @Test
  public void twoPlaceholdersNextToEachOther_invalid(@TestParameter Mode mode) {
    assertThrows(IllegalArgumentException.class, () -> mode.formatOf("{a}{b}").parse("ab"));
  }

  @Test
  public void parse_partiallyOverlappingTemplate(@TestParameter Mode mode) {
    assertThat(mode.formatOf("xyz{?}xzz").parse("xyzzxxzz", v -> v)).hasValue("zx");
  }

  @Test
  public void parse_throwsUponIncorrectNumLambdaParameters(@TestParameter Mode mode) {
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {a} or {b}").parse("bad input", Object::toString));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").parse("bad input", String::concat));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").parse("bad input", (a, b, c) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").parse("bad input", (a, b, c, d) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").parse("bad input", (a, b, c, d, e) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").parse("bad input", (a, b, c, d, e, f) -> a));
  }

  @Test
  public void scan_emptyTemplate_nonEmptyInput() {
    assertThat(new StringFormat("").scan("."))
        .containsExactly(ImmutableList.of(), ImmutableList.of());
    assertThat(new StringFormat("").scan("foo"))
        .containsExactly(
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
  }

  @Test
  public void scan_strict_emptyTemplate_nonEmptyInput() {
    assertThat(Mode.NO_EMPTY_MATCH.formatOf("").scan(".")).isEmpty();
    assertThat(Mode.NO_EMPTY_MATCH.formatOf("").scan("foo")).isEmpty();
  }

  @Test
  public void scan_strict_withAllValidChars() {
    StringFormat format = StringFormat.strict("{user: {user_id}}", DIGIT);
    assertThat(format.scan("{user: 123}", id -> Integer.parseInt(id))).containsExactly(123);
    assertThat(format.scan("{user: 123}, {user: 456}", id -> Integer.parseInt(id)))
        .containsExactly(123, 456)
        .inOrder();
  }

  @Test
  public void scan_strict_invalidCharsFiltered() {
    StringFormat format = StringFormat.strict("{user: {user_id}}", DIGIT);
    assertThat(format.scan("{user: 12x}", id -> Integer.parseInt(id))).isEmpty();
    assertThat(format.scan("{user: 12x}, {user: 456}", id -> Integer.parseInt(id)))
        .containsExactly(456);
  }

  @Test
  public void scan_emptyTemplate_emptyInput() {
    assertThat(new StringFormat("").scan("")).containsExactly(ImmutableList.of());
  }

  @Test
  public void scan_strict_emptyTemplate_emptyInput() {
    assertThat(Mode.NO_EMPTY_MATCH.formatOf("").scan("")).isEmpty();
  }

  @Test
  public void scan_singlePlaceholder(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("id=1", id -> id)).isEmpty();
    assertThat(mode.formatOf("[id={id}]").scan("[id=foo]", id -> id)).containsExactly("foo");
    assertThat(mode.formatOf("[id={id}]").scan("[id=foo][id=bar]", id -> id))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholder_withEllipsis(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={...}]").scan("id=1")).isEmpty();
    assertThat(mode.formatOf("[id={...}]").scan("[id=foo]")).containsExactly(ImmutableList.of());
    assertThat(mode.formatOf("[id={...}]").scan("[id=foo][id=bar]"))
        .containsExactly(ImmutableList.of(), ImmutableList.of())
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholder_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("", id -> id)).isEmpty();
  }

  @Test
  public void scan_singlePlaceholder_nullFilteredOut(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("[id=foo]", id -> null)).isEmpty();
    assertThat(mode.formatOf("[id={id}]").scan("[id=foo][id=]", id -> id.isEmpty() ? null : id))
        .containsExactly("foo");
  }

  @Test
  public void scan_emptyPlaceholderValue() {
    assertThat(new StringFormat("/{a}/{b}/").scan("/foo/bar//zoo//", (a, b) -> a + b))
        .containsExactly("foobar", "zoo")
        .inOrder();
  }

  @Test
  public void scan_strict_emptyPlaceholderValue() {
    assertThat(Mode.NO_EMPTY_MATCH.formatOf("/{a}/{b}/").scan("/foo/bar//zoo//", (a, b) -> a + b))
        .containsExactly("foobar");
  }

  @Test
  public void scan_twoPlaceholders(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}, name={name}]").scan("id=1", (id, name) -> id + "," + name))
        .isEmpty();
    assertThat(
            mode.formatOf("[id={id}, name={name}]")
                .scan("[id=foo, name=bar]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar");
    assertThat(
            mode.formatOf("[id={id}, name={name}]")
                .scan("[id=foo, name=bar][id=zoo, name=boo]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar", "zoo,boo")
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_withEllipsis(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[id={...}, name={name}]")
                .scan("[id=foo, name=bar]")
                .map(l -> l.stream().map(Substring.Match::toString).collect(toImmutableList())))
        .containsExactly(ImmutableList.of("bar"));
    assertThat(
            mode.formatOf("[id={...}, name={name}]")
                .scan("[id=, name=bar][id=zoo, name=boo]")
                .map(l -> l.stream().map(Substring.Match::toString).collect(toImmutableList())))
        .containsExactly(ImmutableList.of("bar"), ImmutableList.of("boo"))
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_withEllipsis_usingLambda(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}, name={...}]").scan("[id=foo, name=bar]", id -> id))
        .containsExactly("foo");
    assertThat(
            mode.formatOf("[id={...}, name={name}]")
                .scan("[id=, name=bar][id=zoo, name=boo]", name -> name))
        .containsExactly("bar", "boo")
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_nullFilteredOut(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[id={id}, name={name}]").scan("[id=foo, name=bar]", (id, name) -> null))
        .isEmpty();
    assertThat(
            mode.formatOf("[id={id}, name={name}]")
                .scan(
                    "[id=, name=bar][id=zoo, name=boo]",
                    (id, name) -> id.isEmpty() ? null : id + "," + name))
        .containsExactly("zoo,boo");
  }

  @Test
  public void scan_twoPlaceholders_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}, name={name}]").scan("", (id, name) -> id + "," + name))
        .isEmpty();
  }

  @Test
  public void scan_threePlaceholders(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[a={a}, b={b}, c={c}]").scan("a=1,b=2,c", (a, b, c) -> a + b + c))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}]").scan("[a=1, b=2, c=3]", (a, b, c) -> a + b + c))
        .containsExactly("123");
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}]")
                .scan("[a=1, b=2, c=3] [a=x, b=y, c=z]", (a, b, c) -> a + b + c))
        .containsExactly("123", "xyz")
        .inOrder();
  }

  @Test
  public void scan_threePlaceholders_nullFilteredOut(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[a={a}, b={b}, c={c}]").scan("[a=1, b=2, c=3]", (a, b, c) -> null))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}]")
                .scan(
                    "[a=1, b=2, c=3] [a=x, b=, c=z]", (a, b, c) -> b.isEmpty() ? null : a + b + c))
        .containsExactly("123");
  }

  @Test
  public void scan_threePlaceholders_emptyInput(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[a={a}, b={b}, c={c}]").scan("", (a, b, c) -> a + b + c)).isEmpty();
  }

  @Test
  public void scan_fourPlaceholders(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}]")
                .scan("a=1,b=2,c=3,d", (a, b, c, d) -> a + b + c + d))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}]")
                .scan("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> a + b + c + d))
        .containsExactly("1234");
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}]")
                .scan("[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=w]", (a, b, c, d) -> a + b + c + d))
        .containsExactly("1234", "zyxw")
        .inOrder();
  }

  @Test
  public void scan_fourPlaceholders_nullFilteredOut(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}]")
                .scan("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> null))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}]")
                .scan(
                    "[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=]",
                    (a, b, c, d) -> d.isEmpty() ? null : a + b + c + d))
        .containsExactly("1234")
        .inOrder();
  }

  @Test
  public void scan_fourPlaceholders_emptyInput(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}]").scan("", (a, b, c, d) -> a + b + c + d))
        .isEmpty();
  }

  @Test
  public void scan_fivePlaceholders(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("a=1,b=2,c=3,d", (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> a + b + c + d + e))
        .containsExactly("12345");
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]",
                    (a, b, c, d, e) -> a + b + c + d + e))
        .containsExactly("12345", "zyxwv")
        .inOrder();
  }

  @Test
  public void scan_fivePlaceholders_nullFilteredOut(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> null))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan(
                    "[a=, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]",
                    (a, b, c, d, e) -> a.isEmpty() ? null : a + b + c + d + e))
        .containsExactly("zyxwv");
  }

  @Test
  public void scan_fivePlaceholders_emptyInput(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("", (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
  }

  @Test
  public void scan_sixPlaceholders(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan("a=1, b=2, c=3, d=4, e=5, f", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6]", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .containsExactly("123456");
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .containsExactly("123456", "zyxwvu")
        .inOrder();
  }

  @Test
  public void scan_sixPlaceholders_nullFiltered(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan("[a=1, b=2, c=3, d=4, e=5, f=6]", (a, b, c, d, e, f) -> null))
        .isEmpty();
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan(
                    "[a=1, b=2, c=3, d=, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]",
                    (a, b, c, d, e, f) -> d.isEmpty() ? null : a + b + c + d + e + f))
        .containsExactly("zyxwvu")
        .inOrder();
  }

  @Test
  public void scan_sixPlaceholders_emptyInput(@TestParameter Mode mode) {
    assertThat(
            mode.formatOf("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan("", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
  }

  @Test
  public void scan_suffixConsumed(@TestParameter Mode mode) {
    assertThat(mode.formatOf("/{a}/{b}/").scan("/foo/bar//zoo/boo/", (a, b) -> a + b))
        .containsExactly("foobar", "zooboo")
        .inOrder();
  }

  @Test
  public void scan_skipsNonMatchingCharsFromBeginning(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("whatever [id=foo] [id=bar]", id -> id))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            mode.formatOf("[k={key}, v={value}]")
                .scan("whatever [k=one, v=1] [k=two, v=2]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_skipsNonMatchingCharsFromMiddle(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("[id=foo] [id=bar]", id -> id))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            mode.formatOf("[k={key}, v={value}]")
                .scan("[k=one, v=1] and then [k=two, v=2]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_skipsNonMatchingCharsFromEnd(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("[id=foo] [id=bar];[id=baz", id -> id))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            mode.formatOf("[k={key}, v={value}]")
                .scan("[k=one, v=1][k=two, v=2];[k=three,v]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_skipsPartialMatches(@TestParameter Mode mode) {
    assertThat(mode.formatOf("[id={id}]").scan("[id [id=bar];[id=baz", id -> id))
        .containsExactly("bar")
        .inOrder();
    assertThat(mode.formatOf("[[id={id}]]").scan("[[id [[[id=bar]];[id=baz", id -> id))
        .containsExactly("bar")
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholderOnly(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{s}").scan("whatever", s -> s)).containsExactly("whatever");
  }

  @Test
  public void scan_singleEllipsisOnly(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{...}").scan("whatever")).containsExactly(ImmutableList.of());
    assertThat(new StringFormat("{...}").scan("")).containsExactly(ImmutableList.of());
  }

  @Test
  public void scan_singlePlaceholderOnly_emptyInput() {
    assertThat(new StringFormat("{s}").scan("", s -> s)).containsExactly("");
  }

  @Test
  public void scan_strict_singlePlaceholderOnly_emptyInput() {
    assertThat(Mode.NO_EMPTY_MATCH.formatOf("{s}").scan("", s -> s)).isEmpty();
  }

  @Test
  public void scan_placeholderAtBeginning(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{s} ").scan("a ", s -> s)).containsExactly("a");
    assertThat(mode.formatOf("{s} ").scan("abc d ", s -> s)).containsExactly("abc", "d").inOrder();
  }

  @Test
  public void scan_placeholderAtEnd(@TestParameter Mode mode) {
    assertThat(mode.formatOf(" {s}").scan(" a", s -> s)).containsExactly("a");
    assertThat(mode.formatOf(" {s}").scan(" abc d ", s -> s)).containsExactly("abc d ").inOrder();
    assertThat(mode.formatOf(" {a} {b}").scan(" abc d ", (a, b) -> a + "," + b))
        .containsExactly("abc,d ")
        .inOrder();
  }

  @Test
  public void scan_throwsUponIncorrectNumLambdaParameters(@TestParameter Mode mode) {
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {a} or {b}").scan("bad input", Object::toString));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").scan("bad input", String::concat));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").scan("bad input", (a, b, c) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").scan("bad input", (a, b, c, d) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").scan("bad input", (a, b, c, d, e) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> mode.formatOf("1 is {what}").scan("bad input", (a, b, c, d, e, f) -> a));
  }

  @Test
  public void format_placeholdersFilled(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{a} + {b} = {c}").format(1, 2, 3)).isEqualTo("1 + 2 = 3");
  }

  @Test
  public void format_ellipsisFilled(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{a} + {b} = {...}").format(1, 2, 3)).isEqualTo("1 + 2 = 3");
  }

  @Test
  public void format_nullValueAllowed(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{key} == {value}").format("x", null)).isEqualTo("x == null");
  }

  @Test
  public void format_noPlaceholder(@TestParameter Mode mode) {
    assertThat(mode.formatOf("hello").format()).isEqualTo("hello");
  }

  @Test
  public void format_withEmptyValue(@TestParameter Mode mode) {
    assertThat(mode.formatOf("{a} + {b} = {c}").format(1, 2, "")).isEqualTo("1 + 2 = ");
  }

  @Test
  public void format_tooFewArgs(@TestParameter Mode mode) {
    assertThrows(IllegalArgumentException.class, () -> mode.formatOf("{foo}:{bar}").format(1));
  }

  @Test
  public void format_tooManyArgs(@TestParameter Mode mode) {
    assertThrows(
        IllegalArgumentException.class, () -> mode.formatOf("{foo}:{bar}").format(1, 2, 3));
  }
  @Test
  public void span_emptyFormatString() {
    assertPatternMatch(StringFormat.span(""), "foo").hasValue("[]foo");
    assertPatternMatch(StringFormat.span(""), "").hasValue("[]");
  }

  @Test
  public void span_noPlaceholder_noMatch() {
    assertPatternMatch(StringFormat.span("world"), "hello word 2").isEmpty();
  }

  @Test
  public void span_noPlaceholder_matches() {
    assertPatternMatch(StringFormat.span("world"), "hello world 2").hasValue("hello [world] 2");
  }

  @Test
  public void span_singlePlaceholder_noMatch() {
    assertPatternMatch(StringFormat.span("name: {name}"), "name").isEmpty();
  }

  @Test
  public void span_singlePlaceholder_matches() {
    assertPatternMatch(StringFormat.span("name: {name}."), " name: foo.").hasValue(" [name: foo.]");
  }

  @Test
  public void span_twoPlaceholders_matches() {
    assertPatternMatch(StringFormat.span("{key={key}, value={value}}"), "{key=one, value=1}")
        .hasValue("[{key=one, value=1}]");
  }

  @Test
  public void span_twoPlaceholders_noMatch() {
    assertPatternMatch(StringFormat.span("{key={key}, value={value}}"), "{key=one, }").isEmpty();
  }

  @Test
  public void span_placeholderAtBeginning() {
    assertPatternMatch(StringFormat.span("{foo}=1"), "x=1, y=1").hasValue("[x=1], y=1");
  }

  @Test
  public void span_placeholderAtEnd() {
    assertPatternMatch(StringFormat.span("name: {name}"), "name: 1").hasValue("[name: 1]");
  }

  @Test
  public void span_placeholdersNextToEachOther() {
    assertPatternMatch(StringFormat.span("{key}{value}"), "k:v").hasValue("[k:v]");
    assertPatternMatch(StringFormat.span("{{key}{value}}"), "{k:v}").hasValue("[{k:v}]");
  }

  @Test
  public void testToString(@TestParameter Mode mode) {
    assertThat(mode.formatOf("projects/{project}/locations/{location}").toString())
        .isEqualTo("projects/{project}/locations/{location}");
  }

  @Test
  public void testNulls() throws Exception {
    new ClassSanityTester().testNulls(StringFormat.class);
    new ClassSanityTester().forAllPublicStaticMethods(StringFormat.class).testNulls();
  }

  private static OptionalSubject assertPatternMatch(Substring.Pattern pattern, String input) {
    return assertWithMessage(pattern.toString())
        .about(OptionalSubject.optionals())
        .that(pattern.in(input).map(m -> m.before() + "[" + m + "]" + m.after()));
  }

  private enum Mode {
    ANY_MATCH {
      @Override
      StringFormat formatOf(@CompileTimeConstant String format) {
        return new StringFormat(format);
      }
    },
    NO_EMPTY_MATCH {
      @Override
      StringFormat formatOf(@CompileTimeConstant String format) {
        return StringFormat.strict(format, c -> true);
      }
    };

    abstract StringFormat formatOf(@CompileTimeConstant String format);
  }
}

