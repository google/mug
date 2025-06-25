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
  public void hasConditionalOperator_regularPlaceholder() {
    Placeholder placeholder = placeholder("{foo}", "my {foo}-{bar}");
    assertThat(placeholder.hasConditionalOperator()).isFalse();
  }

  @Test
  public void hasConditionalOperator_equalOperatorIsNotBooleanType() {
    assertThat(placeholder("{foo=bar}").hasConditionalOperator()).isFalse();
  }

  @Test
  public void hasConditionalOperator_arrowOperatorIsBooleanType() {
    assertThat(placeholder("{foo->bar}").hasConditionalOperator()).isTrue();
  }

  @Test
  public void hasConditionalOperator_arrowOperatorAfterEqualOperatorIsNotBooleanType() {
    assertThat(placeholder("{foo=a->b}").hasConditionalOperator()).isFalse();
  }

  @Test
  public void hasConditionalOperator_arrowOperatorBeforeEqualOperatorIsBooleanType() {
    assertThat(placeholder("{foo->a=b}").hasConditionalOperator()).isTrue();
  }

  @Test
  public void hasOptionalParameter_noQuestionMark_not() {
    assertThat(placeholder("{foo->a=b}").hasOptionalParameter()).isFalse();
  }

  @Test
  public void hasOptionalParameter_notProperWord_not() {
    assertThat(placeholder("{foo-bar?->a=b}").hasOptionalParameter()).isFalse();
  }

  @Test
  public void hasOptionalParameter_properWordWithQuestionMark_yes() {
    assertThat(placeholder("{optional_bar? -> a=b}").hasOptionalParameter()).isTrue();
  }

  @Test
  public void optionalParametersFromOperatorRhs_none() {
    assertThat(placeholder("{optional_bar? -> a=b}").optionalParametersFromOperatorRhs()).isEmpty();
  }

  @Test
  public void optionalParametersFromOperatorRhs_one() {
    assertThat(placeholder("{optional_bar? -> optional_bar?}").optionalParametersFromOperatorRhs())
        .containsExactly("optional_bar?")
        .inOrder();
  }

  @Test
  public void optionalParametersFromOperatorRhs_two() {
    assertThat(placeholder("{optional_bar? -> a?=b?}").optionalParametersFromOperatorRhs())
        .containsExactly("a?", "b?")
        .inOrder();
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

  @Test
  public void getStartIndexInSource_firstPlaceholder() {
    Placeholder placeholder = placeholder("{foo}", "{foo}");
    assertThat(placeholder.getStartIndexInSource("my {foo}")).isEqualTo(3);
  }

  @Test
  public void getStartIndexInSource_secondPlaceholder() {
    Placeholder placeholder = placeholder("{bar}", "{foo} {bar}");
    assertThat(placeholder.getStartIndexInSource("my {foo} {bar} and {baz}")).isEqualTo(9);
  }

  @Test
  public void getStartIndexInSource_nestedPlaceholder() {
    Placeholder placeholder = placeholder("{bar}", "my {foo = {bar}}");
    assertThat(placeholder.getStartIndexInSource("my {foo = {bar}} and...")).isEqualTo(10);
  }

  @Test
  public void getStartIndexInSource_notFoundInsource() {
    Placeholder placeholder = placeholder("{bar}", "my {foo = {bar}}");
    assertThat(placeholder.getStartIndexInSource("my {bar} and...")).isEqualTo(0);
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
