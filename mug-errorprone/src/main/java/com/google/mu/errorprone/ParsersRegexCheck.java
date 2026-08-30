/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.errorprone;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import com.google.auto.service.AutoService;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.labs.parse.Parsers;
import com.google.common.labs.regex.RegexPattern;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.google.guava.labs.base.CaseFormats;
import com.google.mu.errorprone.regex.ReDos;
import com.google.mu.errorprone.regex.RegexPatternUtils;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** Validates the regular expression literal string passed to {@code Parsers.regex()}. */
@BugPattern(
    summary = "Checks that the regex literal string used by Parsers.regex() is valid.",
    link = "https://github.com/google/mug/blob/master/dot-parse/README.md",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
@SuppressWarnings("restriction")
public final class ParsersRegexCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck {
  private static final Matcher<ExpressionTree> MATCHER =
      staticMethod().onClass("com.google.common.labs.parse.Parsers").named("regex");
  private static final CharMatcher ALPHA_NUM = CharMatcher.inRange('a', 'z')
      .or(CharMatcher.inRange('A', 'Z'))
      .or(CharMatcher.inRange('0', '9'));

  @Override public void checkMethodInvocation(
      MethodInvocationTree tree, VisitorState state) throws ErrorReport {
    if (!MATCHER.matches(tree, state)) {
      return;
    }
    List<? extends ExpressionTree> args = tree.getArguments();
    if (args.size() == 1) {
      validateRegex(args.get(0));
    } else if (args.size() == 2) {
      String pattern = validateRegex(args.get(0));
      if (pattern != null) {
        MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        Type mapperType = symbol.getParameters().get(1).type;
        int expectedGroups =
            state.getTypes().findDescriptorType(mapperType).getParameterTypes().size();
        int actualGroups = Pattern.compile(pattern).matcher("").groupCount();
        checkingOn(args.get(0))
            .require(
                actualGroups == expectedGroups,
                "regex pattern '%s' has %s capturing group(s), but %s expected",
                pattern,
                actualGroups,
                expectedGroups);
        RegexPattern ast = RegexPattern.of(pattern);
        List<RegexPattern.Group> groups = RegexPatternUtils.capturingGroupsIn(ast);
        ExpressionTree mapperArg = args.get(1);
        if (mapperArg instanceof LambdaExpressionTree lambda) {
          checkLambdaParameters(tree, pattern, lambda, groups);
        } else if (mapperArg instanceof MemberReferenceTree methodRef) {
          checkMethodReference(tree, pattern, methodRef, groups, state);
        }
      }
    }
  }

  private void checkLambdaParameters(
      ExpressionTree invocation,
      String pattern,
      LambdaExpressionTree lambda,
      List<RegexPattern.Group> groups)
      throws ErrorReport {
    ImmutableList<String> lambdaParamNames = lambda.getParameters().stream()
        .map(param -> param.getName().toString())
        .collect(toImmutableList());
    if (lambdaParamNames.size() != groups.size()) {
      return;
    }
    ImmutableList<String> namedGroupNames = groups.stream()
        .filter(g -> g instanceof RegexPattern.Group.Named)
        .map(g -> ((RegexPattern.Group.Named) g).name())
        .collect(toImmutableList());
    ImmutableList<String> correspondingLambdaParamNames = IntStream.range(0, groups.size())
        .filter(i -> groups.get(i) instanceof RegexPattern.Group.Named)
        .mapToObj(lambdaParamNames::get)
        .collect(toImmutableList());
    ImmutableList<String> normalizedLambdaParamNames =
        normalizeNamesForComparison(correspondingLambdaParamNames);
    ImmutableList<String> normalizedNamedGroupNames = normalizeNamesForComparison(namedGroupNames);
    checkingOn(invocation)
        .require(
            !outOfOrder(normalizedLambdaParamNames, normalizedNamedGroupNames),
            "lambda variables %s appear to be in inconsistent order with the capturing groups"
                + " as defined by: %s",
            correspondingLambdaParamNames,
            pattern);
    for (int i = 0; i < groups.size(); i++) {
      if (groups.get(i) instanceof RegexPattern.Group.Named named) {
        String paramName = lambdaParamNames.get(i);
        String groupName = named.name();
        checkingOn(invocation)
            .require(
                mightBeForSameThing(normalizeName(paramName), normalizeName(groupName)),
                "Lambda variable `%s` doesn't look to be for named group (?<%s>...) as defined by:"
                    + " %s\n"
                    + "Consider using `%s` as the lambda variable name or renaming the (?<%s>...)"
                    + " group. A prefix or suffix will work too.",
                paramName,
                groupName,
                pattern,
                groupName,
                groupName);
      }
    }
  }

  private void checkMethodReference(
      ExpressionTree invocation,
      String pattern,
      MemberReferenceTree methodRef,
      List<RegexPattern.Group> groups,
      VisitorState state)
      throws ErrorReport {
    MethodTree method = JavacTrees.instance(state.context).getTree(ASTHelpers.getSymbol(methodRef));
    if (method == null) {
      return;
    }
    ImmutableList<String> paramNames = method.getParameters().stream()
        .map(param -> param.getName().toString())
        .collect(toImmutableList());
    if (paramNames.size() != groups.size()) {
      return;
    }
    ImmutableList<String> namedGroupNames = groups.stream()
        .filter(g -> g instanceof RegexPattern.Group.Named)
        .map(g -> ((RegexPattern.Group.Named) g).name())
        .collect(toImmutableList());
    ImmutableList<String> correspondingParamNames = IntStream.range(0, groups.size())
        .filter(i -> groups.get(i) instanceof RegexPattern.Group.Named)
        .mapToObj(paramNames::get)
        .collect(toImmutableList());
    ImmutableList<String> normalizedParamNames =
        normalizeNamesForComparison(correspondingParamNames);
    ImmutableList<String> normalizedNamedGroupNames = normalizeNamesForComparison(namedGroupNames);
    checkingOn(invocation)
        .require(
            !outOfOrder(normalizedParamNames, normalizedNamedGroupNames),
            "Parameters of referenced method %s(%s) appear to be in inconsistent order with the"
                + " capturing groups as defined by: %s",
            methodRef,
            String.join(", ", paramNames),
            pattern);
    if (paramNames.size() < 3 && !ASTHelpers.inSamePackage(ASTHelpers.getSymbol(method), state)) {
      return;
    }
    for (int i = 0; i < groups.size(); i++) {
      if (groups.get(i) instanceof RegexPattern.Group.Named named) {
        String paramName = paramNames.get(i);
        String groupName = named.name();
        checkingOn(invocation)
            .require(
                mightBeForSameThing(normalizeName(paramName), normalizeName(groupName)),
                "Method parameter `%s` of referenced method `%s` doesn't look to be for"
                    + " named group (?<%s>...) as defined by: %s\n"
                    + "Consider using `%s` as the method parameter name, renaming the (?<%s>...)"
                    + " group, or using a lambda expression where you can use the"
                    + " group name as the parameter name.",
                paramName,
                methodRef,
                groupName,
                pattern,
                groupName,
                groupName);
      }
    }
  }

  private static ImmutableList<String> normalizeNamesForComparison(List<String> names) {
    return names.stream().map(ParsersRegexCheck::normalizeName).collect(toImmutableList());
  }

  private static String normalizeName(String name) {
    return ALPHA_NUM.negate().removeFrom(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, name));
  }

  private static boolean outOfOrder(List<String> names1, List<String> names2) {
    ImmutableSet<String> nameSet = ImmutableSet.copyOf(names2);
    return names1.size() > 1 && names1.stream().allMatch(nameSet::contains)
        && !names1.equals(names2);
  }

  private static boolean mightBeForSameThing(String name1, String name2) {
    return name1.startsWith(name2) || name2.startsWith(name1) || name1.endsWith(name2)
        || name2.endsWith(name1) || Strings.commonPrefix(name1, name2).length() > 3;
  }

  @SuppressWarnings("CompileTimeConstant")
  private String validateRegex(ExpressionTree expression) throws ErrorReport {
    String pattern = ASTHelpers.constValue(expression, String.class);
    checkingOn(expression).require(pattern != null, "compile-time string constant expected");
    try {
      Parsers.regex(pattern);
      ReDos.checkRedosVulnerability(RegexPattern.of(pattern));
      return pattern;
    } catch (IllegalArgumentException e) {
      throw checkingOn(expression).report(e.getMessage());
    }
  }
}
