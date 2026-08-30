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

  @Test public void withFunction_anchor_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: anchors are not allowed in regex parser: ^",
            "      \"^(\\\\d+)\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void withFunction_lookaround_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: lookarounds are not allowed in regex parser",
            "      \"(?=\\\\d)(\\\\w+)\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void withFunction_backreference_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: backreferences are not allowed in regex parser",
            "      \"(\\\\w+)-\\\\1\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void withFunction_matchesEmpty_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: regex must not match empty string",
            "      \"(\\\\d*)\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void withFunction_emptyPattern_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: regex must not match empty string",
            "      \"\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void withFunction_emptyGroup_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: regex must not match empty string",
            "      \"()\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void withFunction_zeroWidthAlternation_failsCompilation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: regex must not match empty string",
            "      \"(a|)\",",
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

  @Test public void cardinalityMismatch_onlyNonCapturingGroups() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(",
            "      // BUG: Diagnostic contains: has 0 capturing group(s), but 1 expected",
            "      \"(?:abc)(?:def)\",",
            "      s -> s);",
            "}")
        .doTest();
  }

  @Test public void properUsage_withNonCapturingAndNamedGroup() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<Integer> PARSER =",
            "      Parsers.regex(\"(?:prefix:)(?<id>\\\\d+)\", id -> Integer.parseInt(id));",
            "}")
        .doTest();
  }

  @Test public void nameMismatch_withNonCapturingAndNamedGroup() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER =",
            "      // BUG: Diagnostic contains: Lambda variable `foo` doesn't look to be for named"
                + " group",
            "      Parsers.regex(\"(?:prefix:)(?<bar>\\\\d+)\", foo -> foo);",
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
            "      Parsers.regex(\"id:(?<id>\\\\d+)\", id -> Integer.parseInt(id));",
            "}")
        .doTest();
  }

  @Test public void properUsage_withNamedGroup_caseDifference() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<Integer> PARSER =",
            "      Parsers.regex(\"id:(?<jobId>\\\\d+)\", job_id -> Integer.parseInt(job_id));",
            "}")
        .doTest();
  }

  @Test public void namedGroup_nameMismatch_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER =",
            "      // BUG: Diagnostic contains: Lambda variable `foo` doesn't look to be for named"
                + " group",
            "      Parsers.regex(\"id:(?<bar>\\\\d+)\", foo -> foo);",
            "}")
        .doTest();
  }

  @Test public void namedGroups_outOfOrder_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "import java.util.List;",
            "class Test {",
            "  private static final Parser<List<String>> PARSER =",
            "      // BUG: Diagnostic contains: lambda variables [bar, foo] appear to be in"
                + " inconsistent order with the capturing groups",
            "      Parsers.regex(\"(?<foo>\\\\w+)=(?<bar>\\\\d+)\", (bar, foo) -> List.of(foo,"
                + " bar));",
            "}")
        .doTest();
  }

  @Test public void methodRef_namedGroup_nameMismatch_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  void test() {",
            "    Parser<String> parser =",
            "        // BUG: Diagnostic contains: Method parameter `x` of referenced method"
                + " `this::combine` doesn't look to be for named group",
            "        Parsers.regex(\"(?<foo>\\\\w+)-(?<bar>\\\\w+)-(?<baz>\\\\w+)\","
                + " this::combine);",
            "  }",
            "  public String combine(String x, String bar, String baz) {",
            "    return x + bar + baz;",
            "  }",
            "}")
        .doTest();
  }

  @Test public void namedAndNumberedGroups_namedMatches_numberedIgnored() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "import java.util.List;",
            "class Test {",
            "  private static final Parser<List<String>> PARSER =",
            "      Parsers.regex(\"(?<key>\\\\w+)=(\\\\d+)\", (key, value) -> List.of(key,"
                + " value));",
            "}")
        .doTest();
  }

  @Test public void partiallyNamedGroups_namedMatches_success() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "import java.util.List;",
            "class Test {",
            "  private static final Parser<List<String>> PARSER =",
            "      Parsers.regex(\"(\\\\w+)-(?<id>\\\\d+)-(\\\\w+)\", (prefix, id, suffix) ->"
                + " List.of(prefix, id, suffix));",
            "}")
        .doTest();
  }

  @Test public void partiallyNamedGroups_namedMismatch_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "import java.util.List;",
            "class Test {",
            "  private static final Parser<List<String>> PARSER =",
            "      // BUG: Diagnostic contains: Lambda variable `foo` doesn't look to be for named"
                + " group",
            "      Parsers.regex(\"(\\\\w+)-(?<id>\\\\d+)-(\\\\w+)\", (prefix, foo, suffix) ->"
                + " List.of(prefix, foo, suffix));",
            "}")
        .doTest();
  }

  @Test public void partiallyNamedGroups_namedOutOfOrder_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "import java.util.List;",
            "class Test {",
            "  private static final Parser<List<String>> PARSER =",
            "      // BUG: Diagnostic contains: lambda variables [second, first] appear to be in"
                + " inconsistent order with the capturing groups",
            "      Parsers.regex(\"(\\\\w+)-(?<first>\\\\w+)-(?<second>\\\\w+)\", (x, second,"
                + " first) -> List.of(x, first, second));",
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
