Disclaimer: This is not an official Google product.

# Mug
A small Java 8+ utilities library ([javadoc](http://google.github.io/mug/apidocs/index.html)), widely used in Google's internal Java codebase, with **0 deps** (Proto, BigQuery, Guava addons are in separate artifacts). ![](https://travis-ci.org/google/mug.svg?branch=master)

Offers:
* **Intuitive to read**, powerful string manipulation ([StringFormat](https://github.com/google/mug/wiki/StringFormat-Explained), [Substring](https://github.com/google/mug/wiki/Substring-Explained))
   * `new StringFormat("/{user}-home/{yyyy}/{mm}/{dd}").parse(path, (user, yyyy, mm, dd) -> ...)`
   * `String user = before(first('@')).from(email).orElseThrow();`
* Streaming pairs ([BiStream](https://github.com/google/mug/wiki/BiStream-Explained))
   * `Map<Instant, Long> histogram = zip(times, counts).toMap();`
   * `Map<K, V> combined = concat(map1, map2).toMap();`
   * `Map<Principal, V> keyedByPrincipal = BiStream.from(keyedByUserId).mapKeys(UserId::principal).toMap();`
* More ([MoreStreams](#morestreams), [Optionals](#optionals), [DateTimeFormats](https://github.com/google/mug/wiki/Parsing-Date-Time-Should-Be-10x-Easier), [...](https://github.com/google/mug/wiki))
  * Create `Optional` with a guard condition:
    * `return optionally(count > 0, () -> total / count);`
  * Parse any legit date/time string (without a pattern string):
    * `Instant timestamp = DateTimeFormats.parseToInstant("2024-01-30 15:30:00-08")`
    * `DateTimeFormats.formatOf("Tue, 10 Jan 2023 10:00:00.123 America/Los_Angeles")`

## Installation
### Maven

Add the following to pom.xml:
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug</artifactId>
    <version>8.3</version>
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
                <version>8.3</version>
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
    <version>8.3</version>
  </dependency>
```

Guava add-ons (with [`SafeSql`](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeSql.html), [`SafeQuery`](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeQuery.html) and [`GoogleSql`](https://google.github.io/mug/apidocs/com/google/mu/safesql/GoogleSql.html)):
```
  <dependency>
    <groupId>com.google.mug</groupId>
    <artifactId>mug-guava</artifactId>
    <version>8.3</version>
  </dependency>
```

### Gradle

Add to build.gradle:
```
  implementation 'com.google.mug:mug:8.3'
  implementation 'com.google.mug:mug-guava:8.3'
  implementation 'com.google.mug:mug-protobuf:8.3'
```


## StringFormat

Extracts structured data from string:

```java
new StringFormat("/users/{user}/.{hidden_file_name}")
    .parse(filePath, (user, fileName) -> ...);
```

```java
new StringFormat("{hour}:{minute}:{second}.{millis}")
    .parse(“10:26:30.748”, (hour, minute, second, millis) -> ...);
```

An ErrorProne check is in place to check that the number of lambda parameters and
the parameter names match the format string.

This allows you to define `StringFormat` objects as private class constant, and safely use them
many lines away.


## Substring

**Example 1: strip off a prefix if existent:**
```java
String httpStripped = Substring.prefix("http://").removeFrom(uri);
```

**Example 2: strip off any scheme prefix from a uri:**
```java
String schemeStripped = Substring.upToIncluding(first("://")).removeFrom(uri);
```

**Example 3: split a string in the format of "name=value" into `name` and `value`:**
```java
Substring.first('=').split("name=value").map((name, value) -> ...);
```

**Example 4: replace trailing "//" with "/" :**
```java
Substring.suffix("//").replaceFrom(path, "/");
```


**Example 5: strip off the suffix starting with a dash (-) character :**
```java
last('-').toEnd().removeFrom(str);
```

**Example 6: extract a substring using regex :**
```java
String quoted = Substring.first(Pattern.compile("'(.*?)'"), 1)
    .from(str)
    .orElseThrow(...);
```

**Example 7: find the substring between the first and last curly braces ({) :**
```java
String body = Substring.between(first('{'), last('}'))
    .from(source)
    .orElseThrow(...);
```

## Stream

#### [BiStream](https://google.github.io/mug/apidocs/com/google/mu/util/stream/BiStream.html) streams pairs of objects.

This class closely mirrors JDK `Stream` API (the few extra methods of "its own" are very straight-forward). If you are familiar with Jdk stream, learning curve is minimal.

**Example 1: to concatenate `Map`s:**

```java
import static com.google.mu.util.stream.BiStream.concat;

Map<AccountId, Account> allAccounts = concat(primaryAccouunts, secondaryAccounts).toMap();
```

**Example 2: to combine two streams:**

```java
BiStream.zip(requests, responses)
    .mapToObj(RequestAndResponseLog::new);
```

**Example 3: to build a Map fluently:**

```java
Map<DoctorId, Patient> patientsByDoctorId = BiStream.zip(doctors, patients)
    .filter((doctor, patient) -> patient.likes(doctor))
    .mapKeys(Doctor::getId)
    .collect(toMap());
```

**Example 4: to build Guava ImmutableListMultimap fluently:**

```java
ImmutableListMultimap<ZipCode, Address> addressesByZipCode = BiStream.from(addresses)
    .mapKeys(Address::getZipCode)
    .collect(ImmutableListMultimap::toImmutableListMultimap);
```

**Example 5: to 
a `Map` into sub-maps:**

```java
import static com.google.mu.util.stream.BiCollectors.groupingBy;

Map<Address, PhoneNumber> phonebooks = ...;
Map<State, Map<Address, PhoneNumber>> statePhonebooks = BiStream.from(phonebooks)
    .collect(groupingBy(Address::state, Collectors::toMap))
    .toMap();
```

**Example 6: to merge `Map` entries:**

```java
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;

Map<Account, Money> totalPayouts = projects.stream()
    .map(Project::payments)  // Stream<Map<Account, Money>>
    .collect(flatteningMaps(toMap(Money::add)));
```

**Example 7: to apply grouping over `Map` entries:**

```java
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;
import static java.util.stream.Collectors.summingInt;

Map<EmployeeId, Integer> workerHours = projects.stream()
    .map(Project::getTaskAssignments)  // Stream<Map<Employee, Task>>
    .collect(flatteningMaps(toMap(summingInt(Task::hours))));
```

**Example 8: to turn a `Collection<Pair<K, V>>` to `BiStream<K, V>`:**

```java
BiStream<K, V> stream = RiStream.from(pairs, Pair::getKey, Pair::getValue);
```

**Q: Why not `Map<Foo, Bar>` or `Multimap<Foo, Bar>`?**

A: Sometimes Foo and Bar are just an arbitrary pair of objects, with no key-value relationship. Or you may not trust `Foo#equals()` and `hashCode()`. Instead, drop-in replace your `Stream<Pair<Foo, Bar>>`/`List<Pair<Foo, Bar>>` with `BiStream<Foo, Bar>`/`BiCollection<Foo, Bar>` to get better readability.

**Q: Why not `Stream<FooAndBar>`?**

A: When you already have a proper domain object, sure. But you might find it cumbersome to define a bunch of FooAndBar, PatioChairAndKitchenSink one-off classes especially if the relationship between the two types is only relevant in the local code context.

**Q: Why not `Stream<Pair<Foo, Bar>>`?**

A: It's distracting to read code littered with opaque method names like `getFirst()` and `getSecond()`.

#### [MoreStreams](https://google.github.io/mug/apidocs/com/google/mu/util/stream/MoreStreams.html)

**Example 1: to group consecutive elements in a stream:**

```java
List<StockPrice> pricesOrderedByTime = ...;

List<List<StockPrice>> priceSequences =
    MoreStreams.groupConsecutive(
            pricesOrderedByTime.stream(), (p1, p2) -> closeEnough(p1, p2), toList())
        .collect(toList());
```

**Example 2: to iterate over `Stream`s in the presence of checked exceptions or control flow:**

The `Stream` API provides `forEach()` to iterate over a stream, if you don't have to throw checked exceptions.

When checked exception is in the way, or if you need control flow (`continue`, `return` etc.), `iterateThrough()` and `iterateOnce()` can help. The following code uses `iterateThrough()` to write objects into an `ObjectOutputStream`, with  `IOException` propagated:

```java
Stream<?> stream = ...;
ObjectOutput out = ...;
iterateThrough(stream, out::writeObject);
```

with control flow:
```java
for (Object obj : iterateOnce(stream)) {
  if (...) continue;
  else if (...) return;
  out.writeObject(obj);
}
```

**Example 3: to merge maps:**
```java
interface Page {
  Map<Day, Long> getTrafficHistogram();
}

List<Page> pages = ...;

// Merge traffic histogram across all pages of the web site
Map<Day, Long> siteTrafficHistogram = pages.stream()
    .map(Page::getTrafficHistogram)
    .collect(flatteningMaps(groupingBy(day -> day, Long::sum)))
    .toMap();
```


## Optionals

**Example 1: to combine two Optional instances into a single one:**
```java
Optional<Couple> couple = Optionals.both(optionalHusband, optionalWife).map(Couple::new);
```

**Example 2: to run code when two Optional instances are both present:**
```java
Optionals.both(findTeacher(), findStudent()).ifPresent(Teacher::teach);
```

**Example 3: or else run a fallback code block:**
```java
static import com.google.mu.util.Optionals.ifPresent;

Optional<Teacher> teacher = findTeacher(...);
Optional<Student> student = findStudent(...);
ifPresent(teacher, student, Teacher::teach)             // teach if both present
    .or(() -> ifPresent(teacher, Teacher::workOut))     // teacher work out if present
    .or(() -> ifPresent(student, Student::doHomework))  // student do homework if present
    .orElse(() -> log("no teacher. no student"));       // or else log
```

**Example 4: wrap a value in Optional if it exists:**
```java
static import com.google.mu.util.Optionals.optionally;

Optional<String> id = optionally(request.hasId(), request::getId);
```

**Example 5: add an optional element to a list if present:**
```java
static import com.google.mu.util.Optionals.asSet;

names.addAll(asSet(optionalName));
```

All Optionals utilites propagate checked exception from the the lambda/method references.

