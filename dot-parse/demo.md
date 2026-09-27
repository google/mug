# Parsing Map Flag

This document uses a common, everyday parsing task of modest complexity—turning a command-line flag into a map—to show how it can be implemented in different JVM parser libraries.

## The Format

The task is to pass a `Map<String, Object>` as a command-line flag, for example `--overrides='{k1 = "v1", k2 = 3, k3 = [10, 20.5]}'`. Because the map is typed by hand on a command line, the format is intentionally kept simple: nested maps, nested lists, and complex objects would be unrealistic in a flag, so they are not supported.

- A map is enclosed in curly braces `{ ... }` and contains zero or more comma-separated `key = value` entries.
- **Keys** are unquoted and made of ASCII letters, digits, hyphens (`-`), and underscores (`_`), such as `k1`, `retry-limit`, or `8080`. Each key may appear only once.
- **Values** are either a single scalar or a flat, bracketed list of scalars `[ ... ]`:
  - **Integers** (with an optional leading `-`) are parsed as `Integer` (e.g. `3`, `-42`).
  - **Decimals** (with an optional leading `-` and a fractional part) are parsed as `Double` (e.g. `20.5`, `-0.25`).
  - As in JSON, numbers can't have leading zeros: `0` and `0.5` are valid, `05` is not.
  - **Quoted strings** are enclosed in double quotes `"..."`. A backslash makes the next character literal: `\"` is `"`, `\\` is `\`, and `\n` is `n`.
- Trailing commas are allowed in both maps and lists, and whitespace may appear around any delimiter or token.

Examples, with parsed values written as Java literals:

| Input | Parsed `Map<String, Object>` |
|---|---|
| `{}` | `{}` |
| `{k1 = "v1", k2 = 3}` | `{k1="v1", k2=3}` |
| `{k1 = "v1", k2=3, k3=[10, 20.5], }` | `{k1="v1", k2=3, k3=[10, 20.5]}` |
| `{msg = "say \"hi\"", retry-limit = -1, tags = []}` | `{msg="say \"hi\"", retry-limit=-1, tags=[]}` |
| `{k = 1, k = 2}` | Rejected (duplicate key) |

## Summary Comparison

| Library | LoC | Clarity |
|---|---|---|
| [dot-parse](../mug-examples/src/main/java/com/google/mu/examples/mapflag/dotparse/DotParseMapFlagParser.java) | 21 | High; reads directly like the grammar. |
| cats-parse (Scala) | 33 | Low; compact, but obscured by symbolic operators. |
| better-parse (Kotlin) | 37 | Moderate; clear grammar, verbose token declarations. |
| [jjparse](../mug-examples/src/main/java/com/google/mu/examples/mapflag/jjparse/JjparseMapFlagParser.java) | 38 | Moderate; fluent combinators mixed with regexes. |
| [taker](../mug-examples/src/main/java/com/google/mu/examples/mapflag/taker/TakerMapFlagParser.java) | 38 | Moderate; lookaheads and casts clutter combinators. |
| [JParsec](../mug-examples/src/main/java/com/google/mu/examples/mapflag/jparsec/JparsecMapFlagParser.java) | 39 | Moderate; manual whitespace wrapping and scanner adapters. |
| [ParsecJ](../mug-examples/src/main/java/com/google/mu/examples/mapflag/parsecj/ParsecjMapFlagParser.java) | 43 | Moderate; manual whitespace wrapping and monadic-bind noise. |
| [Regex](../mug-examples/src/main/java/com/google/mu/examples/mapflag/regex/RegexMapFlagParser.java) | 52 | Low; dense regexes and imperative loop. |
| [ANTLR 4](../mug-examples/src/main/java/com/google/mu/examples/mapflag/antlr/AntlrMapFlagParser.java) | 56 (13 `.g4` + 43 `.java`) | Moderate; clear grammar, verbose Java plumbing. |
| [PetitParser](../mug-examples/src/main/java/com/google/mu/examples/mapflag/petitparser/PetitParserMapFlagParser.java) | 68 | Low; untyped lists and positional indexing. |

For brevity, the Java implementations share a number-conversion helper, [ParseUtils.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/ParseUtils.java):

```java
public final class ParseUtils {
  public static Number toNumber(String s) {
    // (Number) cast prevents ternary numeric promotion from widening Integer to double.
    return s.contains(".") ? (Number) Double.parseDouble(s) : Integer.parseInt(s);
  }
}
```

LoC counts the non-blank, non-comment lines of each snippet below, excluding imports.

---

## 1. dot-parse ([DotParseMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/dotparse/DotParseMapFlagParser.java))

```java
public final class DotParseMapFlagParser {
  private static final Parser<Object> SCALAR = anyOf(
      literally(one('-').optional(), UNSIGNED_DECIMAL)  // "-5": accepted, "- 5": rejected
          .source()
          .map(ParseUtils::toNumber),           // to Integer or Double
      quotedByWithEscapes('"', '"', chars(1))); // chars(1) unescapes any "\x" to "x"

