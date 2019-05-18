package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class BiCollectorsTest {

  @Test public void testNulls() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicStaticMethods(BiCollectors.class);
  }
}
