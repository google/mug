package com.google.mu.annotations;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Methods and constructors annotated as such will require the call site
 * argument expressions match (include) the tokenized and normalized formal parameter name.
 *
 * <p>For example: {@code new Profile(currentUser.getId(), currentUser.getName())} will match {@code
 * record Profile(String userId, String userName)}, but will fail to compile if the constructor were
 * defined as {@code record Profile(String userName, String userId)}. The {@code
 * currentUser.getId()} expression matches the {@code userId} parameter name because the effective
 * tokens of {@code currentUser.getId()} is {@code ["current", "user", "id"]}, which includes as a
 * subsequence the ({@code ["user", "id"]}) tokens from {@code userId}.
 *
 * <p>If the argument expression is indeed as expected despite not matching the parameter name,
 * you can always use an explicit comment, for example: <pre>
 * new Dimension(&#47;* width *&#47; list.get(0), &#47;* height *&#47; list.get(1));
 * </pre>
 *
 * <p>For literal string or number parameters, the parameter name matching rule is relaxed if the
 * corresponding method parameter's type is unique (no other parameters share the same type).
 *
 * <p>Note that method references used as functional interfaces are not checked for parameter
 * name matching between the method declaration and the functional interface's method names.
 *
 * <p>Usually, if your method or constructor has multiple parameters of the same type, it adds
 * risk of them being passed in the wrong order, particularly if they are primitive types like
 * strings or ints. You could create a builder, but builders carry significant boilerplate and
 * you could forget to set a required parameter, resulting in runtime error.
 * By simply annotating the constructor with {@code @ParametersMustMatchByName},
 * you get compile-time safety for free. Just ensure you have the following snippet in your pom.xml:
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
 *               <version>10.0</version>
 *             </path>
 *           </annotationProcessorPaths>
 *         </configuration>
 *       </plugin>
 *     </plugins>
 *   </pluginManagement>
 * </build>
 * }</pre>
 *
 * @since 10.0
 */
@Documented
@Retention(CLASS)
@Target({ METHOD, CONSTRUCTOR, TYPE })
public @interface ParametersMustMatchByName {}