  private static final Parser<Map<String, Object>> MAP = Parser.zeroOrMoreDelimited(
          consecutive("[a-zA-Z0-9_-]").followedBy("="),
          anyOf(
              SCALAR,
              SCALAR.zeroOrMoreDelimitedBy(",")
                  .optionallyFollowedBy(",")
                  .between("[", "]")),
          ",",
          toMap())
      .optionallyFollowedBy(",")
      .between("{", "}");

  public static Map<String, Object> parse(String input) {
    return MAP.parseSkipping(Character::isWhitespace, input);
  }
}
```

---

## 2. cats-parse (Scala)

```scala
import cats.parse.{Numbers, Parser => P, Parser0}

object FlagMap {
  // .with1 bridges Parser0 (nullable) into P (non-empty); <* and *> discard one side.
  private val ws0: Parser0[Unit] = P.charsWhile0(_.isWhitespace).void
  private def sym(c: Char): P[Unit] = P.char(c) <* ws0
  private def items[A](p: P[A]): Parser0[List[A]] =
    (p.repSep(sym(','))
        <* sym(',').?)
      .?
      .map(_.fold(List.empty[A])(_.toList))

  private val quoted: P[String] =
    (P.char('\\') *> P.anyChar
        | P.charWhere(c => c != '"' && c != '\\'))
      .rep0
      .map(_.mkString)
      .with1.surroundedBy(P.char('"'))

  private val scalar: P[Any] =
    ((Numbers.signedIntString ~ (P.char('.') ~ Numbers.digits).?)
        .string
        .map(s => s.toIntOption.getOrElse(s.toDouble))
      | quoted) <* ws0

  private val map: P[Map[String, Any]] =
    ws0.with1 *> items(
      (P.charIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') :+ '-' :+ '_')
          .rep
          .string
          <* ws0 <* sym('=')) ~
        (scalar
          | items(scalar)
              .with1.between(sym('['), sym(']')))
    ).with1.between(sym('{'), sym('}'))
      .filter(kvs => kvs.map(_._1).distinct.size == kvs.size)
      .map(_.toMap)

  def parse(input: String) = map.parseAll(input)
}
```

---

## 3. better-parse (Kotlin)

```kotlin
object FlagMapGrammar : Grammar<Map<String, Any>>() {
    val ws by regexToken("\\p{javaWhitespace}+", ignore = true)
    val lbrace by literalToken("{")
    val rbrace by literalToken("}")
    val lbracket by literalToken("[")
    val rbracket by literalToken("]")
    val comma by literalToken(",")
    val eq by literalToken("=")
    val string by regexToken("(?s)\"(?:[^\"\\\\]|\\\\.)*\"")
    // Negative lookaheads stop a number from matching a prefix, e.g. the "0" in "05" or "0.5".
    val int by regexToken("-?(?:0|[1-9]\\d*)(?![\\w.-])")
    val decimal by regexToken("-?(?:0|[1-9]\\d*)\\.\\d+(?![\\w-])")
    val key by regexToken("[\\w-]+")

    private val ESCAPE = Regex("(?s)\\\\(.)")
    val scalar: Parser<Any> by
        (int use { text.toInt() }) or
        (decimal use { text.toDouble() }) or
        (string use { text.substring(1, text.length - 1)
            .replace(ESCAPE, "$1") })

