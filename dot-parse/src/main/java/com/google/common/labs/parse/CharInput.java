package com.google.common.labs.parse;

import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

import com.google.mu.util.Substring;

/** An abstraction over sequentially read characters. */
abstract class CharInput {

  /** Reads the character at {@code index}. */
  abstract char charAt(int index);

  /** Returns the index of {@code str} starting from {@code fromIndex}, or -1 if not found. */
  abstract int indexOf(String str, int fromIndex);

  /** Do the characters starting from {@code index} start with {@code prefix}? */
  abstract boolean startsWith(String prefix, int index);

  /** Is {@code index} the end of the input? */
  abstract boolean isEof(int index);

  final boolean isInRange(int index) {
    return !isEof(index);
  }

  /** Returns a snippet of string starting from {@code index} with at most {@code maxChars}. */
  abstract String snippet(int index, int maxChars);

  /** characters before {@code checkpointIndex} are no longer needed. */
  void markCheckpoint(int checkpointIndex) {}

  /**
   * Returns the source position of the character at {@code at}. It's assumed that the index {@code
   * at} has been read.
   */
  abstract String sourcePosition(int at);

  /** An input backed by in-memory string. */
  static CharInput from(String text) {
    requireNonNull(text);
    return new CharInput() {
      @Override char charAt(int index) {
        return text.charAt(index);
      }

      @Override int indexOf(String str, int fromIndex) {
        return text.indexOf(str, fromIndex);
      }

      @Override boolean startsWith(String prefix, int index) {
        return text.startsWith(prefix, index);
      }

      @Override boolean isEof(int index) {
        return index >= text.length();
      }

      @Override String snippet(int index, int maxLength) {
        return text.substring(index, Math.min(text.length(), index + maxLength));
      }

      @Override String sourcePosition(int at) {
        int line = 1;
        int lineStartIndex = 0;
        for (Substring.Match match :
            iterateOnce(Substring.all('\n').match(text).takeWhile(m -> m.index() < at))) {
          lineStartIndex = match.index() + 1;
          line++;
        }
        return line + ":" + (at - lineStartIndex + 1);
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
      private final StringBuilder chars = new StringBuilder();
      private int garbageCharCount = 0;

      @Override char charAt(int index) {
        ensureCharCount(index + 1);
        return chars.charAt(toPhysicalIndex(index));
      }

      @Override int indexOf(String str, int fromIndex) {
        for (int i = fromIndex; i >= 0; ) {
          ensureCharCount(i + str.length());
          int fromPhysicalIndex = toPhysicalIndex(i);
          if (fromPhysicalIndex + str.length() > chars.length()) {
            return -1;
          }
          int foundPhysicalIndex = chars.indexOf(str, fromPhysicalIndex);
          if (foundPhysicalIndex >= fromPhysicalIndex) {
            return foundPhysicalIndex + garbageCharCount;
          }
          i = garbageCharCount + chars.length() - str.length() + 1;
        }
        return -1;
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

      @Override String sourcePosition(int at) {
        return garbageCharCount > 0
            ? Integer.toString(at)
            : from(chars.toString()).sourcePosition(at);
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
