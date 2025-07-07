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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import java.util.function.Supplier;

/**
 * A convenience base class allowing subclasses to use precondition-style checking such as {@code
 * checkingOn(tree).require(formatString != null, "message...")}.
 *
 * <p>The subclass should implement one or multiple of the "mixin" interfaces ({@link
 * ConstructorCallCheck}, {@link MethodInvocationCheck}, {@link MemberReferenceCheck}). They adapt
 * from the {@link ErrorReport} exception thrown by the `check` abstract methods to the {@link
 * com.google.errorprone.matchers.Description} return value expected by Error-Prone.
 */
abstract class AbstractBugChecker extends BugChecker {
  /**
   * Mixin interface for checkers that check constructor calls. Subclasses can implement the {@link
   * #checkConstructorCall} and throw {@code ErrorReport} to indicate failures.
   */
  interface ConstructorCallCheck extends NewClassTreeMatcher {
    /** DO NOT override this method. Implement {@link #checkConstructorCall} instead. */
    @Override
    public default Description matchNewClass(NewClassTree tree, VisitorState state) {
      return ErrorReport.checkAndReportError(tree, state, this::checkConstructorCall);
    }

    void checkConstructorCall(NewClassTree tree, VisitorState state) throws ErrorReport;
  }

  /**
   * Mixin interface for checkers that check member references. Subclasses can implement the {@link
   * #checkMemberReference} and throw {@code ErrorReport} to indicate failures.
   */
  interface MemberReferenceCheck extends MemberReferenceTreeMatcher {
    /** DO NOT override this method. Implement {@link #checkMemberReference} instead. */
    @Override
    public default Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
      return ErrorReport.checkAndReportError(tree, state, this::checkMemberReference);
    }

    void checkMemberReference(MemberReferenceTree tree, VisitorState state) throws ErrorReport;
  }

  /**
   * Mixin interface for checkers that check method invocations. Subclasses can implement the {@link
   * #checkMethodInvocation} and throw {@code ErrorReport} to indicate failures.
   */
  interface MethodInvocationCheck extends MethodInvocationTreeMatcher {
    /** DO NOT override this method. Implement {@link #checkMethodInvocation} instead. */
    @Override
    public default Description matchMethodInvocation(
        MethodInvocationTree tree, VisitorState state) {
      return ErrorReport.checkAndReportError(tree, state, this::checkMethodInvocation);
    }

    void checkMethodInvocation(MethodInvocationTree tree, VisitorState state) throws ErrorReport;
  }

  /**
   * Mixin interface for checkers that check method definitions. Subclasses can implement the {@link
   * #checkMethod} and throw {@code ErrorReport} to indicate failures.
   */
  interface MethodCheck extends MethodTreeMatcher {
    /** DO NOT override this method. Implement {@link #checkMethod} instead. */
    @Override
    public default Description matchMethod(MethodTree tree, VisitorState state) {
      return ErrorReport.checkAndReportError(tree, state, this::checkMethod);
    }

    void checkMethod(MethodTree tree, VisitorState state) throws ErrorReport;
  }

  /** Starts checking on {@code node}. Errors will be reported as pertaining to this node. */
  final NodeCheck checkingOn(Tree node) {
    checkNotNull(node);
    return (message, args) -> new ErrorReport(buildDescription(node), message, args);
  }

  /**
   * Starts node checking chain. Errors will be reported as pertaining to the returned position from
   * {@code lazyPosition}.
   */
  final NodeCheck checkingOn(Supplier<? extends DiagnosticPosition> lazyPosition) {
    return (message, args) -> new ErrorReport(buildDescription(lazyPosition.get()), message, args);
  }

  /** Fluently checking on a tree node. */
  interface NodeCheck {
    /**
     * Checks that {code condition} holds or else reports error using {@code message} with {@code
     * args}. If an arg is an instance of {@link Tree}, the source code of that tree node will be
     * reported in the error message.
     */
    @CanIgnoreReturnValue
    default NodeCheck require(boolean condition, String message, Object... args)
        throws ErrorReport {
      if (condition) {
        return this;
      }
      throw report(message, args);
    }

    ErrorReport report(String message, Object... args);
  }

  /** An error report of a violation. */
  static final class ErrorReport extends Exception {
    private final Description.Builder description;
    private final Object[] args;

    private ErrorReport(Description.Builder description, String message, Object... args) {
      super(message, null, false, false);
      this.description = description;
      this.args = args.clone();
    }

    private static <T extends Tree> Description checkAndReportError(
        T tree, VisitorState state, Checker<? super T> impl) {
      try {
        impl.check(tree, state);
      } catch (ErrorReport report) {
        return report.buildDescription(state);
      }
      return Description.NO_MATCH;
    }

    private interface Checker<T extends Tree> {
      void check(T tree, VisitorState state) throws ErrorReport;
    }

    private Description buildDescription(VisitorState state) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] instanceof Tree) {
          args[i] = state.getSourceForNode((Tree) args[i]);
        }
      }
      return description.setMessage(Strings.lenientFormat(getMessage(), args)).build();
    }
  }
}
