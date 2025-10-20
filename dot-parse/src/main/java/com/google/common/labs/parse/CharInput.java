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

  /** characters before {@code checkpointIndex} are no longer needed. */
  void markCheckpoint(int checkpointIndex) {}

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
    return from(reader, /* bufferSize= */ 8192, /* compactionThreshold= */ 128 * 1024);
  }

  /**
   * A lazily-loaded input from {@code reader}.
   *
   * @param compactionThreshold compact the buffer if we have this number of chars no longer needed.
   */
  static CharInput from(Reader reader, int bufferSize, int compactionThreshold) {
    requireNonNull(reader);
    return new CharInput() {
      private final char[] temp = new char[bufferSize];
      private final StringBuilder chars = new StringBuilder(temp.length);
      private int garbageCharCount = 0;

      @Override char charAt(int index) {
        ensureCharCount(index + 1);
        return chars.charAt(toPhysicalIndex(index));
      }

      @Override boolean startsWith(String prefix, int index) {
        ensureCharCount(index + prefix.length());
        index = toPhysicalIndex(index);
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
        return toPhysicalIndex(index) >= chars.length();
      }

      @Override String snippet(int index, int maxLength) {
        ensureCharCount(index + maxLength);
        index = toPhysicalIndex(index);
        return chars.substring(index, Math.min(chars.length(), index + maxLength));
      }

      @Override void markCheckpoint(int checkpointIndex) {
        int unused = checkpointIndex - garbageCharCount;
        if (unused > compactionThreshold) {
          chars.delete(0, unused);
          garbageCharCount += unused;
        }
      }

      @Override public String toString() {
        return chars.toString();  // Just show me what you've got
      }

      private int toPhysicalIndex(int index) {
         index -= garbageCharCount;
        if (index < 0) {
          throw new IllegalArgumentException("index must be at least " + garbageCharCount);
        }
        return index;
      }

      private void ensureCharCount(int charCount) {
        for (int missing = charCount - garbageCharCount - chars.length(); missing > 0; ) {
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
