package com.google.mu.errorprone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.errorprone.CompilationTestHelper;

@RunWith(JUnit4.class)
public class TemplateStringAnnotationCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(TemplateStringAnnotationCheck.class, getClass());

  @Test
  public void missingTemplateStringAnnotation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  // BUG: Diagnostic contains: @TemplateString",
            "  void test(String tmpl, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void missingTemplateFormatMethodAnnotation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  // BUG: Diagnostic contains: @TemplateFormatMethod",
            "  void test(@TemplateString String tmpl, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void multipleTemplateStringAnnotation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  // BUG: Diagnostic contains: @TemplateString",
            "  void test(@TemplateString String tmpl1, @TemplateString String tmpl2) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateStringAnnotationOnNonStringParameter() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  // BUG: Diagnostic contains: String",
            "  void test(@TemplateString Object tmpl1, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void goodTemplateFormatMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void test(@TemplateString String tmpl, Object... args) {}",
            "}")
        .doTest();
  }
}
