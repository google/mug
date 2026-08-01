package com.google.mu.benchmarks.parsers.parboiled;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.parserunners.BasicParseRunner;
import org.parboiled.support.ParsingResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON and JSON-with-Comments parser implemented using Java Parboiled. */
public class ParboiledJsonParser extends BaseParser<Object> {

  public static JsonValue parse(String input) {
    ParboiledJsonParser parser = Parboiled.createParser(ParboiledJsonParser.class);
    ParsingResult<Object> result = new BasicParseRunner<>(parser.jsonRoot()).run(input);
    if (!result.matched) {
      throw new RuntimeException("Parse failed");
    }
    return (JsonValue) result.resultValue;
  }

  public static JsonValue parseWithComments(String input) {
    ParboiledJsonParser parser = Parboiled.createParser(ParboiledJsonParser.class);
    ParsingResult<Object> result = new BasicParseRunner<>(parser.jsonRootWithComments()).run(input);
    if (!result.matched) {
      throw new RuntimeException("Parse failed");
    }
    return (JsonValue) result.resultValue;
  }

  // =========================================================================
  // 1. Standard JSON Rules (No Comments)
  // =========================================================================

  public Rule jsonRoot() {
    return Sequence(whitespace(), value(), EOI);
  }

  public Rule value() {
    return FirstOf(
        object(),
        array(),
        stringVal(),
        numberVal(),
        boolVal(),
        nullVal()
    );
  }

  public Rule object() {
    return Sequence(
        Symbol("{"),
        push(new LinkedHashMap<String, JsonValue>()),
        Optional(
            Sequence(
                property(),
                ZeroOrMore(
                    Symbol(","),
                    property()
                )
            )
        ),
        Symbol("}"),
        wrapObject()
    );
  }

  public Rule property() {
    return Sequence(
        stringVal(),
        Symbol(":"),
        value(),
        putProperty()
    );
  }

  public Rule array() {
    return Sequence(
        Symbol("["),
        push(new ArrayList<JsonValue>()),
        Optional(
            Sequence(
                value(),
                addArrayElement(),
                ZeroOrMore(
                    Symbol(","),
                    value(),
                    addArrayElement()
                )
            )
        ),
        Symbol("]"),
        wrapArray()
    );
  }

  public Rule Symbol(String s) {
    return Sequence(s, whitespace());
  }

  public Rule stringVal() {
    return Sequence(
        Sequence(
            '"',
            ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("\"\\"))),
            '"'
        ),
        push(new JsonString(unescape(match()))),
        whitespace()
    );
  }

  public Rule numberVal() {
    return Sequence(
        Sequence(
            Optional('-'),
            FirstOf('0', Sequence(CharRange('1', '9'), ZeroOrMore(CharRange('0', '9')))),
            Optional('.', OneOrMore(CharRange('0', '9'))),
            Optional(AnyOf("eE"), Optional(AnyOf("+-")), OneOrMore(CharRange('0', '9')))
        ),
        push(new JsonNumber(Double.parseDouble(match()))),
        whitespace()
    );
  }

  public Rule boolVal() {
    return Sequence(
        FirstOf(
            Sequence("true", push(JsonBoolean.TRUE)),
            Sequence("false", push(JsonBoolean.FALSE))
        ),
        whitespace()
    );
  }

  public Rule nullVal() {
    return Sequence(
        "null", 
        push(JsonNull.INSTANCE),
        whitespace()
    );
  }

  // =========================================================================
  // 2. JSON with Comments Rules
  // =========================================================================

  public Rule jsonRootWithComments() {
    return Sequence(whitespaceWithComments(), valueWithComments(), EOI);
  }

  public Rule valueWithComments() {
    return FirstOf(
        objectWithComments(),
        arrayWithComments(),
        stringValWithComments(),
        numberValWithComments(),
        boolValWithComments(),
        nullValWithComments()
    );
  }

  public Rule objectWithComments() {
    return Sequence(
        SymbolWithComments("{"),
        push(new LinkedHashMap<String, JsonValue>()),
        Optional(
            Sequence(
                propertyWithComments(),
                ZeroOrMore(
                    SymbolWithComments(","),
                    propertyWithComments()
                )
            )
        ),
        SymbolWithComments("}"),
        wrapObject()
    );
  }

  public Rule propertyWithComments() {
    return Sequence(
        stringValWithComments(),
        SymbolWithComments(":"),
        valueWithComments(),
        putProperty()
    );
  }

  public Rule arrayWithComments() {
    return Sequence(
        SymbolWithComments("["),
        push(new ArrayList<JsonValue>()),
        Optional(
            Sequence(
                valueWithComments(),
                addArrayElement(),
                ZeroOrMore(
                    SymbolWithComments(","),
                    valueWithComments(),
                    addArrayElement()
                )
            )
        ),
        SymbolWithComments("]"),
        wrapArray()
    );
  }

  public Rule SymbolWithComments(String s) {
    return Sequence(s, whitespaceWithComments());
  }

  public Rule stringValWithComments() {
    return Sequence(
        Sequence(
            '"',
            ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("\"\\"))),
            '"'
        ),
        push(new JsonString(unescape(match()))),
        whitespaceWithComments()
    );
  }

  public Rule numberValWithComments() {
    return Sequence(
        Sequence(
            Optional('-'),
            FirstOf('0', Sequence(CharRange('1', '9'), ZeroOrMore(CharRange('0', '9')))),
            Optional('.', OneOrMore(CharRange('0', '9'))),
            Optional(AnyOf("eE"), Optional(AnyOf("+-")), OneOrMore(CharRange('0', '9')))
        ),
        push(new JsonNumber(Double.parseDouble(match()))),
        whitespaceWithComments()
    );
  }

  public Rule boolValWithComments() {
    return Sequence(
        FirstOf(
            Sequence("true", push(JsonBoolean.TRUE)),
            Sequence("false", push(JsonBoolean.FALSE))
        ),
        whitespaceWithComments()
    );
  }

  public Rule nullValWithComments() {
    return Sequence(
        "null", 
        push(JsonNull.INSTANCE),
        whitespaceWithComments()
    );
  }

  // =========================================================================
  // 3. Shared Helpers and Actions
  // =========================================================================

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

  public Rule whitespace() {
    return ZeroOrMore(AnyOf(" \t\r\n"));
  }

  public Rule whitespaceWithComments() {
    return ZeroOrMore(
        FirstOf(
            OneOrMore(AnyOf(" \t\r\n")),
            comment()
        )
    );
  }

  public Rule comment() {
    return FirstOf(
        Sequence("/*", ZeroOrMore(Sequence(TestNot("*/"), ANY)), "*/"),
        Sequence("//", ZeroOrMore(Sequence(TestNot(AnyOf("\r\n")), ANY)), Optional(AnyOf("\r\n")))
    );
  }
}
