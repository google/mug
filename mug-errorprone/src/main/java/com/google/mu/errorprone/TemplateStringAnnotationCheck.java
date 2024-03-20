package com.google.mu.errorprone;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static java.util.stream.Collectors.toList;

import java.util.List;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Type;

/**
 * {@link BugChecker} to assert validity of methods annotated with {@code @TemplateFormatMethod} and
 * {@code TemplateString} annotations.
 */
@BugPattern(summary = "Invalid use of @TemplateString annotation.", severity = ERROR)
@AutoService(BugChecker.class)
public final class TemplateStringAnnotationCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodCheck {
  @Override
  public void checkMethod(MethodTree tree, VisitorState state) throws ErrorReport {
    List<VariableTree> templateStringParams =
        tree.getParameters().stream()
            .filter(param -> ASTHelpers.hasAnnotation(
                ASTHelpers.getSymbol(param), "com.google.mu.annotations.TemplateString", state))
            .collect(toList());
    checkingOn(tree).require(
        templateStringParams.size() <= 1,
        "A method cannot have more than one @TemplateString parameter.");
    if (ASTHelpers.hasAnnotation(ASTHelpers.getSymbol(tree), "com.google.mu.annotations.TemplateFormatMethod", state)) {
      checkingOn(tree).require(
          templateStringParams.size() == 1,
          "Method is annotated with @TemplateFormatMethod, but has no String parameter annotated with @TemplateString.");
      Type stringType = state.getSymtab().stringType;
      for (VariableTree param : templateStringParams) {
        checkingOn(param).require(
            ASTHelpers.isSameType(ASTHelpers.getType(param), stringType, state),
            "Only a string parameter can be annotated @TemplateString.");
      }
    } else {
      checkingOn(tree).require(
          templateStringParams.isEmpty(),
          "Parameter annotated with @TemplateString but method isn't annotated with @TemplateFormatMethod");
    }
  }
}
