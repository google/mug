# Compile Time Plugin for `StringFormat`

The `com.google.mu.util.StringFormat` class in Mug and `com.google.mu.safesql.SafeSql` class provide parsing and formatting functionality based on a string template. This artifact provides compile-time checks to help using these classes safely.

If you use bazel, the `mug:format` and `mug-guava:safe_sql` build targets export the plugin out of the box.

If you use Maven, add the following POM snippet to your `maven-compiler-plugin`:

```
<configuration>
  <annotationProcessorPaths>
    <path>
      <groupId>com.google.errorprone</groupId>
      <artifactId>error_prone_core</artifactId>
      <version>2.40.0</version>
    </path>
    <path>
      <groupId>com.google.mug</groupId>
      <artifactId>mug-errorprone</artifactId>
      <version>9.4</version>
    </path>
</configuration>
```
