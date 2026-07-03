package com.google.common.labs.parse;

import static com.google.mu.util.Substring.all;
import static com.google.mu.util.Substring.consecutive;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static com.google.mu.util.Substring.upToIncluding;
import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.lang.Character.isWhitespace;
import static java.lang.Math.min;
import static java.util.stream.Collectors.joining;

import com.google.mu.util.Substring;

record Snippet(int indentation, CharInput input, int at) {
  Snippet(CharInput input, int at) {
    this(4, input, at);
  }

  @Override
  public String toString() {
    String backward;
    try {
      backward = lookBackward();
    } catch (IndexOutOfBoundsException e) {
      // for streaming parsing on top of a Reader, looking back may not be
      // possible since the input of the last record may have been purged.
      return showForwardOnly();
    }
    String toShow = backward + first('\n').toEnd().removeFrom(lookForward());
    return newLine()
        + (toShow.isEmpty() ? "<EOF>" : indented(toShow))
        + newLine()
        + " ".repeat(upToIncluding(last('\n')).removeFrom(backward).length())
        + "^\n";
  }

  private String showForwardOnly() {
    if (input.isEof(at)) {
      return "<EOF>";
    }
    String ahead = lookForward();
    return "[" + (input.isInRange(at + ahead.length()) ? ahead + "..." : ahead) + "]";
  }

  private String lookForward() {
    return peek(input.snippet(at, 50), 5, 50);
  }

  private String lookBackward() {
    int chars = min(at, 25);
    String source = input.snippet(at - chars, chars);
    return reverse(peek(reverse(source), 15, 25));
  }

  private String indented(String s) {
    return all('\n').split(s).collect(joining(newLine()));
  }

  private String newLine() {
    return "\n" + " ".repeat(indentation);
  }

  private static String peek(String s, int targetChars, int maxChars) {
    for (Substring.Match segment :
        iterateOnce(consecutive(c -> !isWhitespace(c)).repeatedly().match(s))) {
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

  private static String reverse(String s) {
    return new StringBuilder(s).reverse().toString();
  }
}