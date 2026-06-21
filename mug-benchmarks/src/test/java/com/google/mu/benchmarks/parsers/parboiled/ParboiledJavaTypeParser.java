package com.google.mu.benchmarks.parsers.parboiled;

import com.google.mu.benchmarks.parsers.javatype.*;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.parserunners.BasicParseRunner;
import org.parboiled.support.ParsingResult;
import org.parboiled.support.Var;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.mu.benchmarks.parsers.parboiled.ParboiledShowdown.ParboiledParser;

/** Parboiled-based parser for Java Types. */
@BuildParseTree
public class ParboiledJavaTypeParser extends ParboiledParser {

  private static final Set<String> PRIMITIVES = Set.of(
      "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  );

  // =========================================================================
  // 1. Whitespace and Comment Skipping
  // =========================================================================
  public Rule skip() {
    return ZeroOrMore(AnyOf(" \t\r\n"));
  }

  public Rule token(String s) {
    return Sequence(s, skip());
  }

  public Rule token(char c) {
    return Sequence(c, skip());
  }

  // =========================================================================
  // 2. String and Number Literals (No whitespace skipping inside)
  // =========================================================================
  public Rule stringLiteral() {
    return Sequence(quotedString(), skip());
  }

  public Rule integerPart() {
    return Sequence(
        Optional('-'),
        OneOrMore(CharRange('0', '9'))
    );
  }

  public Rule numberVal() {
    return Sequence(
        Sequence(
            integerPart(),
            Optional(Sequence('.', OneOrMore(CharRange('0', '9'))))
        ),
        pushNumber(match()),
        skip()
    );
  }

  // =========================================================================
  // 3. Package and Identifiers
  // =========================================================================
  public Rule packageSegment() {
    return Sequence(
        Sequence(
            CharRange('a', 'z'),
            ZeroOrMore(FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9')))
        ),
        checkNotPrimitive(match()),
        push(match()),
        skip()
    );
  }

  public Rule packagePrefix(Var<List<String>> pkg) {
    return ZeroOrMore(
        Sequence(
            packageSegment(),
            addAction(pkg, (String) pop()),
            token('.')
        )
    );
  }

  public Rule identifier() {
    return Sequence(
        Sequence(
            FirstOf(CharRange('a', 'z'), CharRange('A', 'Z')),
            ZeroOrMore(FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9'), '_'))
        ),
        push(match()),
        skip()
    );
  }

  public Rule primitiveType() {
    return Sequence(
        FirstOf("int", "double", "float", "long", "short", "byte", "char", "boolean", "void"),
        push(match()),
        skip()
    );
  }

  public Rule typeName() {
    return FirstOf(
        primitiveType(),
        Sequence(
            Sequence(
                CharRange('A', 'Z'),
                ZeroOrMore(FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9')))
            ),
            push(match()),
            skip()
        )
    );
  }

  // =========================================================================
  // 4. Annotations and Annotation Parameters
  // =========================================================================
  public Rule javaAnnotation() {
    Var<List<String>> pkg = new Var<>(new ArrayList<>());
    Var<List<String>> types = new Var<>(new ArrayList<>());
    Var<Map<String, AnnotationValue>> params = new Var<>(new LinkedHashMap<>());

    return Sequence(
        '@', skip(),
        packagePrefix(pkg),
        typeName(),
        addAction(types, (String) pop()),
        ZeroOrMore(
            Sequence(
                token('.'),
                typeName(),
                addAction(types, (String) pop())
            )
        ),
        Optional(annotationParams(params)),
        pushAnnotation(pkg, types, params)
    );
  }

  public Rule annotationParams(Var<Map<String, AnnotationValue>> params) {
    return FirstOf(
        namedParamsList(params),
        singleParam(params)
    );
  }

  public Rule namedParamsList(Var<Map<String, AnnotationValue>> params) {
    return Sequence(
        token('('),
        namedParam(params),
        ZeroOrMore(
            Sequence(
                token(','),
                namedParam(params)
            )
        ),
        token(')')
    );
  }

  public Rule namedParam(Var<Map<String, AnnotationValue>> params) {
    Var<String> name = new Var<>();
    return Sequence(
        identifier(),
        setAction(name, (String) pop()),
        token('='),
        annotationValue(),
        putAction(params, name, (AnnotationValue) pop())
    );
  }

  public Rule singleParam(Var<Map<String, AnnotationValue>> params) {
    return Sequence(
        token('('),
        annotationValue(),
        putAction(params, "value", (AnnotationValue) pop()),
        token(')')
    );
  }

  // =========================================================================
  // 5. Annotation Values
  // =========================================================================
  public Rule annotationValue() {
    return FirstOf(
        stringVal(),
        nestedAnnoVal(),
        classLiteralVal(),
        numberVal0(),
        arrayVal()
    );
  }

  public Rule stringVal() {
    return Sequence(
        stringLiteral(),
        push(new AnnotationValue.StringValue((String) pop()))
    );
  }

  public Rule nestedAnnoVal() {
    return Sequence(
        javaAnnotation(),
        push(new AnnotationValue.AnnotationValueHolder((JavaAnnotation) pop()))
    );
  }

  public Rule classLiteralVal() {
    return Sequence(
        javaTypeParser(),
        token(".class"),
        push(new AnnotationValue.ClassLiteralValue((JavaType) pop()))
    );
  }

  public Rule numberVal0() {
    return Sequence(
        numberVal(),
        push(new AnnotationValue.NumberValue((Number) pop()))
    );
  }

  public Rule arrayVal() {
    Var<List<AnnotationValue>> list = new Var<>(new ArrayList<>());
    return Sequence(
        token('{'),
        Optional(
            Sequence(
                annotationValue(),
                addAction(list, (AnnotationValue) pop()),
                ZeroOrMore(
                    Sequence(
                        token(','),
                        annotationValue(),
                        addAction(list, (AnnotationValue) pop())
                    )
                )
            )
        ),
        token('}'),
        push(new AnnotationValue.ArrayValue(list.get()))
    );
  }

  // =========================================================================
  // 6. Type Segments and Java Type Parser
  // =========================================================================
  public Rule typeSegment() {
    return FirstOf(
        typeSegmentWithArgs(),
        typeSegmentWithoutArgs()
    );
  }

  public Rule typeSegmentWithArgs() {
    Var<List<JavaAnnotation>> annos = new Var<>(new ArrayList<>());
    Var<List<JavaType>> args = new Var<>(new ArrayList<>());

    return Sequence(
        ZeroOrMore(
            Sequence(
                javaAnnotation(),
                addAction(annos, (JavaAnnotation) pop())
            )
        ),
        typeName(),
        token('<'),
        javaTypeParser(),
        addAction(args, (JavaType) pop()),
        ZeroOrMore(
            Sequence(
                token(','),
                javaTypeParser(),
                addAction(args, (JavaType) pop())
            )
        ),
        token('>'),
        pushTypeSegment(annos, (String) pop(), args)
    );
  }

  public Rule typeSegmentWithoutArgs() {
    Var<List<JavaAnnotation>> annos = new Var<>(new ArrayList<>());

    return Sequence(
        ZeroOrMore(
            Sequence(
                javaAnnotation(),
                addAction(annos, (JavaAnnotation) pop())
            )
        ),
        typeName(),
        pushTypeSegment(annos, (String) pop())
    );
  }

  public Rule javaTypeParser() {
    Var<List<String>> pkg = new Var<>(new ArrayList<>());
    Var<List<TypeSegment>> segments = new Var<>(new ArrayList<>());
    Var<Integer> dims = new Var<>(0);

    return Sequence(
        packagePrefix(pkg),
        typeSegment(),
        addAction(segments, (TypeSegment) pop()),
        ZeroOrMore(
            Sequence(
                Sequence(token('.'), TestNot("class")),
                typeSegment(),
                addAction(segments, (TypeSegment) pop())
            )
        ),
        ZeroOrMore(
            Sequence(
                token('['), token(']'),
                incrementAction(dims)
            )
        ),
        pushJavaType(pkg, segments, dims)
    );
  }

  public Rule entry() {
    return Sequence(skip(), javaTypeParser(), EOI);
  }

  // =========================================================================
  // 7. Action Helper Methods
  // =========================================================================


  boolean pushNumber(String matched) {
    if (matched.contains(".")) {
      push(Double.valueOf(matched));
    } else {
      push(Integer.valueOf(matched));
    }
    return true;
  }

  boolean checkNotPrimitive(String matched) {
    return !PRIMITIVES.contains(matched);
  }

  <T> boolean addAction(Var<List<T>> list, T val) {
    list.get().add(val);
    return true;
  }

  boolean setAction(Var<String> var, String val) {
    var.set(val);
    return true;
  }

  boolean putAction(
      Var<Map<String, AnnotationValue>> params, Var<String> name, AnnotationValue val) {
    params.get().put(name.get(), val);
    return true;
  }

  boolean putAction(Var<Map<String, AnnotationValue>> params, String name, AnnotationValue val) {
    params.get().put(name, val);
    return true;
  }

  boolean pushAnnotation(
      Var<List<String>> pkg,
      Var<List<String>> types,
      Var<Map<String, AnnotationValue>> params) {
    String name =
        String.join(".", pkg.get())
            + (pkg.get().isEmpty() ? "" : ".")
            + String.join(".", types.get());
    push(new JavaAnnotation(name, params.get()));
    return true;
  }

  boolean pushTypeSegment(Var<List<JavaAnnotation>> annos, String name, Var<List<JavaType>> args) {
    push(new TypeSegment(annos.get(), name, args.get()));
    return true;
  }

  boolean pushTypeSegment(Var<List<JavaAnnotation>> annos, String name) {
    push(new TypeSegment(annos.get(), name, Collections.emptyList()));
    return true;
  }

  boolean incrementAction(Var<Integer> var) {
    var.set(var.get() + 1);
    return true;
  }

  boolean pushJavaType(Var<List<String>> pkg, Var<List<TypeSegment>> segments, Var<Integer> dims) {
    push(new JavaType(pkg.get(), segments.get(), dims.get()));
    return true;
  }

  // =========================================================================
  // 8. Public Parse Entry Point
  // =========================================================================
  private static final ParboiledJavaTypeParser PARSER = Parboiled.createParser(ParboiledJavaTypeParser.class);
  private static final BasicParseRunner<Object> RUNNER = new BasicParseRunner<>(PARSER.entry());

  public static JavaType parse(String input) {
    ParsingResult<Object> result = RUNNER.run(input);
    if (!result.matched) {
      throw new IllegalArgumentException("Parboiled parsing error: failed to match input.");
    }
    return (JavaType) result.valueStack.peek();
  }
}
