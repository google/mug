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
  public void with_stringArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT '{v}' FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(\"value\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_stringArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    return SafeQuery.of(\"SELECT '{v}' FROM tbl\", \"value\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_stringArgCanBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT \\\"{v}\\\" FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(\"value\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_stringArgCanBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    return SafeQuery.of(\"SELECT \\\"{v}\\\" FROM tbl\", \"value\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_charArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT '{v}' FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with('v');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_charArgCanBeSingleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    return SafeQuery.of(\"SELECT '{v}' FROM tbl\", 'v');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_charArgCanBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT \\\"{v}\\\" FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with('v');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_charArgCanBeDoubleQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    return SafeQuery.of(\"SELECT \\\"{v}\\\" FROM tbl\", 'v');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_stringArgMustBeQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT {c} FROM tbl\");",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: java.lang.String must be quoted",
            "    return SELECT.with(\"column\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_stringArgMustBeQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: java.lang.String must be quoted",
            "    return SafeQuery.of(\"SELECT {c} FROM tbl\", \"column\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_stringArgCanBeBackquoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT `{c}` FROM tbl\");",
            "  SafeQuery test() {",
            "    return SELECT.with(\"column\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_stringArgCanBeBackquoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    return SafeQuery.of(\"SELECT `{c}` FROM tbl\", \"column\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_charArgMustBeQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  private static final StringFormat.Template<SafeQuery> SELECT =",
            "     SafeQuery.template(\"SELECT {c} FROM tbl\");",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: for example '{c}'",
            "    return SELECT.with('x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_charArgMustBeQuoted() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "class Sql {",
            "  SafeQuery test() {",
            "    // BUG: Diagnostic contains: for example '{c}'",
            "    return SafeQuery.of(\"SELECT {c} FROM tbl\", 'x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_formatStringNotFound() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.util.StringFormat;",
            "class Sql {",
            "  SafeQuery test(StringFormat.Template<SafeQuery> select) {",
            "    return select.with('x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_formatStringNotFound() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.errorprone.annotations.CompileTimeConstant;",
            "class Sql {",
            "  SafeQuery test(@CompileTimeConstant String template) {",
            "    return SafeQuery.of(template, 'x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void with_regularStringFormat_notChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class NotSafeQuery {",
            "  private static final StringFormat.Template<NotSafeQuery> UPDATE =",
            "     StringFormat.to(NotSafeQuery::new, \"SELECT * FROM {c}\");",
            "  NotSafeQuery(String query) {} ",
            "  NotSafeQuery test() {",
            "    return UPDATE.with('x');",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_templateStringNotTheFirstParameter_noError() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Sql {",
            "  SafeQuery test() {",
            "    return query(\"foo\", \"SELECT \\\"{v}\\\" FROM tbl\", 'v');",
            "  }",
            "  @TemplateFormatMethod",
            "  SafeQuery query(String base, @TemplateString String template, Object... args) {",
            "    return SafeQuery.of(\"null\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_templateStringNotTheFirstParameter_withError() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.guava.labs.safesql.SafeQuery;",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Sql {",
            "  SafeQuery test(String v) {",
            "    // BUG: Diagnostic contains: argument of type java.lang.String must be quoted",
            "    return query(\"foo\", \"SELECT {v} FROM tbl\", v);",
            "  }",
            "  @TemplateFormatMethod",
            "  SafeQuery query(String base, @TemplateString String template, Object... args) {",
            "    return SafeQuery.of(\"null\");",
            "  }",
            "}")
        .doTest();
  }
}
