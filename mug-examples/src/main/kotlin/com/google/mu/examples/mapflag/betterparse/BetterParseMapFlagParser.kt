package com.google.mu.examples.mapflag.betterparse

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser

object BetterParseMapFlagParser : Grammar<Map<String, Any>>() {
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
                for ((k, v) in entries) require(put(k, v) == null) { "Duplicate key: $k" }
            }
        }

    fun parse(input: String): Map<String, Any> = parseToEnd(input)
}
