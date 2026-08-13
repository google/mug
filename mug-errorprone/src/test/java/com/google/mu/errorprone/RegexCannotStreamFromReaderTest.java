package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RegexCannotStreamFromReaderTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(RegexCannotStreamFromReader.class, getClass());

  @Test public void properUsageBindsCorrectly() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER =",
            "      Parsers.regex(\"[0-9]{3}-[0-9]{3,4}\");",
            "}")
        .doTest();
  }

  @Test public void infiniteMaxSizeWarns() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: Regex has an unbounded quantifier (or is too"
                + " large). It may cause excessive memory usage or UnsupportedOperationException"
                + " when calling parseToStream(Reader).",
            "      \"[a-z]+\");",
            "}")
        .doTest();
  }

  @Test public void extremelyLargeBoundedMaxSizeWarns() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: Regex has an unbounded quantifier (or is too"
                + " large). It may cause excessive memory usage or UnsupportedOperationException"
                + " when calling parseToStream(Reader).",
            "      \"a{150000000}\");",
            "}")
        .doTest();
  }

  @Test public void malformedRegexIsIgnored() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  // No warning expected from this checker because it cannot be parsed",
            "  private static final Parser<String> PARSER = Parsers.regex(\"[\");",
            "}")
        .doTest();
  }
}
