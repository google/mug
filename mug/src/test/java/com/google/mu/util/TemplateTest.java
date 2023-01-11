package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.testing.ClassSanityTester;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class TemplateTest {

  @Test public void parse_singlePlaceholder() {
    Template template = new Template("Hello {name}!", Character::isLetter);
    assertThat(template.parse("Hello Tom!").toMap()).containsExactly("{name}", "Tom");
  }

  @Test public void parse_multiplePlaceholders() {
    Template template =
        new Template("Hello {name}, welcome to {where}!", Character::isLetter);
    assertThat(template.parse("Hello Gandolf, welcome to Isengard!").toMap())
        .containsExactly("{name}", "Gandolf", "{where}", "Isengard")
        .inOrder();
  }

  @Test public void parse_multiplePlaceholdersWithSameName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Template("Hello {name} and {name}!", Character::isLetter));
  }

  @Test public void parse_emptyPlaceholdervalue() {
    Template template =
        new Template("Hello {name}!", Character::isLetter);
    assertThat(template.parse("Hello !").toMap()).containsExactly("{name}", "");
  }

  @Test public void parse_preludeFailsToMatch() {
    Template template = new Template("Hello {name}!", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hell Tom!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("elloh Tom!"));
  }

  @Test public void parse_postludeFailsToMatch() {
    Template template = new Template("Hello {name}!", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom?"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom!!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom"));
  }

  @Test public void parse_nonEmptyTemplate_emptyInput() {
    Template template = new Template("Hello {name}!", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse(""));
  }

  @Test public void parse_emptyTemplate_nonEmptyInput() {
    Template template = new Template("", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse("."));
  }

  @Test public void parse_emptyTemplate_emptyInput() {
    Template template = new Template("", Character::isLetter);
    assertThat(template.parse("").toMap()).isEmpty();
  }

  @Test public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.Pattern.class, Substring.between("{", "}"))
        .testNulls(Template.class);
  }
}
