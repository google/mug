package com.google.mu.benchmarks

import cats.parse.{Parser => P, Parser0 => P0}

object CatsParseRules {
  // 1. IP Parser
  private val digits = P.charIn('0' to '9').rep.string
  private val dot = P.char('.')
  
  val ip = digits ~ dot ~ digits ~ dot ~ digits ~ dot ~ digits
  
  def parseIp(input: String): Boolean = {
    ip.parse(input) match {
      case Right(_) => true
      case _ => false
    }
  }

  // 2. Quoted String Parser
  private val escape = P.char('\\') *> P.anyChar
  private val normal = P.charsWhile(c => c != '"' && c != '\\')
  
  val str = P.char('"') *> (escape | normal).rep0 *> P.char('"')
  
  def parseString(input: String): Boolean = {
    str.parse(input) match {
      case Right(_) => true
      case _ => false
    }
  }

  // 3. Keywords Parser (Trie-based)
  val keywords = P.oneOf(List(
    P.string("select"), P.string("insert"), P.string("update"),
    P.string("delete"), P.string("create"), P.string("drop"),
    P.string("alter"),  P.string("where"),  P.string("group"),
    P.string("order"),  P.string("having"), P.string("limit")
  ))
  
  def parseKeywords(input: String): Boolean = {
    keywords.parse(input) match {
      case Right(_) => true
      case _ => false
    }
  }

  // 4. Case-Insensitive Keywords Parser
  val ignoreCaseKeywords = P.oneOf(List(
    P.ignoreCase("select"), P.ignoreCase("insert"), P.ignoreCase("update"),
    P.ignoreCase("delete"), P.ignoreCase("create"), P.ignoreCase("drop"),
    P.ignoreCase("alter"),  P.ignoreCase("where"),  P.ignoreCase("group"),
    P.ignoreCase("order"),  P.ignoreCase("having"), P.ignoreCase("limit")
  ))
  
  def parseIgnoreCaseKeywords(input: String): Boolean = {
    ignoreCaseKeywords.parse(input) match {
      case Right(_) => true
      case _ => false
    }
  }
}
