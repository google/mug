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
public final class RegexLiteralCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(RegexLiteralCheck.class, getClass());

  @Test public void properUsage() {
    helper.addSourceLines(
            "Test.java",
            "import com.google.common.labs.parse.Parsers;",
            "import com.google.common.labs.parse.Parser;",
            "class Test {",
            "  private static final Parser<String> PARSER = Parsers.regex(\"[a-zA-Z0-9]+\");",
            "}")
        .doTest();
  }

  @Test public void notCompileTimeConstant() {
    helper.addSourceLines(
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
    helper.addSourceLines(
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
    helper.addSourceLines(
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
    helper.addSourceLines(
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
    helper.addSourceLines(
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
}
