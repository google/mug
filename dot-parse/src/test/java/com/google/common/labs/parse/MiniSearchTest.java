package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.word;
import static com.google.common.truth.Truth.assertThat;

import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MiniSearchTest {

  @Test public void testTerm() {
    assertThat(SearchCriteria.parse("foo"))
        .isEqualTo(new SearchCriteria.Term("foo"));
  }

  @Test public void testQuoted() {
    assertThat(SearchCriteria.parse("\"foo AND bar\""))
        .isEqualTo(new SearchCriteria.Term("foo AND bar"));
  }

  @Test public void testQuotedWithEscape() {
    assertThat(SearchCriteria.parse("\"foo AND \\\"bar\\\"\""))
        .isEqualTo(new SearchCriteria.Term("foo AND \"bar\""));
  }

  @Test public void testAnd() {
    assertThat(SearchCriteria.parse("foo AND bar"))
        .isEqualTo(new SearchCriteria.And(new SearchCriteria.Term("foo"), new SearchCriteria.Term("bar")));
  }

  @Test public void testNot() {
    assertThat(SearchCriteria.parse("NOT bar"))
        .isEqualTo(new SearchCriteria.Not(new SearchCriteria.Term("bar")));
  }

  @Test public void testOr() {
    assertThat(SearchCriteria.parse("foo OR bar"))
        .isEqualTo(new SearchCriteria.Or(new SearchCriteria.Term("foo"), new SearchCriteria.Term("bar")));
  }

  @Test public void testNested() {
    assertThat(SearchCriteria.parse("(foo OR bar) AND baz"))
        .isEqualTo(
            new SearchCriteria.And(
                new SearchCriteria.Or(new SearchCriteria.Term("foo"), new SearchCriteria.Term("bar")),
                new SearchCriteria.Term("baz")));
  }

  sealed interface SearchCriteria
      permits SearchCriteria.Term, SearchCriteria.And, SearchCriteria.Or, SearchCriteria.Not {

    record Term(String term) implements SearchCriteria {}

    record And(SearchCriteria left, SearchCriteria right) implements SearchCriteria {}

    record Or(SearchCriteria left, SearchCriteria right) implements SearchCriteria {}

    record Not(SearchCriteria criteria) implements SearchCriteria {}

    static SearchCriteria parse(String input) {
      Set<String> keywords = Set.of("AND", "OR", "NOT");

      // A search term is either quoted, or unquoted (but cannot be a keyword)
      Parser<Term> unquoted =
          word().suchThat(w -> !keywords.contains(w), "search term").map(Term::new);
      Parser<Term> quoted = Parser.quotedStringWithEscapes('"', Parser.chars(1)).map(Term::new);

      // Leaf-level search term can be a quoted, unquoted term, or a sub-criteria inside parentheses.
      // They are then grouped by the boolean operators.
      Parser<SearchCriteria> parser = Parser.define(
          sub -> new OperatorTable<SearchCriteria>()
              .prefix("NOT", Not::new, 30)
              .leftAssociative("AND", And::new, 20)
              .leftAssociative("OR", Or::new, 10)
              .build(Parser.anyOf(unquoted, quoted, sub.between("(", ")"))));

       // Skip the whitespaces
      return parser.parseSkipping(Character::isWhitespace, input);
    }
  }
}
