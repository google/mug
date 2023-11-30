package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SafeSqlArgsCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(SafeSqlArgsCheck.class, getClass());

  @Test
  public void stringArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT '{v}' FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT \\\"{v}\\\" FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT '{v}' FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT \\\"{v}\\\" FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT {c} FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT `{c}` FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT {c} FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
            "    // BUG: Diagnostic contains: char must be quoted (for example '{c}')",
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
            "import com.google.mu.util.StringFormat;",
            "import com.google.mu.safesql.SafeQuery;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> UPDATE =",
            "     StringFormat.to(Sql::new, \"UPDATE '{c}' FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "import com.google.mu.safesql.SafeQuery;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> SELECT =",
            "     StringFormat.to(Sql::new, \"SELECT {c} FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "import com.google.mu.safesql.SafeQuery;",
            "class Sql {",
            "  private static final StringFormat.To<Sql> UPDATE =",
            "     StringFormat.to(Sql::new, \"UPDATE \\\"{c}\\\" FROM tbl\");",
            "  Sql(String query) {} ",
            "  Sql test() {",
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
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  Sql test(StringFormat.To<Sql> select) {",
            "    return select.with('x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void nonSql_notChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class NotSql {",
            "  private static final StringFormat.To<NotSql> UPDATE =",
            "     StringFormat.to(NotSql::new, \"update {c}\");",
            "  NotSql(String query) {} ",
            "  NotSql test() {",
            "    return UPDATE.with('x');",
            "  }",
            "}")
        .doTest();
  }
}