    // Unary '-' drops the matched token from the sequence tuple.
    override val rootParser by
        -lbrace *
        separatedTerms(
            ((key or int) use { text }) * -eq *
                (scalar or
                    (-lbracket *
                        separatedTerms(scalar, comma, acceptZero = true) *
                        -optional(comma) *
                        -rbracket)),
            comma,
            acceptZero = true) *
        -optional(comma) *
        -rbrace map { entries ->
            buildMap {
                for ((k, v) in entries) require(put(k, v) == null) { "duplicate key: $k" }
            }
        }

    fun parse(input: String): Map<String, Any> = parseToEnd(input)
}
```

---

## 4. jjparse ([JjparseMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/jjparse/JjparseMapFlagParser.java))

```java
// StringParsing skips whitespace before every terminal and EOF.
public final class JjparseMapFlagParser extends StringParsing {
  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");
  private static final JjparseMapFlagParser INSTANCE = new JjparseMapFlagParser();

  private final Parser<Character> comma = character(',');
  private final Parser<Object> scalar = choice(
      regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
          .map(ParseUtils::toNumber),
      regex("(?s)\"([^\"\\\\]|\\\\.)*\"")
          .map(s -> ESCAPE.matcher(s.substring(1, s.length() - 1))
              .replaceAll("$1")));
  private final Parser<Map<String, Object>> map = regex("[a-zA-Z0-9_-]+")
      .andl(character('='))
      .and(
          choice(
              scalar,
              // separate(comma) resets position when the element after comma fails,
              // leaving a trailing comma for comma.optional().
              scalar
                  .separate(comma)
                  .andl(comma.optional())
                  .between(character('['), character(']'))))
      .separate(comma)
      .andl(comma.optional())
      .between(character('{'), character('}'))
      .map(entries -> entries.stream()
          .collect(
              toMap(
                  Product::first, Product::second,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)));

  private JjparseMapFlagParser() {
    // The default skip parser, regex("\\s+"), only matches ASCII whitespace.
    setSkipParser(regex("\\p{javaWhitespace}+"));
  }

  public static Map<String, Object> parse(String input) {
    return INSTANCE.parse(INSTANCE.map, Input.of("input", requireNonNull(input)))
        .getOrFail();
  }
}
```

---

## 5. taker ([TakerMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/taker/TakerMapFlagParser.java))

```java
public final class TakerMapFlagParser {
  // Lexical.trim() in taker only skips spaces and tabs; trimWhitespace() also skips '\n' and '\r'.
  private static final Taker<Character> COMMA = trimWhitespace(chr(','));
  private static final Taker<Character> RBRACKET = trimWhitespace(chr(']'));
  private static final Taker<Character> RBRACE = trimWhitespace(chr('}'));

  private static final Taker<Object> SCALAR = oneOf(
      widen(trimWhitespace(regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))
          .map(ParseUtils::toNumber)),
      // Empty escape map unescapes any "\x" to "x".
      widen(trimWhitespace(escapedString('"', '\\', Map.of()))));

  private static final Taker<Map<String, Object>> MAP = trimWhitespace(regex("[a-zA-Z0-9_-]+"))
      .thenSkip(trimWhitespace(chr('=')))
      .then(
          oneOf(
              SCALAR,
              widen(
                  // zeroOrMoreSeparatedBy() does not backtrack over a trailing comma;
                  // peek(not(RBRACKET)) leaves the trailing comma for thenSkip(COMMA.optional()).
                  SCALAR
                      .zeroOrMoreSeparatedBy(COMMA.peek(not(RBRACKET)))
                      .thenSkip(COMMA.optional())
                      .between(trimWhitespace(chr('[')), RBRACKET))))
      .map(Map::entry)
      .zeroOrMoreSeparatedBy(COMMA.peek(not(RBRACE)))
      .thenSkip(COMMA.optional())
      .between(trimWhitespace(chr('{')), RBRACE)
      .map(entries -> entries.stream()
          .collect(
              toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)));

  public static Map<String, Object> parse(String input) {
    return MAP.parseAll(requireNonNull(input)).value();
  }

  @SuppressWarnings("unchecked") // Taker only produces A
  private static <A> Taker<A> widen(Taker<? extends A> taker) {
    return (Taker<A>) taker;
  }
}
```

---

## 6. JParsec ([JparsecMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/jparsec/JparsecMapFlagParser.java))

```java
public final class JparsecMapFlagParser {
  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");
  private static final Parser<?> COMMA = tok(isChar(','));

