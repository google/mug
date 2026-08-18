package com.google.mu.errorprone.regex;

import static com.google.mu.errorprone.regex.RegexPatternUtils.findOverlappingQuantifiers;
import static com.google.mu.errorprone.regex.RegexPatternUtils.unwrapGroup;
import static com.google.mu.util.Optionals.optionally;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.graph.Walker;

/**
 * Synthesizes safe alternatives (Safe Regex, Substring, StringFormat, dot-parse Parser) for
 * vulnerable regexes.
 */
final class SuggestionSynthesizer {

  private SuggestionSynthesizer() {}

  static Optional<Suggestion.RegexSuggestion> rewriteRedosToSafeRegex(RegexPattern pattern) {
    requireNonNull(pattern);
    RegexPattern rewritten = transform(pattern, SuggestionSynthesizer::rewriteRedosNode);
    return optionally(
        !rewritten.equals(pattern), () -> new Suggestion.RegexSuggestion(rewritten.toString()));
  }

  private static Optional<RegexPattern> rewriteRedosNode(RegexPattern node) {
    if (node instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified innerQ) {
        boolean canBeEmpty = innerQ.metadata().minSize() == 0 || q.metadata().minSize() == 0;
        return Optional.of(
            new RegexPattern.Quantified(
                innerQ.element(), RegexPattern.Quantifier.atLeast(canBeEmpty ? 0 : 1)));
      }
      return optionally(
          inner.metadata().minSize() == 0 && inner.metadata().maxSize() > 0,
          () -> new RegexPattern.Quantified(inner, RegexPattern.Quantifier.atLeast(0)));
    }
    return Optional.empty();
  }

  static Optional<Suggestion.RegexSuggestion> rewritePolynomialToSafeRegex(RegexPattern pattern) {
    requireNonNull(pattern);
    List<String> caveats = new ArrayList<>();
    RegexPattern rewritten = transform(pattern, node -> rewritePolynomialNode(node, caveats));
    return optionally(
        !rewritten.equals(pattern),
        () ->
            caveats.isEmpty()
                ? new Suggestion.RegexSuggestion(rewritten.toString())
                : new Suggestion.RegexSuggestion(rewritten.toString(), caveats.get(0)));
  }

  private static Optional<RegexPattern> rewritePolynomialNode(
      RegexPattern node, List<String> caveats) {
    if (node instanceof RegexPattern.Sequence seq) {
      return findOverlappingQuantifiers(seq)
          .map(p -> {
            if (p.secondIndex() == p.firstIndex() + 1
                && p.first().element().equals(p.second().element())
                && p.first().quantifier() instanceof RegexPattern.AtLeast q1
                && p.second().quantifier() instanceof RegexPattern.AtLeast q2) {
              int totalMin = q1.min() + q2.min();
              RegexPattern merged = new RegexPattern.Quantified(
                  p.first().element(), RegexPattern.Quantifier.atLeast(totalMin));
              RegexPattern preservedMerged =
                  preserveGroup(seq.elements().get(p.firstIndex()), merged);
              List<RegexPattern> newElements = new ArrayList<>(seq.elements());
              newElements.set(p.firstIndex(), preservedMerged);
              newElements.remove(p.secondIndex());
              return newElements.size() == 1
                  ? newElements.get(0)
                  : new RegexPattern.Sequence(newElements);
            }
            RegexPattern rewrittenFirst = new RegexPattern.Quantified(
                p.first().element(), p.first().quantifier().possessive());
            RegexPattern preservedFirst =
                preserveGroup(seq.elements().get(p.firstIndex()), rewrittenFirst);
            List<RegexPattern> newElements = new ArrayList<>(seq.elements());
            newElements.set(p.firstIndex(), preservedFirst);
            caveats.add(
                "Possessive quantifier '" + rewrittenFirst
                    + "' prevents backtracking and may fail if subsequent tokens require"
                    + " characters greedily consumed by '" + rewrittenFirst + "'");
            return newElements.size() == 1
                ? newElements.get(0)
                : new RegexPattern.Sequence(newElements);
          })
          .findFirst();
    }
    return Optional.empty();
  }

  private static RegexPattern preserveGroup(RegexPattern original, RegexPattern rewritten) {
    if (original instanceof RegexPattern.Group.Capturing) {
      return new RegexPattern.Group.Capturing(rewritten);
    }
    if (original instanceof RegexPattern.Group.NonCapturing) {
      return new RegexPattern.Group.NonCapturing(rewritten);
    }
    if (original instanceof RegexPattern.Group.Named g) {
      return new RegexPattern.Group.Named(g.name(), rewritten);
    }
    return rewritten;
  }

  private static RegexPattern transform(
      RegexPattern node, Function<RegexPattern, Optional<RegexPattern>> rule) {
    Optional<RegexPattern> direct = rule.apply(node);
    if (direct.isPresent()) {
      return direct.get();
    }
    return switch (node) {
      case RegexPattern.Sequence seq -> {
        List<RegexPattern> newElements =
            seq.elements().stream().map(e -> transform(e, rule)).toList();
        yield newElements.size() == 1 ? newElements.get(0) : new RegexPattern.Sequence(newElements);
      }
      case RegexPattern.Group.Capturing g ->
          new RegexPattern.Group.Capturing(transform(g.content(), rule));
      case RegexPattern.Group.NonCapturing g ->
          new RegexPattern.Group.NonCapturing(transform(g.content(), rule));
      case RegexPattern.Group.Atomic g ->
          new RegexPattern.Group.Atomic(transform(g.content(), rule));
      case RegexPattern.Group.Named g ->
          new RegexPattern.Group.Named(g.name(), transform(g.content(), rule));
      case RegexPattern.Quantified q ->
          new RegexPattern.Quantified(transform(q.element(), rule), q.quantifier());
      case RegexPattern.Alternation alt -> new RegexPattern.Alternation(
          alt.alternatives().stream().map(a -> transform(a, rule)).toList());
      case RegexPattern.Lookaround.Lookahead l ->
          new RegexPattern.Lookaround.Lookahead(transform(l.target(), rule));
      case RegexPattern.Lookaround.NegativeLookahead l ->
          new RegexPattern.Lookaround.NegativeLookahead(transform(l.target(), rule));
      case RegexPattern.Lookaround.Lookbehind l ->
          new RegexPattern.Lookaround.Lookbehind(transform(l.target(), rule));
      case RegexPattern.Lookaround.NegativeLookbehind l ->
          new RegexPattern.Lookaround.NegativeLookbehind(transform(l.target(), rule));
      default -> node;
    };
  }

  static Optional<String> suggestRedosRewrite(RegexPattern pattern) {
    return rewriteRedosToSafeRegex(pattern).map(Suggestion.RegexSuggestion::replacement);
  }

  static Optional<String> suggestPolynomialRewrite(RegexPattern pattern) {
    return rewritePolynomialToSafeRegex(pattern).map(Suggestion.RegexSuggestion::replacement);
  }

  static Optional<Suggestion.ParserSuggestion> rewriteToParser(RegexPattern pattern) {
    pattern = unwrapGroup(requireNonNull(pattern));
    if (isStructuredNumberGrammar(pattern)) {
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
          if (tokenParser.isPresent() && delimExt.isPresent()
              && !tokenParser.get().contains("\"" + delimExt.get().delimiter() + "\"")) {
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
    RegexPattern innerP =
        pattern instanceof RegexPattern.Quantified q ? unwrapGroup(q.element()) : pattern;
    if (innerP instanceof RegexPattern.Sequence seq && seq.elements().size() == 2) {
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
          Optional<DelimiterExtraction> d =
              extractDelimiter(delimPattern).or(() -> extractLiteralDelimiter(delimPattern));
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

  private static Optional<DelimiterExtraction> extractLiteralDelimiter(RegexPattern p) {
    p = unwrapGroup(p);
    if (p instanceof RegexPattern.Literal lit) {
      return optionally(
          !lit.value().isEmpty(),
          () -> new DelimiterExtraction(lit.value(), lit.value().isBlank()));
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
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Literal lit) {
        return optionally(
            !lit.value().isEmpty(),
            () ->
                lit.value().isBlank()
                    ? new DelimiterExtraction(" ", true)
                    : new DelimiterExtraction(lit.value(), false));
      }
      return extractDelimiter(inner);
    }
    if (p instanceof RegexPattern.Literal lit) {
      return optionally(
          !lit.value().isEmpty() && (lit.value().isBlank() || isPunctuationDelimiter(lit.value())),
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

  private static boolean isPunctuationDelimiter(String s) {
    return s.chars().allMatch(c -> ",;:|/\\ \t\r\n".indexOf(c) >= 0);
  }

  private static boolean isOptionalWhitespace(RegexPattern p) {
    p = unwrapGroup(p);
    return p instanceof RegexPattern.Quantified q
        && q.metadata().minSize() == 0
        && switch (unwrapGroup(q.element())) {
          case RegexPattern.PredefinedCharClass pcc ->
              pcc == RegexPattern.PredefinedCharClass.WHITESPACE;
          case RegexPattern.CharacterSet.AnyOf cs ->
              cs.elements().stream().allMatch(SuggestionSynthesizer::isWhitespaceCharSetElement);
          case RegexPattern.Literal lit -> lit.value().isBlank();
          default -> false;
        };
  }

  private static boolean isWhitespaceCharSetElement(RegexPattern.CharSetElement elem) {
    return switch (elem) {
      case RegexPattern.PredefinedCharClass pcc ->
          pcc == RegexPattern.PredefinedCharClass.WHITESPACE;
      case RegexPattern.LiteralChar lc -> Character.isWhitespace(lc.value());
      case RegexPattern.CharRange range ->
          Character.isWhitespace(range.start()) && Character.isWhitespace(range.end());
      default -> false;
    };
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
      if (inner instanceof RegexPattern.PosixCharClass pcc) {
        return switch (pcc) {
          case ALPHA -> Optional.of("Parser.consecutive(\"[a-zA-Z]\")");
          case ALNUM -> Optional.of("Parser.consecutive(\"[a-zA-Z0-9]\")");
          case DIGIT -> Optional.of("Parser.consecutive(\"[0-9]\")");
          case LOWER -> Optional.of("Parser.consecutive(\"[a-z]\")");
          case UPPER -> Optional.of("Parser.consecutive(\"[A-Z]\")");
          case SPACE, BLANK -> Optional.of("Parser.consecutive(\"[ \\t]\")");
          default -> Optional.empty();
        };
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

  private static boolean isStructuredNumberGrammar(RegexPattern pattern) {
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

  static Optional<Suggestion.SubstringSuggestion> rewriteToSubstring(RegexPattern pattern) {
    pattern = unwrapGroup(requireNonNull(pattern));
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

  static Optional<Suggestion.StringFormatSuggestion> rewriteToStringFormat(RegexPattern pattern) {
    pattern = unwrapGroup(requireNonNull(pattern));
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

  private static boolean isWildcard(RegexPattern pattern) {
    return pattern instanceof RegexPattern.Quantified q
        && unwrapGroup(q.element()) == RegexPattern.PredefinedCharClass.ANY_CHAR;
  }
}
