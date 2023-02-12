package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.ClassSanityTester;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringFormatTest {

  @Test
  public void parse_noPlaceholder() {
    StringFormat template = new StringFormat("this is literal");
    assertThat(template.parse("this is literal").get()).isEmpty();
  }

  @Test
  public void parse_onlyPlaceholder() {
    StringFormat template = new StringFormat("{v}");
    assertThat(template.parse("Hello Tom!", v -> v)).hasValue("Hello Tom!");
  }

  @Test
  public void parse_singlePlaceholder() {
    StringFormat template = new StringFormat("Hello {v}!");
    assertThat(template.parse("Hello Tom!", v -> v)).hasValue("Tom");
  }

  @Test
  public void parse_multiplePlaceholders() {
    StringFormat template = new StringFormat("Hello {person}, welcome to {place}!");
    assertThat(
            template.parse("Hello Gandolf, welcome to Isengard!").get().stream()
                .map(Object::toString))
        .containsExactly("Gandolf", "Isengard")
        .inOrder();
  }

  @Test
  public void parse_multiplePlaceholdersWithSameName() {
    StringFormat template = new StringFormat("Hello {name} and {name}!");
    assertThat(template.parse("Hello Gandolf and Aragon!").get().stream().map(Object::toString))
        .containsExactly("Gandolf", "Aragon")
        .inOrder();
  }

  @Test
  public void parse_emptyPlaceholderValue() {
    StringFormat template = new StringFormat("Hello {what}!");
    assertThat(template.parse("Hello !").get().stream().map(Substring.Match::toString))
        .containsExactly("");
  }

  @Test
  public void parse_preludeFailsToMatch() {
    StringFormat template = new StringFormat("Hello {person}!");
    assertThat(template.parse("Hell Tom!")).isEmpty();
    assertThat(template.parse("elloh Tom!")).isEmpty();
    assertThat(template.parse(" Hello Tom!")).isEmpty();
  }

  @Test
  public void parse_postludeFailsToMatch() {
    StringFormat template = new StringFormat("Hello {person}!");
    assertThat(template.parse("Hello Tom?")).isEmpty();
    assertThat(template.parse("Hello Tom! ")).isEmpty();
    assertThat(template.parse("Hello Tom")).isEmpty();
  }

  @Test
  public void parse_nonEmptyTemplate_emptyInput() {
    StringFormat template = new StringFormat("Hello {person}!");
    assertThat(template.parse("")).isEmpty();
  }

  @Test
  public void parse_emptyTemplate_nonEmptyInput() {
    assertThat(new StringFormat("").parse(".")).isEmpty();
  }

  @Test
  public void parse_emptyTemplate_emptyInput() {
    assertThat(new StringFormat("").parse("")).hasValue(ImmutableList.of());
  }

  @Test
  public void parse_withOneArgLambda() {
    assertThat(new StringFormat("1 is {what}").parse("1 is one", Object::toString)).hasValue("one");
  }

  @Test
  public void parse_withOneArgLambda_lambdaReturnsNull() {
    assertThat(new StringFormat("1 is {what}").parse("1 is one", x -> null)).isEmpty();
  }

  @Test
  public void parse_withTwoArgsLambda() {
    assertThat(
            new StringFormat("1 is {what}, 2 is {what}")
                .parse("1 is one, 2 is two", String::concat))
        .hasValue("onetwo");
  }

  @Test
  public void parse_withTwoArgsLambda_lambdaReturnsNull() {
    assertThat(new StringFormat("1 is {x}, 2 is {y}").parse("1 is one, 2 is two", (x, y) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withThreeArgsLambda() {
    assertThat(
            new StringFormat("1 is {x}, 2 is {y}, 3 is {z}")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> x + "," + y + "," + z))
        .hasValue("one,two,three");
  }

  @Test
  public void parse_withThreeArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {x}, 2 is {y}, 3 is {z}")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withFourArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> a + b + c + d))
        .hasValue("onetwothreefour");
  }

  @Test
  public void parse_withFourArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withFiveArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue("onetwothreefourfive");
  }

  @Test
  public void parse_withFiveArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> null))
        .isEmpty();
  }

  @Test
  public void parse_withSixArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue("onetwothreefourfivesix");
  }

  @Test
  public void parse_withSixArgsLambd_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> null))
        .isEmpty();
  }

  @Test
  public void twoPlaceholdersNextToEachOther_invalid() {
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("{a}{b}"));
  }

  @Test
  public void parse_partiallyOverlappingTemplate() {
    assertThat(new StringFormat("xyz{?}xzz").parse("xyzzxxzz", v -> v)).hasValue("zx");
  }

  @Test
  public void parse_throwsUponIncorrectNumLambdaParameters() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {a} or {b}").parse("bad input", Object::toString));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", String::concat));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", (a, b, c) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", (a, b, c, d) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", (a, b, c, d, e) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", (a, b, c, d, e, f) -> a));
  }

  @Test
  public void scan_singlePlaceholder() {
    assertThat(new StringFormat("[id={d}]").scan("id=1", v -> v)).isEmpty();
    assertThat(new StringFormat("[id={d}]").scan("[id=foo]", v -> v)).containsExactly("foo");
    assertThat(new StringFormat("[id={d}]").scan("[id=foo][id=bar]", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholder_emptyInput() {
    assertThat(new StringFormat("[id={d}]").scan("", v -> v)).isEmpty();
  }

  @Test
  public void scan_singlePlaceholder_nullFilteredOut() {
    assertThat(new StringFormat("[id={d}]").scan("[id=foo]", v -> null)).isEmpty();
    assertThat(new StringFormat("[id={d}]").scan("[id=foo][id=]", v -> v.isEmpty() ? null : v))
        .containsExactly("foo");
  }

  @Test
  public void scan_emptyPlaceholderValue() {
    assertThat(new StringFormat("/{a}/{b}/").scan("/foo/bar//zoo//", (a, b) -> a + b))
        .containsExactly("foobar", "zoo")
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders() {
    assertThat(new StringFormat("[id={d}, name={e}]").scan("id=1", (id, name) -> id + "," + name))
        .isEmpty();
    assertThat(
            new StringFormat("[id={d}, name={e}]")
                .scan("[id=foo, name=bar]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar");
    assertThat(
            new StringFormat("[id={d}, name={e}]")
                .scan("[id=foo, name=bar][id=zoo, name=boo]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar", "zoo,boo")
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[id={d}, name={e}]").scan("[id=foo, name=bar]", (id, name) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[id={d}, name={e}]")
                .scan(
                    "[id=, name=bar][id=zoo, name=boo]",
                    (id, name) -> id.isEmpty() ? null : id + "," + name))
        .containsExactly("zoo,boo");
  }

  @Test
  public void scan_twoPlaceholders_emptyInput() {
    assertThat(new StringFormat("[id={d}, name={e}]").scan("", (id, name) -> id + "," + name))
        .isEmpty();
  }

  @Test
  public void scan_threePlaceholders() {
    assertThat(new StringFormat("[a={a}, b={b}, c={c}]").scan("a=1,b=2,c", (a, b, c) -> a + b + c))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .scan("[a=1, b=2, c=3]", (a, b, c) -> a + b + c))
        .containsExactly("123");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .scan("[a=1, b=2, c=3] [a=x, b=y, c=z]", (a, b, c) -> a + b + c))
        .containsExactly("123", "xyz")
        .inOrder();
  }

  @Test
  public void scan_threePlaceholders_nullFilteredOut() {
    assertThat(new StringFormat("[a={a}, b={b}, c={c}]").scan("[a=1, b=2, c=3]", (a, b, c) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .scan(
                    "[a=1, b=2, c=3] [a=x, b=, c=z]", (a, b, c) -> b.isEmpty() ? null : a + b + c))
        .containsExactly("123");
  }

  @Test
  public void scan_threePlaceholders_emptyInput() {
    assertThat(new StringFormat("[a={a}, b={b}, c={c}]").scan("", (a, b, c) -> a + b + c))
        .isEmpty();
  }

  @Test
  public void scan_fourPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .scan("a=1,b=2,c=3,d", (a, b, c, d) -> a + b + c + d))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .scan("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> a + b + c + d))
        .containsExactly("1234");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .scan("[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=w]", (a, b, c, d) -> a + b + c + d))
        .containsExactly("1234", "zyxw")
        .inOrder();
  }

  @Test
  public void scan_fourPlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .scan("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .scan(
                    "[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=]",
                    (a, b, c, d) -> d.isEmpty() ? null : a + b + c + d))
        .containsExactly("1234")
        .inOrder();
  }

  @Test
  public void scan_fourPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .scan("", (a, b, c, d) -> a + b + c + d))
        .isEmpty();
  }

  @Test
  public void scan_fivePlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("a=1,b=2,c=3,d", (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> a + b + c + d + e))
        .containsExactly("12345");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]",
                    (a, b, c, d, e) -> a + b + c + d + e))
        .containsExactly("12345", "zyxwv")
        .inOrder();
  }

  @Test
  public void scan_fivePlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan(
                    "[a=, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]",
                    (a, b, c, d, e) -> a.isEmpty() ? null : a + b + c + d + e))
        .containsExactly("zyxwv");
  }

  @Test
  public void scan_fivePlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .scan("", (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
  }

  @Test
  public void scan_sixPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan("a=1, b=2, c=3, d=4, e=5, f", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6]", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .containsExactly("123456");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .containsExactly("123456", "zyxwvu")
        .inOrder();
  }

  @Test
  public void scan_sixPlaceholders_nullFiltered() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan("[a=1, b=2, c=3, d=4, e=5, f=6]", (a, b, c, d, e, f) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan(
                    "[a=1, b=2, c=3, d=, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]",
                    (a, b, c, d, e, f) -> d.isEmpty() ? null : a + b + c + d + e + f))
        .containsExactly("zyxwvu")
        .inOrder();
  }

  @Test
  public void scan_sixPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .scan("", (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
  }

  @Test
  public void scan_suffixConsumed() {
    assertThat(new StringFormat("/{a}/{b}/").scan("/foo/bar//zoo/boo/", (a, b) -> a + b))
        .containsExactly("foobar", "zooboo")
        .inOrder();
  }

  @Test
  public void scan_skipsNonMatchingCharsFromBeginning() {
    assertThat(new StringFormat("[id={d}]").scan("whatever [id=foo] [id=bar]", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k={key}, v={value}]")
                .scan("whatever [k=one, v=1] [k=two, v=2]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_skipsNonMatchingCharsFromMiddle() {
    assertThat(new StringFormat("[id={d}]").scan("[id=foo] [id=bar]", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k={key}, v={value}]")
                .scan("[k=one, v=1] and then [k=two, v=2]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_skipsNonMatchingCharsFromEnd() {
    assertThat(new StringFormat("[id={d}]").scan("[id=foo] [id=bar];[id=baz", v -> v))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k={key}, v={value}]")
                .scan("[k=one, v=1][k=two, v=2];[k=three,v]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholderOnly() {
    assertThat(new StringFormat("{s}").scan("whatever", s -> s)).containsExactly("whatever");
    assertThat(new StringFormat("{s}").scan("", s -> s)).containsExactly("");
  }

  @Test
  public void scan_placeholderAtBeginning() {
    assertThat(new StringFormat("{s} ").scan("a ", s -> s)).containsExactly("a");
    assertThat(new StringFormat("{s} ").scan("abc d ", s -> s))
        .containsExactly("abc", "d")
        .inOrder();
  }

  @Test
  public void scan_placeholderAtEnd() {
    assertThat(new StringFormat(" {s}").scan(" a", s -> s)).containsExactly("a");
    assertThat(new StringFormat(" {s}").scan(" abc d ", s -> s))
        .containsExactly("abc d ")
        .inOrder();
    assertThat(new StringFormat(" {a} {b}").scan(" abc d ", (a, b) -> a + "," + b))
        .containsExactly("abc,d ")
        .inOrder();
  }

  @Test
  public void testToString() {
    assertThat(new StringFormat("projects/{project}/locations/{location}").toString())
        .isEqualTo("projects/{project}/locations/{location}");
  }

  @Test
  public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.RepeatingPattern.class, Substring.first("%s").repeatedly())
        .testNulls(StringFormat.class);
  }
}
