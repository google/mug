package com.google.common.labs.parse;

import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.CharPredicate.isNot;
import static java.util.stream.Collectors.reducing;

import com.google.mu.util.CharPredicate;

/**
 * Represents a set of characters specified by a regex-like character set string.
 *
 * <p>For example {@code charsIn("[a-zA-Z-_]")} is a shorthand of {@code
 * CharPredicate.range('a', 'z').orRange('A', 'Z').or('-').or('_')}.
 *
 * <p>You can also use {@code '^'} to get negative character set like:
 * {@code charsIn("[^a-zA-Z]")}, which is any non-alphabet character.
 *
 * <p>Note that it's different from {@code CharPredicate.anyOf(string)},
 * which treats the string as a list of literal characters, not a regex-like
 * character set.
 *
 * <p>It's strongly recommended to install the mug-errorprone plugin (v9.4+) in your
 * compiler's and IDE's annotationProcessorPaths so that you can get instant feedback
 * against incorrect character set syntax.
 *
 * <p>Implementation Note: regex isn't used during parsing. The character set string is translated
 * to a plain {@link CharPredicate} at construction time.
 *
 * @since 9.4
 */
public final class CharacterSet implements CharPredicate {
  private final String string;
  private final CharPredicate predicate;

  private CharacterSet(String string, CharPredicate predicate) {
    this.string = string;
    this.predicate = predicate;
  }

  /**
   * Returns a {@link CharacterSet} instance compiled from the given {@code characterSet} specifier.
   *
   * @param characterSet A regex-like character set string (e.g. {@code "[a-zA-Z0-9-_]"}),
   *        but disallows backslash so doesn't support escaping.
   *        If your character set includes special characters like literal backslash
   *        or right bracket, use {@link CharPredicate} instead.
   * @throws IllegalArgumentException if {@code characterSet} includes backslash
   *         or the right bracket (except the outmost pairs of {@code []}).
   */
  public static CharacterSet charsIn(String characterSet) {
    return new CharacterSet(characterSet, compileCharacterSet(characterSet));
  }

  /** Returns true if this set contains the character {@code ch}. */
  @Override public boolean test(char ch) {
    return predicate.test(ch);
  }

  /** Returns true if this set contains the character {@code ch}. */
  public boolean contains(char ch) {
    return predicate.test(ch);
  }

  @Override public boolean equals(Object obj) {
    return (obj instanceof CharacterSet that) && string.equals(that.string);
  }

  @Override public int hashCode() {
    return string.hashCode();
  }

  /** Returns the character set string. For example {@code "[a-zA-Z0-9-_]"}. */
  @Override public String toString() {
    return string;
  }

  private static CharPredicate compileCharacterSet(String characterSet) {
    checkArgument(characterSet.startsWith("[") && characterSet.endsWith("]"),
        "Character set must be in square brackets. Use [%s] instead.", characterSet);
    checkArgument(
        !characterSet.contains("\\"),
        "Escaping (%s) not supported in a character set. Please use CharePredicate instead.",
        characterSet);
    Parser<Character> validChar = Parser.single(isNot(']'), "character");
    Parser<CharPredicate> range =
        Parser.sequence(validChar.followedBy("-"), validChar, CharPredicate::range);
    Parser<CharPredicate>.OrEmpty positiveSet =
        Parser.anyOf(range, validChar.map(CharPredicate::is))
            .zeroOrMore(reducing(CharPredicate.NONE, CharPredicate::or));
    Parser<CharPredicate> negativeSet =
        Parser.string("^").then(positiveSet).map(CharPredicate::not);
    return negativeSet.or(positiveSet).between("[", "]").parse(characterSet);
  }
}
