package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StringUnformatArgsCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(StringUnformatArgsCheck.class, getClass());

  @Test
  public void goodParseWithLambda() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar_id}\").parse(s, (foo, barId) -> foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithStringClassMethodRef() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, String::concat);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithPublicMethodRefWithTwoArgs_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, this::combine);",
            "  }",
            "  public String combine(String x, String y) {",
            "    return x + y;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithOtherPackageMethodRefWithTwoArgs_ignoreNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "package com.google.devtools.build.buildjar.plugin.format.tests;",
            "import com.google.mu.util.StringFormat;",
            "import com.google.devtools.build.buildjar.plugin.format.lib.Util;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, Util::combine);",
            "  }",
            "}")
        .addSourceLines(
            "Util.java",
            "package com.google.devtools.build.buildjar.plugin.format.lib;",
            "public class Util {",
            "  public static String combine(String x, String y) {",
            "    return x + y;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithPublicConstructorRefWithTwoArgs_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  public Test(String x, String y) {}",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, Test::new);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithOtherPackageConstructorRefWithTwoArgs_ignoreNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "package com.google.devtools.build.buildjar.plugin.format.tests;",
            "import com.google.mu.util.StringFormat;",
            "import com.google.devtools.build.buildjar.plugin.format.lib.Util;",
            "class Test {",
            "  public Test(String x, String y) {}",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, Util::new);",
            "  }",
            "}")
        .addSourceLines(
            "Util.java",
            "package com.google.devtools.build.buildjar.plugin.format.lib;",
            "public class Util {",
            "  public Util(String x, String y) {}",
            "}")
        .doTest();
  }

  @Test
  public void parseWithInterfaceMethodRefWithTwoArgs_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s, Combiner combiner) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, combiner::combine);",
            "  }",
            "  interface Combiner {",
            "    String combine(String x, String y);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithInterfaceMethodRefWithThreeArgs_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s, Combiner combiner) {",
            "    new StringFormat(\"{foo}-{bar}-{zoo}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, combiner::combine);",
            "  }",
            "  interface Combiner {",
            "    String combine(String x, String y, String z);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithPublicMethodRefWithThreeArgs_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}-{zoo}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, this::combine);",
            "  }",
            "  public String combine(String x, String y, String zoo) {",
            "    return x + y + zoo;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithPublicConstructorRefWithThreeArgs_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  public Test(String x, String y, String zoo) {}",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}-{zoo}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, Test::new);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithPublicMethodRefWithTwoArgs_reportOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, this::combine);",
            "  }",
            "  public String combine(String bar, String foo) {",
            "    return foo + bar;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithPublicConstructorRefWithTwoArgs_reportOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  public Test(String bar, String foo) {}",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, Test::new);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithNonPublicMethodRef_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, this::combine);",
            "  }",
            "  private String combine(String x, String y) {",
            "    return x + y;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseWithNonConstructorMethodRef_requireNameMismatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private Test(String x, String y) {}",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar}\")",
            "        // BUG: Diagnostic contains:",
            "        .parse(s, Test::new);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void goodScanWithLambda() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar_id}\").scan(s, (foo, barId) -> foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void placeholderSpecialCharactersIgnored() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo#}-{*}\").parse(s, (foo, unused) -> foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void placeholderNameCanBeSuffix() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"job: {id}\").parse(s, jobId -> jobId);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void resourceNameSubPatternPlaceholderSyntax() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{container=foo/*}\").parse(s, container -> 1);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatStringHasNoPlaceholder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"foo-bar\").parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void tooFewParametersInMethodRef() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, Object::toString);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void tooFewLambdaArguments() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void tooManyParametersInMethodRef() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}\").parse(s, String::concat);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void tooManyLambdaArguments() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, (x, y, z) -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void lambdaParametersOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, (bar, foo) -> bar);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void lambdaParametersWithNumericOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo1}-{foo2}\").parse(s, (foo2, foo1) -> foo1);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void methodReferenceParametersOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").parse(s, this::combine);",
            "  }",
            "  String combine(String bar, String foo) {",
            "    return bar + foo;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void lambdaParametersOutOfOrder_ignoringCase() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}\").parse(s, (barId, foo) -> foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void lambdaParameterNameIsPrefix() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "         new StringFormat(\"{foo}-{bar}\")",
            "             .parse(s, (fooId, barId) -> fooId);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void lambdaParameterNameAndPlaceholderNameShareCommonPrefix() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "         new StringFormat(\"{is_cool}-{is_not_cool}\")",
            "             .parse(s, (isCold, isNot) -> isNot);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void lambdaParameterNameAndPlaceholderNameDontMatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "         new StringFormat(\"{is_cool}-{is_not_cool}\")",
            "              // BUG: Diagnostic contains:",
            "             .parse(s, (dry, wet) -> dry);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void constantUnformatterObjectChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat PARSER =",
            "      new StringFormat(\"{foo}-{bar}\");",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    PARSER.parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void localUnformatterVariableChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    StringFormat parser = new StringFormat(\"{foo}-{bar}\");",
            "    // BUG: Diagnostic contains:",
            "    parser.parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void localUnformatterVariableWithoutInitializerIgnored() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    StringFormat parser;",
            "    parser = new StringFormat(\"xyz\");",
            "    // BUG: Diagnostic contains:",
            "    parser.parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void patternProvidedThroughFactoryMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  StringFormat formatOf(String s) {",
            "    return new StringFormat(s);",
            "  }",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    formatOf(\"{foo}-{bar}\").parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void patternProvidedThroughFactoryMethodWithUnknownParameters() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  StringFormat formatOf(String s, boolean unused) {",
            "    return new StringFormat(s);",
            "  }",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    formatOf(\"{foo}-{bar}\", true).parse(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void scanMethodChecked() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").scan(s, x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void scanAndCollectFromMethodChecked_withCollector() {
    helper
        .addSourceLines(
            "Test.java",
            "import static java.util.stream.Collectors.toList;",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar}\").scanAndCollectFrom(s, toList());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void scanAndCollectFromMethodChecked_withBiCollector() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Collectors;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}\").scanAndCollectFrom(s, Collectors::toMap);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void methodWithoutMapperParameterIsIgnored() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{foo}-{bar_id}\").parse(s);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void twoPlaceholdersNextToEachOther_fail() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}{bar_id}\").parse(s);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void twoEllipsisPlaceholdersNextToEachOther_fail() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{...}{...}\").parse(s);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void namedAndEllipsisPlaceholdersNextToEachOther_fail() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}{...}\").parse(s);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void nestingPlaceholderIsOk() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String s) {",
            "    new StringFormat(\"{{foo}}\").parse(s);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void cannotUseLambdaWhenFormatIsntCompileTimeConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(StringFormat format) {",
            "    // BUG: Diagnostic contains:",
            "    format.parse(\"foo\", x -> x);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void nonLambdaParseMethodDoesNotRequireCompileTimeConstant() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(StringFormat format) {",
            "    format.parse(\"foo\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseMethod_ellipsisNotCountedAsPlaceholder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{key}={...}\").parse(\"foo=1\", k -> k);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void parseMethod_cannotProvideLambdaArgForEllipsisPlaceholder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{key}={...}\").parse(\"foo=1\", (k, v) -> k);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void scanMethod_ellipsisNotCountedAsPlaceholder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{key}={...}\").scan(\"foo=1\", k -> k);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void scanMethod_cannotProvideLambdaArgForEllipsisPlaceholder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{key}={...}\").scan(\"foo=1\", (k, v) -> k);",
            "  }",
            "}")
        .doTest();
  }
}
