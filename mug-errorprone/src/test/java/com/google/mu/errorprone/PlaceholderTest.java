package com.google.mu.errorprone;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.mu.util.Substring;

@RunWith(JUnit4.class)
public final class PlaceholderTest {
  @Test
  public void toString_singlePlaceholderAtBeginning() {
    Placeholder placeholder = placeholder("{foo}", "{foo}-{bar}");
    assertThat(placeholder.toString()).isEqualTo("<{foo}>-{bar}");
  }

  @Test
  public void toString_singlePlaceholderAtEnd() {
    Placeholder placeholder = placeholder("{foo}", "my {foo}");
    assertThat(placeholder.toString()).isEqualTo("my <{foo}>");
  }

  @Test
  public void toString_singlePlaceholderInMiddle() {
    Placeholder placeholder = placeholder("[foo]", "my [foo] is {bar}");
    assertThat(placeholder.toString()).isEqualTo("my <[foo]> is {bar}");
  }

  @Test
  public void toString_singlePlaceholderOnly() {
    Placeholder placeholder = placeholder("{foo}", "{foo}");
    assertThat(placeholder.toString()).isEqualTo("<{foo}>");
  }

  @Test
  public void toString_notTheFirstLine() {
    Placeholder placeholder = placeholder("{foo}", "Hello:\n my {foo}");
    assertThat(placeholder.toString()).isEqualTo("... my <{foo}>");
  }

  @Test
  public void toString_notTheLastLine() {
    Placeholder placeholder = placeholder("{foo}", "my {foo} {bar}\n World");
    assertThat(placeholder.toString()).isEqualTo("my <{foo}> {bar}...");
  }

  @Test
  public void toString_notTheFirstLine_notTheLastLine() {
    Placeholder placeholder = placeholder("{foo}", "Hello:\nmy {foo} bar\n World of {bar}");
    assertThat(placeholder.toString()).isEqualTo("...my <{foo}> bar...");
  }

  @Test
  public void toString_multiLinePlaceholder() {
    Placeholder placeholder =
        placeholder(
            Substring.spanningInOrder("{", "}"), "SELECT\n {foo -> \nbar,\nbaz,}\n FROM tbl");
    assertThat(placeholder.toString()).isEqualTo("... <{foo -> \nbar,\nbaz,}>...");
  }

  @Test
  public void requiresBooleanArg_regularPlaceholder() {
    Placeholder placeholder = placeholder("{foo}", "my {foo}-{bar}");
    assertThat(placeholder.requiresBooleanArg()).isFalse();
  }

  @Test
  public void requiresBooleanArg_equalOperatorIsNotBooleanType() {
    assertThat(placeholder("{foo=bar}").requiresBooleanArg()).isFalse();
  }

  @Test
  public void requiresBooleanArg_arrowOperatorIsBooleanType() {
    assertThat(placeholder("{foo->bar}").requiresBooleanArg()).isTrue();
  }

  @Test
  public void requiresBooleanArg_arrowOperatorAfterEqualOperatorIsNotBooleanType() {
    assertThat(placeholder("{foo=a->b}").requiresBooleanArg()).isFalse();
  }

  @Test
  public void requiresBooleanArg_arrowOperatorBeforeEqualOperatorIsBooleanType() {
    assertThat(placeholder("{foo->a=b}").requiresBooleanArg()).isTrue();
  }

  @Test
  public void isFollowedBy_notNextToEachOther() {
    Placeholder foo = placeholder("{foo}", "my {foo}-{bar}");
    Placeholder bar = placeholder("{bar}", "my {foo}-{bar}");
    assertThat(foo.isFollowedBy(bar)).isFalse();
    assertThat(bar.isFollowedBy(foo)).isFalse();
  }

  @Test
  public void isFollowedBy_nextToEachOther() {
    Placeholder foo = placeholder("{foo}", "my {foo}{bar}");
    Placeholder bar = placeholder("{bar}", "my {foo}{bar}");
    assertThat(foo.isFollowedBy(bar)).isTrue();
    assertThat(bar.isFollowedBy(foo)).isFalse();
  }

  private static Placeholder placeholder(Substring.Pattern pattern, String template) {
    return new Placeholder(pattern.in(template).orElseThrow());
  }

  private static Placeholder placeholder(String placeholder, String template) {
    return placeholder(Substring.first(placeholder), template);
  }

  private static Placeholder placeholder(String placeholder) {
    return placeholder(placeholder, placeholder);
  }
}
