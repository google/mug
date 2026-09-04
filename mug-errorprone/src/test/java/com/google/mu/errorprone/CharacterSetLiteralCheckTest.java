package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharacterSetLiteralCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(CharacterSetLiteralCheck.class, getClass());

  @Test public void properUsage() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<?> CHARS = Parser.one(\"[a-zA-Z-_0-9]\");",
            "}")
        .doTest();
  }

  @Test public void notCompileTimeConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private Parser<?> chars(String charSet) {",
            "    return Parser.one(",
            "        // BUG: Diagnostic contains: compile-time string constant",
            "        charSet);",
            "  }",
            "}")
        .doTest();
  }

  @Test public void missingSquareBrackets() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String>.OrEmpty CHARS = Parser.zeroOrMore(",
            "      // BUG: Diagnostic contains: Use [a-zA-Z] instead",
            "      \"a-zA-Z\");",
            "}")
        .doTest();
  }

  @Test public void allowsBackslash() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<?> CHARS = Parser.one(",
            "      \"[\\\\n]\");",
            "}")
        .doTest();
  }

  @Test public void parserParameterCheck_properUsage() {
    helper
        .addSourceLines(
            "Test.java",
            "package com.google.common.labs.parse;",
            "abstract class Parser {",
            "  public abstract void foo(String characterClass);",
            "  void test(Parser parser) {",
            "    parser.foo(\"[a-z]\");",
            "  }",
            "}")
        .doTest();
  }

  @Test public void parserParameterCheck_invalidUsage() {
    helper
        .addSourceLines(
            "Test.java",
            "package com.google.common.labs.parse;",
            "abstract class Parser {",
            "  public abstract void foo(String characterClass);",
            "  void test(Parser parser) {",
            "    parser.foo(",
            "        // BUG: Diagnostic contains: Use [a-z] instead",
            "        \"a-z\");",
            "  }",
            "}")
        .doTest();
  }

  @Test public void parserParameterCheck_nonConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "package com.google.common.labs.parse;",
            "abstract class Parser {",
            "  public abstract void foo(String characterClass);",
            "  void test(Parser parser, String invalid) {",
            "    parser.foo(",
            "        // BUG: Diagnostic contains: compile-time string constant expected",
            "        invalid);",
            "  }",
            "}")
        .doTest();
  }

  @Test public void parserParameterCheck_nonPublicMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "package com.google.common.labs.parse;",
            "abstract class Parser {",
            "  abstract void foo(String characterClass);",
            "  void test(Parser parser) {",
            "    parser.foo(\"a-z\");",
            "  }",
            "}")
        .doTest();
  }

  @Test public void allowsRightBracketAsFirstChar() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<?> CHARS1 = Parser.one(\"[]]\");",
            "  private static final Parser<?> CHARS2 = Parser.one(\"[^]]\");",
            "}")
        .doTest();
  }

  @Test public void canUseRightBracketIfNotFirstChar() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<?> CHARS = Parser.one(",
            "      \"[a-z]]\");",
            "}")
        .doTest();
  }
}
