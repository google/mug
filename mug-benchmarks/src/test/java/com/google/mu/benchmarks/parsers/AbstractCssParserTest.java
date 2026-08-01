package com.google.mu.benchmarks.parsers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Dimension;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Ident;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Url;
import com.google.mu.benchmarks.parsers.ast.css.Declaration;
import com.google.mu.benchmarks.parsers.ast.css.Rule.AtRule;
import com.google.mu.benchmarks.parsers.ast.css.Rule.QualifiedRule;
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import java.util.List;
import org.junit.Test;

public abstract class AbstractCssParserTest {

  protected abstract Stylesheet parse(String input) throws Throwable;

  @Test
  public void parse_nullInput_throws() {
    assertThrows(Throwable.class, () -> parse(null));
  }

  @Test
  public void parse_emptyInput_success() throws Throwable {
    Stylesheet stylesheet = parse("");
    assertThat(stylesheet.rules()).isEmpty();
  }

  @Test
  public void parse_whitespaceOnlyInput_success() throws Throwable {
    Stylesheet stylesheet = parse("   \n\t  ");
    assertThat(stylesheet.rules()).isEmpty();
  }

  @Test
  public void parse_simpleRuleset_success() throws Throwable {
    Stylesheet stylesheet = parse("h1 { color: red; }");
    assertThat(stylesheet.rules()).hasSize(1);
    
    QualifiedRule rule = (QualifiedRule) stylesheet.rules().get(0);
    assertThat(rule.selector()).isEqualTo("h1");
    assertThat(rule.declarations()).containsExactly(
        new Declaration("color", List.of(new Ident("red")), false)
    );
  }

  @Test
  public void parse_rulesetWithMultipleDeclarations_success() throws Throwable {
    Stylesheet stylesheet = parse("""
        h1, h2 {
          color: red;
          font-size: 12px;
        }
        """);
    assertThat(stylesheet.rules()).hasSize(1);
    
    QualifiedRule rule = (QualifiedRule) stylesheet.rules().get(0);
    assertThat(rule.selector()).isEqualTo("h1, h2");
    assertThat(rule.declarations()).containsExactly(
        new Declaration("color", List.of(new Ident("red")), false),
        new Declaration("font-size", List.of(new Dimension(12.0, "px")), false)
    );
  }

  @Test
  public void parse_rulesetWithImportantDeclaration_success() throws Throwable {
    Stylesheet stylesheet = parse("div { margin: 10px !important; }");
    assertThat(stylesheet.rules()).hasSize(1);
    
    QualifiedRule rule = (QualifiedRule) stylesheet.rules().get(0);
    assertThat(rule.declarations()).containsExactly(
        new Declaration("margin", List.of(new Dimension(10.0, "px")), true)
    );
  }

  @Test
  public void parse_scientificNumber_success() throws Throwable {
    Stylesheet stylesheet = parse("h1 { margin: 1.5e3px; }");
    assertThat(stylesheet.rules()).hasSize(1);
    
    QualifiedRule rule = (QualifiedRule) stylesheet.rules().get(0);
    assertThat(rule.declarations()).containsExactly(
        new Declaration("margin", List.of(new Dimension(1500.0, "px")), false)
    );
  }

  @Test
  public void parse_atRule_success() throws Throwable {
    Stylesheet stylesheet = parse("@import url(\"subs.css\");");
    assertThat(stylesheet.rules()).hasSize(1);
    
    AtRule rule = (AtRule) stylesheet.rules().get(0);
    assertThat(rule.name()).isEqualTo("import");
    assertThat(rule.options()).containsExactly(new Url("subs.css"));
  }

  @Test
  public void parse_atRule_caseInsensitiveUrl_success() throws Throwable {
    Stylesheet stylesheet = parse("@import URL(\"subs.css\");");
    assertThat(stylesheet.rules()).hasSize(1);
    
    AtRule rule = (AtRule) stylesheet.rules().get(0);
    assertThat(rule.name()).isEqualTo("import");
    assertThat(rule.options()).containsExactly(new Url("subs.css"));
  }

  @Test
  public void parse_nestedAtRule_success() throws Throwable {
    Stylesheet stylesheet = parse("""
        @media screen {
          body {
            background-color: blue;
          }
        }
        """);
    assertThat(stylesheet.rules()).hasSize(1);
    
    AtRule atRule = (AtRule) stylesheet.rules().get(0);
    assertThat(atRule.name()).isEqualTo("media");
    assertThat(atRule.options()).containsExactly(new Ident("screen"));
    assertThat(atRule.rules()).hasSize(1);
    
    QualifiedRule subRule = (QualifiedRule) atRule.rules().get(0);
    assertThat(subRule.selector()).isEqualTo("body");
    assertThat(subRule.declarations()).containsExactly(
        new Declaration("background-color", List.of(new Ident("blue")), false)
    );
  }

  @Test
  public void parse_nestedAtRuleWithPseudo_success() throws Throwable {
    Stylesheet stylesheet = parse("""
        @media screen {
          body:hover {
            background-color: blue;
          }
        }
        """);
    assertThat(stylesheet.rules()).hasSize(1);
    
    AtRule atRule = (AtRule) stylesheet.rules().get(0);
    assertThat(atRule.rules()).hasSize(1);
    
    QualifiedRule subRule = (QualifiedRule) atRule.rules().get(0);
    assertThat(subRule.selector()).isEqualTo("body:hover");
    assertThat(subRule.declarations()).containsExactly(
        new Declaration("background-color", List.of(new Ident("blue")), false)
    );
  }

  @Test
  public void parse_commentSkipping_success() throws Throwable {
    Stylesheet stylesheet = parse("""
        /* before */
        h1 {
          color: red; /* inline */
        }
        /* after */
        """);
    assertThat(stylesheet.rules()).hasSize(1);
    
    QualifiedRule rule = (QualifiedRule) stylesheet.rules().get(0);
    assertThat(rule.selector()).isEqualTo("h1");
    assertThat(rule.declarations()).containsExactly(
        new Declaration("color", List.of(new Ident("red")), false)
    );
  }

  @Test
  public void parse_invalidRulesetMissingCurly_throws() {
    assertThrows(Throwable.class, () -> parse("h1 color: red; }"));
  }

  @Test
  public void parse_invalidDeclarationMissingColon_throws() {
    assertThrows(Throwable.class, () -> parse("h1 { color red; }"));
  }

  @Test
  public void parse_unclosedComment_throws() {
    assertThrows(Throwable.class, () -> parse("h1 { color: red; } /* unclosed"));
  }

  @Test
  public void parse_bootstrapCss_success() throws Throwable {
    String bootstrapCss = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/bootstrap.css"));
    Stylesheet stylesheet = parse(bootstrapCss);
    assertThat(stylesheet.rules()).isNotEmpty();
  }
}
