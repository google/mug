package com.google.mu.benchmarks.parsers.betterparse

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import com.google.mu.benchmarks.parsers.dotparse.JsonValue
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*
import java.util.LinkedHashMap

/** Strictly RFC 8259-compliant Kotlin Better-Parse JSON parser. */
object BetterParseJsonParser : Grammar<JsonValue>() {

    // 1. Lexer Tokens (Whitespace is ignored automatically)
    val ws by regexToken("\\s+", ignore = true)

    val nullToken by literalToken("null")
    val trueToken by literalToken("true")
    val falseToken by literalToken("false")

    // Strict number token (regex matching RFC 8259 number rules)
    val numberToken by regexToken("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    // Double-quoted string token
    val stringToken by regexToken("\"([^\"\\\\]|\\\\.)*\"")

    val lbracket by literalToken("[")
    val rbracket by literalToken("]")
    val lbrace by literalToken("{")
    val rbrace by literalToken("}")
    val colon by literalToken(":")
    val comma by literalToken(",")

    // 2. Parser Definitions
    private val jsonNull: Parser<JsonNull> by nullToken asJust JsonNull.INSTANCE

    private val jsonBoolean: Parser<JsonBoolean> by (trueToken asJust JsonBoolean.TRUE) or 
                                                     (falseToken asJust JsonBoolean.FALSE)

    private val jsonNumber: Parser<JsonNumber> by numberToken map { 
        JsonNumber(it.text.toDouble()) 
    }

    private val jsonString: Parser<JsonString> by stringToken map { 
        JsonString(strictUnescape(it.text)) 
    }

    // Recursive JSON value parser, wrapped in lazy parser {} for self-reference
    private val jsonValue: Parser<JsonValue> by parser {
        jsonNull or jsonBoolean or jsonNumber or jsonString or jsonArray or jsonObject
    }

    private val jsonArray: Parser<JsonArray> by -lbracket * optional(separatedTerms(jsonValue, comma)) * -rbracket map {
        JsonArray(it ?: emptyList())
    }

    private val member: Parser<Pair<String, JsonValue>> by jsonString * -colon * jsonValue map { (key, value) ->
        Pair(key.value(), value)
    }

    private val jsonObject: Parser<JsonObject> by -lbrace * optional(separatedTerms(member, comma)) * -rbrace map {
        val map = LinkedHashMap<String, JsonValue>()
        it?.forEach { (key, value) -> map[key] = value }
        JsonObject(map)
    }

    override val rootParser: Parser<JsonValue> by jsonValue

    fun parse(input: String): JsonValue {
        return parseToEnd(input)
    }

    // Strict unescape complying with RFC 8259 Section 7 string constraints
    fun strictUnescape(quoted: String): String {
        val text = quoted.substring(1, quoted.length - 1)
        if (text.indexOf('\\') == -1) {
            for (j in 0 until text.length) {
                val charVal = text[j]
                if (charVal.code < 0x20) {
                    throw IllegalArgumentException("Unescaped control character: 0x${Integer.toHexString(charVal.code)}")
                }
            }
            return text
        }
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                if (i + 1 >= text.length) {
                    throw IllegalArgumentException("Trailing backslash")
                }
                val esc = text[++i]
                when (esc) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 4 >= text.length) {
                            throw IllegalArgumentException("Invalid unicode escape")
                        }
                        val hex = text.substring(i + 1, i + 5)
                        i += 4
                        sb.append(hex.toInt(16).toChar())
                    }
                    else -> throw IllegalArgumentException("Invalid escape character: \\$esc")
                }
            } else if (c.code < 0x20) {
                throw IllegalArgumentException("Unescaped control character: 0x${Integer.toHexString(c.code)}")
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
