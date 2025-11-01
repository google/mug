package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.oneOrMoreCharsIn;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JavaTypeParserTest {
  @Test
  public void simpleClassName() {
    assertThat(TypeDeclaration.parse("String"))
        .isEqualTo(new SimpleTypeName("String"));
  }

  @Test
  public void primitiveTypeName() {
    assertThat(TypeDeclaration.parse("int")).isEqualTo(new SimpleTypeName("int"));
  }

  @Test
  public void fullyQualifiedClassName() {
    assertThat(TypeDeclaration.parse("java.lang.String"))
        .isEqualTo(new FullyQualifiedClassName("java.lang", "String"));
  }

  @Test
  public void fullyQualifiedClassNameWithDollarSign() {
    assertThat(TypeDeclaration.parse("java.lang.String$Inner"))
        .isEqualTo(new FullyQualifiedClassName("java.lang", "String$Inner"));
  }

  @Test
  public void arrayType() {
    assertThat(TypeDeclaration.parse("java.lang.String[]"))
        .isEqualTo(
            new ArrayType(new FullyQualifiedClassName("java.lang", "String")));
    assertThat(TypeDeclaration.parse("int[]"))
        .isEqualTo(new ArrayType(new SimpleTypeName("int")));
    assertThat(TypeDeclaration.parse("int[][]"))
        .isEqualTo(new ArrayType(new ArrayType(new SimpleTypeName("int"))));
  }

  @Test
  public void simpleNestedType() {
    assertThat(TypeDeclaration.parse("java.lang.String.Inner"))
        .isEqualTo(
            new NestedTypeName(
                new FullyQualifiedClassName("java.lang", "String"), "Inner"));
    assertThat(TypeDeclaration.parse("java.lang.String.Inner.Inner2"))
        .isEqualTo(
            new NestedTypeName(
                new NestedTypeName(
                    new FullyQualifiedClassName("java.lang", "String"), "Inner"),
                "Inner2"));
  }

  @Test
  public void parameterizedType() {
    assertThat(TypeDeclaration.parse("java.util.List<String>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "List"),
                List.of(new SimpleTypeName("String"))));
    assertThat(TypeDeclaration.parse("java.util.Map<String, Integer>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "Map"),
                List.of(
                    new SimpleTypeName("String"), new SimpleTypeName("Integer"))));
    assertThat(TypeDeclaration.parse("java.util.Map<String, java.lang.Integer>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "Map"),
                List.of(
                    new SimpleTypeName("String"),
                    new FullyQualifiedClassName("java.lang", "Integer"))));
  }

  @Test
  public void parameterizedType_withWildcards() {
    assertThat(TypeDeclaration.parse("java.util.List<?>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "List"),
                List.of(new UnboundedWildcard())));
    assertThat(TypeDeclaration.parse("java.util.List<? extends String>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "List"),
                List.of(
                    new UpperBoundedWildcard(new SimpleTypeName("String")))));
    assertThat(
            TypeDeclaration.parse(
                "java.util.List<? extends String&java.util.Comparator<?>>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "List"),
                List.of(
                    new UpperBoundedWildcard(
                        List.of(
                            new SimpleTypeName("String"),
                            new ParameterizedType(
                                new FullyQualifiedClassName("java.util", "Comparator"),
                                List.of(new UnboundedWildcard())))))));
    assertThat(TypeDeclaration.parse("java.util.List<? super String>"))
        .isEqualTo(
            new ParameterizedType(
                new FullyQualifiedClassName("java.util", "List"),
                List.of(
                    new LowerBoundedWildcard(new SimpleTypeName("String")))));
  }

  @Test
  public void nestedParameterizedType() {
    assertThat(TypeDeclaration.parse("com.google.Outer<K>.Inner<V>"))
        .isEqualTo(
            new ParameterizedType(
                new NestedTypeName(
                    new ParameterizedType(
                        new FullyQualifiedClassName("com.google", "Outer"),
                        List.of(new SimpleTypeName("K"))),
                    "Inner"),
                List.of(new SimpleTypeName("V"))));
  }

  private static final Parser<String> PACKAGE =
      oneOrMoreCharsIn("[a-z0-0_]").atLeastOnceDelimitedBy(".", joining("."));
  private static final Parser<String> IDENTIFIER =
      consecutive(Character::isJavaIdentifierPart, "identifier part");
  private static final Parser<TypeName> TYPE_NAME =
      anyOf(
          sequence(PACKAGE.followedBy("."), IDENTIFIER, FullyQualifiedClassName::new),
          IDENTIFIER.map(SimpleTypeName::new));

  /**
   * A type declaration, such as {@code java.lang.String}, {@code T}, {@code List<T>} or {@code ?
   * extends Number}.
   */
  sealed interface TypeDeclaration extends TypeParameter
      permits TypeName, ArrayType, ParameterizedType {

    /** Parses a type declaration from a string. */
    static TypeDeclaration parse(String type) {
      Parser<TypeDeclaration> parser = Parser.define(rule -> {
          Parser<WildcardType> wildcardType =
              anyOf(
                  string("?")
                      .then(word("extends"))
                      .then(rule.atLeastOnceDelimitedBy("&"))
                      .map(UpperBoundedWildcard::new),
                  string("?").then(word("super")).then(rule).map(LowerBoundedWildcard::new),
                  string("?").thenReturn(new UnboundedWildcard()));
          var typeParams =
              anyOf(wildcardType, rule).atLeastOnceDelimitedBy(",").between("<", ">");
          int precedence = 0;
          return new OperatorTable<TypeDeclaration>()
              .postfix("[]", ArrayType::new, precedence)
              .postfix(
                  typeParams.map(params -> rawType -> new ParameterizedType(rawType, params)),
                  precedence)
              .postfix(
                  string(".")
                      .then(IDENTIFIER)
                      .map(inner -> enclosing -> new NestedTypeName(enclosing, inner)),
                  precedence)
              .build(TYPE_NAME);
          });
      return parser.parseSkipping(Character::isWhitespace, type);
    }

    @Override
    abstract String toString();
  }

  /** A type name, such as {@code java.lang.String}, {@code T} or {@code int}. */
  sealed interface TypeName extends TypeDeclaration
      permits FullyQualifiedClassName, SimpleTypeName, NestedTypeName {}

  /** A fully qualified class name, such as {@code java.lang.String}. */
  record FullyQualifiedClassName(String packageName, String className) implements TypeName {
    @Override
    public String toString() {
      return packageName + "." + className;
    }
  }

  /** A simple type name, such as {@code String}, {@code T} or {@code int}. */
  record SimpleTypeName(String name) implements TypeName {
    @Override
    public String toString() {
      return name;
    }
  }

  /** A nested type name, such as {@code java.lang.String.Inner}. */
  record NestedTypeName(TypeDeclaration enclosingType, String name) implements TypeName {
    @Override
    public String toString() {
      return enclosingType + "." + name;
    }
  }

  /** An array type, such as {@code java.lang.String[]}. */
  record ArrayType(TypeDeclaration elementType) implements TypeDeclaration {
    @Override
    public String toString() {
      return elementType + "[]";
    }
  }

  /** A parameterized type, such as {@code List<T>} or {@code Map<K, V>}. */
  record ParameterizedType(TypeDeclaration rawType, List<TypeParameter> typeParameters)
      implements TypeDeclaration {
    @Override
    public String toString() {
      return rawType
          + "<"
          + typeParameters.stream().map(TypeParameter::toString).collect(joining(", "))
          + ">";
    }
  }

  /** A type parameter, such as {@code T} or {@code ? extends Number}. */
  sealed interface TypeParameter permits WildcardType, TypeDeclaration {
    @Override
    abstract String toString();
  }

  /** A wildcard type, such as {@code ?}, {@code ? extends Number} or {@code ? super String}. */
  public sealed interface WildcardType extends TypeParameter
      permits UpperBoundedWildcard, LowerBoundedWildcard, UnboundedWildcard {}

  /**
   * A wildcard type with upper bounds, such as {@code ? extends Number} or {@code ? extends
   * String&Number}.
   */
  record UpperBoundedWildcard(List<TypeDeclaration> upperBounds)
      implements WildcardType {
    public UpperBoundedWildcard(TypeDeclaration upperBound) {
      this(List.of(upperBound));
    }

    @Override
    public String toString() {
      return "? extends "
          + upperBounds.stream().map(TypeDeclaration::toString).collect(joining("&"));
    }
  }

  /** A wildcard type with lower bounds, such as {@code ? super String}. */
  record LowerBoundedWildcard(TypeDeclaration lowerBound) implements WildcardType {
    @Override
    public String toString() {
      return "? super " + lowerBound;
    }
  }

  /** An unbounded wildcard type, such as {@code ?}. */
  record UnboundedWildcard() implements WildcardType {
    @Override
    public String toString() {
      return "?";
    }
  }

  private static Parser<String> word(String word) {
    return Parser.word().suchThat(word::equals, word);
  }
}
