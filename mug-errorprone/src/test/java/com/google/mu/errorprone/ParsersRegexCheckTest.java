/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParsersRegexCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(ParsersRegexCheck.class, getClass());

  @Test public void properUsage() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(\"[a-zA-Z0-9]+\");",
            "}")
        .doTest();
  }

  @Test public void notCompileTimeConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private Parser<String> myParser(String pattern) {",
            "    return Parsers.regex(",
            "        // BUG: Diagnostic contains: compile-time string constant expected",
            "        pattern);",
            "  }",
            "}")
        .doTest();
  }

  @Test public void emptyMatchNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: regex must not match empty string",
            "      \"a*\");",
            "}")
        .doTest();
  }

  @Test public void anchorsNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: anchors are not allowed",
            "      \"^a\");",
            "}")
        .doTest();
  }

  @Test public void lookaroundsNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: lookarounds are not allowed",
            "      \"a(?=b)\");",
            "}")
        .doTest();
  }

  @Test public void backreferencesNotAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: backreferences are not allowed",
            "      \"(a)\\\\1\");",
            "}")
        .doTest();
  }

  @Test public void invalidJdkRegexSyntax() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: expecting <4 hex digits>",
            "      \"\\\\u123z\");",
            "}")
        .doTest();
  }

  @Test public void constantRegex_valid() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final String PATTERN = \"[a-zA-Z0-9]+\";",
            "  private static final Parser<String> PARSER = Parsers.regex(PATTERN);",
            "}")
        .doTest();
  }

  @Test public void constantRegex_invalid() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final String PATTERN = \"a*\";",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: regex must not match empty string",
            "      PATTERN);",
            "}")
        .doTest();
  }

  @Test public void parsersRegex_redosVulnerable_fails() {
    helper
        .addSourceLines(
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

  @Test public void properUsage_withFunction() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<Integer> PARSER = Parsers.regex(\"id:(\\\\d+)\", s ->"
                + " Integer.parseInt(s));",
            "}")
        .doTest();
  }

  @Test public void properUsage_withBiFunction() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "import java.util.List;",
            "class Test {",
            "  private static final Parser<List<String>> PARSER ="
                + " Parsers.regex(\"(\\\\w+)=(\\\\d+)\", (k, v) -> List.of(k, v));",
            "}")
        .doTest();
  }

  @Test public void cardinalityMismatch_zeroGroups_expectedOne() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: has 0 capturing group(s), but 1 expected",
            "      \"[a-z]+\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void cardinalityMismatch_oneGroup_expectedTwo() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: has 1 capturing group(s), but 2 expected",
            "      \"(\\\\d+)\",",
            "      (a, b) -> a + b);",
            "}")
        .doTest();
  }

  @Test public void cardinalityMismatch_threeGroups_expectedTwo() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: has 3 capturing group(s), but 2 expected",
            "      \"(\\\\d+)-(\\\\d+)-(\\\\d+)\",",
            "      (a, b) -> a + b);",
            "}")
        .doTest();
  }

  @Test public void withFunction_notCompileTimeConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private Parser<String> myParser(String pattern) {",
            "    return Parsers.regex(",
            "        // BUG: Diagnostic contains: compile-time string constant expected",
            "        pattern,",
            "        s -> s);",
            "  }",
            "}")
        .doTest();
  }

  @Test public void withFunction_redosVulnerable() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: vulnerable to exponential backtracking (ReDoS)",
            "      \"((a+)+)\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void properUsage_withNonCapturingGroup() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<Integer> PARSER =",
            "      Parsers.regex(\"(?:prefix:)(\\\\d+)\", s -> Integer.parseInt(s));",
            "}")
        .doTest();
  }

  @Test public void cardinalityMismatch_withNonCapturingGroup() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: has 1 capturing group(s), but 2 expected",
            "      \"(?:prefix:)(\\\\d+)\",",
            "      (a, b) -> a + b);",
            "}")
        .doTest();
  }

  @Test public void properUsage_withNamedGroup() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<Integer> PARSER =",
            "      Parsers.regex(\"id:(?<id>\\\\d+)\", s -> Integer.parseInt(s));",
            "}")
        .doTest();
  }

  @Test public void cardinalityMismatch_withNamedGroup() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: has 2 capturing group(s), but 1 expected",
            "      \"(?<k>\\\\w+)=(?<v>\\\\d+)\",",
            "      s -> s);",
            "}")
        .doTest();
  }
}
