package com.google.mu.annotations;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Methods and constructors annotated as such will ensure the call sites pass in expressions
 * that "look like" matching the parameter names.
 *
 * <p>Usually, if your method or constructor has multiple parameters of the same type, it adds
 * risk of them being passed in the wrong order, particularly if they are primitive types like
 * strings or ints. You could create a builder, but builders carry significant boilerplate and
 * you could forget to set a required parameter, resulting in runtime error.
 * By simply annotating the constructor with {@code @ParametersMustMatchByName},
 * you get compile-time safety for free.
 *
 * <p>For example if you have a record like the following:
 * 
 * <pre>{@code
 * @ParametersMustMatchByName
 * record Profile(String userId, String userName) {}
 * }</pre>
 *
 * You can construct it with <pre>{@code
 *   new Profile(currentUser.getId(), currentUser.getName())
 * }</pre>
 *
 * But if you change the constructor signature to {@code
 * record Profile(String userName, String userId)}. it will fail to compile because the parameter
 * names no longer match the provided argument expressions, in order.
 * 
 * <p>The {@code currentUser.getId()} expression matches the {@code userId} parameter name
 * because the effective tokens of {@code currentUser.getId()} is {@code ["current", "user", "id"]}
 * ("get" and "is" prefixes are ignored). It includes as a subsequence the {@code ["user", "id"]}
 * tokens from {@code userId}.
 *
 * <p>Occasionally, if the argument expression is indeed as expected despite not matching the
 * parameter name, you can always add an explicit comment to tell the compiler
 * <em>and the code readers</em> that: "trust me, I know what I'm doing".
 *
 * <p>For example: <pre>
 *   new Dimension(&#47;* width *&#47; list.get(0), &#47;* height *&#47; list.get(1));
 * </pre>
 * 
 * Or, trailing comments can also be used under the style of one-arg-per-line: <pre>{@code
 *   new Dimension(
 *       list.get(0),  // width
 *       list.get(1)); // height
 * }</pre>
 *
 * <p>In a sense, <pre>&#47;* width *&#47; list.get(0)</pre> serves a similar purpose to
 * {@code .setWidth(list.get(0))} in a builder chain – they both explicitly spell out "width"
 * as the target to ensure you don't pass in {@code height} by mistake. Except with a {@code
 * @ParametersMustMatchByName}-annotated constructor, the per-parameter comment is on a need basis
 * that's only necessary for code not already self-evident.
 * If you have a {@code width} local variable for example, simply pass it in without the syntax
 * redundancy mandated by {@code builder.setWidth(width)}. The most concise code is also the safe
 * code, guaranteed by the compile-time plugin.
 *
 * <p>For literal parameters (string literals, int literals, enum constants, class literals),
 * the parameter name matching rule is relaxed if the corresponding method parameter's type is unique
 * (no other parameters share the same type).
 *
 * <p>Note that method references used as functional interfaces are not checked for parameter
 * name matching between the method declaration and the functional interface's method names.
 *
 * <p>To use, just ensure you have the following snippet in your pom.xml:
 *
 * <pre>{@code
 * <build>
 *   <pluginManagement>
 *     <plugins>
 *       <plugin>
 *         <artifactId>maven-compiler-plugin</artifactId>
 *         <configuration>
 *           <annotationProcessorPaths>
 *             <path>
 *               <groupId>com.google.errorprone</groupId>
 *               <artifactId>error_prone_core</artifactId>
 *               <version>2.40.0</version>
 *             </path>
 *             <path>
 *               <groupId>com.google.mug</groupId>
 *               <artifactId>mug-errorprone</artifactId>
 *               <version>9.9.1</version>
 *             </path>
 *           </annotationProcessorPaths>
 *         </configuration>
 *       </plugin>
 *     </plugins>
 *   </pluginManagement>
 * </build>
 * }</pre>
 *
 * @since 9.9.1
 */
@Documented
@Retention(CLASS)
@Target({ METHOD, CONSTRUCTOR, TYPE, PACKAGE })
public @interface ParametersMustMatchByName {}
