package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParametersMustMatchByNameCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(ParametersMustMatchByNameCheck.class, getClass());

  @Test
  public void onMethod_argsInWrongOrder_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width, int height) {}",
            "",
            "  void callSite(int height, int width) {",
            "    test(",
            "        // BUG: Diagnostic contains: must match",
            "        height,",
            "        width);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_parametersMatchExactly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width, int height) {}",
            "",
            "  void callSite(int height, int width) {",
            "    test(width, height);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_parametersMatchByComment() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width, int height) {}",
            "",
            "  void callSite(int height, int width) {",
            "    test(/* width */ 1, /* height= */ 2);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_parametersMatch_ignoreCommonPrefixes() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  interface Bean {",
            "    int getWidth();",
            "    int getHeight();",
            "    boolean isActive();",
            "  }",
            "  @ParametersMustMatchByName",
            "  void test(int width, int height, boolean isActive) {}",
            "",
            "  void callSite(Bean bean) {",
            "    test(bean.getWidth(), bean.getHeight(), bean.isActive());",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_varargs_succeeds() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int... nums) {}",
            "  void callSite(int a, int b) {",
            "    test(a, b);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_regularArgsFollowedByVarargs_succeeds() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(String message, int... nums) {}",
            "  void callSite(String message, int a, int b) {",
            "    test(message, a, b);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_regularArgsFollowedByVarargs_regularArgWrong_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(String message, int... nums) {}",
            "  void callSite(String s, int a, int b) {",
            "    test(",
            "        // BUG: Diagnostic contains: must match",
            "        s,",
            "        a, b);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_zeroArgsAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test() {}",
            "  void callSite() {",
            "    test();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_literalOnOneArgAllowed() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width) {}",
            "",
            "  void callSite() {",
            "    test(100);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_literalOnOneArg_incorrectComment_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width) {}",
            "  void callSite() {",
            "    test(",
            "        // BUG: Diagnostic contains: must match",
            "        /* height */ 100);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_literalOnTwoArgsOfSameType_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width, int height) {}",
            "  void callSite() {",
            "    test(",
            "        // BUG: Diagnostic contains: must match",
            "        100,",
            "        200);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onMethod_literalOnTwoArgsOfDifferentTypes_succeeds() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  void test(int width, String height) {}",
            "  void callSite() {",
            "    test(100, \"200\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onConstructor_argsInWrongOrder_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  Test(int width, int height) {}",
            "",
            "  static Test factory(int height, int width) {",
            "    return new Test(",
            "        // BUG: Diagnostic contains: must match",
            "        height,",
            "        width);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onConstructor_parametersMatchExactly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  Test(int width, int height) {}",
            "",
            "  static Test factory(int height, int width) {",
            "    return new Test(width, height);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onConstructor_crossCompilationUnit_argsInWrongOrder_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  Test(int width, int height) {}",
            "}")
        .addSourceLines(
            "Caller.java",
            "class Caller {",
            "  void callSite(int height, int width) {",
            "    new Test(",
            "        // BUG: Diagnostic contains: must match",
            "        height,",
            "        width);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onConstructor_crossCompilationUnit_parametersMatchExactly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  Test(int width, int height) {}",
            "}")
        .addSourceLines(
            "Caller.java",
            "class Caller {",
            "  void callSite(int height, int width) {",
            "    new Test(width, height);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onRecordConstructor_argsInWrongOrder_fails() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  record Dimension(int width) {}",
            "",
            "  static Dimension factory(int height) {",
            "    return new Dimension(",
            "        // BUG: Diagnostic contains: must match",
            "        height);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void onRecordConstructor_parametersMatchExactly() {
    helper
        .addSourceLines(
            "Test.java",
            "import com.google.mu.annotations.ParametersMustMatchByName;",
            "",
            "class Test {",
            "  @ParametersMustMatchByName",
            "  record Dimension(int width, int height) {}",
            "",
            "  static Dimension factory(int height, int width) {",
            "    return new Dimension(width, height);",
            "  }",
            "}")
        .doTest();
  }
}
