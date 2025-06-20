Disclaimer: This is not an official Google product.

# Mug
A small Java 8+ utilities library ([javadoc](http://google.github.io/mug/apidocs/index.html)), widely used in Google's internal Java codebase, with **0 deps** (Proto, BigQuery, Guava addons are in separate artifacts). ![](https://travis-ci.org/google/mug.svg?branch=master)

## Highlights

- ✅ [`BiStream`](./mug/src/main/java/com/google/mu/util/stream/README.md) – streams `Map` and pair-wise collections  
  → `BiStream.zip(keys, values).toMap()`
- ✅ [`Substring`](https://github.com/google/mug/wiki/Substring-Explained) – composable substring extraction & manipulation  
  → `Substring.between("(", ")").from("call(foo)") → "foo"`
- ✅ [`StringFormat`](https://github.com/google/mug/wiki/StringFormat-Explained) – compile-time-safe bidirectional parsing/formatting  
  → `new StringFormat("/home/{user}/{date}").parse(filePath, (user, date) -> ...)`
- ✅ [`SafeSql`](./mug-guava/src/main/java/com/google/mu/safesql/README.md) – _the only_ safe SQL template (***think your SQL is safe? think again!***)  
  → ```SafeSql.of("select id, `{col}` from Users where id = {id}", col, id)```
- ✅ [`DateTimeFormats`](./mug/src/main/java/com/google/mu/time/README.md) – parse datetimes by example  
  → `DateTimeFormatter format = formatOf("2024-03-14 10:00:00.123 America/New_York")`

<details>
<summary>More tools</summary>
 
- [`Iteration`](https://github.com/google/mug/wiki/Iteration-Explained) - implement lazy stream with recursive code
- [`BinarySearch`](./mug-guava/src/main/java/com/google/mu/collect/README.md) - solve LeetCode binary search problems  
  → `BinarySearch.inSortedArrayWithTolerance(doubleArray, 0.0001).find(target)`
- [`StructuredConcurrency`](./mug/src/main/java/com/google/mu/util/concurrent/README.md) - simple structured concurrency on virtual threads  
  → `concurrently(() -> fetchArm(), () -> fetchLeg(), (arm, leg) -> makeRobot(arm, leg))`
- [`MoreStreams`](https://google.github.io/mug/apidocs/com/google/mu/util/stream/MoreStreams.html)  
  → `whileNotNull(queue::poll).filter(...).map(...)`
- [`Optionals`](https://google.github.io/mug/apidocs/com/google/mu/util/Optionals.html)  
  → `return optionally(obj.hasFoo(), obj::getFoo);`

</details>

<details>
<summary>Installation</summary>

##### Maven

Add the following to pom.xml:
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug</artifactId>
    <version>8.7</version>
  </dependency>
```

Add `mug-errorprone` to your annotationProcessorPaths:

```
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <annotationProcessorPaths>
              <path>
                <groupId>com.google.errorprone</groupId>
                <artifactId>error_prone_core</artifactId>
                <version>2.23.0</version>
              </path>
              <path>
                <groupId>com.google.mug</groupId>
                <artifactId>mug-errorprone</artifactId>
                <version>8.7</version>
              </path>
            </annotationProcessorPaths>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
```

Protobuf utils ([javadoc](https://google.github.io/mug/apidocs/com/google/mu/protobuf/util/package-summary.html)):
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-protobuf</artifactId>
    <version>8.7</version>
  </dependency>
```

Guava add-ons (with [`SafeSql`](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeSql.html), [`SafeQuery`](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeQuery.html) and [`GoogleSql`](https://google.github.io/mug/apidocs/com/google/mu/safesql/GoogleSql.html)):
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-guava</artifactId>
    <version>8.7</version>
  </dependency>
```

##### Gradle

Add to build.gradle:
```
  implementation 'com.google.mug:mug:8.7'
  implementation 'com.google.mug:mug-guava:8.7'
  implementation 'com.google.mug:mug-protobuf:8.7'
```
</details>


