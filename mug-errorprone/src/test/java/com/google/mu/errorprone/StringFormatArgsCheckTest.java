package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StringFormatArgsCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(StringFormatArgsCheck.class, getClass());

  @Test
  public void templateStringNotTheFirstParameter_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void report(String name, @TemplateString String template, Object... args) {}",
            "  void test(int foo) {",
            "    report(\"name\", \"{foo}\", foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotTheFirstParameter_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  Test(String name, @TemplateString String template, Object... args) {}",
            "  void test(int foo) {",
            "    new Test(\"name\", \"{foo}\", foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotImmediatelyFollowedByVarargs_argsMatch_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, String name, Object... args) {}",
            "  void test(int foo) {",
            "    report(\"{name}: {foo}\", \"name\", foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotImmediatelyFollowedByVarargs_argsMatch_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, String name, Object... args) {}",
            "  void test(int foo) {",
            "    new Test(\"{name}: {foo}\", \"name\", foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotImmediatelyFollowedByVarargs_argsNotMatch_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, String name, Object... args) {}",
            "  void test(int foo, String bar) {",
            "    // BUG: Diagnostic contains: {name}",
            "    report(\"{name}: {foo}\", bar, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotImmediatelyFollowedByVarargs_argsNotMatch_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, String name, Object... args) {}",
            "  void test(int foo, String bar) {",
            "    // BUG: Diagnostic contains: {name}",
            "    new Test(\"{name}: {foo}\", bar, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotTheFirstParameter_doesNotMatchPlaceholder_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void report(String name, @TemplateString String template, Object... args) {}",
            "  void test(int bar) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    report(\"name\", \"{foo}\", bar);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotTheFirstParameter_doesNotMatchPlaceholder_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  Test(String name, @TemplateString String template, Object... args) {}",
            "  void test(int bar) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    new Test(\"name\", \"{foo}\", bar);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_good_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    report(\"{foo}-{bar_id}-{camelCase}\", foo, barId, camelCase);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_good_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    new Test(\"{foo}-{bar_id}-{camelCase}\", foo, barId, camelCase);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_goodInlinedFormat_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    report(\"{foo}-{bar_id}-{CamelCase}\", foo, barId, camelCase);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_goodInlinedFormat_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    new Test(\"{foo}-{bar_id}-{CamelCase}\", foo, barId, camelCase);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_concatenatedInlinedFormat_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    report(\"{foo}-{bar_id}\" + \"-{CamelCase}\", foo, barId, camelCase);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_concatenatedInlinedFormat_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    new Test(\"{foo}-{bar_id}\" + \"-{CamelCase}\", foo, barId, camelCase);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_usesPrintfStyle_method_fail() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: 0 placeholders defined",
            "    report(\"%s=%s\", foo1, foo2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_usesPrintfStyle_constructor_fail() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: 0 placeholders defined",
            "    new Test(\"%s=%s\", foo1, foo2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_conflictingValueForTheSamePlaceholderNameDisallowed_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    report(\"{foo}={foo}\", foo1, foo2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_conflictingValueForTheSamePlaceholderNameDisallowed_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    new Test(\"{foo}={foo}\", foo1, foo2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_okToUseSamePlaceholderNameIfArgExpressionsAreIdentical_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo) {",
            "    report(\"{foo}={foo}\", foo, foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_okToUseSamePlaceholderNameIfArgExpressionsAreIdentical_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo) {",
            "    new Test(\"{foo}={foo}\", foo, foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_okToUseSamePlaceholderNameIfArgExpressionsHaveSameTokens_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo) {",
            "    report(\"{foo}={foo}\", foo.toString(), foo .toString());",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_okToUseSamePlaceholderNameIfArgExpressionsHaveSameTokens_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo) {",
            "    new Test(\"{foo}={foo}\", foo.toString(), foo .toString());",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_argsOutOfOrder_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId) {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}\", barId, foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_argsOutOfOrder_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId) {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}\", barId, foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_i18nArgsOutOfOrder_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{用户}-{地址}\", \"地址\", \"用户\");",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_i18nArgsOutOfOrder_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{用户}-{地址}\", \"地址\", \"用户\");",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_i18nArgsMatched_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{用户}-{地址}\", \"用户\", \"地址\");",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_i18nArgsMatched_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{用户}-{地址}\", \"用户\", \"地址\");",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_namedArgsCommented_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    report(",
            "        \"{foo}-{bar_id}-{camelCase}\",",
            "         /*foo=*/ barId, /*barId=*/ camelCase, /*camelCase=*/ foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_namedArgsCommented_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    new Test(",
            "        \"{foo}-{bar_id}-{camelCase}\",",
            "         /*foo=*/ barId, /*barId=*/ camelCase, /*camelCase=*/ foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_literalArgsCommented_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(",
            "        \"{foo}-{bar_id}-{CamelCase}\", /*foo=*/ 1, /*bar_id=*/ 2, /*camelCase=*/ 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_literalArgsCommented_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(",
            "        \"{foo}-{bar_id}-{CamelCase}\", /*foo=*/ 1, /*bar_id=*/ 2, /*camelCase=*/ 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_argIsMethodInvocation_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    report(\"{foo}-{bar_id}-{camelCased}\", foo, bar.id(), camel.cased());",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_argIsMethodInvocation_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    new Test(\"{foo}-{bar_id}-{camelCased}\", foo, bar.id(), camel.cased());",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_usingStringConstant_argsMatchPlaceholders_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{foo}-{bar_id}-{camelCased}\";",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    report(FORMAT_STR, foo, bar.id(), camel.cased());",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_usingStringConstant_argsMatchPlaceholders_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{foo}-{bar_id}-{camelCased}\";",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    new Test(FORMAT_STR, foo, bar.id(), camel.cased());",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_usingStringConstant_argsDoNotMatchPlaceholders_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(FORMAT_STR, 1, 2, 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_usingStringConstant_argsDoNotMatchPlaceholders_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(FORMAT_STR, 1, 2, 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_usingStringConstantConcatenated_argsDoNotMatchPlaceholders_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(FORMAT_STR + \"-{d}\", 1, 2, 3, 4);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_usingStringConstantConcatenated_argsDoNotMatchPlaceholders_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(FORMAT_STR + \"-{d}\", 1, 2, 3, 4);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_usingStringConstantInStringFormatConstant_argsDoNotMatchPlaceholders_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(FORMAT_STR, 1, 2, 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_usingStringConstantInStringFormatConstant_argsDoNotMatchPlaceholders_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(FORMAT_STR, 1, 2, 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_usingStringConstantInStringFormatConstant_argsCommented_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  void test() {",
            "    report(FORMAT_STR, /* a */ 1, /* b */ 2, /* c */ 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_usingStringConstantInStringFormatConstant_argsCommented_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  void test() {",
            "    new Test(FORMAT_STR, /* a */ 1, /* b */ 2, /* c */ 3);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_getOrIsprefixIgnorableInArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  interface Bar {",
            "    String getId();",
            "  }",
            "  interface Camel {",
            "    String isCase();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    report(\"{foo}-{bar_id}-{camelCase}\", foo, bar.getId(), camel.isCase());",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_getOrIsprefixIgnorableInArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  interface Bar {",
            "    String getId();",
            "  }",
            "  interface Camel {",
            "    String isCase();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    new Test(\"{foo}-{bar_id}-{camelCase}\", foo, bar.getId(), camel.isCase());",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_argIsLiteral_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{foo}-{bar_id}\", \"foo\", \"bar id\");",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_argIsLiteral_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{foo}-{bar_id}\", \"foo\", \"bar id\");",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_inlinedFormatDoesNotRequireArgNameMatchingForLiterals_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{foo}-{bar_id}\", 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_inlinedFormatDoesNotRequireArgNameMatchingForLiterals_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{foo}-{bar_id}\", 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_3args_inlinedFormatRequiresArgNameMatchingForExpressions_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String x, String y, String z) {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}-{z}\", x, y, z);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_3args_inlinedFormatRequiresArgNameMatchingForExpressions_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String x, String y, String z) {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}-{z}\", x, y, z);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_3args_inlinedFormatAllowsLiteralArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{foo}-{bar_id}-{z}\", 1, 2, \"a\" + \"3\");",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_3args_inlinedFormatAllowsLiteralArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{foo}-{bar_id}-{z}\", 1, 2, \"a\" + \"3\");",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatRequiresArgNameMatch_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String x, String y) {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}\", x, y);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatRequiresArgNameMatch_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String x, String y) {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}\", x, y);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatWithMiscommentedLiteralArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}\", /* bar */ 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatWithMiscommentedLiteralArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}\", /* bar */ 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatWithLiteralArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{foo}-{bar_id}\", 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatWithLiteralArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{foo}-{bar_id}\", 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatWithCommentedLiteralArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{foo}-{bar_id}\", /* foo */ 1, /* bar id */ 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_2args_inlinedFormatWithCommentedLiteralArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{foo}-{bar_id}\", /* foo */ 1, /* bar id */ 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_parenthesizedInlinedFormatDoesNotRequireArgNameMatching_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report((\"{foo}-{bar_id}\"), 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_parenthesizedInlinedFormatDoesNotRequireArgNameMatching_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test((\"{foo}-{bar_id}\"), 1, 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatWithOutOfOrderLiteralArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}\", /*bar*/ 1, /*foo*/ 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatWithOutOfOrderLiteralArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}\", /*bar*/ 1, /*foo*/ 2);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatWithOutOfOrderNamedArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String bar, String foo) {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}\", bar, foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatWithOutOfOrderNamedArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String bar, String foo) {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}\", bar, foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatWithMoreThan3Args_requiresNameMatch_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{a}-{b}-{c}-{d}\", 1, 2, 3, 4);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_inlinedFormatWithMoreThan3Args_requiresNameMatch_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{a}-{b}-{c}-{d}\", 1, 2, 3, 4);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_inlinedFormatWithMoreThan3Args_argsMatchPlaceholderNames_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(int a, int b, int c, int d) {",
            "    report(\"{a}-{b}-{c}-{d}\", a, b, c, d);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_inlinedFormatWithMoreThan3Args_argsMatchPlaceholderNames_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(int a, int b, int c, int d) {",
            "    new Test(\"{a}-{b}-{c}-{d}\", a, b, c, d);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatChecksNumberOfArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains:",
            "    report(\"{foo}-{bar_id}\", foo, barId, baz);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_inlinedFormatChecksNumberOfArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains:",
            "    new Test(\"{foo}-{bar_id}\", foo, barId, baz);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_tooManyArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains: 3 provided",
            "    report(\"{foo}-{bar_id}\", foo, barId, baz);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_tooManyArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains: 3 provided",
            "    new Test(\"{foo}-{bar_id}\", foo, barId, baz);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_tooFewArgs_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo) {",
            "    // BUG: Diagnostic contains: No value is provided for placeholder {bar_id}",
            "    report(\"{foo}-{bar_id}\", foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_tooFewArgs_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test(String foo) {",
            "    // BUG: Diagnostic contains: No value is provided for placeholder {bar_id}",
            "    new Test(\"{foo}-{bar_id}\", foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_nestingPlaceholderIsOk_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    report(\"{{foo}}\", 1);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_nestingPlaceholderIsOk_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  void test() {",
            "    new Test(\"{{foo}}\", 1);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_cannotBeUsedForMethodReference_onMethod() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test() {",
            "  // BUG: Diagnostic contains: format arguments cannot be validated",
            "    Stream.of(\"\").forEach(this::report);",
            "  }",
            "  @TemplateFormatMethod",
            "  void report(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_cannotBeUsedForMethodReference_onConstructor() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test() {",
            "  // BUG: Diagnostic contains: format arguments cannot be validated",
            "    Stream.of(\"\").forEach(Test::new);",
            "  }",
            "  @TemplateFormatMethod",
            "  Test(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void notTemplateFormatConstructor_noCheck() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  static void test(String foo1, String foo2) {",
            "    new Test(\"{foo}={foo}\", foo1, foo2);",
            "  }",
            "  Test(String template, Object... args) {}",
            "}")
        .doTest();
  }


  @Test
  public void templateStringNotTheFirstParameter() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void report(String name, @TemplateString String template, Object... args) {}",
            "  void test(int foo) {",
            "    report(\"name\", \"{foo}\", foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateStringNotTheFirstParameter_doesNotMatchPlaceholder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "class Test {",
            "  @TemplateFormatMethod",
            "  void report(String name, @TemplateString String template, Object... args) {}",
            "  void test(int bar) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    report(\"name\", \"{foo}\", bar);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_good() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    StringFormat.using(\"{foo}-{bar_id}-{camelCase}\", foo, barId, camelCase);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_goodInlinedFormat() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    StringFormat.using(\"{foo}-{bar_id}-{CamelCase}\", foo, barId, camelCase);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_concatenatedInlinedFormat() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    StringFormat.using(",
            "        \"{foo}-{bar_id}\" + \"-{CamelCase}\", foo, barId, camelCase);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_usesPrintfStyle_fail() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: 0 placeholders defined",
            "    StringFormat.using(\"%s=%s\", foo1, foo2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_conflictingValueForTheSamePlaceholderNameDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    StringFormat.using(\"{foo}={foo}\", foo1, foo2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_okToUseSamePlaceholderNameIfArgExpressionsAreIdentical() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo) {",
            "    StringFormat.using(\"{foo}={foo}\", foo, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_okToUseSamePlaceholderNameIfArgExpressionsHaveSameTokens() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo) {",
            "    StringFormat.using(\"{foo}={foo}\", foo.toString(), foo .toString());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_argsOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId) {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}\", barId, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_i18nArgsOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{用户}-{地址}\", \"地址\", \"用户\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_i18nArgsMatched() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{用户}-{地址}\", \"用户\", \"地址\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_namedArgsCommented() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    StringFormat.using(",
            "        \"{foo}-{bar_id}-{camelCase}\",",
            "         /*foo=*/ barId, /*barId=*/ camelCase, /*camelCase=*/ foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_literalArgsCommented() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(",
            "        \"{foo}-{bar_id}-{CamelCase}\", /*foo=*/ 1, /*bar_id=*/ 2, /*camelCase=*/ 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_argIsMethodInvocation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    StringFormat.using(\"{foo}-{bar_id}-{camelCased}\", foo, bar.id(), camel.cased());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_usingStringConstant_argsMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{foo}-{bar_id}-{camelCased}\";",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    StringFormat.using(FORMAT_STR, foo, bar.id(), camel.cased());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_usingStringConstant_argsDoNotMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(FORMAT_STR, 1, 2, 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_usingStringConstantConcatenated_argsDoNotMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(FORMAT_STR + \"-{d}\", 1, 2, 3, 4);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_usingStringConstantInStringFormatConstant_argsDoNotMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(FORMAT_STR, 1, 2, 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_usingStringConstantInStringFormatConstant_argsCommented() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  void test() {",
            "    StringFormat.using(FORMAT_STR, /* a */ 1, /* b */ 2, /* c */ 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_getOrIsprefixIgnorableInArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  interface Bar {",
            "    String getId();",
            "  }",
            "  interface Camel {",
            "    String isCase();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    StringFormat.using(",
            "        \"{foo}-{bar_id}-{camelCase}\", foo, bar.getId(), camel.isCase());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_argIsLiteral() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{foo}-{bar_id}\", \"foo\", \"bar id\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_inlinedFormatDoesNotRequireArgNameMatchingForLiterals() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{foo}-{bar_id}\", 1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_3args_inlinedFormatRequiresArgNameMatchingForExpressions() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String x, String y, String z) {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}-{z}\", x, y, z);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_3args_inlinedFormatAllowsLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{foo}-{bar_id}-{z}\", 1, 2, \"a\" + \"3\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_2args_inlinedFormatRequiresArgNameMatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String x, String y) {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}\", x, y);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_2args_inlinedFormatWithMiscommentedLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}\", /* bar */ 1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_2args_inlinedFormatWithLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{foo}-{bar_id}\", 1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_2args_inlinedFormatWithCommentedLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{foo}-{bar_id}\", /* foo */ 1, /* bar id */ 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_parenthesizedInlinedFormatDoesNotRequireArgNameMatching() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using((\"{foo}-{bar_id}\"), 1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_inlinedFormatWithOutOfOrderLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}\", /*bar*/ 1, /*foo*/ 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_inlinedFormatWithOutOfOrderNamedArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String bar, String foo) {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}\", bar, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_inlinedFormatWithMoreThan3Args_requiresNameMatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{a}-{b}-{c}-{d}\", 1, 2, 3, 4);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_inlinedFormatWithMoreThan3Args_argsMatchPlaceholderNames() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(int a, int b, int c, int d) {",
            "    StringFormat.using(\"{a}-{b}-{c}-{d}\", a, b, c, d);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_inlinedFormatChecksNumberOfArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains:",
            "    StringFormat.using(\"{foo}-{bar_id}\", foo, barId, baz);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_tooManyArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains: 3 provided",
            "    StringFormat.using(\"{foo}-{bar_id}\", foo, barId, baz);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_tooFewArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo) {",
            "    // BUG: Diagnostic contains: placeholder {bar_id}",
            "    StringFormat.using(\"{foo}-{bar_id}\", foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_nestingPlaceholderIsOk() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    StringFormat.using(\"{{foo}}\", 1);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatWith_cannotBeUsedForMethodReference() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  // BUG: Diagnostic contains: format arguments cannot be validated",
            "  private static final long COUNT = Stream.of(\"\").map(StringFormat::using).count();",
            "}")
        .doTest();
  }

  @Test
  public void goodFormat() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{foo}-{bar_id}-{camelCase}\");",
            "  void test(String foo, String barId, String camelCase) {",
            "    FORMAT.format(foo, barId, camelCase);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_goodInlinedFormat() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    new StringFormat(\"{foo}-{bar_id}-{CamelCase}\").format(foo, barId, camelCase);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_concatenatedInlinedFormat() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, String barId, String camelCase) {",
            "    new StringFormat(\"{foo}-{bar_id}\" + \"-{CamelCase}\")",
            "    .format(foo, barId, camelCase);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_duplicatePlaceholderNameWithConflictingValues() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo1, String foo2) {",
            "    // BUG: Diagnostic contains: {foo}",
            "    new StringFormat(\"{foo}={foo}\").format(foo1, foo2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void to_wildcardDoesNotCountAsDuplicateName() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat.Template<IllegalArgumentException> TEMPLATE =",
            "      StringFormat.to(IllegalArgumentException::new, \"{...}={...}\");",
            "  void test() {",
            "    TEMPLATE.with(1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_duplicatePlaceholderNameWithConsistentValues() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo) {",
            "    new StringFormat(\"{foo}={foo}\").format(foo, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_duplicatePlaceholderNameWithEquivalentValues() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo) {",
            "    new StringFormat(\"{foo}={foo}\").format(foo.toString(), foo .toString());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_argsOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}-{bar_id}\");",
            "  void test(String foo, String barId) {",
            "    // BUG: Diagnostic contains:",
            "    FORMAT.format(barId, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_i18nArgsOutOfOrder() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{用户}-{地址}\");",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    FORMAT.format(\"地址\", \"用户\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_i18nArgsMatched() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{用户}-{地址}\");",
            "  void test() {",
            "    FORMAT.format(\"用户\", \"地址\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_namedArgsCommented() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{foo}-{bar_id}-{camelCase}\");",
            "  void test(String foo, String barId, String camelCase) {",
            "    FORMAT.format(/*foo=*/ barId, /*barId=*/ camelCase, /*camelCase=*/ foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_literalArgsCommented() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{foo}-{bar_id}-{CamelCase}\");",
            "  void test() {",
            "    FORMAT.format(/*foo=*/ 1, /*bar_id=*/ 2, /*camelCase=*/ 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_argIsMethodInvocation() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{foo}-{bar_id}-{camelCased}\");",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    FORMAT.format(foo, bar.id(), camel.cased());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_usingStringConstant_argsMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{foo}-{bar_id}-{camelCased}\";",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(FORMAT_STR);",
            "  interface Bar {",
            "    String id();",
            "  }",
            "  interface Camel {",
            "    String cased();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    FORMAT.format(foo, bar.id(), camel.cased());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_usingStringConstant_argsDoNotMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(FORMAT_STR).format(1, 2, 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_usingStringConstantConcatenated_argsDoNotMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}-{c}\";",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(FORMAT_STR + \"-{d}\").format(1, 2, 3, 4);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_usingStringConstantInStringFormatConstant_argsDoNotMatchPlaceholders() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(FORMAT_STR);",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    FORMAT.format(1, 2, 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_usingStringConstantInStringFormatConstant_argsCommented() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final String FORMAT_STR =",
            "      \"{a}-{b}\" + \"-{c}\";",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(FORMAT_STR);",
            "  void test() {",
            "    FORMAT.format(/* a */ 1, /* b */ 2, /* c */ 3);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_getOrIsprefixIgnorableInArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT =",
            "      new StringFormat(\"{foo}-{bar_id}-{camelCase}\");",
            "  interface Bar {",
            "    String getId();",
            "  }",
            "  interface Camel {",
            "    String isCase();",
            "  }",
            "  void test(String foo, Bar bar, Camel camel) {",
            "    FORMAT.format(foo, bar.getId(), camel.isCase());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_argIsLiteral() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}-{bar_id}\");",
            "  void test() {",
            "    FORMAT.format(\"foo\", \"bar id\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_inlinedFormatDoesNotRequireArgNameMatchingForLiterals() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}-{bar_id}\").format(1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_3args_inlinedFormatRequiresArgNameMatchingForExpressions() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String x, String y, String z) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}-{z}\").format(x, y, z);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_3args_inlinedFormatAllowsLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}-{bar_id}-{z}\").format(1, 2, \"a\" + \"3\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_2args_inlinedFormatRequiresArgNameMatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String x, String y) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}\").format(x, y);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_2args_inlinedFormatWithMiscommentedLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}\").format(/* bar */ 1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_2args_inlinedFormatWithLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}-{bar_id}\").format(1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_2args_inlinedFormatWithCommentedLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}-{bar_id}\").format(/* foo */ 1, /* bar id */ 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_parenthesizedInlinedFormatDoesNotRequireArgNameMatching() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat((\"{foo}-{bar_id}\")).format(1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_inlinedFormatWithOutOfOrderLiteralArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}\").format(/*bar*/ 1, /*foo*/ 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_inlinedFormatWithOutOfOrderNamedArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String bar, String foo) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}\").format(bar, foo);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_inlinedFormatWithMoreThan3Args_requiresNameMatch() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{a}-{b}-{c}-{d}\").format(1, 2, 3, 4);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_inlinedFormatWithMoreThan3Args_argsMatchPlaceholderNames() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(int a, int b, int c, int d) {",
            "    new StringFormat(\"{a}-{b}-{c}-{d}\").format(a, b, c, d);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_argLiteralDoesNotMatchPlaceholderName() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}-{bar_id}\");",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    FORMAT.format(1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_inlinedFormatChecksNumberOfArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains:",
            "    new StringFormat(\"{foo}-{bar_id}\").format(foo, barId, baz);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_tooManyArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    // BUG: Diagnostic contains: 3 provided",
            "    new StringFormat(\"{foo}-{bar_id}\").format(foo, barId, baz);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void format_tooFewArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo) {",
            "    // BUG: Diagnostic contains: placeholder {bar_id}",
            "    new StringFormat(\"{foo}-{bar_id}\").format(foo);",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void to_argLiteralDoesNotMatchPlaceholderName() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat.Template<IllegalArgumentException> TEMPLATE =",
            "      StringFormat.to(IllegalArgumentException::new, \"{foo}-{bar_id}\");",
            "  void test() {",
            "    // BUG: Diagnostic contains:",
            "    TEMPLATE.with(1, 2);",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void to_tooManyArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    StringFormat.to(IllegalArgumentException::new, \"{foo}-{bar_id}\")",
            "        // BUG: Diagnostic contains: 3 provided",
            "        .with(foo, barId, baz);",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void to_tooFewArgs() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(String foo, int barId, String baz) {",
            "    StringFormat.to(IllegalArgumentException::new, \"{foo}-{bar_id}\")",
            "        // BUG: Diagnostic contains: placeholder #2 {bar_id}",
            "        .with(foo);",
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
            "  void test() {",
            "    new StringFormat(\"{{foo}}\").format(1);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void optionalArgAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.Optional;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        .format(Optional.of(\"foo\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void optionalIntArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.OptionalInt;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ OptionalInt.empty());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void optionalLongArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.OptionalLong;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ OptionalLong.empty());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void optionalDoubleArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.OptionalDouble;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ OptionalDouble.empty());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void streamArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(Stream.of(\"foo\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void intStreamArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.IntStream;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ IntStream.empty());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void longStreamArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.LongStream;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ LongStream.empty());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void doubleStreamArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.DoubleStream;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ DoubleStream.empty());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_nonBooleanForArrowOperator_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test(int fooId) {",
            "    // BUG: Diagnostic contains: guard placeholder {foo_id ->} is",
            "    // expected to be boolean, Optional or Collection,",
            "    // whereas argument <fooId> at line 9 is of type int",
            "    query(\"SELECT {foo_id -> foo_id}\", fooId);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_primitiveBooleanForArrowOperator_allowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test(boolean isFoo) {",
            "    query(\"SELECT {is_foo -> foo}\", isFoo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_wrapperBooleanForArrowOperator_allowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test(Boolean isFoo) {",
            "    query(\"SELECT {is_foo -> foo}\", isFoo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_booleanWithArrowOperator_questionMarkAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test(boolean isFoo) {",
            "    query(\"SELECT {is_foo? -> foo}\", isFoo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_booleanWithArrowOperator_questionMarkReferenceDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  void test(boolean isFoo) {",
            "    query(",
            "        // BUG: Diagnostic contains: placeholder {is_foo? ->}",
            "        // <isFoo> at line 11. The optional placeholder references [is_foo?] to the",
            "        // right of the `->` operator should only be used for an optional placeholder",
            "        \"SELECT {is_foo? -> is_foo?}\",",
            "        isFoo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_optionalParameterForArrowOperator_allowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.Optional;",
            "class Test {",
            "  void test(Optional<String> foo) {",
            "    query(\"SELECT {foo? -> , concat(foo?, 'foo?')}\", foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_optionalParameterForArrowOperator_notProperWord_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.Optional;",

            "class Test {",
            "  void test(Optional<String> foo) {",
            "    // BUG: Diagnostic contains: {foo->} must be an identifier followed by a '?'",
            "    query(\"SELECT {foo -> , concat(foo, 'foo')}\", foo);",
            "  }",

            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_optionalParameterForArrowOperator_missingQuestionMark_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.Optional;",

            "class Test {",
            "  void test(Optional<String> foo) {",
            "    // BUG: Diagnostic contains: {foo->} must be an identifier followed by a '?'",
            "    query(\"SELECT {foo -> , concat(foo, 'foo')}\", foo);",
            "  }",

            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_optionalParameterForArrowOperator_typoInReference_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.Optional;",

            "class Test {",
            "  void test(Optional<String> foo) {",
            "    // BUG: Diagnostic contains: to the right of {foo?->}: [food?]",
            "    query(\"SELECT {foo? -> , concat(food?, 'foo')}\", foo);",
            "  }",

            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_collectionParameterForArrowOperator_allowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.Set;",
            "class Test {",
            "  void test(Set<String> foo) {",
            "    query(\"SELECT {foo? -> , concat(foo?, 'foo?')}\", foo);",
            "  }",
            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_collectionParameterForArrowOperator_notProperWord_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.List;",

            "class Test {",
            "  void test(List<String> foo) {",
            "    // BUG: Diagnostic contains: {foo->} must be an identifier followed by a '?'",
            "    query(\"SELECT {foo -> , concat(foo, 'foo')}\", foo);",
            "  }",

            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void
      templateFormatMethod_collectionParameterForArrowOperator_missingQuestionMark_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.List;",

            "class Test {",
            "  void test(List<String> foo) {",
            "    // BUG: Diagnostic contains: {foo->} must be an identifier followed by a '?'",
            "    query(\"SELECT {foo -> , concat(foo, 'foo')}\", foo);",
            "  }",

            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void templateFormatMethod_collectionParameterForArrowOperator_typoInReference_disallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.TemplateFormatMethod;",
            "import com.google.mu.annotations.TemplateString;",
            "import java.util.List;",

            "class Test {",
            "  void test(List<String> foo) {",
            "    // BUG: Diagnostic contains: to the right of {foo?->}: [food?]",
            "    query(\"SELECT {foo? -> , concat(food?, 'foo')}\", foo);",
            "  }",

            "  @TemplateFormatMethod",
            "  void query(@TemplateString String template, Object... args) {}",
            "}")
        .doTest();
  }

  @Test
  public void arrayArgDisallowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test() {",
            "    new StringFormat(\"{foo}\")",
            "        // BUG: Diagnostic contains:",
            "        .format(/* foo */ new int[] {1});",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void cannotPassStringFormatAsParameter() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  void test(StringFormat format) {",
            "    // BUG: Diagnostic contains:",
            "    format.format(1);",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void usingSquareBrackets_correctMethodReference() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  private static final StringFormat.WithSquareBracketedPlaceholders FORMAT =",
            "      new StringFormat.WithSquareBracketedPlaceholders(\"[foo]-[bar_id]\");",
            "  String test() {",
            "    return Stream.of(\"x\").reduce(\"\", FORMAT::format);",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void usingSquareBrackets_incorrectMethodReference() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  private static final StringFormat.WithSquareBracketedPlaceholders FORMAT =",
            "      new StringFormat.WithSquareBracketedPlaceholders(\"[foo]\");",
            "  String test() {",
            "    // BUG: Diagnostic contains: (2) will be provided",
            "    return Stream.of(\"\").reduce(\"\", FORMAT::format);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsFunctionCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}\");",
            "  private static final long COUNT = Stream.of(1).map(FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsIntFunctionCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.IntStream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}\");",
            "  private static final long COUNT = IntStream.of(1).mapToObj(FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsLongFunctionCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.LongStream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}\");",
            "  private static final long COUNT =",
            "      LongStream.of(1).mapToObj(FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsDoubleFunctionCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.DoubleStream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{foo}\");",
            "  private static final long COUNT =",
            "      DoubleStream.of(1).mapToObj(FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsFunctionIncorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  long test() {",
            "    // BUG: Diagnostic contains: (1) will be provided from ",
            "    return Stream.of(1).map(new StringFormat(\"{foo}.{bar}\")::format).count();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsBiFunctionCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  String test() {",
            "    return Stream.of(\"x\").reduce(\"\", new StringFormat(\"{a}:{b}\")::format);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsBiFunctionIncorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  String test() {",
            "    // BUG: Diagnostic contains: (2) will be provided from ",
            "    return Stream.of(\"x\").reduce(\"\", new StringFormat(\"{foo}\")::format);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsUnknownInterface() {
    helper
        .addSourceLines(
            "MyFunction.java", "interface MyFunction {", "  String test(String s);", "}")
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  MyFunction test() {",
            "    // BUG: Diagnostic contains: format() is used as a MyFunction",
            "    return new StringFormat(\"{foo}\")::format;",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void lenientFormatUsedAsMethodReferenceCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  long test() {",
            "    return Stream.of(1).map(new StringFormat(\"{foo}\")::lenientFormat).count();",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void lenientFormatUsedAsMethodReferenceIncorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  long test() {",
            "    // BUG: Diagnostic contains: (1) will be provided",
            "    return Stream.of(1).map(new StringFormat(\"{foo}{bar}\")::lenientFormat).count();",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void toWithMethodUsedAsMethodReferenceCorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  private static final StringFormat.Template<Exception> FAIL =",
            "      StringFormat.to(Exception::new, \"error: {foo}\");",
            "  long test() {",
            "    return Stream.of(1).map(FAIL::with).count();",
            "  }",
            "}")
        .doTest();
  }

  @Ignore
  @Test
  public void toWithMethodUsedAsMethodReferenceIncorrectly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  private static final StringFormat.Template<Exception> FAIL =",
            "      StringFormat.to(Exception::new, \"{foo}:{bar}\");",
            "  long test() {",
            "    // BUG: Diagnostic contains: (1) will be provided",
            "    return Stream.of(1).map(FAIL::with).count();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsFunctionButFormatStringCannotBeDetermined() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  long test(StringFormat format) {",
            "    // BUG: Diagnostic contains: definition not found",
            "    return Stream.of(1).map(format::format).count();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsFunctionWithIndex() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import com.google.common.collect.Streams;",
            "import java.util.stream.Stream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{index}:{foo}\");",
            "  private static final long COUNT =",
            "      // BUG: Diagnostic contains: FunctionWithIndex",
            "      Streams.mapWithIndex(Stream.of(1), FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsIntFunctionWithIndex() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import com.google.common.collect.Streams;",
            "import java.util.stream.IntStream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{index}:{foo}\");",
            "  private static final long COUNT =",
            "      // BUG: Diagnostic contains: IntFunctionWithIndex",
            "      Streams.mapWithIndex(IntStream.of(1), FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsLongFunctionWithIndex() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import com.google.common.collect.Streams;",
            "import java.util.stream.LongStream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{index}:{foo}\");",
            "  private static final long COUNT =",
            "      // BUG: Diagnostic contains: LongFunctionWithIndex",
            "      Streams.mapWithIndex(LongStream.of(1), FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void formatMethodReferenceUsedAsDoubleFunctionWithIndex() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "import com.google.common.collect.Streams;",
            "import java.util.stream.DoubleStream;",
            "class Test {",
            "  private static final StringFormat FORMAT = new StringFormat(\"{index}:{foo}\");",
            "  private static final long COUNT =",
            "      // BUG: Diagnostic contains: DoubleFunctionWithIndex",
            "      Streams.mapWithIndex(DoubleStream.of(1), FORMAT::format).count();",
            "}")
        .doTest();
  }

  @Test
  public void to_duplicatePlaceholderNameWithConflictingValues() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat.Template<IllegalArgumentException> TEMPLATE =",
            "      StringFormat.to(IllegalArgumentException::new, \"{foo}={foo}\");",
            "  void test() {",
            "    // BUG: Diagnostic contains: {foo}",
            "    TEMPLATE.with(/* foo */ 1, /* foo */ 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void to_duplicatePlaceholderNameWithConsistentValues() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.util.StringFormat;",
            "class Test {",
            "  private static final StringFormat.Template<IllegalArgumentException> TEMPLATE =",
            "      StringFormat.to(IllegalArgumentException::new, \"{foo}={foo}\");",
            "  void test() {",
            "    TEMPLATE.with(/* foo */ 1, /* foo expected to be the same */ 1);",
            "  }",
            "}")
        .doTest();
  }
}
