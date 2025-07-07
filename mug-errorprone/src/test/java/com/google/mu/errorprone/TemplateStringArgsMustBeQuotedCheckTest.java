package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TemplateStringArgsMustBeQuotedCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(TemplateStringArgsMustBeQuotedCheck.class, getClass());

  @Test
  public void constructor_unquotedStringArg() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  Test(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  static Test test(String foo) {\n"
                + "    return new Test(\n"
                + "        \"name\",\n"
                + "        // BUG: Diagnostic contains: `{foo}`\n"
                + "        \"SELECT {foo}\",\n"
                + "        foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void constructor_quotedStringArg() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  Test(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  static Test test(String foo) {\n"
                + "    return new Test(\"name\", \"SELECT \\'{foo}\\'\", foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void rawStringArg_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(String foo) {\n"
                + "    report(\n"
                + "        \"name\",\n"
                + "        // BUG: Diagnostic contains: `{foo}`\n"
                + "        \"SELECT {foo}\",\n"
                + "        foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void backtickQuotedStringArg_ok() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(String foo) {\n"
                + "    report(\"name\", \"SELECT `{foo}`\", foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void singleQuotedStringArg_ok() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(String foo) {\n"
                + "    report(\"name\", \"SELECT \\'{foo}\\'\", foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void doubleQuotedStringArg_ok() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(String foo) {\n"
                + "    report(\"name\", \"SELECT \\\"{foo}\\\"\", foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void nonStringArg_ok() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(int foo) {\n"
                + "    report(\"name\", \"SELECT {foo}\", foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void cardinalityMismatch_leaveAsIs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(int foo, String bar) {\n"
                + "    report(\"name\", \"SELECT {foo}\", foo, bar);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void formatStringNotFound_leaveAsIs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;\n"
                + "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateFormatMethod\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "\n"
                + "  void test(String template, String foo) {\n"
                + "    report(\"name\", template, foo);\n"
                + "  }\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void methodMissingTemplateFormatMethodAnnotation_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
             "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  // BUG: Diagnostic contains: @TemplateFormatMethod\n"
                + "  void report(String name, @TemplateString String template, Object... args) {}\n"
                + "}\n")
        .doTest();
  }

  @Test
  public void constructorMissingTemplateFormatMethodAnnotation_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateString;\n"
                + "import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;\n"
                + "\n"
                + "class Test {\n"
                + "  @TemplateStringArgsMustBeQuoted\n"
                + "  // BUG: Diagnostic contains: @TemplateFormatMethod\n"
                + "  Test(String name, @TemplateString String template, Object... args) {}\n"
                + "}\n")
        .doTest();
  }
}