package com.google.mu.benchmarks.parsers.scratch

import com.google.mu.benchmarks.parsers.parboiled2.Parboiled2JavaTypeParser
import com.google.mu.benchmarks.parsers.parboiled2.Parboiled2Showdown.IpParser
import org.parboiled2.ParserInput

class DummyRawClass(val input: String)

object ProfileInstantiation {
  def main(args: Array[String]): Unit = {
    val iterations = 10000000
    val inputStr = "String"
    val ipStr = "192.168.1.1"

    println("Warming up JIT...")
    for (_ <- 1 to 1000000) {
      new DummyRawClass(inputStr)
      new IpParser(ipStr)
      new Parboiled2JavaTypeParser(inputStr)
    }

    println("Running benchmark for DummyRawClass...")
    val startRaw = System.nanoTime()
    var i = 0
    while (i < iterations) {
      val x = new DummyRawClass(inputStr)
      i += 1
    }
    val endRaw = System.nanoTime()
    val rawTimeMs = (endRaw - startRaw) / 1000000.0

    println("Running benchmark for IpParser...")
    val startIp = System.nanoTime()
    i = 0
    while (i < iterations) {
      val x = new IpParser(ipStr)
      i += 1
    }
    val endIp = System.nanoTime()
    val ipTimeMs = (endIp - startIp) / 1000000.0

    println("Running benchmark for Parboiled2JavaTypeParser...")
    val startType = System.nanoTime()
    i = 0
    while (i < iterations) {
      val x = new Parboiled2JavaTypeParser(inputStr)
      i += 1
    }
    val endType = System.nanoTime()
    val typeTimeMs = (endType - startType) / 1000000.0

    println("=== RESULTS (Time for 10M instantiations) ===")
    println(f"DummyRawClass:           $rawTimeMs%.2f ms (${iterations / rawTimeMs / 1000.0}%.2fM instantiations/sec)")
    println(f"IpParser:                $ipTimeMs%.2f ms (${iterations / ipTimeMs / 1000.0}%.2fM instantiations/sec)")
    println(f"Parboiled2JavaTypeParser: $typeTimeMs%.2f ms (${iterations / typeTimeMs / 1000.0}%.2fM instantiations/sec)")
  }
}
