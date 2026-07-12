package com.google.mu.benchmarks.parsers.ast.css;

import java.util.List;

public interface Rule {
  record QualifiedRule(String selector, List<Declaration> declarations) implements Rule {}
  record AtRule(String name, List<ComponentValue> options, List<Rule> rules, List<Declaration> declarations) implements Rule {}

  interface AtRuleBody {
    List<Rule> rules();
    List<Declaration> declarations();
  }

  record SimpleBody() implements AtRuleBody {
    public List<Rule> rules() { return List.of(); }
    public List<Declaration> declarations() { return List.of(); }
  }

  record DeclBody(List<Declaration> declarations) implements AtRuleBody {
    public List<Rule> rules() { return List.of(); }
  }

  record RuleBody(List<Rule> rules) implements AtRuleBody {
    public List<Declaration> declarations() { return List.of(); }
  }
}
