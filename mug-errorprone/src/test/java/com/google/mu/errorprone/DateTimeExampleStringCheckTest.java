package com.google.mu.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DateTimeExampleStringCheckTest {
  private final CompilationTestHelper helper =
      CompilationTestHelper.newInstance(DateTimeExampleStringCheck.class, getClass());

  @Test
  public void mmddyyNotSupported() {
    helper
        .addSourceLines(
            "Test.java",
            "import static com.google.mu.time.DateTimeFormats.formatOf;",
            "import java.time.format.DateTimeFormatter;",
            "class Test {",
            "  private static final DateTimeFormatter FORMAT = formatOf(",
            "      // BUG: Diagnostic contains: unsupported date time example: 10/20/2023",
            "      \"10/20/2023 10:10:10\");",
            "}")
        .doTest();
  }
}