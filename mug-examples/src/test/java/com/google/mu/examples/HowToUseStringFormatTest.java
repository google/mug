package com.google.mu.examples;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.mu.util.StringFormat;

@RunWith(JUnit4.class)
public class HowToUseStringFormatTest {

  @Test public void testIt() {
  }

  private String badUsage() {
	  return new StringFormat("{key}:{value}").parseOrThrow("k:v", key -> key);
  }
}
