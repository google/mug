package com.google.mu.errorprone.regex;

import static com.google.mu.errorprone.regex.RegexPatternUtils.findOverlappingQuantifiers;
import static com.google.mu.errorprone.regex.RegexPatternUtils.isWildcard;
import static com.google.mu.errorprone.regex.RegexPatternUtils.unwrapGroup;
import static com.google.mu.util.Optionals.optionally;
import static java.util.Objects.requireNonNull;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.graph.Walker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Synthesizes safe alternatives (Safe Regex, Substring, StringFormat, dot-parse Parser) for
 * vulnerable regexes.
 */
final class SuggestionSynthesizer {
  private final RegexPattern pattern;

  SuggestionSynthesizer(RegexPattern pattern) {
    this.pattern = requireNonNull(pattern);
  }

  List<Suggestion> forRedos() {
    return suggestions(suggestRedosRegexSuggestion());
  }

  List<Suggestion> forPolynomial() {
    return suggestions(suggestPolynomialRegexSuggestion());
  }

  Optional<String> suggestRedosRewrite() {
    return suggestRedosRegexSuggestion().map(Suggestion.RegexSuggestion::replacement);
  }

  Optional<String> suggestPolynomialRewrite() {
    return suggestPolynomialRegexSuggestion().map(Suggestion.RegexSuggestion::replacement);
  }

  private List<Suggestion> suggestions(Optional<Suggestion.RegexSuggestion> regexSuggestion) {
    List<Suggestion> suggestions = new ArrayList<>();
    regexSuggestion.ifPresent(suggestions::add);
    Optional<Suggestion.SubstringSuggestion> substring = suggestSubstring();
    Optional<Suggestion.StringFormatSuggestion> stringFormat = suggestStringFormat();
    if (substring.isPresent() && substring.get().caveats().isEmpty()) {
      suggestions.add(substring.get());
    } else {
      stringFormat.ifPresent(suggestions::add);
      substring.ifPresent(suggestions::add);
    }
    suggestParser().ifPresent(suggestions::add);
    return List.copyOf(suggestions);
  }