  private static final Parser<Object> SCALAR = or(
      tok(regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
              .toScanner("number")
              .source())
          .map(ParseUtils::toNumber),
      tok(DOUBLE_QUOTE_STRING)
          .map(s -> ESCAPE.matcher(s.substring(1, s.length() - 1))
              .replaceAll("$1")));

  // sepEndBy(COMMA) parses 0+ items separated and optionally terminated by COMMA.
  private static final Parser<List<Map.Entry<String, Object>>> ENTRIES = sequence(
          tok(regex("[a-zA-Z0-9_-]+")
                  .toScanner("key")
                  .source())
              .followedBy(tok(isChar('='))),
          or(
              SCALAR,
              SCALAR.sepEndBy(COMMA)
                  .between(tok(isChar('[')), tok(isChar(']')))),
          Map::entry)
      .sepEndBy(COMMA)
      .between(
          WHITESPACES.skipMany().next(tok(isChar('{'))),
          tok(isChar('}')));

  public static Map<String, Object> parse(String input) {
    // Built outside the parser, which would wrap the duplicate-key exception in ParserException.
    return ENTRIES.parse(input).stream()
        .collect(
            toMap(
                Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> {
                  throw new IllegalArgumentException("Duplicate key");
                },
                LinkedHashMap::new));
  }

  // Scanner-level JParsec has no global whitespace skipping; each terminal is wrapped in tok().
  private static <T> Parser<T> tok(Parser<T> p) {
    return p.followedBy(WHITESPACES.skipMany());
  }
}
```

---

## 7. ParsecJ ([ParsecjMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/parsecj/ParsecjMapFlagParser.java))

```java
public final class ParsecjMapFlagParser {
  private static final Parser<Character, Character> COMMA = tok(chr(','));

  private static final Parser<Character, Object> SCALAR = or(
      tok(regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))
          .map(ParseUtils::toNumber),
      tok(chr('\\')
              .then(satisfy((Character c) -> true))
              .or(satisfy((Character c) -> c != '"' && c != '\\'))
              .many()
              .between(chr('"'), chr('"')))
          .map(IList::listToString));

  // Entries are built with bind() over the key and map() over the value;
  // sepEndBy(COMMA) parses 0+ items separated and optionally terminated by COMMA.
  private static final Parser<Character, Map<String, Object>> MAP = tok(regex("[a-zA-Z0-9_-]+"))
      .bind(k -> tok(chr('='))
          .then(
              or(
                  SCALAR,
                  SCALAR.sepEndBy(COMMA)
                      .between(tok(chr('[')), tok(chr(']')))
                      .map(IList::toList)))
          .map(v -> Map.entry(k, v)))
      .sepEndBy(COMMA)
      .between(tok(chr('{')), tok(chr('}')))
      .between(wspaces, eof())
      .map(entries -> entries.stream()
          .collect(
              toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)));

  public static Map<String, Object> parse(String input) {
    return MAP.parse(Input.of(requireNonNull(input)))
        .match(
            Reply.Ok::getResult,
            err -> {
              throw new IllegalArgumentException(err.getMsg());
            });
  }

  // ParsecJ has no global whitespace skipping; each terminal is wrapped in tok().
  private static <T> Parser<Character, T> tok(Parser<Character, T> p) {
    return p.bind(x -> wspaces.then(retn(x)));
  }
}
```

---

## 8. Regex ([RegexMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/regex/RegexMapFlagParser.java))

```java
public final class RegexMapFlagParser {
  private static final String SCALAR =
      "(?s:-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"
          + "|\"(?:[^\"\\\\]|\\\\.)*+\")";
  private static final String LIST =
      "\\[\\s*(?:"
          + SCALAR + "\\s*(?:,\\s*" + SCALAR + "\\s*)*"
          + "(?:,\\s*)?"
          + ")?\\]";
  // (?U) extends \s to Unicode whitespace.
  // Possessive \s*+ prevents polynomial backtracking when '}' is missing.
  private static final Pattern BRACES = Pattern.compile("(?sU)\\s*+\\{\\s*+(.*)\\}\\s*");
  // (?:,\s*|\z) requires a comma or end-of-body after each entry.
  private static final Pattern ENTRY = Pattern.compile(
      "(?U)([a-zA-Z0-9_-]+)\\s*=\\s*"
          + "(" + SCALAR + "|" + LIST + ")"
          + "\\s*(?:,\\s*|\\z)");
  private static final Pattern SCALAR_PATTERN = Pattern.compile(SCALAR);
  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");

