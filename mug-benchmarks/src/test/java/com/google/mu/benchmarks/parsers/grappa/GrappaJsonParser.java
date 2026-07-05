package com.google.mu.benchmarks.parsers.grappa;

import com.github.fge.grappa.Grappa;
import com.github.fge.grappa.annotations.Label;
import com.github.fge.grappa.parsers.BaseParser;
import com.github.fge.grappa.rules.Rule;
import com.github.fge.grappa.run.ParseEventListener;
import com.github.fge.grappa.run.ParseRunner;
import com.github.fge.grappa.run.ParsingResult;
import com.github.fge.grappa.run.events.MatchFailureEvent;
import com.github.fge.grappa.run.events.PreParseEvent;
import com.github.fge.grappa.support.Position;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON and JSON-with-Comments parser implemented using Grappa (adapting official grappa-examples). */
public class GrappaJsonParser extends BaseParser<Object> {

  private static final GrappaJsonParser PARSER = Grappa.createParser(GrappaJsonParser.class);

  private static final ParseRunner<Object> STANDARD_RUNNER = new ParseRunner<>(PARSER.jsonText());
  private static final GrappaFailureListener STANDARD_LISTENER = new GrappaFailureListener();

  private static final ParseRunner<Object> COMMENTS_RUNNER = new ParseRunner<>(PARSER.jsonTextWithComments());
  private static final GrappaFailureListener COMMENTS_LISTENER = new GrappaFailureListener();

  static {
    STANDARD_RUNNER.registerListener(STANDARD_LISTENER);
    COMMENTS_RUNNER.registerListener(COMMENTS_LISTENER);
  }

  public static JsonValue parse(String input) {
    ParsingResult<Object> result = STANDARD_RUNNER.run(input);
    if (!result.isSuccess()) {
      Position pos = result.getInputBuffer().getPosition(STANDARD_LISTENER.getMaxFailIndex());
      throw new IllegalArgumentException("Parse failed at line " + pos.getLine() + ":" + pos.getColumn());
    }
    return (JsonValue) result.getTopStackValue();
  }

  public static JsonValue parseWithComments(String input) {
    ParsingResult<Object> result = COMMENTS_RUNNER.run(input);
    if (!result.isSuccess()) {
      Position pos = result.getInputBuffer().getPosition(COMMENTS_LISTENER.getMaxFailIndex());
      throw new IllegalArgumentException("Parse failed at line " + pos.getLine() + ":" + pos.getColumn());
    }
    return (JsonValue) result.getTopStackValue();
  }

  // =========================================================================
  // 1. Official Grappa JSON Rules (with AST building actions)
  // =========================================================================

  @Label("e")
  public Rule e() {
    return sequence(anyOf("eE"), optional(anyOf("+-")));
  }

  @Label("digits")
  public Rule digits() {
    return oneOrMore(digit());
  }

  @Label("exp")
  public Rule exp() {
    return sequence(e(), digits());
  }

  @Label("frac")
  public Rule frac() {
    return sequence('.', digits());
  }

  @Label("int")
  public Rule integer() {
    return sequence(
        optional('-'),
        firstOf(
            sequence(charRange('1', '9'), zeroOrMore(digit())),
            digit()
        )
    );
  }

  @Label("number")
  public Rule number() {
    return sequence(
        sequence(integer(), optional(frac()), optional(exp())),
        push(new JsonNumber(Double.parseDouble(match())))
    );
  }

  @Label("charUnescaped")
  public Rule charUnescaped() {
    return sequence(
        testNot(
            firstOf(anyOf("\\\""), ctl())
        ),
        unicodeRange(0, 0x10ffff)
    );
  }

  @Label("charEscaped")
  public Rule charEscaped() {
    return sequence(
        '\\',
        firstOf(
            anyOf("\"\\/bfnrt"),
            sequence('u', repeat(hexDigit()).times(4))
        )
    );
  }

  @Label("char")
  public Rule character() {
    return firstOf(charUnescaped(), charEscaped());
  }

  @Label("string")
  public Rule string() {
    return sequence(
        '"',
        zeroOrMore(character()),
        push(new JsonString(unescape(match()))),
        '"'
    );
  }

  @Label("booleanOrNull")
  public Rule booleanOrNull() {
    return sequence(
        trie("true", "false", "null"),
        pushBooleanOrNull(match())
    );
  }

  @Label("primitiveValue")
  public Rule primitiveValue() {
    return firstOf(string(), number(), booleanOrNull());
  }

  @Label("whiteSpaces")
  public Rule whiteSpaces() {
    return join(zeroOrMore(wsp())).using(sequence(optional(cr()), lf()))
        .min(0);
  }

  @Label("arrayElement")
  public Rule arrayElement() {
    return sequence(value(), addArrayElement());
  }

