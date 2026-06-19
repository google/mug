package com.google.common.labs.parse;

import static com.google.mu.util.Substring.consecutive;
import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.lang.Character.isWhitespace;
import static java.lang.Math.min;

import com.google.mu.util.Substring;

record Snippet(int indentation, CharInput input, int at) {

  Snippet(CharInput input, int at) {
    this(4, input, at);
  }

  @Override public String toString() {
    String backward;
    try {
      backward = lookBackward();
    } catch (IndexOutOfBoundsException e) {
      // for streaming parsing on top of a Reader, looking back may not be
      // possible since the input of the last record may have been purged.
      return showForwardOnly();
    }
    String toShow = backward + lookForward();
    return newLineIndentedBy(indentation)
        + (toShow.isEmpty() ? "<EOF>" : toShow)
        + newLineIndentedBy(indentation + backward.length()) + "^\n";
  }

  private String showForwardOnly() {
    if (input.isEof(at)) {
      return "<EOF>";
    }
    String ahead = lookForward();
    String toShow = input.isInRange(at + ahead.length()) ? ahead + "..." : ahead;
    return "[" + toShow + "]";
  }

  private String lookForward() {
    return peek(input.snippet(at, 50), 5, 50);
  }

  private String lookBackward() {
    int chars = min(at, 25);
    String source = input.snippet(at - chars, chars);
    return reverse(peek(reverse(source), 5, 25));
  }

  private static String peek(String s, int targetChars, int maxChars) {
    for (Substring.Match segment
        : iterateOnce(consecutive(c -> !isWhitespace(c)).repeatedly().match(s))) {
      int chars = segment.index() + segment.length();
      if (chars >= maxChars) {
        return s.substring(0, maxChars);
      }
      if (chars >= targetChars) {
        return segment.before() + segment;
      }
    }
    return s;
  }

  private static String newLineIndentedBy(int chars) {
    return "\n" + " ".repeat(chars);
  }

  private static String reverse(String s) {
    return new StringBuilder(s).reverse().toString();
  }
}