  public static Map<String, Object> parse(String input) {
    Matcher braces = BRACES.matcher(input);
    if (!braces.matches()) {
      throw new IllegalArgumentException("expecting {...}");
    }
    String body = braces.group(1);
    Map<String, Object> result = new LinkedHashMap<>();
    Matcher entry = ENTRY.matcher(body);
    for (int pos = 0; pos < body.length(); pos = entry.end()) {
      if (!entry.region(pos, body.length()).lookingAt()) {
        throw new IllegalArgumentException("invalid entry at " + pos + ": " + body.substring(pos));
      }
      result.merge(
          entry.group(1),
          toValue(entry.group(2)),
          (a, b) -> {
            throw new IllegalArgumentException("Duplicate key");
          });
    }
    return result;
  }

  private static Object toValue(String value) {
    return value.startsWith("[")
        ? SCALAR_PATTERN.matcher(value)
            .results()
            .map(m -> toScalar(m.group()))
            .toList()
        : toScalar(value);
  }

  private static Object toScalar(String scalar) {
    return scalar.startsWith("\"")
        ? ESCAPE.matcher(scalar.substring(1, scalar.length() - 1))
            .replaceAll("$1")
        : toNumber(scalar);
  }
}
```

---

## 9. ANTLR 4 ([FlagMap.g4](../mug-examples/src/main/antlr4/com/google/mu/examples/mapflag/antlr/FlagMap.g4), [AntlrMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/antlr/AntlrMapFlagParser.java))

```antlr
grammar FlagMap;

map    : '{' (entry (',' entry)* ','?)? '}' EOF ;
entry  : key '=' value ;
// Purely numeric keys like "123" lex as INT; splitting INT and DECIMAL rejects "1.5" as a key.
key    : KEY | INT ;
value  : scalar                                  # single
       | '[' (scalar (',' scalar)* ','?)? ']'    # list
       ;
scalar : INT | DECIMAL | STRING ;

