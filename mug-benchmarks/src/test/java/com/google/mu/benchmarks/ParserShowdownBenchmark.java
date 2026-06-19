package com.google.mu.benchmarks;

import io.github.parseworks.taker.Result;
import io.github.parseworks.taker.Taker;
import io.github.parseworks.taker.CharPredicate;
import io.github.parseworks.taker.parsers.Numeric;
import com.google.common.labs.parse.Parser;
import com.google.common.labs.parse.Production;
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

import java.util.concurrent.TimeUnit;

import static io.github.parseworks.taker.parsers.Combinators.oneOf;
import static io.github.parseworks.taker.parsers.Lexical.string;
import static io.github.parseworks.taker.parsers.Lexical.stringIgnoreCase;
import static io.github.parseworks.taker.parsers.Chars.chr;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ParserShowdownBenchmark {

    @State(Scope.Benchmark)
    public static class ParserState {
        // 1. IP Address
        Taker<String> takerSimpleIp;
        Production<?> dotParseSimpleIp;
        String simpleIpInput;

        // 2. Quoted String
        Taker<String> takerSimpleString;
        Production<?> dotParseSimpleString;
        String simpleStringInput;

        // 3. 12 Keywords
        Taker<String> takerSimpleKeywords;
        Production<String> dotParseSimpleKeywords;
        String simpleKeywordsInput;

        // 4. Case-Insensitive Keywords
        Taker<String> takerIgnoreCaseKeywords;
        Production<String> dotParseSimpleIgnoreCase;
        String simpleIgnoreCaseInput;

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
                "\"", '"', 
                Parser.one(c -> true, "any").map(c -> String.valueOf(c))
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
        }

        private static void requireMatch(String name, Result<?> result) {
            if (!result.matches() || !result.input().isEof()) {
                throw new IllegalStateException(name + " benchmark parser did not consume the intended input");
            }
        }
    }

    // 1. IP Address Benchmarks
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

    // 2. Quoted String Benchmarks
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

    // 3. Keywords Benchmarks
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

    // 4. Case-Insensitive Keywords Benchmarks
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
}
