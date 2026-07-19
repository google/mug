# Mug CEL Parser

A modern (Java 21+), lightweight, high-performance parser for the Common Expression Language (CEL) in Java.

`mug-cel` parses CEL expressions directly into a modern, type-safe Java AST (represented by [CelExpr](https://google.github.io/mug/apidocs/com/google/mu/cel/CelExpr.html) records and sealed interfaces), and provides seamless serialization to standard CEL Protobuf messages.

---

## When to Use `mug-cel`

Consider using `mug-cel` if your project has the following requirements:

### 1. Zero-Dependency Footprint (No ANTLR conflicts)
Standard `cel-java` relies on the ANTLR runtime. In large enterprise deployments, different transitive dependencies using conflicting ANTLR versions can lead to runtime class-loading or linkage failures. 

`mug-cel` has a **zero-dependency footprint** (built on top of `dot-parse`, a lightweight parser combinator library). It eliminates ANTLR versioning conflicts entirely, making it highly suitable for shared libraries or framework integrations.

### 2. Modern Java AST with Pattern Matching
The AST in `mug-cel` is represented using Java **sealed interfaces** and **records** ([CelExpr](https://google.github.io/mug/apidocs/com/google/mu/cel/CelExpr.html)). This allows developers to write clean, type-safe, and exhaustive AST traversals or rewriters using Java 21 pattern matching:

```java
String cel = ...;
switch (CelExpr.of(cel)) {
  case FunctionCall(Ident(var name, _), List.of(var arg), _) when name.equals("size") -> ...
  case MemberCall(var target, Ident(var member, _), var args, _) -> ...
  default -> ...
}
```

It's a significant ergonomic improvement over the traditional nested checks or `AutoValue`-based structures in `cel-java`.

### 3. First-Class Macro Representation (Easier Macro Analysis)
Standard CEL parsers (including `cel-java`) immediately expand macro calls (like `has(...)` or `.filter(...)`) into complex, low-level AST representations (such as `Comprehension` or `Select` with `testOnly=true`). The original macro structure is lost in the parsed AST and only stored as metadata in `SourceInfo`.

`mug-cel` retains macros as first-class, semantically rich nodes in the AST (`CelExpr.Macro.Filter`, `CelExpr.Macro.Exists`, `CelExpr.Macro.Has`, etc.). This makes inspecting, traversing, or rewriting macros simple and type-safe (e.g. without having to parse complex 7-field `Comprehension` trees). 

The standard low-level expanded AST protos are generated transparently by [`CelParser.parseToProto()`](https://google.github.io/mug/apidocs/com/google/mu/cel/CelParser.html#parseToProto(java.lang.String)), allowing seamless compatibility with the rest of the `cel-java` ecosystem (such as the type checker and interpreter).

### 4. High Performance Parsing (Stateless & Thread-Safe)
`mug-cel` is optimized for parsing speed. JMH benchmarks show it parses expressions **1.5x to 2.7x faster** than the standard ANTLR-based `cel-java` parser across various workloads.

Unlike ANTLR-based parsers which maintain mutable state during parsing and force `cel-java` to allocate a fresh pipeline of stateful helper objects (`Lexer`, `Parser`, `TokenStream`, etc.) for every single parse invocation, combinator-based parsers are stateless and inherently thread-safe. A single static parser tree is reused globally, eliminating thread allocation overhead on the hot path.

### 5. Production-Safe (StackOverflow Protection)
Parser combinators are traditionally susceptible to JVM-crashing `StackOverflowError` when parsing deeply nested structures (e.g. `(((...)))` or long chained operations), making them risky for parsing untrusted user inputs.

`mug-cel` takes advantage of `dot-parse`'s built-in stack depth protection. It safely limits recursion depth and throws a catchable `ParseException` instead of crashing the JVM, matching the production safety guarantees provided by `cel-java`.

---

## Integration with `cel-java`

`mug-cel` is a parser-only library. If you need type-checking or evaluation, you can easily bridge `mug-cel` to the `cel-java` runtime.

### Direct Proto Consumption
`cel-java` natively supports the standard `com.google.api.expr.v1alpha1.ParsedExpr` proto object produced by `mug-cel`:

```java
CelParser parser = new CelParser();

// 1. Parse using mug-cel
com.google.api.expr.v1alpha1.ParsedExpr parsedExpr = parser.parseToProto("a[3]");

// 2. Wrap with CelProtoAbstractSyntaxTree directly
CelAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromParsedExpr(parsedExpr).getAst();

// 3. Perform type-checking or execution using cel-java
CelValidationResult validationResult = celCompiler.check(ast);
```

### Bridging to `cel.expr` (`dev.cel.expr`)
If your classpath uses the newer `dev.cel.expr` package namespace, you can bridge the identical schemas via standard protobuf serialization/deserialization:

```java
com.google.api.expr.v1alpha1.ParsedExpr parsedExpr = parser.parseToProto("a[3]");

// Translate namespaces via byte copy
dev.cel.expr.ParsedExpr celJavaExpr = 
    dev.cel.expr.ParsedExpr.parseFrom(parsedExpr.toByteArray());

CelAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromParsedExpr(celJavaExpr).getAst();
```
