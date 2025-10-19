package com.google.common.labs.parse;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

/**
 * An abstraction over sequentially read characters.
 *
 * <p>Indexes passed to this API generally are expected to be in strict sequence. For example, it's
 * illegal to call {@code charAt(i + 1)} if you haven't called {@code charAt(i)}.
 */
interface CharInput {

  /** Reads the character at {@code index}. */
  char charAt(int index);

  /** Do the characters starting from {@code index} start with {@code prefix}? */
  boolean startsWith(String prefix, int index);

  /** Is {@code index} the end of the input? */
  boolean isEof(int index);

  @Override
  String toString();

  default boolean isInRange(int index) {
    return !isEof(index);
  }

  default boolean isEmpty() {
    return isEof(0);
  }

  /** Returns a snippet of string starting from {@code index} with at most {@code maxChars}. */
  String snippet(int index, int maxChars);

  /** An input backed by in-memory string. */
  static CharInput from(String str) {
    requireNonNull(str);
    return new CharInput() {
      @Override
      public char charAt(int index) {
        return str.charAt(index);
      }

      @Override
      public boolean startsWith(String prefix, int index) {
        return str.startsWith(prefix, index);
      }

      @Override
      public boolean isEof(int index) {
        return index >= str.length();
      }

      @Override
      public String snippet(int index, int maxLength) {
        return str.substring(index, Math.min(str.length(), index + maxLength));
      }

      @Override
      public String toString() {
        return str;
      }
    };
  }

  /** A lazily-loaded input from {@code reader}. */
  static CharInput from(Reader reader) {
    requireNonNull(reader);
    return new CharInput() {
      private final char[] temp = new char[8192];
      private final StringBuilder chars = new StringBuilder(temp.length);
      private int charsAlreadyRead = 0;

      @Override
      public char charAt(int index) {
        checkSequentialIndex(index);
        ensureCharCount(index + 1);
        return chars.charAt(index);
      }

      @Override
      public boolean startsWith(String prefix, int index) {
        checkSequentialIndex(index);
        ensureCharCount(index + prefix.length());
        for (int i = 0; i < prefix.length(); i++) {
          int bufIndex = index + i;
          if (bufIndex >= chars.length() || prefix.charAt(i) != chars.charAt(bufIndex)) {
            return false;
          }
        }
        return true;
      }

      @Override
      public boolean isEof(int index) {
        checkSequentialIndex(index);
        ensureCharCount(index + 1);
        return index == chars.length();
      }

      @Override
      public String snippet(int index, int maxLength) {
        checkSequentialIndex(index);
        ensureCharCount(index + maxLength);
        return chars.substring(index, Math.min(chars.length(), index + maxLength));
      }

      @Override
      public String toString() {
        return chars.toString();
      }

      private void ensureCharCount(int numChars) {
        if (charsAlreadyRead > numChars) {
          return;
        }
        for (int missing = numChars - chars.length() + 1; missing > 0; ) {
          try {
            int loaded = reader.read(temp);
            if (loaded <= 0) { // no more to load
              break;
            }
            chars.append(temp, 0, loaded);
            missing -= loaded;
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        charsAlreadyRead = numChars;
      }

      private void checkSequentialIndex(int index) {
        if (index < 0 || index > charsAlreadyRead) {
        throw new IllegalStateException(
            String.format(
                "index (%s) must be in range of [0, %s]",
                index, charsAlreadyRead));
        }
      }
    };
  }
}
