package com.google.mu.benchmarks.parsers.betterparse

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.*
import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.*
import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword

object BetterParseShowdown {

    class IpFixture : Grammar<String>() {
        val digit by regexToken("\\d+")
        val dot by literalToken(".")
        override val rootParser: Parser<String> by digit * -dot * digit * -dot * digit * -dot * digit map { (d1, d2, d3, d4) ->
            "${d1.text}.${d2.text}.${d3.text}.${d4.text}"
        }
        fun run(): String = parseToEnd(BenchmarkInputs.IP)
    }

    class StringFixture : Grammar<String>() {
        val quote by literalToken("\"")
        val charChunk by regexToken("[^\"\\\\]+")
        val escaped by regexToken("\\\\[\"\\\\]")
        
        override val rootParser: Parser<String> by -quote * zeroOrMore(charChunk or escaped) * -quote map { chunks ->
            BenchmarkInputs.unescape("\"" + chunks.joinToString("") { it.text } + "\"")
        }
        
        fun run(input: String): String = parseToEnd(input)
    }

    class KeywordsFixture : Grammar<List<Keyword>>() {
        val select by literalToken("select")
        val insert by literalToken("insert")
        val update by literalToken("update")
        val delete by literalToken("delete")
        val create by literalToken("create")
        val drop by literalToken("drop")
        val alter by literalToken("alter")
        val where by literalToken("where")
        val group by literalToken("group")
        val order by literalToken("order")
        val having by literalToken("having")
        val limit by literalToken("limit")
        val comma by literalToken(",")
        val ws by regexToken("\\s+", ignore = true)
        
        val keywordParser: Parser<Keyword> by (
            select asJust Keyword.SELECT or
            (insert asJust Keyword.INSERT) or
            (update asJust Keyword.UPDATE) or
            (delete asJust Keyword.DELETE) or
            (create asJust Keyword.CREATE) or
            (drop asJust Keyword.DROP) or
            (alter asJust Keyword.ALTER) or
            (where asJust Keyword.WHERE) or
            (group asJust Keyword.GROUP) or
            (order asJust Keyword.ORDER) or
            (having asJust Keyword.HAVING) or
            (limit asJust Keyword.LIMIT)
        )
        
        override val rootParser: Parser<List<Keyword>> by separatedTerms<Keyword, TokenMatch>(keywordParser, comma)
        
        fun run(input: String): List<Keyword> = parseToEnd(input)
    }

    class IgnoreCaseFixture : Grammar<List<Keyword>>() {
        val select by regexToken("select\\b".toRegex(RegexOption.IGNORE_CASE))
        val insert by regexToken("insert\\b".toRegex(RegexOption.IGNORE_CASE))
        val update by regexToken("update\\b".toRegex(RegexOption.IGNORE_CASE))
        val delete by regexToken("delete\\b".toRegex(RegexOption.IGNORE_CASE))
        val create by regexToken("create\\b".toRegex(RegexOption.IGNORE_CASE))
        val drop by regexToken("drop\\b".toRegex(RegexOption.IGNORE_CASE))
        val alter by regexToken("alter\\b".toRegex(RegexOption.IGNORE_CASE))
        val where by regexToken("where\\b".toRegex(RegexOption.IGNORE_CASE))
        val group by regexToken("group\\b".toRegex(RegexOption.IGNORE_CASE))
        val order by regexToken("order\\b".toRegex(RegexOption.IGNORE_CASE))
        val having by regexToken("having\\b".toRegex(RegexOption.IGNORE_CASE))
        val limit by regexToken("limit\\b".toRegex(RegexOption.IGNORE_CASE))
        val comma by literalToken(",")
        val ws by regexToken("\\s+", ignore = true)

        val keywordParser: Parser<Keyword> by (
            select asJust Keyword.SELECT or
            (insert asJust Keyword.INSERT) or
            (update asJust Keyword.UPDATE) or
            (delete asJust Keyword.DELETE) or
            (create asJust Keyword.CREATE) or
            (drop asJust Keyword.DROP) or
            (alter asJust Keyword.ALTER) or
            (where asJust Keyword.WHERE) or
            (group asJust Keyword.GROUP) or
            (order asJust Keyword.ORDER) or
            (having asJust Keyword.HAVING) or
            (limit asJust Keyword.LIMIT)
        )

        override val rootParser: Parser<List<Keyword>> by separatedTerms<Keyword, TokenMatch>(keywordParser, comma)

        fun run(input: String): List<Keyword> = parseToEnd(input)
    }

    class CalculatorFixture : Grammar<Int>() {
        val num by regexToken("\\d+")
        val plus by literalToken("+")
        val minus by literalToken("-")
        val mul by literalToken("*")
        val div by literalToken("/")
        val lpar by literalToken("(")
        val rpar by literalToken(")")
        val ws by regexToken("\\s+", ignore = true)

        val term: Parser<Int> by (num map { it.text.toInt() }) or
            (-minus * parser(this::term) map { -it }) or
            (-lpar * parser(this::rootParser) * -rpar)

        val mulChain by leftAssociative<Int, TokenMatch>(term, mul or div) { l, op, r ->
            if (op.text == "*") l * r else l / r
        }

        override val rootParser: Parser<Int> by leftAssociative<Int, TokenMatch>(mulChain, plus or minus) { l, op, r ->
            if (op.text == "+") l + r else l - r
        }

        fun run(): Int = parseToEnd(BenchmarkInputs.CALCULATOR)
    }

    class NestedCommentFixture : Grammar<String>() {
        val open by literalToken("/*")
        val close by literalToken("*/")
        val text by regexToken("([^\\*\\/]+|\\*[^\\/]|\\/[^\\*])+")

        val comment: Parser<String> by open * zeroOrMore(text map { it.text } or parser(this::comment)) * close map { (openMatch, inner, closeMatch) ->
            openMatch.text + inner.joinToString("") + closeMatch.text
        }

        override val rootParser: Parser<String> by comment

        fun run(input: String): String = parseToEnd(input)
    }

    class UsPhoneFixture : Grammar<String>() {
        val phone by regexToken("\\(\\d{3}\\)\\d{3}-\\d{4}")
        override val rootParser: Parser<String> by phone map { it.text }
        fun run(input: String): String = parseToEnd(input)
    }

    class UsPhoneListFixture : Grammar<List<String>>() {
        val ws by regexToken("\\s+", ignore = true)
        val phone by regexToken("\\(\\d{3}\\)\\d{3}-\\d{4}")
        override val rootParser: Parser<List<String>> by zeroOrMore(phone map { it.text })
        fun run(input: String): List<String> = parseToEnd(input)
    }
}
