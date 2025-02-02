package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StringFormatPlaceholderNamesCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(StringFormatPlaceholderNamesCheck.class, getClass());

  @Test
  public void blankNameNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: { }",
            "      new StringFormat(\"{ }\");",
            "}")
        .doTest();
  }

  @Test
  public void dashOnlyNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {-}",
            "      new StringFormat(\"{-}\");",
            "}")
        .doTest();
  }

  @Test
  public void underscoreOnlyNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {_}",
            "      new StringFormat(\"{_}\");",
            "}")
        .doTest();
  }

  @Test
  public void numberOnlyNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {345}",
            "      new StringFormat(\"{345}\");",
            "}")
        .doTest();
  }

  @Test
  public void quantifierNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {1,2}",
            "      new StringFormat(\"{1,2}\");",
            "}")
        .doTest();
  }

  @Test
  public void i18nNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {用户}",
            "      new StringFormat(\"{用户}\");",
            "}")
        .doTest();
  }

  @Test
  public void parensNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {(a)}",
            "      new StringFormat(\"{(a)}\");",
            "}")
        .doTest();
  }

  @Test
  public void ellipsisAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{...}\");",
            "}")
        .doTest();
  }

  @Test
  public void starNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {*}",
            "      new StringFormat(\"{*}\");",
            "}")
        .doTest();
  }

  @Test
  public void normalNameAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{name}\");",
            "}")
        .doTest();
  }

  @Test
  public void dashCaseAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{job-name}\");",
            "}")
        .doTest();
  }

  @Test
  public void dotCaseAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{job.name}\");",
            "}")
        .doTest();
  }

  @Test
  public void spaceInBetweenNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {job name}",
            "      new StringFormat(\"{job name}\");",
            "}")
        .doTest();
  }

  @Test
  public void snakeCaseAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{job_name}\");",
            "}")
        .doTest();
  }

  @Test
  public void leadingSpacesNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: { project}",
            "      new StringFormat(\"{ project}\");",
            "}")
        .doTest();
  }

  @Test
  public void trailingSpacesNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      // BUG: Diagnostic contains: {project }",
            "      new StringFormat(\"{project }\");",
            "}")
        .doTest();
  }

  @Test
  public void trailingSpacesBeforeEqualSignIgnored() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{shows_id => id,}\");",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void squareBracketedPlaceholdersChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat.WithSquareBracketedPlaceholders FORMAT =",
            "      // BUG: Diagnostic contains: 123",
            "      new StringFormat.WithSquareBracketedPlaceholders(\"[123]\");",
            "}")
        .doTest();
  }
}