  @Label("array")
  public Rule array() {
    return sequence(
        '[',
        push(new ArrayList<JsonValue>()),
        whiteSpaces(),
        join(arrayElement())
            .using(sequence(whiteSpaces(), ',', whiteSpaces()))
            .min(0),
        whiteSpaces(),
        ']',
        wrapArray()
    );
  }

  @Label("objectMember")
  public Rule objectMember() {
    return sequence(string(), whiteSpaces(), ':', whiteSpaces(), value(), putProperty());
  }

  @Label("object")
  public Rule object() {
    return sequence(
        '{',
        push(new LinkedHashMap<String, JsonValue>()),
        whiteSpaces(),
        join(objectMember())
            .using(sequence(whiteSpaces(), ',', whiteSpaces()))
            .min(0),
        whiteSpaces(),
        '}',
        wrapObject()
    );
  }

  @Label("value")
  public Rule value() {
    return firstOf(primitiveValue(), object(), array());
  }

  @Label("jsonText")
  public Rule jsonText() {
    return sequence(whiteSpaces(), value(), whiteSpaces(), EOI);
  }

  // =========================================================================
  // 2. Comments Support Rules (for shootout parseWithComments)
  // =========================================================================

  @Label("comment")
  public Rule comment() {
    return firstOf(
        sequence("/*", zeroOrMore(sequence(testNot("*/"), ANY)), "*/"),
        sequence("//", zeroOrMore(sequence(testNot(anyOf("\r\n")), ANY)), optional(anyOf("\r\n")))
    );
  }

  @Label("whiteSpacesWithComments")
  public Rule whiteSpacesWithComments() {
    return zeroOrMore(
        firstOf(
            oneOrMore(anyOf(" \t\r\n")),
            comment()
        )
    );
  }

  @Label("arrayElementWithComments")
  public Rule arrayElementWithComments() {
    return sequence(valueWithComments(), addArrayElement());
  }

  @Label("arrayWithComments")
  public Rule arrayWithComments() {
    return sequence(
        '[',
        push(new ArrayList<JsonValue>()),
        whiteSpacesWithComments(),
        join(arrayElementWithComments())
            .using(sequence(whiteSpacesWithComments(), ',', whiteSpacesWithComments()))
            .min(0),
        whiteSpacesWithComments(),
        ']',
        wrapArray()
    );
  }

  @Label("objectMemberWithComments")
  public Rule objectMemberWithComments() {
    return sequence(string(), whiteSpacesWithComments(), ':', whiteSpacesWithComments(), valueWithComments(), putProperty());
  }

  @Label("objectWithComments")
  public Rule objectWithComments() {
    return sequence(
        '{',
        push(new LinkedHashMap<String, JsonValue>()),
        whiteSpacesWithComments(),
        join(objectMemberWithComments())
            .using(sequence(whiteSpacesWithComments(), ',', whiteSpacesWithComments()))
            .min(0),
        whiteSpacesWithComments(),
        '}',
        wrapObject()
    );
  }

  @Label("valueWithComments")
  public Rule valueWithComments() {
    return firstOf(primitiveValue(), objectWithComments(), arrayWithComments());
  }

  @Label("jsonTextWithComments")
  public Rule jsonTextWithComments() {
    return sequence(whiteSpacesWithComments(), valueWithComments(), whiteSpacesWithComments(), EOI);
  }

  // =========================================================================
  // 3. Shared Helpers and Stack Actions
  // =========================================================================

  boolean pushBooleanOrNull(String s) {
    if ("true".equals(s)) push(JsonBoolean.TRUE);
    else if ("false".equals(s)) push(JsonBoolean.FALSE);
    else push(JsonNull.INSTANCE);
    return true;
  }

  boolean putProperty() {
    JsonValue val = (JsonValue) pop();
    JsonString keyStr = (JsonString) pop();
    @SuppressWarnings("unchecked")
    Map<String, JsonValue> map = (Map<String, JsonValue>) peek();
    map.put(keyStr.value(), val);
    return true;
  }

  boolean addArrayElement() {
    JsonValue val = (JsonValue) pop();
    @SuppressWarnings("unchecked")
    List<JsonValue> list = (List<JsonValue>) peek();
    list.add(val);
    return true;
  }

  boolean wrapArray() {
    @SuppressWarnings("unchecked")
    List<JsonValue> list = (List<JsonValue>) pop();
    push(new JsonArray(list));
    return true;
  }

  boolean wrapObject() {
    @SuppressWarnings("unchecked")
    Map<String, JsonValue> map = (Map<String, JsonValue>) pop();
    push(new JsonObject(map));
    return true;
  }

  String unescape(String literal) {
    return com.google.mu.benchmarks.parsers.BenchmarkInputs.unescape(literal);
  }
}