  private Optional<Suggestion.RegexSuggestion> suggestRedosRegexSuggestion() {
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified innerQ) {
        boolean canBeEmpty = innerQ.metadata().minSize() == 0 || q.metadata().minSize() == 0;
        String op = canBeEmpty ? "*" : "+";
        String replacement = innerQ.element().toString() + op;
        return Optional.of(new Suggestion.RegexSuggestion(replacement));
      }
      return optionally(
          inner.metadata().minSize() == 0 && inner.metadata().maxSize() > 0,
          () -> new Suggestion.RegexSuggestion(inner.toString() + "*"));
    }
    return Optional.empty();
  }

  private Optional<Suggestion.RegexSuggestion> suggestPolynomialRegexSuggestion() {
    if (pattern instanceof RegexPattern.Sequence seq) {
      return findOverlappingQuantifiers(seq)
          .map(p -> {
            if (p.secondIndex() == p.firstIndex() + 1
                && p.first().element().equals(p.second().element())
                && p.first().quantifier() instanceof RegexPattern.AtLeast q1
                && p.second().quantifier() instanceof RegexPattern.AtLeast q2) {
              int totalMin = q1.min() + q2.min();
              RegexPattern merged = new RegexPattern.Quantified(
                  p.first().element(), RegexPattern.Quantifier.atLeast(totalMin));
              List<RegexPattern> rewritten = new ArrayList<>(seq.elements());
              rewritten.set(p.firstIndex(), merged);
              rewritten.remove(p.secondIndex());
              String replacement =
                  rewritten.size() == 1
                      ? rewritten.get(0).toString()
                      : new RegexPattern.Sequence(rewritten).toString();
              return new Suggestion.RegexSuggestion(replacement);
            }
            RegexPattern rewrittenFirst = new RegexPattern.Quantified(
                p.first().element(), p.first().quantifier().possessive());
            List<RegexPattern> rewritten = new ArrayList<>(seq.elements());
            rewritten.set(p.firstIndex(), rewrittenFirst);
            String replacement = new RegexPattern.Sequence(rewritten).toString();
            return new Suggestion.RegexSuggestion(
                replacement,
                "Possessive quantifier '" + rewrittenFirst
                    + "' prevents backtracking and may fail if subsequent tokens require"
                    + " characters greedily consumed by '" + rewrittenFirst + "'");
          })
          .findFirst();
    }
    return Optional.empty();
  }

  private Optional<Suggestion.ParserSuggestion> suggestParser() {
    if (isStructuredNumberGrammar()) {
      return Optional.of(
          new Suggestion.ParserSuggestion(
              "Parsers.UNSIGNED_INTEGER.atLeastOnce()",
              "Parser combinators parse deterministically with prioritized choice and do not"
                  + " backtrack non-deterministically across ambiguous boundaries"));
    }
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Sequence seq) {
        Optional<KeyValueExtraction> kv = extractKeyValue(seq.elements());
        if (kv.isPresent()) {
          Optional<String> kp = translateToParser(kv.get().key());
          Optional<String> vp = translateToParser(kv.get().value());
          if (kp.isPresent() && vp.isPresent()) {
            String combinator =
                q.metadata().minSize() > 0 ? "atLeastOnceDelimitedBy" : "zeroOrMoreDelimitedBy";
            String replacement =
                "Parser.sequence(" + kp.get() + ".followedBy(\"=\"), " + vp.get() + ", Map::entry)."
                    + combinator + "(\"" + kv.get().delim().delimiter() + "\")";
            return Optional.of(
                new Suggestion.ParserSuggestion(
                    replacement, false, parserCaveats(kv.get().hasWhitespace())));
          }
        }
        if (seq.elements().size() >= 2) {
          RegexPattern token = unwrapGroup(seq.elements().get(0));
          List<RegexPattern> delimElements = seq.elements().subList(1, seq.elements().size());
          RegexPattern delimPattern =
              delimElements.size() == 1
                  ? delimElements.get(0)
                  : new RegexPattern.Sequence(delimElements);
          Optional<String> tokenParser = translateToParser(token);
          Optional<DelimiterExtraction> delimExt = extractDelimiter(delimPattern);
          if (tokenParser.isPresent() && delimExt.isPresent()) {
            String combinator =
                q.metadata().minSize() > 0 ? "atLeastOnceDelimitedBy" : "zeroOrMoreDelimitedBy";
            String replacement =
                tokenParser.get() + "." + combinator + "(\"" + delimExt.get().delimiter() + "\")";
            return Optional.of(
                new Suggestion.ParserSuggestion(
                    replacement, false, parserCaveats(delimExt.get().hasWhitespace())));
          }
        }
      }
    }
    RegexPattern p =
        pattern instanceof RegexPattern.Quantified q ? unwrapGroup(q.element()) : pattern;
    if (p instanceof RegexPattern.Sequence seq && seq.elements().size() == 2) {
      RegexPattern token1 = unwrapGroup(seq.elements().get(0));
      RegexPattern rest = unwrapGroup(seq.elements().get(1));
      if (rest instanceof RegexPattern.Quantified rq) {
        RegexPattern restInner = unwrapGroup(rq.element());
        if (restInner instanceof RegexPattern.Sequence restSeq && restSeq.elements().size() >= 2) {
          RegexPattern token2 = unwrapGroup(restSeq.elements().get(restSeq.elements().size() - 1));
          List<RegexPattern> delimElements =
              restSeq.elements().subList(0, restSeq.elements().size() - 1);
          RegexPattern delimPattern =
              delimElements.size() == 1
                  ? delimElements.get(0)
                  : new RegexPattern.Sequence(delimElements);
          Optional<String> t1 = translateToParser(token1);
          Optional<String> t2 = translateToParser(token2);
          Optional<DelimiterExtraction> d = extractDelimiter(delimPattern);
          return optionally(
              t1.isPresent() && t1.equals(t2) && d.isPresent(),
              () -> {
                String replacement =
                    t1.get() + ".atLeastOnceDelimitedBy(\"" + d.get().delimiter() + "\")";
                return new Suggestion.ParserSuggestion(
                    replacement, false, parserCaveats(d.get().hasWhitespace()));
              });
        }
      }
    }
    return Optional.empty();
  }

  private record DelimiterExtraction(String delimiter, boolean hasWhitespace) {}

  private record KeyValueExtraction(
      RegexPattern key, RegexPattern value, DelimiterExtraction delim, boolean hasWhitespace) {}

  private static Optional<KeyValueExtraction> extractKeyValue(List<RegexPattern> elements) {
    int eqIndex = -1;
    for (int i = 0; i < elements.size(); i++) {
      RegexPattern elem = unwrapGroup(elements.get(i));
      if (elem instanceof RegexPattern.Literal lit && lit.value().equals("=")) {
        eqIndex = i;
        break;
      }
    }
    if (eqIndex <= 0 || eqIndex >= elements.size() - 1) {
      return Optional.empty();
    }
    List<RegexPattern> left = new ArrayList<>();
    boolean hasWhitespace = false;
    for (int i = 0; i < eqIndex; i++) {
      RegexPattern elem = unwrapGroup(elements.get(i));
      if (isOptionalWhitespace(elem)) {
        hasWhitespace = true;
      } else {
        left.add(elem);
      }
    }
    if (left.size() != 1) {
      return Optional.empty();
    }
    RegexPattern key = left.get(0);

    int valIndex = eqIndex + 1;
    while (valIndex < elements.size()
        && isOptionalWhitespace(unwrapGroup(elements.get(valIndex)))) {
      hasWhitespace = true;
      valIndex++;
    }
    if (valIndex >= elements.size()) {
      return Optional.empty();
    }
    RegexPattern value = unwrapGroup(elements.get(valIndex));

    List<RegexPattern> delimElements = new ArrayList<>();
    for (int i = valIndex + 1; i < elements.size(); i++) {
      delimElements.add(elements.get(i));
    }
    if (delimElements.isEmpty()) {
      return Optional.empty();
    }
    RegexPattern delimPattern =
        delimElements.size() == 1 ? delimElements.get(0) : new RegexPattern.Sequence(delimElements);
    Optional<DelimiterExtraction> delim = extractDelimiter(delimPattern);
    boolean ws = hasWhitespace;
    return delim.map(d -> new KeyValueExtraction(key, value, d, ws || d.hasWhitespace()));
  }

  private static Optional<DelimiterExtraction> extractDelimiter(RegexPattern p) {
    p = unwrapGroup(p);
    if (p instanceof RegexPattern.Quantified q && q.metadata().minSize() == 0) {
      return extractDelimiter(q.element());
    }
    if (p instanceof RegexPattern.Literal lit) {
      return optionally(
          !lit.value().isEmpty(),
          () ->
              lit.value().isBlank()
                  ? new DelimiterExtraction(" ", true)
                  : new DelimiterExtraction(lit.value(), false));
    }
    if (p instanceof RegexPattern.PredefinedCharClass pcc
        && pcc == RegexPattern.PredefinedCharClass.WHITESPACE) {
      return Optional.of(new DelimiterExtraction(" ", true));
    }
    if (p instanceof RegexPattern.Sequence seq) {
      boolean hasWhitespace = false;
      List<RegexPattern> nonWhitespace = new ArrayList<>();
      for (RegexPattern elem : seq.elements()) {
        RegexPattern unwrapped = unwrapGroup(elem);
        if (isOptionalWhitespace(unwrapped)) {
          hasWhitespace = true;
        } else {
          nonWhitespace.add(unwrapped);
        }
      }
      if (nonWhitespace.isEmpty() && hasWhitespace) {
        return Optional.of(new DelimiterExtraction(" ", true));
      }
      if (nonWhitespace.size() == 1) {
        RegexPattern single = nonWhitespace.get(0);
        if (single instanceof RegexPattern.Literal lit) {
          return optionally(
              !lit.value().isEmpty(), () -> new DelimiterExtraction(lit.value(), true));
        }
        if (single instanceof RegexPattern.Quantified q && q.metadata().minSize() == 0) {
          RegexPattern qInner = unwrapGroup(q.element());
          if (qInner instanceof RegexPattern.Literal lit) {
            return optionally(
                !lit.value().isEmpty(), () -> new DelimiterExtraction(lit.value(), true));
          }
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isOptionalWhitespace(RegexPattern p) {
    p = unwrapGroup(p);
    if (p instanceof RegexPattern.Quantified q && q.metadata().minSize() == 0) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.PredefinedCharClass pcc
          && pcc == RegexPattern.PredefinedCharClass.WHITESPACE) {
        return true;
      }
      if (inner instanceof RegexPattern.CharacterSet.AnyOf cs) {
        return cs.elements().stream().allMatch(SuggestionSynthesizer::isWhitespaceCharSetElement);
      }
      if (inner instanceof RegexPattern.Literal lit && lit.value().isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isWhitespaceCharSetElement(RegexPattern.CharSetElement elem) {
    if (elem instanceof RegexPattern.PredefinedCharClass pcc) {
      return pcc == RegexPattern.PredefinedCharClass.WHITESPACE;
    }
    if (elem instanceof RegexPattern.LiteralChar lc) {
      return Character.isWhitespace(lc.value());
    }
    if (elem instanceof RegexPattern.CharRange range) {
      return Character.isWhitespace(range.start()) && Character.isWhitespace(range.end());
    }
    return false;
  }

  private static List<String> parserCaveats(boolean hasWhitespace) {
    List<String> caveats = new ArrayList<>();
    caveats.add(
        "Parser combinators parse deterministically with prioritized choice and do not backtrack"
            + " non-deterministically across ambiguous boundaries");
    if (hasWhitespace) {
      caveats.add(
          "Use parseSkipping(Character::isWhitespace, input) to skip surrounding whitespace"
              + " during parsing");
    }
    return List.copyOf(caveats);
  }

  private static Optional<String> translateToParser(RegexPattern p) {
    p = unwrapGroup(p);
    if (p instanceof RegexPattern.Quantified q && q.metadata().minSize() > 0) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.CharacterSet.AnyOf cs) {
        return Optional.of("Parser.consecutive(\"" + cs + "\")");
      }
      if (inner instanceof RegexPattern.CharacterSet.NoneOf cs) {
        return Optional.of("Parser.consecutive(\"" + cs + "\")");
      }
      if (inner instanceof RegexPattern.PredefinedCharClass pcc) {
        return switch (pcc) {
          case WORD -> Optional.of("Parser.consecutive(\"[a-zA-Z0-9_]\")");
          case DIGIT -> Optional.of("Parser.consecutive(\"[0-9]\")");
          case WHITESPACE -> Optional.of("Parser.consecutive(\"[ \\t\\r\\n]\")");
          case NON_WHITESPACE -> Optional.of("Parser.consecutive(\"[^ \\t\\r\\n]\")");
          default -> Optional.empty();
        };
      }
      if (inner instanceof RegexPattern.Literal lit) {
        return optionally(
            lit.value().length() == 1, () -> "Parser.consecutive(\"" + lit.value() + "\")");
      }
    }
    return Optional.empty();
  }

  private boolean isStructuredNumberGrammar() {
    return Walker.inTree(RegexPatternUtils::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Alternation.class::isInstance)
        .map(RegexPattern.Alternation.class::cast)
        .anyMatch(alt -> {
          if (alt.alternatives().size() == 2) {
            RegexPattern b0 = unwrapGroup(alt.alternatives().get(0));
            RegexPattern b1 = unwrapGroup(alt.alternatives().get(1));
            return (b0 instanceof RegexPattern.Literal lit && lit.value().equals("0")
                    && b1 instanceof RegexPattern.Sequence)
                || (b1 instanceof RegexPattern.Literal lit2 && lit2.value().equals("0")
                    && b0 instanceof RegexPattern.Sequence);
          }
          return false;
        });
  }

  private Optional<Suggestion.SubstringSuggestion> suggestSubstring() {
    if (pattern instanceof RegexPattern.Sequence seq) {
      List<RegexPattern> elements = seq.elements();
      if (elements.size() == 3) {
        RegexPattern e0 = unwrapGroup(elements.get(0));
        RegexPattern e1 = elements.get(1);
        RegexPattern e2 = unwrapGroup(elements.get(2));
        if (isWildcard(e0) && e1 instanceof RegexPattern.Literal lit && isWildcard(e2)) {
          String delim = lit.value();
          return optionally(
              !delim.isEmpty(),
              () -> {
                String delimEscaped = delim.length() == 1 ? "'" + delim + "'" : "\"" + delim + "\"";
                String replacement = "Substring.last(" + delimEscaped + ").split(input)";
                return new Suggestion.SubstringSuggestion(replacement);
              });
        }
      }
      if (elements.size() == 5) {
        RegexPattern e0 = unwrapGroup(elements.get(0));
        RegexPattern e1 = elements.get(1);
        RegexPattern e2 = unwrapGroup(elements.get(2));
        RegexPattern e3 = elements.get(3);
        RegexPattern e4 = unwrapGroup(elements.get(4));
        if (isWildcard(e0) && e1 instanceof RegexPattern.Literal openLit && isWildcard(e2)
            && e3 instanceof RegexPattern.Literal closeLit && isWildcard(e4)) {
          String open = openLit.value();
          String close = closeLit.value();
          return optionally(
              !open.isEmpty() && !close.isEmpty(),
              () -> {
                String replacement =
                    "Substring.between(\"" + open + "\", \"" + close + "\").from(input)";
                return new Suggestion.SubstringSuggestion(
                    replacement, "Substring.between extracts the first matching enclosed range");
              });
        }
      }
    }
    return Optional.empty();
  }

  private Optional<Suggestion.StringFormatSuggestion> suggestStringFormat() {
    if (pattern instanceof RegexPattern.Sequence seq) {
      List<RegexPattern> elements = seq.elements();
      if (elements.size() == 3) {
        RegexPattern e0 = unwrapGroup(elements.get(0));
        RegexPattern e1 = elements.get(1);
        RegexPattern e2 = unwrapGroup(elements.get(2));
        if (isWildcard(e0) && e1 instanceof RegexPattern.Literal lit && isWildcard(e2)) {
          String delim = lit.value();
          return optionally(
              !delim.isEmpty(),
              () -> new Suggestion.StringFormatSuggestion(
                  "{left}" + delim + "{right}",
                  "StringFormat matches delimiters from left to right, whereas greedy '.*' in"
                      + " regex matches the last occurrence"));
        }
      }
    }
    return Optional.empty();
  }
}
