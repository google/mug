package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharacterSetLiteralCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(CharacterSetLiteralCheck.class, getClass());

  @Test
  public void properUsage() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.CharacterSet;",
            "class Test {",
            "  private static final CharacterSet CHARS = CharacterSet.charsIn(\"[a-zA-Z-_0-9]\");",
            "}")
        .doTest();
  }

  @Test
  public void notCompileTimeConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.CharacterSet;",
            "class Test {",
            "  private CharacterSet CHARS(String charSet) {",
            "    return CharacterSet.charsIn(",
            "        // BUG: Diagnostic contains: compile-time string constant",
            "        charSet);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void missingSquareBrackets() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.CharacterSet;",
            "class Test {",
            "  private static final CharacterSet CHARS = CharacterSet.charsIn(",
            "      // BUG: Diagnostic contains: Use [a-zA-Z] instead",
            "      \"a-zA-Z\");",
            "}")
        .doTest();
  }

  @Test
  public void cannotUseBackslash() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.CharacterSet;",
            "class Test {",
            "  private static final CharacterSet CHARS = CharacterSet.charsIn(",
            "      // BUG: Diagnostic contains: Escaping ([\\n]) not supported",
            "      \"[\\\\n]\");",
            "}")
        .doTest();
  }
}