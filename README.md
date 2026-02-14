Disclaimer: This is not an official Google product.

# Mug (![Coverage](.github/badges/mug-coverage.svg))
A small Java 8+ string processing and streams library ([javadoc](http://google.github.io/mug/apidocs/index.html)), widely used in Google's internal Java codebase, with **0 deps** (Proto, BigQuery, Guava addons are in separate artifacts). ![](https://travis-ci.org/google/mug.svg?branch=master)

## Highlights

- ✅ [`Substring`](https://github.com/google/mug/wiki/Substring-Explained) – composable substring extraction & manipulation  
  → `Substring.between("(", ")").from("call(foo)") → "foo"`
- ✅ [`StringFormat`](https://github.com/google/mug/wiki/StringFormat-Explained) – compile-time-safe bidirectional parsing/formatting  
  → `new StringFormat("/home/{user}/{date}").parse(filePath, (user, date) -> ...)`
- ✅ [`Parser`](https://google.github.io/mug/apidocs/com/google/common/labs/parse/Parser.html) – no more regex  
  → `zeroOrMore(noneOf("\\'")).immediatelyBetween("'", "'").parse(input);`
- ✅ [`BiStream`](./mug/src/main/java/com/google/mu/util/stream/README.md) – streams `Map` and pair-wise collections  
  → `BiStream.zip(keys, values).toMap()`
- ✅ [`SafeSql`](./mug-safesql/src/main/java/com/google/mu/safesql/README.md) – _library-enforced_ **safe**, **composable** SQL template  
  → ```SafeSql.of("select id, `{col}` from Users where id = {id}", col, id)```

<details>
<summary>More tools</summary>
 
- [`DateTimeFormats`](./mug/src/main/java/com/google/mu/time/README.md) – parse datetimes by example  
  → `DateTimeFormatter format = formatOf("2024-03-14 10:00:00.123 America/New_York")`
- [`Iteration`](./mug/wiki/Iteration-Explained) - implement lazy stream with recursive code
- [`BinarySearch`](./mug-guava/src/main/java/com/google/guava/labs/collect/README.md) - solve LeetCode binary search problems  
  → `BinarySearch.inSortedArrayWithTolerance(doubleArray, 0.0001).find(target)`
- [`StructuredConcurrency`](./mug/src/main/java/com/google/mu/util/concurrent/README.md) - simple structured concurrency on virtual threads  
  → `concurrently(() -> fetchArm(), () -> fetchLeg(), (arm, leg) -> makeRobot(arm, leg))`
- [`MoreStreams`](./mug/apidocs/com/google/mu/util/stream/MoreStreams.html)  
  → `whileNotNull(queue::poll).filter(...).map(...)`
- [`Optionals`](./mug/apidocs/com/google/mu/util/Optionals.html)  
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
    <version>9.9.2</version>
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
                <version>9.9.2</version>
              </path>
            </annotationProcessorPaths>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
```
SafeSql ([javadoc](https://google.github.io/mug/apidocs/com/google/mu/safesql/package-summary.html)):
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-safesql</artifactId>
    <version>9.9.2</version>
  </dependency>
```


Dot Parse Combinators ([javadoc](https://google.github.io/mug/apidocs/com/google/common/labs/parse/package-summary.html)):
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>dot-parse</artifactId>
    <version>9.9.2</version>
  </dependency>
```

Protobuf utils ([javadoc](https://google.github.io/mug/apidocs/com/google/mu/protobuf/util/package-summary.html)):
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-protobuf</artifactId>
    <version>9.9.2</version>
  </dependency>
```

##### Gradle

Add to build.gradle:
```
  implementation 'com.google.mug:mug:9.9.2'
  implementation 'com.google.mug:mug-safesql:9.9.2'
  implementation 'com.google.mug:dot-parse:9.9.2'
  implementation 'com.google.mug:mug-guava:9.9.2'
  implementation 'com.google.mug:mug-protobuf:9.9.2'
```
</details>


