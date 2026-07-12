package com.google.mu.benchmarks.parsers.betterparse

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import com.google.mu.benchmarks.parsers.ast.css.*
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.*
import com.google.mu.benchmarks.parsers.ast.css.Rule.*

object BetterParseCssParser : Grammar<Stylesheet>() {

    // =========================================================================
    // 1. Lexer Tokens
    // =========================================================================

    // Ignore comments and whitespaces eagerly
    val comment by regexToken("/\\*[\\s\\S]*?\\*/", ignore = true)
    val ws by regexToken("\\s+", ignore = true)

    val important by regexToken("!\\s*important")

    val lbrace by literalToken("{")
    val rbrace by literalToken("}")
    val lparen by literalToken("(")
    val rparen by literalToken(")")
    val lbracket by literalToken("[")
    val rbracket by literalToken("]")

    val colon by literalToken(":")
    val semicolon by literalToken(";")
    val comma by literalToken(",")
    val percentage by literalToken("%")
    val doubleColon by literalToken("::")

    val hashToken by regexToken("#[a-zA-Z0-9_-]+")
    val atPrefix by literalToken("@")

    val urlToken by regexToken("(?i)url\\(([^)]*)\\)")
    val stringToken by regexToken("\"([^\"\\\\\n\r]|\\\\.)*\"|'([^'\\\\\n\r]|\\\\.)*'")

    // Standard number regex (matches integer, decimal, and exponent parts)
    val numberToken by regexToken("[+-]?([0-9]+\\.[0-9]+|\\.[0-9]+|[0-9]+)([eE][+-]?[0-9]+)?")

    // CSS identifier regex matching optional dash and identifier characters
    val identifierToken by regexToken("-?([a-zA-Z_]|\\\\.)([a-zA-Z0-9_-]|\\\\.)*")

    // Operator/Delimiter characters
    val delimToken by regexToken("[-#$*+,./:<>^~=]")

    // =========================================================================
    // 2. Parser Definitions
    // =========================================================================

    private val numberVal: Parser<Double> by numberToken map { it.text.toDouble() }

    private val urlVal: Parser<Url> by urlToken map {
        val text = it.text
        var content = text.substring(4, text.length - 1).trim()
        if ((content.startsWith("\"") && content.endsWith("\"")) ||
            (content.startsWith("'") && content.endsWith("'"))) {
            content = content.substring(1, content.length - 1)
        }
        Url(content)
    }

    private val percentageVal: Parser<Percentage> by numberVal * -percentage map { Percentage(it) }

    private val dimensionVal: Parser<Dimension> by numberVal * identifierToken map { (num, ident) ->
        Dimension(num, ident.text)
    }

    private val numericValue: Parser<ComponentValue> by percentageVal or dimensionVal or (numberVal map { Num(it) })

    private val hashVal: Parser<HashWord> by hashToken map { HashWord(it.text.substring(1)) }

    private val atWordVal: Parser<AtWord> by -atPrefix * identifierToken map { AtWord(it.text) }

    private val delimVal: Parser<Delim> by (doubleColon asJust Delim("::")) or (comma asJust Delim(",")) or (colon asJust Delim(":")) or (delimToken map { Delim(it.text) })

    private val bracketsBlock: Parser<BracketsBlock> by -lparen * zeroOrMore(parser { componentValue }) * -rparen map { BracketsBlock(it) }

    private val curlyBracketsBlock: Parser<CurlyBracketsBlock> by -lbrace * zeroOrMore(parser { componentValue }) * -rbrace map { CurlyBracketsBlock(it) }

    private val squareBracketsBlock: Parser<SquareBracketsBlock> by -lbracket * zeroOrMore(parser { componentValue }) * -rbracket map { SquareBracketsBlock(it) }

    private val functionBlock: Parser<FunctionBlock> by identifierToken * -lparen * zeroOrMore(parser { componentValue }) * -rparen map { (name, list) ->
        FunctionBlock(name.text, BracketsBlock(list))
    }

    private val simpleToken: Parser<ComponentValue> by atWordVal or hashVal or urlVal or numericValue or
            (stringToken map { Str(it.text.substring(1, it.text.length - 1)) }) or
            (identifierToken map { Ident(it.text) }) or delimVal

    private val componentValue: Parser<ComponentValue> by bracketsBlock or curlyBracketsBlock or squareBracketsBlock or functionBlock or simpleToken

    private val declarationValue: Parser<ComponentValue> by bracketsBlock or squareBracketsBlock or functionBlock or simpleToken

    private val declaration: Parser<Declaration> by identifierToken * -colon * zeroOrMore(declarationValue) * optional(important) map { (ident, values, isImportant) ->
        Declaration(ident.text, values, isImportant != null)
    }

    private val declarationList: Parser<List<Declaration>> by -lbrace * zeroOrMore(declaration * -semicolon) * optional(declaration) * -rbrace map { (list, opt) ->
        val result = list.toMutableList()
        if (opt != null) {
            result.add(opt)
        }
        result
    }

    private val selectorToken by identifierToken or colon or doubleColon or comma or
            hashToken or delimToken or lparen or rparen or lbracket or rbracket or
            stringToken or numberToken or percentage

    private val selector: Parser<String> by oneOrMore(selectorToken) map { tokens ->
        tokens.joinToString("") {
            if (it.type == comma) ", " else it.text
        }.trim()
    }

    private val qualifiedRule: Parser<QualifiedRule> by selector * declarationList map { (sel, decls) ->
        QualifiedRule(sel, decls)
    }

    private interface AtRuleBody {
        val rules: List<Rule>
        val declarations: List<Declaration>
    }

    private val atRuleBody: Parser<AtRuleBody> by (semicolon asJust object : AtRuleBody {
        override val rules = emptyList<Rule>()
        override val declarations = emptyList<Declaration>()
    }) or (-lbrace * zeroOrMore(parser { rule }) * -rbrace map { rules ->
        object : AtRuleBody {
            override val rules = rules
            override val declarations = emptyList<Declaration>()
        }
    }) or (declarationList map { decls ->
        object : AtRuleBody {
            override val rules = emptyList<Rule>()
            override val declarations = decls
        }
    })

    private val atRule: Parser<AtRule> by -atPrefix * identifierToken * zeroOrMore(declarationValue) * atRuleBody map { (ident, options, body) ->
        AtRule(ident.text, options, body.rules, body.declarations)
    }

    private val rule: Parser<Rule> by parser { atRule } or qualifiedRule

    private val stylesheet: Parser<Stylesheet> by zeroOrMore(rule) map { Stylesheet(it) }

    override val rootParser: Parser<Stylesheet> by stylesheet

    fun parse(input: String): Stylesheet {
        return parseToEnd(input)
    }
}
