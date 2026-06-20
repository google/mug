package com.google.mu.benchmarks;

import static io.github.parseworks.taker.parsers.Chars.chr;
import static io.github.parseworks.taker.parsers.Combinators.oneOf;
import static io.github.parseworks.taker.parsers.Lexical.string;
import static io.github.parseworks.taker.parsers.Lexical.stringIgnoreCase;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.parse.Production;

import io.github.parseworks.taker.CharPredicate;
import io.github.parseworks.taker.Result;
import io.github.parseworks.taker.Taker;
import io.github.parseworks.taker.parsers.Numeric;

// jparsec imports
import org.jparsec.Scanners;
import org.jparsec.Parsers;
import org.jparsec.Terminals;

// parboiled imports
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.BasicParseRunner;

// parsecj imports
import org.javafp.parsecj.Text;
import org.javafp.parsecj.Combinators;

// jjparse imports
import jjparse.StringParsing;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ParserShowdownBenchmark {

    @State(Scope.Benchmark)
    public static class ParserState {
        // 1. IP Address inputs and existing parsers
        Taker<String> takerSimpleIp;
        Production<?> dotParseSimpleIp;
        String simpleIpInput;

        // 2. Quoted String inputs and existing parsers
        Taker<String> takerSimpleString;
        Production<?> dotParseSimpleString;
        String simpleStringInput;

        // 3. 12 Keywords inputs and existing parsers
        Taker<String> takerSimpleKeywords;
        Production<String> dotParseSimpleKeywords;
        String simpleKeywordsInput;

        // 4. Case-Insensitive Keywords inputs and existing parsers
        Taker<String> takerIgnoreCaseKeywords;
        Production<String> dotParseSimpleIgnoreCase;
        String simpleIgnoreCaseInput;

        // 5. jparsec Parsers
        org.jparsec.Parser<?> jparsecIp;
        org.jparsec.Parser<?> jparsecString;
        org.jparsec.Parser<?> jparsecKeywords;
        org.jparsec.Parser<?> jparsecIgnoreCase;

        // 6. parboiled Runners
        BasicParseRunner<Object> parboiledIpRunner;
        BasicParseRunner<Object> parboiledStringRunner;
        BasicParseRunner<Object> parboiledKeywordsRunner;
        BasicParseRunner<Object> parboiledIgnoreCaseRunner;

        // 7. parsecj Parsers
        org.javafp.parsecj.Parser<Character, ?> parsecjIp;
        org.javafp.parsecj.Parser<Character, ?> parsecjString;
        org.javafp.parsecj.Parser<Character, ?> parsecjKeywords;
        org.javafp.parsecj.Parser<Character, ?> parsecjIgnoreCase;

        // 8. jjparse Parsers
        JjShowdownParser jjParserInstance;
        jjparse.Parsing<Character>.Parser<?> jjparseIp;
        jjparse.Parsing<Character>.Parser<?> jjparseString;
        jjparse.Parsing<Character>.Parser<?> jjparseKeywords;
        jjparse.Parsing<Character>.Parser<?> jjparseIgnoreCase;

        // 9. antlr4 Reusable instances
        ShowdownLexer antlr4Lexer;
        org.antlr.v4.runtime.CommonTokenStream antlr4TokenStream;
        ShowdownParser antlr4Parser;

        @Setup
        public void setUp() {
            // 1. IP Setup
            simpleIpInput = "192.168.1.1";

            takerSimpleIp = Numeric.number
                .thenSkip(chr('.'))
                .thenSkip(Numeric.number)
                .thenSkip(chr('.'))
                .thenSkip(Numeric.number)
                .thenSkip(chr('.'))
                .thenSkip(Numeric.number)
                .map(parts -> "ip");

            Parser<?> dot = Parser.string(".");
            dotParseSimpleIp = Parser.sequence(
                Parser.digits(), dot,
                Parser.digits(), dot,
                Parser.digits(), dot,
                Parser.digits()
            ).thenReturn("ip");

            requireMatch("takerSimpleIp", takerSimpleIp.parseAll(simpleIpInput));
            dotParseSimpleIp.parse(simpleIpInput);

            // 2. Quoted String Setup
            simpleStringInput = "\"" + "a".repeat(100) + "\"";

            takerSimpleString = chr('"')
                .thenSkip(
                    oneOf(
                        chr('\\').then(chr(c -> true)).map((backslash, c) -> c),
                        chr(CharPredicate.notAnyOf("\"\\"))
                    ).zeroOrMore()
                )
                .thenSkip(chr('"'))
                .map(parts -> "string");

            dotParseSimpleString = Parser.quotedByWithEscapes(
                '"', '"',
                Parser.chars(1)
            );

            requireMatch("takerSimpleString", takerSimpleString.parseAll(simpleStringInput));
            dotParseSimpleString.parse(simpleStringInput);

            // 3. Keywords Setup
            simpleKeywordsInput = "limit";

            takerSimpleKeywords = oneOf(
                string("select"), string("insert"), string("update"),
                string("delete"), string("create"), string("drop"),
                string("alter"),  string("where"),  string("group"),
                string("order"),  string("having"), string("limit")
            );

            dotParseSimpleKeywords = Parser.anyOf(
                Parser.string("select"), Parser.string("insert"), Parser.string("update"),
                Parser.string("delete"), Parser.string("create"), Parser.string("drop"),
                Parser.string("alter"),  Parser.string("where"),  Parser.string("group"),
                Parser.string("order"),  Parser.string("having"), Parser.string("limit")
            ).map(Object::toString);

            requireMatch("takerSimpleKeywords", takerSimpleKeywords.parseAll(simpleKeywordsInput));
            dotParseSimpleKeywords.parse(simpleKeywordsInput);

            // 4. Case-Insensitive Setup
            simpleIgnoreCaseInput = "LIMIT";

            takerIgnoreCaseKeywords = oneOf(
                stringIgnoreCase("select"), stringIgnoreCase("insert"), stringIgnoreCase("update"),
                stringIgnoreCase("delete"), stringIgnoreCase("create"), stringIgnoreCase("drop"),
                stringIgnoreCase("alter"),  stringIgnoreCase("where"),  stringIgnoreCase("group"),
                stringIgnoreCase("order"),  stringIgnoreCase("having"), stringIgnoreCase("limit")
            );

            dotParseSimpleIgnoreCase = Parser.anyOf(
                Parser.caseInsensitive("select"),
                Parser.caseInsensitive("insert"),
                Parser.caseInsensitive("update"),
                Parser.caseInsensitive("delete"),
                Parser.caseInsensitive("create"),
                Parser.caseInsensitive("drop"),
                Parser.caseInsensitive("alter"),
                Parser.caseInsensitive("where"),
                Parser.caseInsensitive("group"),
                Parser.caseInsensitive("order"),
                Parser.caseInsensitive("having"),
                Parser.caseInsensitive("limit")
            ).map(Object::toString);

            requireMatch("takerIgnoreCaseKeywords", takerIgnoreCaseKeywords.parseAll(simpleIgnoreCaseInput));
            dotParseSimpleIgnoreCase.parse(simpleIgnoreCaseInput);

            // Verify fastparse / cats-parse rules
            if (!FastparseRules.parseIp(simpleIpInput)) throw new AssertionError("fastparse ip failed");
            if (!FastparseRules.parseString(simpleStringInput)) throw new AssertionError("fastparse string failed");
            if (!FastparseRules.parseKeywords(simpleKeywordsInput)) throw new AssertionError("fastparse keywords failed");
            if (!FastparseRules.parseIgnoreCaseKeywords(simpleIgnoreCaseInput)) throw new AssertionError("fastparse ignore case keywords failed");

            if (!CatsParseRules.parseIp(simpleIpInput)) throw new AssertionError("cats-parse ip failed");
            if (!CatsParseRules.parseString(simpleStringInput)) throw new AssertionError("cats-parse string failed");
            if (!CatsParseRules.parseKeywords(simpleKeywordsInput)) throw new AssertionError("cats-parse keywords failed");
            if (!CatsParseRules.parseIgnoreCaseKeywords(simpleIgnoreCaseInput)) throw new AssertionError("cats-parse ignore case keywords failed");

            // 5. jparsec Setup
            org.jparsec.Parser<Void> jparsecDot = Scanners.isChar('.');
            org.jparsec.Parser<String> jparsecDigits = Scanners.DEC_INTEGER;
            jparsecIp = Parsers.sequence(
                jparsecDigits, jparsecDot,
                jparsecDigits, jparsecDot,
                jparsecDigits, jparsecDot,
                jparsecDigits
            ).retn("ip");

            jparsecString = Scanners.DOUBLE_QUOTE_STRING;

            jparsecKeywords = Parsers.or(
                Scanners.string("select"), Scanners.string("insert"), Scanners.string("update"),
                Scanners.string("delete"), Scanners.string("create"), Scanners.string("drop"),
                Scanners.string("alter"),  Scanners.string("where"),  Scanners.string("group"),
                Scanners.string("order"),  Scanners.string("having"), Scanners.string("limit")
            );

            jparsecIgnoreCase = Parsers.or(
                Scanners.stringCaseInsensitive("select"), Scanners.stringCaseInsensitive("insert"), Scanners.stringCaseInsensitive("update"),
                Scanners.stringCaseInsensitive("delete"), Scanners.stringCaseInsensitive("create"), Scanners.stringCaseInsensitive("drop"),
                Scanners.stringCaseInsensitive("alter"),  Scanners.stringCaseInsensitive("where"),  Scanners.stringCaseInsensitive("group"),
                Scanners.stringCaseInsensitive("order"),  Scanners.stringCaseInsensitive("having"), Scanners.stringCaseInsensitive("limit")
            );

            jparsecIp.parse(simpleIpInput);
            jparsecString.parse(simpleStringInput);
            jparsecKeywords.parse(simpleKeywordsInput);
            jparsecIgnoreCase.parse(simpleIgnoreCaseInput);

            // 6. parboiled Setup
            ParboiledShowdownParser parboiledParser = Parboiled.createParser(ParboiledShowdownParser.class);
            parboiledIpRunner = new BasicParseRunner<>(parboiledParser.ipAddress());
            parboiledStringRunner = new BasicParseRunner<>(parboiledParser.quotedString());
            parboiledKeywordsRunner = new BasicParseRunner<>(parboiledParser.keywords());
            parboiledIgnoreCaseRunner = new BasicParseRunner<>(parboiledParser.keywordsIgnoreCase());

            if (!parboiledIpRunner.run(simpleIpInput).matched) throw new AssertionError("parboiled ip failed");
            if (!parboiledStringRunner.run(simpleStringInput).matched) throw new AssertionError("parboiled string failed");
            if (!parboiledKeywordsRunner.run(simpleKeywordsInput).matched) throw new AssertionError("parboiled keywords failed");
            if (!parboiledIgnoreCaseRunner.run(simpleIgnoreCaseInput).matched) throw new AssertionError("parboiled ignore case failed");

            // 7. parsecj Setup
            parsecjIp = Text.intr.then(Text.chr('.'))
                .then(Text.intr).then(Text.chr('.'))
                .then(Text.intr).then(Text.chr('.'))
                .then(Text.intr)
                .map(x -> "ip");

            org.javafp.parsecj.Parser<Character, Character> parsecjEscape =
                Text.chr('\\').then(Combinators.satisfy(c -> true));
            org.javafp.parsecj.Parser<Character, Character> parsecjNormalChar =
                Combinators.satisfy(c -> c != '"' && c != '\\');
            parsecjString = Text.chr('"')
                .then(Combinators.many(Combinators.or(parsecjEscape, parsecjNormalChar)))
                .then(Text.chr('"'))
                .map(x -> "string");

            parsecjKeywords = Combinators.choice(
                Text.string("select"), Text.string("insert"), Text.string("update"),
                Text.string("delete"), Text.string("create"), Text.string("drop"),
                Text.string("alter"),  Text.string("where"),  Text.string("group"),
                Text.string("order"),  Text.string("having"), Text.string("limit")
            );

            parsecjIgnoreCase = Text.regex("(?i)select|insert|update|delete|create|drop|alter|where|group|order|having|limit");

            parsecjIp.parse(org.javafp.parsecj.input.Input.of(simpleIpInput));
            parsecjString.parse(org.javafp.parsecj.input.Input.of(simpleStringInput));
            parsecjKeywords.parse(org.javafp.parsecj.input.Input.of(simpleKeywordsInput));
            parsecjIgnoreCase.parse(org.javafp.parsecj.input.Input.of(simpleIgnoreCaseInput));

            // 8. jjparse Setup
            jjParserInstance = new JjShowdownParser();
            jjparseIp = jjParserInstance.ip;
            jjparseString = jjParserInstance.quotedString;
            jjparseKeywords = jjParserInstance.keywords;
            jjparseIgnoreCase = jjParserInstance.keywordsIgnoreCase;

            if (!jjParserInstance.parse(jjparseIp, jjparse.input.Input.of("jjIp", simpleIpInput)).isSuccess()) {
                throw new AssertionError("jjparse ip failed");
            }
            if (!jjParserInstance.parse(jjparseString, jjparse.input.Input.of("jjString", simpleStringInput)).isSuccess()) {
                throw new AssertionError("jjparse string failed");
            }
            if (!jjParserInstance.parse(jjparseKeywords, jjparse.input.Input.of("jjKeywords", simpleKeywordsInput)).isSuccess()) {
                throw new AssertionError("jjparse keywords failed");
            }
            if (!jjParserInstance.parse(jjparseIgnoreCase, jjparse.input.Input.of("jjIgnoreCase", simpleIgnoreCaseInput)).isSuccess()) {
                throw new AssertionError("jjparse ignore case failed");
            }

            // 9. antlr4 Reusable Setup
            antlr4Lexer = new ShowdownLexer(null);
            antlr4TokenStream = new org.antlr.v4.runtime.CommonTokenStream(antlr4Lexer);
            antlr4Parser = new ShowdownParser(antlr4TokenStream);
            antlr4Lexer.removeErrorListeners();
            antlr4Parser.removeErrorListeners();

            // Verification
            if (!antlr4ParseIp(simpleIpInput)) throw new AssertionError("antlr4 ip failed");
            if (!antlr4ParseString(simpleStringInput)) throw new AssertionError("antlr4 string failed");
            if (!antlr4ParseKeywords(simpleKeywordsInput)) throw new AssertionError("antlr4 keywords failed");
            if (!antlr4ParseIgnoreCase(simpleIgnoreCaseInput)) throw new AssertionError("antlr4 ignore case failed");
        }

        private static void requireMatch(String name, Result<?> result) {
            if (!result.matches() || !result.input().isEof()) {
                throw new IllegalStateException(name + " benchmark parser did not consume the intended input");
            }
        }

        private static boolean antlr4ParseIp(String input) {
            ShowdownLexer lexer = new ShowdownLexer(org.antlr.v4.runtime.CharStreams.fromString(input));
            ShowdownParser parser = new ShowdownParser(new org.antlr.v4.runtime.CommonTokenStream(lexer));
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            parser.ip();
            return parser.getNumberOfSyntaxErrors() == 0;
        }

        private static boolean antlr4ParseString(String input) {
            ShowdownLexer lexer = new ShowdownLexer(org.antlr.v4.runtime.CharStreams.fromString(input));
            ShowdownParser parser = new ShowdownParser(new org.antlr.v4.runtime.CommonTokenStream(lexer));
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            parser.quotedString();
            return parser.getNumberOfSyntaxErrors() == 0;
        }

        private static boolean antlr4ParseKeywords(String input) {
            ShowdownLexer lexer = new ShowdownLexer(org.antlr.v4.runtime.CharStreams.fromString(input));
            ShowdownParser parser = new ShowdownParser(new org.antlr.v4.runtime.CommonTokenStream(lexer));
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            parser.keywords();
            return parser.getNumberOfSyntaxErrors() == 0;
        }

        private static boolean antlr4ParseIgnoreCase(String input) {
            ShowdownLexer lexer = new ShowdownLexer(org.antlr.v4.runtime.CharStreams.fromString(input));
            ShowdownParser parser = new ShowdownParser(new org.antlr.v4.runtime.CommonTokenStream(lexer));
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            parser.keywordsIgnoreCase();
            return parser.getNumberOfSyntaxErrors() == 0;
        }
    }

    // =========================================================================
    // 1. IP Address Benchmarks
    // =========================================================================

    @Benchmark
    public void taker_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.takerSimpleIp.parseAll(state.simpleIpInput));
    }

    @Benchmark
    public void dotParse_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.dotParseSimpleIp.parse(state.simpleIpInput));
    }

    @Benchmark
    public void fastparse_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(FastparseRules.parseIp(state.simpleIpInput));
    }

    @Benchmark
    public void catsParse_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(CatsParseRules.parseIp(state.simpleIpInput));
    }

    @Benchmark
    public void jparsec_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jparsecIp.parse(state.simpleIpInput));
    }

    @Benchmark
    public void parboiled_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parboiledIpRunner.run(state.simpleIpInput));
    }

    @Benchmark
    public void parsecj_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parsecjIp.parse(org.javafp.parsecj.input.Input.of(state.simpleIpInput)));
    }

    @Benchmark
    public void jjparse_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jjParserInstance.parse(state.jjparseIp, jjparse.input.Input.of("jjIp", state.simpleIpInput)));
    }

    @Benchmark
    public void antlr4_simpleIpPerformance(ParserState state, Blackhole blackhole) {
        org.antlr.v4.runtime.CharStream charStream = org.antlr.v4.runtime.CharStreams.fromString(state.simpleIpInput);
        state.antlr4Lexer.setInputStream(charStream);
        state.antlr4TokenStream.setTokenSource(state.antlr4Lexer);
        state.antlr4Parser.setInputStream(state.antlr4TokenStream);
        state.antlr4Parser.reset();
        state.antlr4Parser.ip();
        blackhole.consume(state.antlr4Parser);
    }

    // =========================================================================
    // 2. Quoted String Benchmarks
    // =========================================================================

    @Benchmark
    public void taker_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.takerSimpleString.parseAll(state.simpleStringInput));
    }

    @Benchmark
    public void dotParse_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.dotParseSimpleString.parse(state.simpleStringInput));
    }

    @Benchmark
    public void fastparse_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(FastparseRules.parseString(state.simpleStringInput));
    }

    @Benchmark
    public void catsParse_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(CatsParseRules.parseString(state.simpleStringInput));
    }

    @Benchmark
    public void jparsec_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jparsecString.parse(state.simpleStringInput));
    }

    @Benchmark
    public void parboiled_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parboiledStringRunner.run(state.simpleStringInput));
    }

    @Benchmark
    public void parsecj_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parsecjString.parse(org.javafp.parsecj.input.Input.of(state.simpleStringInput)));
    }

    @Benchmark
    public void jjparse_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jjParserInstance.parse(state.jjparseString, jjparse.input.Input.of("jjString", state.simpleStringInput)));
    }

    @Benchmark
    public void antlr4_simpleStringPerformance(ParserState state, Blackhole blackhole) {
        org.antlr.v4.runtime.CharStream charStream = org.antlr.v4.runtime.CharStreams.fromString(state.simpleStringInput);
        state.antlr4Lexer.setInputStream(charStream);
        state.antlr4TokenStream.setTokenSource(state.antlr4Lexer);
        state.antlr4Parser.setInputStream(state.antlr4TokenStream);
        state.antlr4Parser.reset();
        state.antlr4Parser.quotedString();
        blackhole.consume(state.antlr4Parser);
    }

    // =========================================================================
    // 3. Keywords Benchmarks
    // =========================================================================

    @Benchmark
    public void taker_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.takerSimpleKeywords.parseAll(state.simpleKeywordsInput));
    }

    @Benchmark
    public void dotParse_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.dotParseSimpleKeywords.parse(state.simpleKeywordsInput));
    }

    @Benchmark
    public void fastparse_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(FastparseRules.parseKeywords(state.simpleKeywordsInput));
    }

    @Benchmark
    public void catsParse_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(CatsParseRules.parseKeywords(state.simpleKeywordsInput));
    }

    @Benchmark
    public void jparsec_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jparsecKeywords.parse(state.simpleKeywordsInput));
    }

    @Benchmark
    public void parboiled_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parboiledKeywordsRunner.run(state.simpleKeywordsInput));
    }

    @Benchmark
    public void parsecj_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parsecjKeywords.parse(org.javafp.parsecj.input.Input.of(state.simpleKeywordsInput)));
    }

    @Benchmark
    public void jjparse_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jjParserInstance.parse(state.jjparseKeywords, jjparse.input.Input.of("jjKeywords", state.simpleKeywordsInput)));
    }

    @Benchmark
    public void antlr4_simpleKeywordsPerformance(ParserState state, Blackhole blackhole) {
        org.antlr.v4.runtime.CharStream charStream = org.antlr.v4.runtime.CharStreams.fromString(state.simpleKeywordsInput);
        state.antlr4Lexer.setInputStream(charStream);
        state.antlr4TokenStream.setTokenSource(state.antlr4Lexer);
        state.antlr4Parser.setInputStream(state.antlr4TokenStream);
        state.antlr4Parser.reset();
        state.antlr4Parser.keywords();
        blackhole.consume(state.antlr4Parser);
    }

    // =========================================================================
    // 4. Case-Insensitive Keywords Benchmarks
    // =========================================================================

    @Benchmark
    public void taker_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.takerIgnoreCaseKeywords.parseAll(state.simpleIgnoreCaseInput));
    }

    @Benchmark
    public void dotParse_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.dotParseSimpleIgnoreCase.parse(state.simpleIgnoreCaseInput));
    }

    @Benchmark
    public void fastparse_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(FastparseRules.parseIgnoreCaseKeywords(state.simpleIgnoreCaseInput));
    }

    @Benchmark
    public void catsParse_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(CatsParseRules.parseIgnoreCaseKeywords(state.simpleIgnoreCaseInput));
    }

    @Benchmark
    public void jparsec_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jparsecIgnoreCase.parse(state.simpleIgnoreCaseInput));
    }

    @Benchmark
    public void parboiled_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parboiledIgnoreCaseRunner.run(state.simpleIgnoreCaseInput));
    }

    @Benchmark
    public void parsecj_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.parsecjIgnoreCase.parse(org.javafp.parsecj.input.Input.of(state.simpleIgnoreCaseInput)));
    }

    @Benchmark
    public void jjparse_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        blackhole.consume(state.jjParserInstance.parse(state.jjparseIgnoreCase, jjparse.input.Input.of("jjIgnoreCase", state.simpleIgnoreCaseInput)));
    }

    @Benchmark
    public void antlr4_simpleIgnoreCasePerformance(ParserState state, Blackhole blackhole) {
        org.antlr.v4.runtime.CharStream charStream = org.antlr.v4.runtime.CharStreams.fromString(state.simpleIgnoreCaseInput);
        state.antlr4Lexer.setInputStream(charStream);
        state.antlr4TokenStream.setTokenSource(state.antlr4Lexer);
        state.antlr4Parser.setInputStream(state.antlr4TokenStream);
        state.antlr4Parser.reset();
        state.antlr4Parser.keywordsIgnoreCase();
        blackhole.consume(state.antlr4Parser);
    }

    // =========================================================================
    // Nested Parser Implementations for Parboiled and Jar-Jar-Parse
    // =========================================================================

    public static class ParboiledShowdownParser extends BaseParser<Object> {
        public Rule ipAddress() {
            return Sequence(digits(), '.', digits(), '.', digits(), '.', digits(), EOI);
        }

        public Rule digits() {
            return OneOrMore(CharRange('0', '9'));
        }

        public Rule quotedString() {
            return Sequence(
                '"',
                ZeroOrMore(
                    FirstOf(
                        Sequence('\\', ANY),
                        NoneOf("\"\\")
                    )
                ),
                '"',
                EOI
            );
        }

        public Rule keywords() {
            return Sequence(
                FirstOf("select", "insert", "update", "delete", "create", "drop", "alter", "where", "group", "order", "having", "limit"),
                EOI
            );
        }

        public Rule keywordsIgnoreCase() {
            return Sequence(
                FirstOf(
                    IgnoreCase("select"), IgnoreCase("insert"), IgnoreCase("update"),
                    IgnoreCase("delete"), IgnoreCase("create"), IgnoreCase("drop"),
                    IgnoreCase("alter"),  IgnoreCase("where"),  IgnoreCase("group"),
                    IgnoreCase("order"),  IgnoreCase("having"), IgnoreCase("limit")
                ),
                EOI
            );
        }
    }

    public static class JjShowdownParser extends StringParsing {
        public final jjparse.Parsing<Character>.Parser<String> ip = regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
        public final jjparse.Parsing<Character>.Parser<String> quotedString = regex("\"([^\"\\\\]|\\\\.)*\"");
        public final jjparse.Parsing<Character>.Parser<String> keywords = choice(
            literal("select"), literal("insert"), literal("update"),
            literal("delete"), literal("create"), literal("drop"),
            literal("alter"),  literal("where"),  literal("group"),
            literal("order"),  literal("having"), literal("limit")
        );
        public final jjparse.Parsing<Character>.Parser<String> keywordsIgnoreCase = regex("(?i)select|insert|update|delete|create|drop|alter|where|group|order|having|limit");
    }
}
