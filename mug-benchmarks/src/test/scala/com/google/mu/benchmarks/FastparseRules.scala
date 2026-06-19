package com.google.mu.benchmarks

import fastparse._, NoWhitespace._

object FastparseRules {
  // 1. IP Parser
  private def digits[_: P]: P[Unit] = P( CharsWhileIn("0-9") )
  private def dot[_: P]: P[Unit] = P( "." )
  
  def ip[_: P]: P[Unit] = P( digits ~ dot ~ digits ~ dot ~ digits ~ dot ~ digits )
  
  def parseIp(input: String): Boolean = {
    fastparse.parse(input, ip(_)) match {
      case Parsed.Success(_, _) => true
      case _ => false
    }
  }

  // 2. Quoted String Parser
  private def escape[_: P]: P[Unit] = P( "\\" ~ AnyChar )
  private def normal[_: P]: P[Unit] = P( CharsWhile(c => c != '"' && c != '\\') )
  
  def str[_: P]: P[Unit] = P( "\"" ~ (escape | normal).rep ~ "\"" )
  
  def parseString(input: String): Boolean = {
    fastparse.parse(input, str(_)) match {
      case Parsed.Success(_, _) => true
      case _ => false
    }
  }

  // 3. Keywords Parser (Trie-based)
  def keywords[_: P]: P[Unit] = P(
    StringIn(
      "select", "insert", "update",
      "delete", "create", "drop",
      "alter",  "where",  "group",
      "order",  "having", "limit"
    )
  )
  
  def parseKeywords(input: String): Boolean = {
    fastparse.parse(input, keywords(_)) match {
      case Parsed.Success(_, _) => true
      case _ => false
    }
  }

  // 4. Case-Insensitive Keywords Parser
  def ignoreCaseKeywords[_: P]: P[Unit] = P(
    IgnoreCase("select") | IgnoreCase("insert") | IgnoreCase("update") |
    IgnoreCase("delete") | IgnoreCase("create") | IgnoreCase("drop") |
    IgnoreCase("alter")  | IgnoreCase("where")  | IgnoreCase("group") |
    IgnoreCase("order")  | IgnoreCase("having") | IgnoreCase("limit")
  )
  
  def parseIgnoreCaseKeywords(input: String): Boolean = {
    fastparse.parse(input, ignoreCaseKeywords(_)) match {
      case Parsed.Success(_, _) => true
      case _ => false
    }
  }
}
