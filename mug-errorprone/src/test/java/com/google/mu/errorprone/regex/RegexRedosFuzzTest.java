package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.regex.RegexPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RegexRedosFuzzTest {
  private static final long SEED = 0xDEADBEEFL;
  private static final int FUZZ_ITERATIONS = 300;

  private static ExecutorService executor;

  @BeforeClass public static void setUpExecutor() {
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterClass public static void tearDownExecutor() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test public void fuzz_randomCharRanges_algebraicPropertiesHold() {
    Random rng = new Random(SEED);
    for (int i = 0; i < FUZZ_ITERATIONS; i++) {
      CharRanges a = generateRandomCharRanges(rng);
      CharRanges b = generateRandomCharRanges(rng);

      CharRanges union = a.union(b);
      CharRanges intersection = a.intersection(b);
      CharRanges complement = a.complement();

      for (int k = 0; k < 50; k++) {
        int cp = rng.nextInt(0x7F);
        boolean inA = a.contains(cp);
        boolean inB = b.contains(cp);

        assertThat(union.contains(cp)).isEqualTo(inA || inB);
        assertThat(intersection.contains(cp)).isEqualTo(inA && inB);
        assertThat(complement.contains(cp)).isEqualTo(!inA);
      }
      assertThat(a.intersection(complement).isEmpty()).isTrue();
    }
  }

  @Test public void fuzz_randomValidRegexes_analyzerNeverThrows() {
    Random rng = new Random(SEED);
    for (int i = 0; i < FUZZ_ITERATIONS; i++) {
      String regex = generateRandomRegex(rng, /* depth= */ 0, /* maxDepth= */ 3);
      try {
        RegexPattern pattern = RegexPattern.of(regex);
        try {
          Redos.checkRedosVulnerability(pattern);
        } catch (IllegalArgumentException expectedIfVulnerable) {
          // Vulnerability detected as expected
        }
        try {
          Redos.checkPolynomialBacktracking(pattern);
        } catch (IllegalArgumentException expectedIfVulnerable) {
          // Vulnerability detected as expected
        }
      } catch (IllegalArgumentException expectedIfJdkRejects) {
        // Some generated patterns may be invalid in JDK regex, which is expected
      }
    }
  }

  @Test public void fuzz_safeClassifiedRegexes_executeWithoutExponentialBlowup()
      throws InterruptedException, ExecutionException, TimeoutException {
    Random rng = new Random(SEED);
    int safeTestedCount = 0;
    for (int i = 0; i < FUZZ_ITERATIONS && safeTestedCount < 50; i++) {
      String regex = generateRandomRegex(rng, /* depth= */ 0, /* maxDepth= */ 2);
      try {
        RegexPattern ast = RegexPattern.of(regex);
        try {
          Redos.checkRedosVulnerability(ast);
        } catch (IllegalArgumentException vulnerable) {
          continue;
        }
        final Pattern compiled = Pattern.compile(regex);
        safeTestedCount++;
        final String input = repeat("a", 40) + "!";
        Future<Boolean> future = executor.submit(new Callable<Boolean>() {
          @Override public Boolean call() {
            return compiled.matcher(input).matches();
          }
        });
        boolean matches = future.get(100, TimeUnit.MILLISECONDS);
        assertThat(matches).isFalse();
      } catch (IllegalArgumentException ignored) {
        // Syntax not accepted by dot-parse or JDK
      }
    }
    assertThat(safeTestedCount).isGreaterThan(10);
  }

  private static CharRanges generateRandomCharRanges(Random rng) {
    int numRanges = rng.nextInt(4);
    CharRanges result = CharRanges.EMPTY;
    for (int i = 0; i < numRanges; i++) {
      int c1 = rng.nextInt(0x7F);
      int c2 = rng.nextInt(0x7F);
      int start = Math.min(c1, c2);
      int end = Math.max(c1, c2);
      result = result.union(CharRanges.range(start, end));
    }
    return result;
  }

  private static String generateRandomRegex(Random rng, int depth, int maxDepth) {
    if (depth >= maxDepth) {
      return generateRandomTerminal(rng);
    }
    int choice = rng.nextInt(5);
    switch (choice) {
      case 0:
        return generateRandomTerminal(rng);
      case 1:
        {
          int count = 2 + rng.nextInt(2);
          List<String> parts = new ArrayList<>();
          for (int i = 0; i < count; i++) {
            parts.add(generateRandomRegex(rng, depth + 1, maxDepth));
          }
          StringBuilder sb = new StringBuilder();
          for (String part : parts) {
            sb.append(part);
          }
          return sb.toString();
        }
      case 2:
        {
          int count = 2 + rng.nextInt(2);
          List<String> parts = new ArrayList<>();
          for (int i = 0; i < count; i++) {
            parts.add(generateRandomRegex(rng, depth + 1, maxDepth));
          }
          StringBuilder sb = new StringBuilder("(?:");
          for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
              sb.append('|');
            }
            sb.append(parts.get(i));
          }
          sb.append(')');
          return sb.toString();
        }
      case 3:
        {
          String inner = generateRandomRegex(rng, depth + 1, maxDepth);
          String q = generateRandomQuantifier(rng);
          return "(?:" + inner + ")" + q;
        }
      case 4:
        {
          String inner = generateRandomRegex(rng, depth + 1, maxDepth);
          return "(?:" + inner + ")";
        }
      default:
        return "a";
    }
  }

  private static String generateRandomTerminal(Random rng) {
    int choice = rng.nextInt(7);
    switch (choice) {
      case 0:
        return "a";
      case 1:
        return "b";
      case 2:
        return "\\d";
      case 3:
        return "\\w";
      case 4:
        return "[a-z]";
      case 5:
        return "[0-9]";
      case 6:
        return "[^a-z]";
      default:
        return "x";
    }
  }

  private static String generateRandomQuantifier(Random rng) {
    int choice = rng.nextInt(8);
    switch (choice) {
      case 0:
        return "+";
      case 1:
        return "*";
      case 2:
        return "?";
      case 3:
        return "++";
      case 4:
        return "*+";
      case 5:
        return "{1,3}";
      case 6:
        return "{2}";
      case 7:
        return "+?";
      default:
        return "+";
    }
  }

  private static String repeat(String s, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
  }
}
