package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.testing.ClassSanityTester;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringTemplateTest {

  @Test public void parse_singlePlaceholder() {
    StringTemplate template = new StringTemplate("Hello {name}!", Character::isLetter);
    assertThat(template.parse("Hello Tom!").toMap()).containsExactly("{name}", "Tom");
  }

  @Test public void parse_multiplePlaceholders() {
    StringTemplate template =
        new StringTemplate("Hello {name}, welcome to {where}!", Character::isLetter);
    assertThat(template.parse("Hello Gandolf, welcome to Isengard!").toMap())
        .containsExactly("{name}", "Gandolf", "{where}", "Isengard")
        .inOrder();
  }

  @Test public void parse_multiplePlaceholdersWithSameName() {
    StringTemplate template =
        new StringTemplate("Hello {name} and {name}!", Character::isLetter);
    ImmutableListMultimap<String, String> result =
        template.parse("Hello Gandolf and Aragon!")
            .collect(ImmutableListMultimap::toImmutableListMultimap);
    assertThat(result)
        .containsExactly("{name}", "Gandolf", "{name}", "Aragon")
        .inOrder();
  }

  @Test public void parse_emptyPlaceholdervalue() {
    StringTemplate template =
        new StringTemplate("Hello {name}!", Character::isLetter);
    assertThat(template.parse("Hello !").toMap()).containsExactly("{name}", "");
  }

  @Test public void parse_preludeFailsToMatch() {
    StringTemplate template = new StringTemplate("Hello {name}!", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hell Tom!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("elloh Tom!"));
  }

  @Test public void parse_postludeFailsToMatch() {
    StringTemplate template = new StringTemplate("Hello {name}!", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom?"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom!!"));
    assertThrows(IllegalArgumentException.class, () -> template.parse("Hello Tom"));
  }

  @Test public void parse_nonEmptyTemplate_emptyInput() {
    StringTemplate template = new StringTemplate("Hello {name}!", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse(""));
  }

  @Test public void parse_emptyTemplate_nonEmptyInput() {
    StringTemplate template = new StringTemplate("", Character::isLetter);
    assertThrows(IllegalArgumentException.class, () -> template.parse("."));
  }

  @Test public void parse_emptyTemplate_emptyInput() {
    StringTemplate template = new StringTemplate("", Character::isLetter);
    assertThat(template.parse("").toMap()).isEmpty();
  }

  @Test public void testNulls() {
    new ClassSanityTester()
        .setDefault(Substring.Pattern.class, Substring.between("{", "}"))
        .testNulls(StringTemplate.class);
  }
}
