package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SafeQueryArgsCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(SafeQueryArgsCheck.class, getClass());

  @Test
  public void stringArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT '{v}' FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(\"value\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void stringArgCanBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT \\\"{v}\\\" FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(\"value\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void charArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT '{v}' FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with('v');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void charArgCanBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT \\\"{v}\\\" FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with('v');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void stringArgMustBeQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT {c} FROM tbl\");",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: java.lang.String must be quoted",
            "    return SELECT.with(\"column\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void stringArgCanBeBackquoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT `{c}` FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(\"column\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void charArgMustBeQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT {c} FROM tbl\");",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: char must be quoted (for example '{c}'",
            "    return SELECT.with('x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void safeQueryCannotBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> UPDATE =",
            "     SafeQuery.template(\"UPDATE '{c}' FROM tbl\");",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: SafeQuery should not be quoted: '{c}'",
            "    return UPDATE.with(SafeQuery.of(\"foo\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void safeQueryNotQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT {c} FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(SafeQuery.of(\"column\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void safeQueryCannotBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<SafeQuery> UPDATE =",
            "     SafeQuery.template(\"UPDATE \\\"{c}\\\" FROM tbl\");",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: SafeQuery should not be quoted: '{c}'",
            "    return UPDATE.with(SafeQuery.of(\"foo\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatStringNotFound() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  SafeQuery test(StringFormat.To<SafeQuery> select) {",
            "    return select.with('x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void nonSafeQuery_notChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class NotSafeQuery {",
            "  private static final StringFormat.To<NotSafeQuery> UPDATE =",
            "     StringFormat.to(NotSafeQuery::new, \"update {c}\");",
            "  NotSafeQuery(String query) {} ",
            "  NotSafeQuery test() {",
            "    return UPDATE.with('x');",
            "  }",
            "}")
        .doTest();
  }
}
