package com.google.mu.util;

import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class SubstringInvariantsTest {
  @TestParameter private SubstringPatternVariant noOp1;
  @TestParameter private SubstringPatternVariant noOp2;

  @Test public void firstNonEmptyString_found(@TestParameter FindFirst finder) {
    assertPattern(finder.searchFor("foo"), "foo").finds("foo");
    assertPattern(finder.searchFor("foo"), "foo bar foo").finds("foo", "foo");
    assertPattern(finder.searchFor("foo"), "<foo>").finds("foo");
  }

  @Test public void firstEmptyString_found(@TestParameter FindFirst finder) {
    assertPattern(finder.searchFor(""), "").finds("");
    assertPattern(finder.searchFor(""), "f").finds("", "");
    assertPattern(finder.searchFor(""), "fo").finds("", "", "");
  }

  @Test public void firstString_notFound(@TestParameter FindFirst finder) {
    assertPattern(finder.searchFor("foo"), "bar").findsNothing();
    assertPattern(finder.searchFor("foo"), "fo").findsNothing();
    assertPattern(finder.searchFor("foo"), "").findsNothing();
  }

  @Test public void firstChar_notFound(@TestParameter FirstChar finder) {
    assertPattern(finder.searchFor('?'), "bar").findsNothing();
    assertPattern(finder.searchFor('?'), "").findsNothing();
  }

  @Test public void firstChar_found(@TestParameter FirstChar finder) {
    assertPattern(finder.searchFor('?'), "foo?bar?").finds("?", "?");
  }

  @Test  public void word_found(@TestParameter FindWord finder) {
    assertPattern(finder.searchFor("foo"), "foo bar foo zoo").finds("foo", "foo");
  }

  @Test  public void word_notFound(@TestParameter FindWord finder) {
    assertPattern(finder.searchFor("foo"), "food or barfoo").findsNothing();
  }

  @Test  public void anyWord_found(@TestParameter FindAnyWord finder) {
    assertPattern(finder.search(), " foo ").finds("foo");
    assertPattern(finder.search(), "foo bar foo zoo").finds("foo", "bar", "foo", "zoo");
  }

  @Test  public void anyWord_notFound(@TestParameter FindAnyWord finder) {
    assertPattern(finder.search(), "").findsNothing();
    assertPattern(finder.search(), " (?) ").findsNothing();
  }

  @Test public void last_found() {
    assertPattern(last("foo"), "foo bar foo").finds("foo");
    assertPattern(last(""), "ab").finds("");
  }

  @Test public void prefix_found(@TestParameter FindPrefix finder) {
    assertPattern(finder.searchFor("foo"), "foo bar foo").finds("foo");
  }

  @Test public void prefix_notFound(@TestParameter FindPrefix finder) {
    assertPattern(finder.searchFor("foo"), "bar foo").findsNothing();
    assertPattern(finder.searchFor("foo"), "").findsNothing();
  }

  @Test public void suffix_found(@TestParameter FindSuffix finder) {
    assertPattern(finder.searchFor("foo"), "foo bar foo").finds("foo");
    assertPattern(finder.searchFor(""), "ab").finds("");
  }

  @Test public void suffix_notFound(@TestParameter FindSuffix finder) {
    assertPattern(finder.searchFor("foo"), "foo bar").findsNothing();
    assertPattern(finder.searchFor("foo"), "").findsNothing();
  }

  private enum FindFirst {
    FIRST {
      @Override Substring.Pattern searchFor(String needle) {
        return first(needle);
      }
    },
    SPANNING_TO_EMPTY {
      @Override Substring.Pattern searchFor(String needle) {
        return Substring.spanningInOrder(needle, "");
      }
    },
    FIRST_REGEX {
      @Override Substring.Pattern searchFor(String needle) {
        return first(compile(quote(needle)));
      }
    },
    ;
    abstract Substring.Pattern searchFor(String needle);
  }

  private enum FirstChar {
    FIRST {
      @Override Substring.Pattern searchFor(char needle) {
        return first(needle);
      }
    },
    FIRST_REGEX {
      @Override Substring.Pattern searchFor(char needle) {
        return first(compile(quote(Character.toString(needle))));
      }
    },
    ;
    abstract Substring.Pattern searchFor(char needle);
  }

  private enum FindWord {
    WORD {
      @Override Substring.Pattern searchFor(String word) {
        return Substring.word(word);
      }
    },
    WORD_REGEX {
      @Override Substring.Pattern searchFor(String needle) {
        return first(java.util.regex.Pattern.compile("\\b" + quote(needle) + "\\b"));
      }
    },
    ;
    abstract Substring.Pattern searchFor(String word);
  }

  private enum FindAnyWord {
    WORD {
      @Override Substring.Pattern search() {
        return Substring.word();
      }
    },
    WORD_REGEX {
      @Override Substring.Pattern search() {
        return first(compile("\\w+"));
      }
    },
    ;
    abstract Substring.Pattern search();
  }

  private enum FindPrefix {
    PREFIX {
      @Override Substring.Pattern searchFor(String prefix) {
        return Substring.prefix(prefix);
      }
    },
    REGEX_PREFIX {
      @Override Substring.Pattern searchFor(String prefix) {
        return first(compile("^" + quote(prefix)));
      }
    },
    ;
    abstract Substring.Pattern searchFor(String prefix);
  }

  private enum FindSuffix {
    SUFFIX {
      @Override Substring.Pattern searchFor(String suffix) {
        return Substring.suffix(suffix);
      }
    },
    REGEX_SUFFIX {
      @Override Substring.Pattern searchFor(String suffix) {
        return first(compile(quote(suffix) + "$"));
      }
    },
    ;
    abstract Substring.Pattern searchFor(String prefix);
  }

  private SubstringPatternAssertion assertPattern(Substring.Pattern pattern, String input) {
    return new SubstringPatternAssertion(noOp1.wrap(noOp2.wrap(pattern)), input);
  }
}
