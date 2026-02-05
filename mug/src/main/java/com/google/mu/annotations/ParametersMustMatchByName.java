package com.google.mu.annotations;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Methods and constructors annotated as so will require the call site
 * argument expressions match (include) the tokenized and normalized formal parameter name.
 *
 * <p>For example: {@code new Dimension(size.getWidth(), size.getHeight())} will match
 * {@code record Dimension(int width, int height)}, but will fail if it's defined as
 * {@code record Dimension(int height, int width)}.
 *
 * <p>If the argument expression is indeed as expected despite not matching the parameter name,
 * you can always use an explicit comment, for example: <pre>{@code
 * new Dimension(&#47;* width *&#47; list.get(0), &#47;* height *&#47; list.get(1));
 * }</pre>
 *
 * <p>Note that method references used as functional interfaces are not checked for parameter
 * name matching between the method declaration and the functional interface's method names.
 *
 * <p>Usually, if your method or constructor has multiple parameters of the same type, it adds
 * risk of them being passed in the wrong order, particularly if they are primitive types like
 * strings or ints. You could create a builder, but builders carry significant boilerplate and
 * you could forget to set a required parameter, resulting in runtime error.
 * By simply annotating the constructor with {@code @ParametersMustMatchByName}
 * (and using {@code mug-errorprone} artifact in your {@code <annotationProcessorPaths>}),
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
