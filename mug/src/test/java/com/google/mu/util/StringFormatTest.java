package com.google.mu.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.ClassSanityTester;
import com.google.common.truth.OptionalSubject;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.mu.util.stream.BiStream;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringFormatTest {
  @Test
  public void parse_noPlaceholder() {
    StringFormat format = new StringFormat("this is literal");
    assertThat(format.parse("this is literal").get()).isEmpty();
  }

  @Test
  public void parse_emptyCurlyBrace_doesNotCountAsPlaceholder() {
    StringFormat format = new StringFormat("curly brace: {}");
    assertThat(format.parse("curly brace: {}").get()).isEmpty();
  }

  @Test
  public void parse_onlyPlaceholder() {
    StringFormat format = new StringFormat("{v}");
    assertThat(format.parse("Hello Tom!", v -> v)).hasValue("Hello Tom!");
    assertThat(format.parseOrThrow("Hello Tom!", (String v) -> v)).isEqualTo("Hello Tom!");
  }

  @Test
  public void parse_onlyEllipsis() {
    StringFormat format = new StringFormat("{...}");
    assertThat(format.parse("Hello Tom!")).hasValue(ImmutableList.of());
  }

  @Test
  public void replaceAllFrom_placeholderOnly_emptyInput() {
    assertThat(new StringFormat("{x}").replaceAllFrom("", x -> "")).isEqualTo("");
    assertThat(new StringFormat("{x}").replaceAllFrom("", x -> "foo")).isEqualTo("foo");
  }

  @Test
  public void replaceAllFrom_placeholderOnly_nonEmptyInput() {
    assertThat(new StringFormat("{x}").replaceAllFrom("x", x -> "")).isEqualTo("");
    assertThat(new StringFormat("{x}").replaceAllFrom("x", x -> "foo")).isEqualTo("foo");
  }

  @Test
  public void replaceAllFrom_placeholderAtBeginning_found() {
    assertThat(new StringFormat("{x}/").replaceAllFrom("/.", x -> "")).isEqualTo(".");
    assertThat(new StringFormat("{x}/").replaceAllFrom("/.", x -> "foo")).isEqualTo("foo.");
  }

  @Test
  public void replaceAllFrom_placeholderAtBeginning_notFound() {
    assertThat(new StringFormat("{x}/").replaceAllFrom("", x -> "")).isEqualTo("");
    assertThat(new StringFormat("{x}/").replaceAllFrom("..", x -> "")).isEqualTo("..");
  }

  @Test
  public void replaceAllFrom_placeholderAtEnd_found() {
    assertThat(new StringFormat(":{x}").replaceAllFrom(":.?", x -> "")).isEqualTo("");
    assertThat(new StringFormat(":{x}").replaceAllFrom(":.?", x -> "foo")).isEqualTo("foo");
    assertThat(new StringFormat(":{x}").replaceAllFrom(".:", x -> "foo")).isEqualTo(".foo");
  }

  @Test
  public void replaceAllFrom_placeholderAtEnd_notFound() {
    assertThat(new StringFormat(":{x}").replaceAllFrom("", x -> "")).isEqualTo("");
    assertThat(new StringFormat(":{x}").replaceAllFrom("..", x -> "")).isEqualTo("..");
  }

  @Test
  public void replaceAllFrom_noMatch_returnsInputStringAsIs() {
    String input = "foo";
    assertThat(new StringFormat(":{x}").replaceAllFrom(input, x -> "")).isSameInstanceAs(input);
  }

  @Test
  public void parse_singlePlaceholder() {
    StringFormat format = new StringFormat("Hello {v}!");
    assertThat(format.parse("Hello Tom!", v -> v)).hasValue("Tom");
    assertThat(format.parseOrThrow("Hello Tom!", (String v) -> v)).isEqualTo("Tom");
  }

  @Test
  public void parse_singlePlaceholder_withEllipsis() {
    StringFormat format = new StringFormat("Hello {...}!");
    assertThat(format.parse("Hello Tom!")).hasValue(ImmutableList.of());
  }

  @Test
  public void parse_multiplePlaceholders() {
    StringFormat format = new StringFormat("Hello {person}, welcome to {place}!");
    assertThat(
            format.parse("Hello Gandolf, welcome to Isengard!").get().stream()
                .map(Object::toString))
        .containsExactly("Gandolf", "Isengard")
        .inOrder();
  }

  @Test
  public void parse_multiplePlaceholders_withEllipsis() {
    StringFormat format = new StringFormat("Hello {...}, welcome to {place}!");
    assertThat(
            format.parse("Hello Gandolf, welcome to Isengard!").get().stream()
                .map(Object::toString))
        .containsExactly("Isengard");
  }

  @Test
  public void parse_multiplePlaceholders_withEllipsis_usingLambda() {
    StringFormat format = new StringFormat("Hello {...}, welcome to {place}!");
    assertThat(format.parse("Hello Gandolf, welcome to Isengard!", p -> p)).hasValue("Isengard");
    assertThat(format.parseOrThrow("Hello Gandolf, welcome to Isengard!", (String p) -> p))
        .isEqualTo("Isengard");
  }

  @Test
  public void parse_multiplePlaceholdersWithSameName() {
    StringFormat format = new StringFormat("Hello {name} and {name}!");
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
  public void parse_preludeFailsToMatch() {
    StringFormat format = new StringFormat("Hello {person}!");
    assertThat(format.parse("Hell Tom!")).isEmpty();
    assertThat(format.parse("elloh Tom!")).isEmpty();
    assertThat(format.parse(" Hello Tom!")).isEmpty();
  }

  @Test
  public void parse_postludeFailsToMatch() {
    StringFormat format = new StringFormat("Hello {person}!");
    assertThat(format.parse("Hello Tom?")).isEmpty();
    assertThat(format.parse("Hello Tom! ")).isEmpty();
    assertThat(format.parse("Hello Tom")).isEmpty();
  }

  @Test
  public void parse_nonEmptyTemplate_emptyInput() {
    StringFormat format = new StringFormat("Hello {person}!");
    assertThat(format.parse("")).isEmpty();
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
  public void parse_withOneArgLambda_emptyInput() {
    assertThat(new StringFormat("1 is {what}").parse("", Object::toString)).isEmpty();
  }

  @Test
  public void parse_withOneArgLambda_lambdaReturnsNull() {
    assertThat(new StringFormat("1 is {what}").parse("1 is one", w -> null)).isEmpty();
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> new StringFormat("1 is {what}").parseOrThrow("1 is one", w -> null));
    assertThat(thrown).hasMessageThat().contains("format string '1 is {what}'");
    assertThat(thrown).hasMessageThat().contains("input '1 is one'");
  }

  @Test
  public void parse_withTwoArgsLambda() {
    assertThat(
            new StringFormat("1 is {what}, 2 is {what}")
                .parse("1 is one, 2 is two", String::concat))
        .hasValue("onetwo");
    assertThat(
            new StringFormat("1 is {what}, 2 is {what}")
                .parseOrThrow("1 is one, 2 is two", String::concat))
        .isEqualTo("onetwo");
  }

  @Test
  public void parse_withTwoArgsLambda_emptyInput() {
    assertThat(new StringFormat("1 is {x}, 2 is {y}").parse("", (x, y) -> null)).isEmpty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new StringFormat("1 is {x}, 2 is {y}").parseOrThrow("", (x, y) -> null));
  }

  @Test
  public void parse_withTwoArgsLambda_lambdaReturnsNull() {
    assertThat(new StringFormat("1 is {x}, 2 is {y}").parse("1 is one, 2 is two", (x, y) -> null))
        .isEmpty();
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () ->
                new StringFormat("1 is {x}, 2 is {y}")
                    .parseOrThrow("1 is one, 2 is two", (x, y) -> null));
    assertThat(thrown).hasMessageThat().contains("format string '1 is {x}, 2 is {y}'");
    assertThat(thrown).hasMessageThat().contains("input '1 is one, 2 is two'");
  }

  @Test
  public void parse_withThreeArgsLambda() {
    assertThat(
            new StringFormat("1 is {x}, 2 is {y}, 3 is {z}")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> x + "," + y + "," + z))
        .hasValue("one,two,three");
    assertThat(
            new StringFormat("1 is {x}, 2 is {y}, 3 is {z}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three",
                    (String x, String y, String z) -> x + "," + y + "," + z))
        .isEqualTo("one,two,three");
  }

  @Test
  public void parse_withThreeArgsLambda_emptyInput() {
    assertThat(new StringFormat("1 is {x}, 2 is {y}, 3 is {z}").parse("", (x, y, z) -> null))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {x}, 2 is {y}, 3 is {z}").parseOrThrow("", (x, y, z) -> null));
  }

  @Test
  public void parse_withThreeArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {x}, 2 is {y}, 3 is {z}")
                .parse("1 is one, 2 is two, 3 is three", (x, y, z) -> null))
        .isEmpty();
    assertThrows(
        NullPointerException.class,
        () ->
            new StringFormat("1 is {x}, 2 is {y}, 3 is {z}")
                .parseOrThrow("1 is one, 2 is two, 3 is three", (x, y, z) -> null));
  }

  @Test
  public void parse_withFourArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> a + b + c + d))
        .hasValue("onetwothreefour");
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four",
                    (String a, String b, String c, String d) -> a + b + c + d))
        .isEqualTo("onetwothreefour");
  }

  @Test
  public void parse_withFourArgsLambda_emptyInput() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("", (a, b, c, d) -> null))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parseOrThrow("", (a, b, c, d) -> null));
  }

  @Test
  public void parse_withFourArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parse("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> null))
        .isEmpty();
    assertThrows(
        NullPointerException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}")
                .parseOrThrow("1 is one, 2 is two, 3 is three, 4 is four", (a, b, c, d) -> null));
  }

  @Test
  public void parse_withFiveArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue("onetwothreefourfive");
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (String a, String b, String c, String d, String e) -> a + b + c + d + e))
        .isEqualTo("onetwothreefourfive");
  }

  @Test
  public void parse_withFiveArgsLambda_emptyInput() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse("", (a, b, c, d, e) -> null))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parseOrThrow("", (a, b, c, d, e) -> null));
  }

  @Test
  public void parse_withFiveArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> null))
        .isEmpty();
    assertThrows(
        NullPointerException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five",
                    (a, b, c, d, e) -> null));
  }

  @Test
  public void parse_withSixArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue("onetwothreefourfivesix");
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .<String>parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEqualTo("onetwothreefourfivesix");
  }

  @Test
  public void parse_withSixArgsLambda_emptyInput() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse("", (a, b, c, d, e, f) -> null))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parseOrThrow("", (a, b, c, d, e, f) -> null));
  }

  @Test
  public void parse_withSixArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> null))
        .isEmpty();
    assertThrows(
        NullPointerException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six",
                    (a, b, c, d, e, f) -> null));
  }

  @Test
  public void parse_withSevenArgsLambda() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven",
                    (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .hasValue("onetwothreefourfivesixseven");
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}")
                .<String>parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven",
                    (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .isEqualTo("onetwothreefourfivesixseven");
  }

  @Test
  public void parse_withSevenArgsLambda_emptyInput() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}")
                .parse("", (a, b, c, d, e, f, g) -> null))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}")
                .parseOrThrow("", (a, b, c, d, e, f, g) -> null));
  }

  @Test
  public void parse_withSevenArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven",
                    (a, b, c, d, e, f, g) -> null))
        .isEmpty();
    assertThrows(
        NullPointerException.class,
        () ->
            new StringFormat("1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven",
                    (a, b, c, d, e, f, g) -> null));
  }

  @Test
  public void parse_withEightArgsLambda() {
    assertThat(
            new StringFormat(
                    "1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}, 8 is"
                        + " {h}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven, 8"
                        + " is eight",
                    (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .hasValue("onetwothreefourfivesixseveneight");
    assertThat(
            new StringFormat(
                    "1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}, 8 is"
                        + " {h}")
                .<String>parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven, 8"
                        + " is eight",
                    (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .isEqualTo("onetwothreefourfivesixseveneight");
  }

  @Test
  public void parse_withEightArgsLambda_emptyInput() {
    assertThat(
            new StringFormat(
                    "1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}, 8 is"
                        + " {h}")
                .parse("", (a, b, c, d, e, f, g, h) -> null))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat(
                    "1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}, 8 is"
                        + " {h}")
                .parseOrThrow("", (a, b, c, d, e, f, g, h) -> null));
  }

  @Test
  public void parse_withEightArgsLambda_lambdaReturnsNull() {
    assertThat(
            new StringFormat(
                    "1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}, 8 is"
                        + " {h}")
                .parse(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven, 8"
                        + " is eight",
                    (a, b, c, d, e, f, g, h) -> null))
        .isEmpty();
    assertThrows(
        NullPointerException.class,
        () ->
            new StringFormat(
                    "1 is {a}, 2 is {b}, 3 is {c}, 4 is {d}, 5 is {e}, 6 is {f}, 7 is {g}, 8 is"
                        + " {h}")
                .parseOrThrow(
                    "1 is one, 2 is two, 3 is three, 4 is four, 5 is five, 6 is six, 7 is seven, 8"
                        + " is eight",
                    (a, b, c, d, e, f, g, h) -> null));
  }

  @Test
  public void parse_placeholderInsideCurlyBraces() {
    StringFormat format = new StringFormat("{key={key}, value={value}}");
    assertThat(format.parse("{key=one, value=1}", (key, value) -> key + ":" + value))
        .hasValue("one:1");
  }

  @Test
  public void parse_multipleCurlyBracedPlaceholderGroups() {
    StringFormat format = new StringFormat("{key={key}}{value={value}}");
    assertThat(format.parse("{key=one}{value=1}", (key, value) -> key + ":" + value))
        .hasValue("one:1");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void parse_placeholderInsideMultipleCurlyBraces() {
    StringFormat format = new StringFormat("{test: {{key={key}, value={value}}}}");
    assertThat(format.parse("{test: {{key=one, value=1}}}", (key, value) -> key + ":" + value))
        .hasValue("one:1");
  }

  @Test
  @SuppressWarnings("StringUnformatArgsCheck")
  public void twoPlaceholdersNextToEachOther_invalid() {
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("{a}{b}").parse("ab"));
  }

  @Test
  public void parse_partiallyOverlappingTemplate() {
    assertThat(new StringFormat("xyz{v}xzz").parse("xyzzxxzz", v -> v)).hasValue("zx");
  }

  @Test
  @SuppressWarnings("StringUnformatArgsCheck")
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
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", (a, b, c, d, e, f, g) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").parse("bad input", (a, b, c, d, e, f, g, h) -> a));
  }

  @Test
  public void parseGreedy_with2Placeholders() {
    StringFormat format = new StringFormat("{parent}/{child}");
    assertThat(
            format.parseGreedy("foo/bar/c/d", (parent, child) -> ImmutableList.of(parent, child)))
        .hasValue(ImmutableList.of("foo/bar/c", "d"));
    assertThat(format.parseGreedy("a/b", (parent, child) -> ImmutableList.of(parent, child)))
        .hasValue(ImmutableList.of("a", "b"));
    assertThat(format.parseGreedy("a/b", (parent, child) -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with1PlaceholderAndElipsisAtBeginning() {
    StringFormat format = new StringFormat("{...}/{child}");
    assertThat(format.parseGreedy("foo/bar/c/d", child -> child)).hasValue("d");
  }

  @Test
  public void parseGreedy_with1PlaceholderAndElipsisAtEnd() {
    StringFormat format = new StringFormat("{parent}/{...}");
    assertThat(format.parseGreedy("foo/bar/c/d", parent -> parent)).hasValue("foo/bar/c");
    assertThat(format.parseGreedy("foo/bar/c/d", parent -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with2PlaceholdersAndElipsisAtBeginning() {
    StringFormat format = new StringFormat("{...}/{parent}/{child}");
    assertThat(
            format.parseGreedy("foo/bar/c/d", (parent, child) -> ImmutableList.of(parent, child)))
        .hasValue(ImmutableList.of("c", "d"));
    assertThat(format.parseGreedy("foo/bar/c/d", (parent, child) -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with2PlaceholdersAndElipsisAtEnd() {
    StringFormat format = new StringFormat("{parent}/{child}/{...}");
    assertThat(
            format.parseGreedy("foo/bar/c/d", (parent, child) -> ImmutableList.of(parent, child)))
        .hasValue(ImmutableList.of("foo/bar", "c"));
    assertThat(format.parseGreedy("foo/bar/c/d", (parent, child) -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with3Placeholders() {
    StringFormat format = new StringFormat("{home}/{parent}/{child}");
    assertThat(
            format.parseGreedy(
                "foo/bar/c/d", (home, parent, child) -> ImmutableList.of(home, parent, child)))
        .hasValue(ImmutableList.of("foo/bar", "c", "d"));
    assertThat(
            format.parseGreedy(
                "foo/bar/c/", (home, parent, child) -> ImmutableList.of(home, parent, child)))
        .hasValue(ImmutableList.of("foo/bar", "c", ""));
    assertThat(
            format.parseGreedy(
                "/bar/c/d", (home, parent, child) -> ImmutableList.of(home, parent, child)))
        .hasValue(ImmutableList.of("/bar", "c", "d"));
    assertThat(
            format.parseGreedy(
                "///", (home, parent, child) -> ImmutableList.of(home, parent, child)))
        .hasValue(ImmutableList.of("/", "", ""));
    assertThat(
            format.parseGreedy(
                "a/b/c", (home, parent, child) -> ImmutableList.of(home, parent, child)))
        .hasValue(ImmutableList.of("a", "b", "c"));
    assertThat(format.parseGreedy("a/b/c", (home, parent, child) -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with4Placeholders() {
    StringFormat format = new StringFormat("{a}/{b}/{c}/{d}");
    assertThat(format.parseGreedy("x/a/b/c/d", (a, b, c, d) -> ImmutableList.of(a, b, c, d)))
        .hasValue(ImmutableList.of("x/a", "b", "c", "d"));
    assertThat(format.parseGreedy("x/a/b/c/d", (a, b, c, d) -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with4PlaceholdersAndElipsis() {
    StringFormat format = new StringFormat("{a}/{b}/{...}/{c}/{d}");
    assertThat(format.parseGreedy("0/a/b/x/c/d", (a, b, c, d) -> ImmutableList.of(a, b, c, d)))
        .hasValue(ImmutableList.of("0/a", "b", "c", "d"));
  }

  @Test
  public void parseGreedy_with5Placeholders() {
    StringFormat format = new StringFormat("{a}/{b}/{c}/{d}/{e}");
    assertThat(
            format.parseGreedy("x/a/b/c/d/e", (a, b, c, d, e) -> ImmutableList.of(a, b, c, d, e)))
        .hasValue(ImmutableList.of("x/a", "b", "c", "d", "e"));
    assertThat(format.parseGreedy("x/a/b/c/d/e", (a, b, c, d, e) -> null)).isEmpty();
  }

  @Test
  public void parseGreedy_with5PlaceholdersAndElipsis() {
    StringFormat format = new StringFormat("{a}/{b}/{...}/{c}/{d}/{e}");
    assertThat(
            format.parseGreedy("0/a/b/x/c/d/e", (a, b, c, d, e) -> ImmutableList.of(a, b, c, d, e)))
        .hasValue(ImmutableList.of("0/a", "b", "c", "d", "e"));
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
  public void scan_emptyTemplate_emptyInput() {
    assertThat(new StringFormat("").scan("")).containsExactly(ImmutableList.of());
  }

  @Test
  public void scan_singlePlaceholder() {
    assertThat(new StringFormat("[id={id}]").scan("id=1", id -> id)).isEmpty();
    assertThat(new StringFormat("[id={id}]").scan("[id=foo]", id -> id)).containsExactly("foo");
    assertThat(new StringFormat("[id={id}]").scan("[id=foo][id=bar]", id -> id))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholder_withEllipsis() {
    assertThat(new StringFormat("[id={...}]").scan("id=1")).isEmpty();
    assertThat(new StringFormat("[id={...}]").scan("[id=foo]")).containsExactly(ImmutableList.of());
    assertThat(new StringFormat("[id={...}]").scan("[id=foo][id=bar]"))
        .containsExactly(ImmutableList.of(), ImmutableList.of())
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholder_emptyInput() {
    assertThat(new StringFormat("[id={id}]").scan("", id -> id)).isEmpty();
  }

  @Test
  public void scan_singlePlaceholder_nullFilteredOut() {
    assertThat(new StringFormat("[id={id}]").scan("[id=foo]", id -> null)).isEmpty();
    assertThat(new StringFormat("[id={id}]").scan("[id=foo][id=]", id -> id.isEmpty() ? null : id))
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
    assertThat(
            new StringFormat("[id={id}, name={name}]").scan("id=1", (id, name) -> id + "," + name))
        .isEmpty();
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .scan("[id=foo, name=bar]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar");
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .scan("[id=foo, name=bar][id=zoo, name=boo]", (id, name) -> id + "," + name))
        .containsExactly("foo,bar", "zoo,boo")
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_withEllipsis() {
    assertThat(
            new StringFormat("[id={...}, name={name}]")
                .scan("[id=foo, name=bar]")
                .map(l -> l.stream().map(Substring.Match::toString).collect(toImmutableList())))
        .containsExactly(ImmutableList.of("bar"));
    assertThat(
            new StringFormat("[id={...}, name={name}]")
                .scan("[id=, name=bar][id=zoo, name=boo]")
                .map(l -> l.stream().map(Substring.Match::toString).collect(toImmutableList())))
        .containsExactly(ImmutableList.of("bar"), ImmutableList.of("boo"))
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_withEllipsis_usingLambda() {
    assertThat(new StringFormat("[id={id}, name={...}]").scan("[id=foo, name=bar]", id -> id))
        .containsExactly("foo");
    assertThat(
            new StringFormat("[id={...}, name={name}]")
                .scan("[id=, name=bar][id=zoo, name=boo]", name -> name))
        .containsExactly("bar", "boo")
        .inOrder();
  }

  @Test
  public void scan_twoPlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .scan("[id=foo, name=bar]", (id, name) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .scan(
                    "[id=, name=bar][id=zoo, name=boo]",
                    (id, name) -> id.isEmpty() ? null : id + "," + name))
        .containsExactly("zoo,boo");
  }

  @Test
  public void scan_twoPlaceholders_emptyInput() {
    assertThat(new StringFormat("[id={id}, name={name}]").scan("", (id, name) -> id + "," + name))
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
  public void scan_sevenPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .scan(
                    "a=1, b=2, c=3, d=4, e=5, f=6, g",
                    (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7]",
                    (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .containsExactly("1234567");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7] [a=z, b=y, c=x, d=w, e=v, f=u, g=t]",
                    (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .containsExactly("1234567", "zyxwvut")
        .inOrder();
  }

  @Test
  public void scan_sevenPlaceholders_nullFiltered() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .scan("[a=1, b=2, c=3, d=4, e=5, f=6, g=7]", (a, b, c, d, e, f, g) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .scan(
                    "[a=1, b=2, c=3, d=, e=5, f=6, g=7] [a=z, b=y, c=x, d=w, e=v, f=u, g=t]",
                    (a, b, c, d, e, f, g) -> d.isEmpty() ? null : a + b + c + d + e + f + g))
        .containsExactly("zyxwvut")
        .inOrder();
  }

  @Test
  public void scan_sevenPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .scan("", (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .isEmpty();
  }

  @Test
  public void scan_eightPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .scan(
                    "a=1, b=2, c=3, d=4, e=5, f=6, g=7, h",
                    (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8]",
                    (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .containsExactly("12345678");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .scan(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8] [a=z, b=y, c=x, d=w, e=v, f=u, g=t,"
                        + " h=s]",
                    (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .containsExactly("12345678", "zyxwvuts")
        .inOrder();
  }

  @Test
  public void scan_eightPlaceholders_nullFiltered() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .scan("[a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8]", (a, b, c, d, e, f, g, h) -> null))
        .isEmpty();
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .scan(
                    "[a=1, b=2, c=3, d=, e=5, f=6, g=7, h=8] [a=z, b=y, c=x, d=w, e=v, f=u, g=t,"
                        + " h=s]",
                    (a, b, c, d, e, f, g, h) -> d.isEmpty() ? null : a + b + c + d + e + f + g + h))
        .containsExactly("zyxwvuts")
        .inOrder();
  }

  @Test
  public void scan_eightPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .scan("", (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .isEmpty();
  }

  @Test
  @SuppressWarnings("StringUnformatArgsCheck")
  public void replaceAllFrom_emptyTemplate_nonEmptyInput() {
    assertThat(new StringFormat("").replaceAllMatches(".", x -> "foo")).isEqualTo("foo.foo");
    assertThat(new StringFormat("").replaceAllMatches("bar", x -> "foo"))
        .isEqualTo("foobfooafoorfoo");
  }

  @Test
  @SuppressWarnings("StringUnformatArgsCheck")
  public void replaceAllFrom_emptyTemplate_emptyInput() {
    assertThat(new StringFormat("").replaceAllMatches("", x -> "foo")).isEqualTo("foo");
  }

  @Test
  public void replaceAllFrom_singlePlaceholder() {
    assertThat(new StringFormat("[id={id}]").replaceAllFrom("id=1", id -> id)).isEqualTo("id=1");
    assertThat(new StringFormat("[id={id}]").replaceAllFrom("[id=foo]", id -> "found: " + id))
        .isEqualTo("found: foo");
    assertThat(new StringFormat("[id={id}]").replaceAllFrom("[id=foo],[id=bar]", id -> id))
        .isEqualTo("foo,bar");
    assertThat(
            new StringFormat("[id={id}]").replaceAllFrom("[id=foo], [id=bar].", id -> "got: " + id))
        .isEqualTo("got: foo, got: bar.");
  }

  @Test
  @SuppressWarnings("StringUnformatArgsCheck")
  public void replaceAllFrom_singlePlaceholder_withEllipsis() {
    assertThat(new StringFormat("[id={...}]").replaceAllMatches("id=1", x -> "x"))
        .isEqualTo("id=1");
    assertThat(new StringFormat("[id={...}]").replaceAllMatches("[id=foo]", x -> "x"))
        .isEqualTo("x");
    assertThat(new StringFormat("[id={...}]").replaceAllMatches("[id=foo][id=bar]", x -> "x"))
        .isEqualTo("xx");
  }

  @Test
  public void replaceAllFrom_singlePlaceholder_emptyInput() {
    assertThat(new StringFormat("[id={id}]").replaceAllFrom("", id -> id)).isEmpty();
  }

  @Test
  public void replaceAllFrom_singlePlaceholder_nullFilteredOut() {
    assertThat(new StringFormat("[id={id}]").replaceAllFrom("[id=foo]", id -> null))
        .isEqualTo("[id=foo]");
    assertThat(
            new StringFormat("[id={id}]")
                .replaceAllFrom("[id=foo][id=]", id -> id.isEmpty() ? null : id))
        .isEqualTo("foo[id=]");
  }

  @Test
  public void replaceAllFrom_emptyPlaceholderValue() {
    assertThat(
            new StringFormat("/{a}/{b}/").replaceAllFrom("/foo/bar//zoo//", (a, b) -> a + ":" + b))
        .isEqualTo("foo:barzoo:");
  }

  @Test
  public void replaceAllFrom_twoPlaceholders() {
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .replaceAllFrom("id=1", (id, name) -> id + "," + name))
        .isEqualTo("id=1");
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .replaceAllFrom("[id=foo, name=bar]", (id, name) -> id + "," + name))
        .isEqualTo("foo,bar");
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .replaceAllFrom(
                    "[id=foo, name=bar]; [id=zoo, name=boo]", (id, name) -> id + "," + name))
        .isEqualTo("foo,bar; zoo,boo");
  }

  @Test
  public void replaceAllFrom_twoPlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .replaceAllFrom("[id=foo, name=bar]", (id, name) -> null))
        .isEqualTo("[id=foo, name=bar]");
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .replaceAllFrom(
                    "[id=, name=bar] [id=zoo, name=boo]",
                    (id, name) -> id.isEmpty() ? null : id + "," + name))
        .isEqualTo("[id=, name=bar] zoo,boo");
  }

  @Test
  public void replaceAllFrom_twoPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[id={id}, name={name}]")
                .replaceAllFrom("", (id, name) -> id + "," + name))
        .isEmpty();
  }

  @Test
  public void replaceAllFrom_twoPlaceholders_withEllipsis() {
    assertThat(
            new StringFormat("[id={...}, name={name}]")
                .replaceAllFrom("[id=foo, name=bar]", name -> "got " + name))
        .isEqualTo("got bar");
    assertThat(
            new StringFormat("[id={...}, name={name}]")
                .replaceAllFrom("I [id=, name=bar], [id=zoo, name=boo]", name -> "got " + name))
        .isEqualTo("I got bar, got boo");
  }

  @Test
  public void replaceAllFrom_twoPlaceholders_withEllipsis_usingLambda() {
    assertThat(
            new StringFormat("[id={id}, name={...}]")
                .replaceAllFrom("[id=foo, name=bar]", id -> id))
        .isEqualTo("foo");
    assertThat(
            new StringFormat("[id={...}, name={name}]")
                .replaceAllFrom("[id=, name=bar], [id=zoo, name=boo]", name -> name))
        .isEqualTo("bar, boo");
  }

  @Test
  public void replaceAllFrom_threePlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .replaceAllFrom("a=1,b=2,c", (a, b, c) -> "got " + a + b + c))
        .isEqualTo("a=1,b=2,c");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .replaceAllFrom("[a=1, b=2, c=3]", (a, b, c) -> "got " + a + b + c))
        .isEqualTo("got 123");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .replaceAllFrom("[a=1, b=2, c=3] [a=x, b=y, c=z]", (a, b, c) -> "got " + a + b + c))
        .isEqualTo("got 123 got xyz");
  }

  @Test
  public void replaceAllFrom_threePlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .replaceAllFrom("[a=1, b=2, c=3]", (a, b, c) -> null))
        .isEqualTo("[a=1, b=2, c=3]");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3] [a=x, b=, c=z]",
                    (a, b, c) -> b.isEmpty() ? null : "got " + a + b + c))
        .isEqualTo("got 123 [a=x, b=, c=z]");
  }

  @Test
  public void replaceAllFrom_threePlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}]")
                .replaceAllFrom("", (a, b, c) -> "got " + a + b + c))
        .isEmpty();
  }

  @Test
  public void replaceAllFrom_fourPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .replaceAllFrom("a=1,b=2,c=3,d", (a, b, c, d) -> "got " + a + b + c + d))
        .isEqualTo("a=1,b=2,c=3,d");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .replaceAllFrom("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> "got " + a + b + c + d))
        .isEqualTo("got 1234");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=w]",
                    (a, b, c, d) -> "got " + a + b + c + d))
        .isEqualTo("got 1234 got zyxw");
  }

  @Test
  public void replaceAllFrom_fourPlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .replaceAllFrom("[a=1, b=2, c=3, d=4]", (a, b, c, d) -> null))
        .isEqualTo("[a=1, b=2, c=3, d=4]");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4] [a=z, b=y, c=x, d=]",
                    (a, b, c, d) -> d.isEmpty() ? null : "got " + a + b + c + d))
        .isEqualTo("got 1234 [a=z, b=y, c=x, d=]");
  }

  @Test
  public void replaceAllFrom_fourPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}]")
                .replaceAllFrom("", (a, b, c, d) -> "got " + a + b + c + d))
        .isEmpty();
  }

  @Test
  public void replaceAllFrom_fivePlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .replaceAllFrom("a=1,b=2,c=3,d", (a, b, c, d, e) -> "got " + a + b + c + d + e))
        .isEqualTo("a=1,b=2,c=3,d");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> "got " + a + b + c + d + e))
        .isEqualTo("got 12345");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]",
                    (a, b, c, d, e) -> "got " + a + b + c + d + e))
        .isEqualTo("got 12345 got zyxwv");
  }

  @Test
  public void replaceAllFrom_fivePlaceholders_nullFilteredOut() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .replaceAllFrom("[a=1, b=2, c=3, d=4, e=5]", (a, b, c, d, e) -> null))
        .isEqualTo("[a=1, b=2, c=3, d=4, e=5]");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .replaceAllFrom(
                    "[a=, b=2, c=3, d=4, e=5] [a=z, b=y, c=x, d=w, e=v]",
                    (a, b, c, d, e) -> a.isEmpty() ? null : "got " + a + b + c + d + e))
        .isEqualTo("[a=, b=2, c=3, d=4, e=5] got zyxwv");
  }

  @Test
  public void replaceAllFrom_fivePlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}]")
                .replaceAllFrom("", (a, b, c, d, e) -> "got " + a + b + c + d + e))
        .isEmpty();
  }

  @Test
  public void replaceAllFrom_sixPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .replaceAllFrom(
                    "a=1, b=2, c=3, d=4, e=5, f",
                    (a, b, c, d, e, f) -> "got " + a + b + c + d + e + f))
        .isEqualTo("a=1, b=2, c=3, d=4, e=5, f");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6]",
                    (a, b, c, d, e, f) -> "got " + a + b + c + d + e + f))
        .isEqualTo("got 123456");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]",
                    (a, b, c, d, e, f) -> "got " + a + b + c + d + e + f))
        .isEqualTo("got 123456 got zyxwvu");
  }

  @Test
  public void replaceAllFrom_sixPlaceholders_nullFiltered() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .replaceAllFrom("[a=1, b=2, c=3, d=4, e=5, f=6]", (a, b, c, d, e, f) -> null))
        .isEqualTo("[a=1, b=2, c=3, d=4, e=5, f=6]");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=, e=5, f=6] [a=z, b=y, c=x, d=w, e=v, f=u]",
                    (a, b, c, d, e, f) -> d.isEmpty() ? null : "got " + a + b + c + d + e + f))
        .isEqualTo("[a=1, b=2, c=3, d=, e=5, f=6] got zyxwvu");
  }

  @Test
  public void replaceAllFrom_sixPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}]")
                .replaceAllFrom("", (a, b, c, d, e, f) -> "got " + a + b + c + d + e + f))
        .isEmpty();
  }

  @Test
  public void replaceAllFrom_sevenPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .replaceAllFrom(
                    "a=1, b=2, c=3, d=4, e=5, f=6, g",
                    (a, b, c, d, e, f, g) -> "got " + a + b + c + d + e + f + g))
        .isEqualTo("a=1, b=2, c=3, d=4, e=5, f=6, g");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7]",
                    (a, b, c, d, e, f, g) -> "got " + a + b + c + d + e + f + g))
        .isEqualTo("got 1234567");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7]; [a=z, b=y, c=x, d=w, e=v, f=u, g=t]",
                    (a, b, c, d, e, f, g) -> "got " + a + b + c + d + e + f + g))
        .isEqualTo("got 1234567; got zyxwvut");
  }

  @Test
  public void replaceAllFrom_sevenPlaceholders_nullFiltered() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7]", (a, b, c, d, e, f, g) -> null))
        .isEqualTo("[a=1, b=2, c=3, d=4, e=5, f=6, g=7]");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=, e=5, f=6, g=7] [a=z, b=y, c=x, d=w, e=v, f=u, g=t]",
                    (a, b, c, d, e, f, g) ->
                        d.isEmpty() ? null : "got " + a + b + c + d + e + f + g))
        .isEqualTo("[a=1, b=2, c=3, d=, e=5, f=6, g=7] got zyxwvut");
  }

  @Test
  public void replaceAllFrom_sevenPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}]")
                .replaceAllFrom("", (a, b, c, d, e, f, g) -> "got " + a + b + c + d + e + f + g))
        .isEmpty();
  }

  @Test
  public void replaceAllFrom_eightPlaceholders() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .replaceAllFrom(
                    "a=1, b=2, c=3, d=4, e=5, f=6, g=7, h",
                    (a, b, c, d, e, f, g, h) -> "got " + a + b + c + d + e + f + g + h))
        .isEqualTo("a=1, b=2, c=3, d=4, e=5, f=6, g=7, h");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8]",
                    (a, b, c, d, e, f, g, h) -> "got " + a + b + c + d + e + f + g + h))
        .isEqualTo("got 12345678");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .replaceAllFrom(
                    "I [a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8]; [a=z, b=y, c=x, d=w, e=v, f=u,"
                        + " g=t, h=s]",
                    (a, b, c, d, e, f, g, h) -> "got " + a + b + c + d + e + f + g + h))
        .isEqualTo("I got 12345678; got zyxwvuts");
  }

  @Test
  public void replaceAllFrom_eightPlaceholders_nullFiltered() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8]", (a, b, c, d, e, f, g, h) -> null))
        .isEqualTo("[a=1, b=2, c=3, d=4, e=5, f=6, g=7, h=8]");
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .replaceAllFrom(
                    "[a=1, b=2, c=3, d=, e=5, f=6, g=7, h=8] [a=z, b=y, c=x, d=w, e=v, f=u, g=t,"
                        + " h=s]",
                    (a, b, c, d, e, f, g, h) ->
                        d.isEmpty() ? null : "got " + a + b + c + d + e + f + g + h))
        .isEqualTo("[a=1, b=2, c=3, d=, e=5, f=6, g=7, h=8] got zyxwvuts");
  }

  @Test
  public void replaceAllFrom_eightPlaceholders_emptyInput() {
    assertThat(
            new StringFormat("[a={a}, b={b}, c={c}, d={d}, e={e}, f={f}, g={g}, h={h}]")
                .replaceAllFrom(
                    "", (a, b, c, d, e, f, g, h) -> "got " + a + b + c + d + e + f + g + h))
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
    assertThat(new StringFormat("[id={id}]").scan("whatever [id=foo] [id=bar]", id -> id))
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
    assertThat(new StringFormat("[id={id}]").scan("[id=foo] [id=bar]", id -> id))
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
    assertThat(new StringFormat("[id={id}]").scan("[id=foo] [id=bar];[id=baz", id -> id))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(
            new StringFormat("[k={key}, v={value}]")
                .scan("[k=one, v=1][k=two, v=2];[k=three,v]", (k, v) -> k + ":" + v))
        .containsExactly("one:1", "two:2")
        .inOrder();
  }

  @Test
  public void scan_skipsPartialMatches() {
    assertThat(new StringFormat("[id={id}]").scan("[id [id=bar];[id=baz", id -> id))
        .containsExactly("bar")
        .inOrder();
    assertThat(new StringFormat("[[id={id}]]").scan("[[id [[[id=bar]];[id=baz", id -> id))
        .containsExactly("bar")
        .inOrder();
  }

  @Test
  public void scan_singlePlaceholderOnly() {
    assertThat(new StringFormat("{s}").scan("whatever", s -> s)).containsExactly("whatever");
  }

  @Test
  public void scan_singleEllipsisOnly() {
    assertThat(new StringFormat("{...}").scan("whatever")).containsExactly(ImmutableList.of());
    assertThat(new StringFormat("{...}").scan("")).containsExactly(ImmutableList.of());
  }

  @Test
  public void scan_singlePlaceholderOnly_emptyInput() {
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
  @SuppressWarnings("StringUnformatArgsCheck")
  public void scan_throwsUponIncorrectNumLambdaParameters() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {a} or {b}").scan("bad input", Object::toString));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", String::concat));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", (a, b, c) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", (a, b, c, d) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", (a, b, c, d, e) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", (a, b, c, d, e, f) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", (a, b, c, d, e, f, g) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").scan("bad input", (a, b, c, d, e, f, g, h) -> a));
  }

  @Test
  @SuppressWarnings("StringUnformatArgsCheck")
  public void replaceAllFrom_throwsUponIncorrectNumLambdaParameters() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {a} or {b}").replaceAllFrom("bad input", Object::toString));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {what}").replaceAllFrom("bad input", (a, b) -> "got " + a + b));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").replaceAllFrom("bad input", (a, b, c) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").replaceAllFrom("bad input", (a, b, c, d) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").replaceAllFrom("bad input", (a, b, c, d, e) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StringFormat("1 is {what}").replaceAllFrom("bad input", (a, b, c, d, e, f) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {what}")
                .replaceAllFrom("bad input", (a, b, c, d, e, f, g) -> a));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StringFormat("1 is {what}")
                .replaceAllFrom("bad input", (a, b, c, d, e, f, g, h) -> a));
  }

  @Test
  public void format_placeholdersFilled() {
    assertThat(new StringFormat("{a} + {b} = {c}").format(1, 2, 3)).isEqualTo("1 + 2 = 3");
  }

  @Test
  public void format_ellipsisFilled() {
    assertThat(new StringFormat("{a} + {b} = {...}").format(1, 2, 3)).isEqualTo("1 + 2 = 3");
  }

  @Test
  public void format_nullValueAllowed() {
    assertThat(new StringFormat("{key} == {value}").format("x", null)).isEqualTo("x == null");
  }

  @Test
  public void format_noPlaceholder() {
    assertThat(new StringFormat("hello").format()).isEqualTo("hello");
  }

  @Test
  public void format_withEmptyValue() {
    assertThat(new StringFormat("{a} + {b} = {c}").format(1, 2, "")).isEqualTo("1 + 2 = ");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void format_tooFewArgs() {
    assertThrows(IllegalArgumentException.class, () -> new StringFormat("{foo}:{bar}").format(1));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void format_tooManyArgs() {
    assertThrows(
        IllegalArgumentException.class, () -> new StringFormat("{foo}:{bar}").format(1, 2, 3));
  }

  @Test
  public void to_noPlaceholder() {
    StringFormat.To<IllegalArgumentException> template =
        StringFormat.to(IllegalArgumentException::new, "foo");
    assertThat(template.with()).hasMessageThat().isEqualTo("foo");
  }

  @Test
  public void to_withPlaceholders() {
    StringFormat.To<IllegalArgumentException> template =
        StringFormat.to(IllegalArgumentException::new, "bad user id: {id}, name: {name}");
    assertThat(template.with(/*id*/ 123, /*name*/ "Tom"))
        .hasMessageThat()
        .isEqualTo("bad user id: 123, name: Tom");
  }

  @Test
  public void to_withPlaceholders_multilines() {
    StringFormat.To<IllegalArgumentException> template =
        StringFormat.to(IllegalArgumentException::new, "id: {id}, name: {name}, alias: {alias}");
    assertThat(
            template.with(
                /* id */ 1234567890,
                /* name */ "TomTomTomTomTomTom",
                /* alias */ "AtomAtomAtomAtomAtomAtom"))
        .hasMessageThat()
        .isEqualTo("id: 1234567890, name: TomTomTomTomTomTom, alias: AtomAtomAtomAtomAtomAtom");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void to_tooFewArguments() {
    StringFormat.To<IllegalArgumentException> template =
        StringFormat.to(IllegalArgumentException::new, "bad user id: {id}, name: {name}");
    assertThrows(IllegalArgumentException.class, () -> template.with(123));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void to_tooManyArguments() {
    StringFormat.To<IllegalArgumentException> template =
        StringFormat.to(IllegalArgumentException::new, "bad user id: {id}");
    assertThrows(IllegalArgumentException.class, () -> template.with(123, "Tom"));
  }

  @Test
  public void template_noPlaceholder() {
    StringFormat.To<BigQuery> template = BigQuery.template("SELECT *");
    assertThat(template.with().toString()).isEqualTo("SELECT *");
  }

  @Test
  public void template_withPlaceholders() {
    StringFormat.To<BigQuery> template =
        BigQuery.template("SELECT * FROM tbl WHERE id = '{id}' AND timestamp > '{time}'");
    assertThat(template.with(/* id */ "a'b", /* time */ "2023-10-01").toString())
        .isEqualTo("SELECT * FROM tbl WHERE id = 'a\\'b' AND timestamp > '2023-10-01'");
  }

  @Test
  public void template_withNullPlaceholderValue() {
    StringFormat.To<BigQuery> template = BigQuery.template("id = {id}");
    String id = null;
    assertThat(template.with(id).toString()).isEqualTo("id = null");
  }

  @SuppressWarnings("StringFormatArgsCheck")
  @Test
  public void template_tooFewArguments() {
    StringFormat.To<BigQuery> template =
        BigQuery.template("SELECT * FROM tbl WHERE id in ({id1}, {id2})");
    assertThrows(IllegalArgumentException.class, () -> template.with(123));
  }

  @SuppressWarnings("StringFormatArgsCheck")
  @Test
  public void template_tooManyArguments() {
    StringFormat.To<BigQuery> template = BigQuery.template("SELECT * FROM tbl WHERE id = {id}");
    assertThrows(IllegalArgumentException.class, () -> template.with(123, "Tom"));
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
  public void matches_emptyFormat() {
    assertThat(new StringFormat("").matches("")).isTrue();
    assertThat(new StringFormat("").matches("x")).isFalse();
  }

  @Test
  public void matches_true() {
    assertThat(new StringFormat("id:{id}, value:{value}").matches("id:123, value:x")).isTrue();
  }

  @Test
  public void matches_false() {
    assertThat(new StringFormat("id:{id}, value:{value}").matches("id:123")).isFalse();
  }

  @Test
  public void reverseString_emptyString() {
    assertThat(StringFormat.reverse("")).isEqualTo("");
  }

  @Test
  public void reverseString_singleCharRemainsAsIs() {
    String s = "a";
    assertThat(StringFormat.reverse(s)).isSameInstanceAs(s);
  }

  @Test
  public void reverseString_multipleCharsReversed() {
    assertThat(StringFormat.reverse("ab")).isEqualTo("ba");
    assertThat(StringFormat.reverse("abc")).isEqualTo("cba");
    assertThat(StringFormat.reverse("aa")).isEqualTo("aa");
  }

  @Test
  public void reverseList_emptyString() {
    assertThat(StringFormat.reverse(asList())).isEmpty();
  }

  @Test
  public void reverseList_singleElementRemainsAsIs() {
    List<String> list = asList("a");
    assertThat(StringFormat.reverse(list)).isSameInstanceAs(list);
    assertThat(StringFormat.reverse(list)).containsExactly("a");
  }

  @Test
  public void reverseList_multipleElementsReversed() {
    assertThat(StringFormat.reverse(asList(1, 2))).containsExactly(2, 1);
    assertThat(StringFormat.reverse(asList(1, 2, 3))).containsExactly(3, 2, 1);
  }

  @Test
  public void testToString() {
    assertThat(new StringFormat("projects/{project}/locations/{location}").toString())
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

  /** How we expect SPI providers to use {@link StringFormat#template}. */
  private static final class BigQuery {
    private final String query;

    static StringFormat.To<BigQuery> template(@CompileTimeConstant String template) {
      return StringFormat.template(template, BigQuery::safeInterpolate);
    }

    private BigQuery(String query) {
      this.query = query;
    }

    // Some custom interpolation logic. For testing purpose, naively quote the args.
    // Real BQ interpolation should also handle double quotes.
    private static BigQuery safeInterpolate(
        List<String> fragments, BiStream<Substring.Match, ?> placeholders) {
      Iterator<String> it = fragments.iterator();
      return new BigQuery(
          placeholders
              .collect(
                  new StringBuilder(),
                  (b, p, v) -> b.append(it.next()).append(escapeIfNeeded(p, v)))
              .append(it.next())
              .toString());
    }

    private static String escapeIfNeeded(Substring.Match placeholder, Object value) {
      return placeholder.isImmediatelyBetween("'", "'")
          ? escape(String.valueOf(value))
          : String.valueOf(value);
    }

    private static String escape(String s) {
      return Substring.first(CharMatcher.anyOf("\\'")::matches)
          .repeatedly()
          .replaceAllFrom(s, c -> "\\" + c);
    }

    @Override
    public String toString() {
      return query;
    }
  }
}

