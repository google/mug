package com.google.common.labs.parse;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

/** An abstraction over sequentially read characters. */
abstract class CharInput {

  /** Reads the character at {@code index}. */
  abstract char charAt(int index);

  /** Do the characters starting from {@code index} start with {@code prefix}? */
  abstract boolean startsWith(String prefix, int index);

  /** Is {@code index} the end of the input? */
  abstract boolean isEof(int index);

  final boolean isInRange(int index) {
    return !isEof(index);
  }

  final boolean isEmpty() {
    return isEof(0);
  }

  /** Returns a snippet of string starting from {@code index} with at most {@code maxChars}. */
  abstract String snippet(int index, int maxChars);

  /** An input backed by in-memory string. */
  static CharInput from(String str) {
    requireNonNull(str);
    return new CharInput() {
      @Override char charAt(int index) {
        return str.charAt(index);
      }

      @Override boolean startsWith(String prefix, int index) {
        return str.startsWith(prefix, index);
      }

      @Override boolean isEof(int index) {
        return index >= str.length();
      }

      @Override String snippet(int index, int maxLength) {
        return str.substring(index, Math.min(str.length(), index + maxLength));
      }

      @Override public String toString() {
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

      @Override char charAt(int index) {
        ensureCharCount(index + 1);
        return chars.charAt(index);
      }

      @Override boolean startsWith(String prefix, int index) {
        ensureCharCount(index + prefix.length());
        if (chars.length() < index + prefix.length()) {
          return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
          if (prefix.charAt(i) != chars.charAt(index + i)) {
            return false;
          }
        }
        return true;
      }

      @Override boolean isEof(int index) {
        ensureCharCount(index + 1);
        return index >= chars.length();
      }

      @Override String snippet(int index, int maxLength) {
        ensureCharCount(index + maxLength);
        return chars.substring(index, Math.min(chars.length(), index + maxLength));
      }

      @Override public String toString() {
        return chars.toString();  // Just show me what you've got
      }

      private void ensureCharCount(int charCount) {
        for (int missing = charCount - chars.length(); missing > 0; ) {
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
      }
    };
  }
}
