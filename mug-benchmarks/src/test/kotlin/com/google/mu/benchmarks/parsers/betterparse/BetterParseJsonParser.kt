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
    val stringToken by regexToken("\"([^\"\\\\\u0000-\u001F]|\\\\.)*\"")

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
        val text = it.text
        if (text.contains(".") || text.contains("e") || text.contains("E")) {
            JsonNumber(text.toDouble())
        } else {
            JsonNumber(text.toLong().toDouble())
        }
    }

    private val jsonString: Parser<JsonString> by stringToken map { 
        JsonString(strictUnescape(it.input, it.offset + 1, it.offset + it.length - 1)) 
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
    fun strictUnescape(input: CharSequence, start: Int, end: Int): String {
        var hasBackslash = false
        for (i in start until end) {
            if (input[i] == '\\') {
                hasBackslash = true
                break
            }
        }
        if (!hasBackslash) {
            return input.subSequence(start, end).toString()
        }
        val sb = StringBuilder(end - start)
        var i = start
        while (i < end) {
            val c = input[i]
            if (c == '\\') {
                if (i + 1 >= end) {
                    throw IllegalArgumentException("Trailing backslash")
                }
                val esc = input[++i]
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
                        if (i + 4 >= end) {
                            throw IllegalArgumentException("Invalid unicode escape")
                        }
                        val hex = input.subSequence(i + 1, i + 5).toString()
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

object BetterParseJsonWithCommentsParser : Grammar<JsonValue>() {
    val ws by regexToken("\\s+", ignore = true)
    val lineComment by regexToken("//[^\\n]*", ignore = true)
    val blockComment by regexToken("/\\*([^*]|\\*+[^*/])*\\*+/", ignore = true)

    val nullToken by literalToken("null")
    val trueToken by literalToken("true")
    val falseToken by literalToken("false")
    val numberToken by regexToken("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    val stringToken by regexToken("\"([^\"\\\\\u0000-\u001F]|\\\\.)*\"")

    val lbracket by literalToken("[")
    val rbracket by literalToken("]")
    val lbrace by literalToken("{")
    val rbrace by literalToken("}")
    val colon by literalToken(":")
    val comma by literalToken(",")

    private val jsonNull: Parser<JsonNull> by nullToken asJust JsonNull.INSTANCE

    private val jsonBoolean: Parser<JsonBoolean> by (trueToken asJust JsonBoolean.TRUE) or 
                                                     (falseToken asJust JsonBoolean.FALSE)

    private val jsonNumber: Parser<JsonNumber> by numberToken map { 
        val text = it.text
        if (text.contains(".") || text.contains("e") || text.contains("E")) {
            JsonNumber(text.toDouble())
        } else {
            JsonNumber(text.toLong().toDouble())
        }
    }

    private val jsonString: Parser<JsonString> by stringToken map { 
        JsonString(BetterParseJsonParser.strictUnescape(it.input, it.offset + 1, it.offset + it.length - 1)) 
    }

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
}
