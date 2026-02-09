package com.google.mu.errorprone;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.mu.errorprone.SourceUtils.argsAsTexts;
import static com.google.mu.errorprone.SourceUtils.normalizeForComparison;

import java.util.List;
import java.util.stream.IntStream;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.util.ASTHelpers;
import com.google.mu.util.Substring;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;

/**
 * Checks that call sites of {@code @ParametersMustMatchByName} methods must match the declared parameter
 * names.
 */
@BugPattern(
    summary =
        "Checks that call sites of methods and constructors annotated by @ParametersMustMatchByName"
            + " do pass expected expressions for each corresponding parameter matching the"
            + " declared parameter name.",
    link = "go/parameters-must-match-by-name",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class ParametersMustMatchByNameCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck, AbstractBugChecker.ConstructorCallCheck {
  private static final String ANNOTATION_NAME =
      "com.google.mu.annotations.ParametersMustMatchByName";
  private static final Substring.Pattern ARG_COMMENT = Substring.spanningInOrder("/*", "*/");

  @Override public void checkConstructorCall(NewClassTree tree, VisitorState state)
      throws ErrorReport {
    checkParameters(
        ASTHelpers.getSymbol(tree),
        tree.getArguments(),
        argsAsTexts(tree.getIdentifier(), tree.getArguments(), state),
        state);
  }

  @Override public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    checkParameters(
        ASTHelpers.getSymbol(tree),
        tree.getArguments(),
        argsAsTexts(tree.getMethodSelect(), tree.getArguments(), state),
        state);
  }

  private void checkParameters(
      MethodSymbol method,
      List<? extends ExpressionTree> args,
      List<String> argSources,
      VisitorState state)
      throws ErrorReport {
    if (method == null) {
      return;
    }
    if (!isEffectivelyAnnotated(method, state)) {
        return;
    }
    boolean methodAnnotated = ASTHelpers.hasAnnotation(method, ANNOTATION_NAME, state);
    ClassTree classTree = state.findEnclosing(ClassTree.class);
    if (classTree == null) {
      return;
    }
    ClassSymbol currentClass = ASTHelpers.getSymbol(classTree);
    List<VarSymbol> params = method.getParameters();
    ImmutableList<String> normalizedArgTexts = normalizeForComparison(argSources);
    // No need to check for varargs parameter name.
    int argsToCheck = method.isVarArgs() ? params.size() - 1 : params.size();
    for (int i = 0; i < argsToCheck; i++) {
      VarSymbol param = params.get(i);
      ExpressionTree arg = args.get(i);
      if (normalizedArgTexts.get(i).contains(normalizeForComparison(param.toString()))) {
        continue;
      }
      // Literal arg or for class-level annotation where the caller is also in the same class,
      // relax the rule except if there is explicit /* paramName */ or ambiguity.
      boolean trustable =
          isTrustableLiteral(arg)
              || arg instanceof LambdaExpressionTree
              || arg instanceof MemberReferenceTree
              || arg instanceof NewClassTree
              || isClassLiteral(arg)
              || isEnumConstant(arg)
              || (!methodAnnotated && method.enclClass().equals(currentClass));
      checkingOn(arg)
          .require(
              trustable // trust if no other parameter has the same type
                  && !ARG_COMMENT.in(argSources.get(i)).isPresent()
                  && isUniqueType(params, i, state),
              "argument expression must match parameter name `%s`",
              param);
    }
  }

  private static boolean isEffectivelyAnnotated(Symbol symbol, VisitorState state) {
    for (Symbol s = symbol; s != null; s = s.owner) {
      if (ASTHelpers.hasAnnotation(s, ANNOTATION_NAME, state)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTrustableLiteral(ExpressionTree tree) {
    return tree instanceof LiteralTree
        && tree.getKind() != Tree.Kind.BOOLEAN_LITERAL
        && tree.getKind() != Tree.Kind.NULL_LITERAL;
  }

  private static boolean isClassLiteral(ExpressionTree tree) {
    return tree instanceof MemberSelectTree
        && ((MemberSelectTree) tree).getIdentifier().contentEquals("class");
  }

  private static boolean isEnumConstant(ExpressionTree tree) {
    Symbol symbol = ASTHelpers.getSymbol(tree);
    return symbol instanceof VarSymbol && symbol.isEnum();
  }

  private static boolean isUniqueType(List<VarSymbol> params, int paramIndex, VisitorState state) {
    Type type = params.get(paramIndex).type;
    return IntStream.range(0, params.size())
        .filter(i -> i != paramIndex)
        .mapToObj(i -> params.get(i).type)
        .noneMatch(t -> ASTHelpers.isSameType(t, type, state));
  }
}