INT     : '-'? ('0' | [1-9] [0-9]*) ;
DECIMAL : INT '.' [0-9]+ ;
KEY     : [a-zA-Z0-9_-]+ ;
STRING  : '"' ( '\\' . | ~["\\] )* '"' ;
WS      : [\p{White_Space}]+ -> skip ;
```

```java
public final class AntlrMapFlagParser {
  // ANTLR logs errors to stderr and recovers by default; install THROWER on both lexer and parser.
  private static final BaseErrorListener THROWER = new BaseErrorListener() {
    @Override public void syntaxError(
        Recognizer<?, ?> r, Object sym, int line, int col, String msg, RecognitionException e) {
      throw new IllegalArgumentException(line + ":" + (col + 1) + " " + msg);
    }
  };

  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");

  public static Map<String, Object> parse(String input) {
    FlagMapLexer lexer = new FlagMapLexer(CharStreams.fromString(input));
    lexer.removeErrorListeners();
    lexer.addErrorListener(THROWER);
    FlagMapParser parser = new FlagMapParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(THROWER);
    return parser.map().entry().stream()
        .collect(
            toMap(
                e -> e.key().getText(),
                e -> toValue(e.value()),
                (a, b) -> {
                  throw new IllegalArgumentException("Duplicate key");
                },
                LinkedHashMap::new));
  }

  private static Object toValue(ValueContext v) {
    return switch (v) {
      case SingleContext s -> toScalar(s.scalar());
      case ListContext l -> l.scalar().stream()
          .map(AntlrMapFlagParser::toScalar)
          .toList();
      default -> throw new AssertionError(v);
    };
  }

  private static Object toScalar(ScalarContext s) {
    if (s.STRING() != null) {
      String raw = s.STRING().getText();
      return ESCAPE.matcher(raw.substring(1, raw.length() - 1))
          .replaceAll("$1");
    }
    return toNumber(s.getText());
  }
}
```

---

## 10. PetitParser ([PetitParserMapFlagParser.java](../mug-examples/src/main/java/com/google/mu/examples/mapflag/petitparser/PetitParserMapFlagParser.java))

```java
public final class PetitParserMapFlagParser {
  private static final Parser COMMA = of(',').trim();

  // .trim() only skips whitespace outside the flattened number, so "- 5" is rejected.
  // PetitParser is untyped, so map() lambdas cast their input parameter types.
  private static final Parser SCALAR = of('-')
      .optional()
      .seq(
          of('0').or(pattern("1-9").seq(digit().star())),
          of('.').seq(digit().plus()).optional())
      .flatten()
      .trim()
      .map(ParseUtils::toNumber)
      .or(
          of('"')
              .seq(
                  of('\\')
                      .seq(any())
                      .pick(1)
                      .or(noneOf("\"\\"))
                      .star()
                      .map((List<Character> cs) -> cs.stream()
                          .map(String::valueOf)
                          .collect(joining())),
                  of('"'))
              .pick(1)
              .trim());

  private static final Parser MAP = of('{')
      .trim()
      .seq(
          pattern("a-zA-Z0-9_-")
              .plus()
              .flatten()
              .trim()
              .seq(
                  of('=').trim(),
                  SCALAR.or(
                      of('[')
                          .trim()
                          .seq(
                              // delimitedBy() allows a trailing comma but keeps the separators.
                              SCALAR
                                  .delimitedBy(COMMA)
                                  .map(PetitParserMapFlagParser::dropSeparators)
                                  .optional(List.of()),
                              of(']').trim())
                          .pick(1)))
              .map((List<Object> kv) -> Map.entry((String) kv.get(0), kv.get(2)))
              .delimitedBy(COMMA)
              .map(PetitParserMapFlagParser::dropSeparators)
              .optional(List.of()),
          of('}').trim())
      .pick(1)
      .map((List<Map.Entry<String, Object>> entries) -> entries.stream()
          .collect(
              toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)))
      .end();

  public static Map<String, Object> parse(String input) {
    return MAP.parse(requireNonNull(input)).get();
  }

  private static List<Object> dropSeparators(List<Object> items) {
    return IntStream.range(0, items.size())
        .filter(i -> i % 2 == 0)
        .mapToObj(items::get)
        .toList();
  }
}
```

---

## Performance

The eight Java implementations were benchmarked with JMH ([MapFlagBenchmark.java](../mug-examples/src/test/java/com/google/mu/examples/mapflag/MapFlagBenchmark.java)); the Scala and Kotlin versions are not included. There are three inputs:

- **SMALL**: the example flag from [The Format](#the-format) (36 characters).
- **MEDIUM**: 10 entries, including string and number lists, escapes, an empty list, and a trailing comma (230 characters).
- **LARGE**: 100 generated entries cycling through integers, negative decimals, escaped strings, and mixed lists (2,201 characters).

Before measuring, the benchmark checks that all eight implementations return the same map for each input.

Throughput (higher is better):

| Library | SMALL (ops/ms) | MEDIUM (ops/ms) | LARGE (ops/ms) |
|---|---|---|---|
| dot-parse | 2,124 | 513 | 37.4 |
| taker | 1,006 | 232 | 22.3 |
| JParsec | 920 | 196 | 19.6 |
| PetitParser | 709 | 142 | 15.2 |
| Regex | 682 | 167 | 15.0 |
| ANTLR 4 | 503 | 163 | 15.6 |
| ParsecJ | 359 | 77.5 | 7.99 |
| jjparse | 14.2 | 4.42 | 0.316 |

Snapshot: 2026-09-27, JDK 24.0.1, Apple M3 Pro, macOS 15.7.9. JMH, 1 fork, 3 warmup and 5 measurement iterations of 1 second each.

Error margins (99.9% confidence) are within ±5%, except on SMALL for PetitParser (±11%), ANTLR 4 (±12%), and jjparse (±6%), and on MEDIUM for jjparse (±9%). PetitParser and Regex on SMALL, Regex and ANTLR 4 on MEDIUM, and PetitParser, Regex, and ANTLR 4 on LARGE are within each other's error margins.

jjparse formats an error message, with line and column, every time a match fails, including failed whitespace skips and rejected alternatives.
