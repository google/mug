package com.google.mu.errorprone.regex;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RegexRedosCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(RegexRedosCheck.class, getClass());

  @Test public void parsersRegex_safePattern_compiles() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(\"[a-zA-Z0-9]+\");",
            "}")
        .doTest();
  }

  @Test public void parsersRegex_nestedQuantifier_fails() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: vulnerable to exponential backtracking (ReDoS)",
            "      \"(a+)+\");",
            "}")
        .doTest();
  }

  @Test public void parsersRegex_overlappingAlternation_fails() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: vulnerable to exponential backtracking (ReDoS)",
            "      \"(a|a)+\");",
            "}")
        .doTest();
  }

  @Test public void patternCompile_safePattern_compiles() {
    helper.addSourceLines(
            "Test.java",
            "import java.util.regex.Pattern;",
            "class Test {",
            "  private static final Pattern PATTERN = Pattern.compile(\"[a-zA-Z0-9]+\");",
            "}")
        .doTest();
  }

  @Test public void patternCompile_nestedQuantifier_fails() {
    helper.addSourceLines(
            "Test.java",
            "import java.util.regex.Pattern;",
            "class Test {",
            "  private static final Pattern PATTERN = Pattern.compile(",
            "      // BUG: Diagnostic contains: vulnerable to exponential backtracking (ReDoS)",
            "      \"(a+)+\");",
            "}")
        .doTest();
  }

  @Test public void parsersRegex_possessiveNestedQuantifier_compiles() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(\"(a++)++\");",
            "}")
        .doTest();
  }

  @Test public void patternCompile_possessiveNestedQuantifier_compiles() {
    helper.addSourceLines(
            "Test.java",
            "import java.util.regex.Pattern;",
            "class Test {",
            "  private static final Pattern PATTERN = Pattern.compile(\"(a++)++\");",
            "}")
        .doTest();
  }
}
