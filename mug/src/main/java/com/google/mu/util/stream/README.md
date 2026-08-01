## Why BiStream?

[BiStream](https://google.github.io/mug/apidocs/com/google/mu/util/stream/BiStream.html) is a rapidly growing API in Google's internal "labs" library.

What is it for? Current usage data shows that most people use it to stream through Map or Multimap entries fluently.

Java 8's Stream is a hugely popular API and paradigm. It nicely combines functional programming with conventional Java pragmaticsm. Every `.map()`, `.filter()` line cleanly expresses one thing and one thing only, greatly improving readability while reducing bug rate.

But when it comes down to Map and Multimap entries, things become muddy. For example, transforming then filtering the keys of a Map takes this boilerplate:
```java
map.entrySet().stream()
    .map(e -> Map.entry(transform(e.getKey()), e.getValue()))
    .filter(e -> isGood(e.getKey())
    .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
```

Which is equivalent to the following BiStream code:
```java
BiStream.from(map)
    .mapKeys(this::transform)
    .filterKeys(this::isGood)
    .toMap();
```

If you need to flatten a nested Map, it becomes even more awkward:
```java
// Flatten a Map<String, Map<String, V>> to Map<String, V>
// by concatenating the two string keys
map.entrySet().stream()
    .flatMap(e -> e.getValue().entrySet().stream()
        .map(innerEntry ->
            Map.entry(e.getKey() + innerEntry.getKey(), innerEntry.getValue())))
    .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
```
And the equivalent BiStream code is:
```java
BiStream.from(map)
    .flatMap((r, m) -> BiStream.from(m).mapKeys(r::concat))
    .toMap();
```

## How to use a BiStream?

Most BiStream operations are pretty straight-forward and natural extension of their Stream counterparts.

You can `filter()`:
```java
Map<PhoneNumber, Address> phoneBook = ...;

// by key
BiStream.from(phoneBook)
    .filterKeys(phoneNumber -> phoneNumber.startsWith("312"))
    ...

// by value
BiStream.from(phoneBook)
    .filterValues(address -> address.state().equals("IL"))
    ...

// by both key and value
BiStream.from(phoneBook)
    .filterValues((phoneNumber, address) -> isExpired(phoneNumber, address))
    ...
```

You can `map()`:
```java
Map<Address, Household> households = ...;

// by key
BiStream.from(households)
    .mapKeys(Address::state)
    ...;

// by value
BiStream.from(households)
    .mapValues(Household::income)
    ...;

// from both key and value
BiStream.from(households)
    .mapValues((address, household) -> fiveYearAverageIncome(address, household))
    ...;
```

You can `flatMap()`:
```java
Map<Address, Household> households = ...;

// by key
BiStream.from(households)
    .flatMapKeys(address -> address.getPhoneNumbers().stream())
    ...;

// by value
BiStream.from(households)
    .flatMapValues(household -> household.members().stream())
    ...;

// by both key and value
BiStream.from(phoneBook)
    .flatMap((address, household) -> BiStream.from(household.getMemberMap()))
    ...;
```

There're `anyMatch()`, `allMatch()`, `noneMatch()`:
```java
Map<PhoneNumber, Address> phoneBook = ...;
BiStream.from(phoneBook)
    .anyMatch((phoneNumber, address) -> isInvalid(phoneNumber, address));
...
```

## How to create BiStream?

You can create it from a JDK collection or stream:
```java
// From Map
BiStream.from(map);

// From Multimap
BiStream.from(multimap.entries());

// From any collection or stream
Map<Id, String> idToName = BiStream.from(students, Student::id, Student::name).toMap();
Map<Id, Student> studentMap = BiStream.biStream(students)
    .mapKeys(Student::id)
    .toMap();
```

Or zip two collections or streams:
```
// If you have a list of requests and responses to pair up:
BiStream.zip(requests, responses)
    .mapKeys(Request::fingerprint)
    ...;
```

Through concatenation:
```java
// a handful of Maps
Map<Request, Response> cached = ...;
Map<Request, Response> onDemand = ...;
Map<Request, Response> all = BiStream.concat(cached, onDemand).toMap();

// a stream of maps
BiStream<K, V> biStream = maps.stream()
    .collect(concatenating(BiStream::from));

// a stream of multimaps
BiStream<K, V> biStream = multimaps.stream()
    .collect(concatenating(multimap -> BiStream.from(multimap.entries()));
```

With `groupingBy()`:
```java
import static com.google.mu.util.stream.BiStream.groupingBy;
import java.util.stream.Collectors.counting;

Map<City, Long> cityHouseholds = addresses.stream()
    .collect(groupingBy(Address::city, counting()))
    .toMap();

// Using a BiFunction to reduce group members is more convenient than JDK's groupingBy()
Map<City, Household> richestHouseholds = households.stream()
    .collect(groupingBy(Household::city, this::richerHousehold))
    .toMap();
```

By splitting strings:
```java
Map<String, String> keyValues =
    BiStream.from(flags, flag -> Substring.first('=').splitThenTrim(flag).orElseThrow(...));

// or via a Collector in the middle of a stream pipeline
import static com.google.mu.util.stream.BiStream.toBiStream;

Map<String, String> keyValues = lines.stream()
    ...
    .collect(toBiStream(kv -> Substring.first('=').splitThenTrim(kv).orElseThrow(...)))
    .toMap();
```

## How to get data out of BiStream?

Obviously you can call `.toMap()` to create a Map, or use `.mapToObj()` to convert back to a Stream. But the library is extensible and supports flexible options through the concept of `BiCollector`.

You can collect to a Guava ImmutableMap:
```java
ImmutableMap<K, V> all = BiStream.concat(cached, onDemand)
     .collect(ImmutableMap::toImmutableMap);
```

> **_TIP:_**  At first glance this shouldn't have worked because the guava toImmutableMap() collector requires two Function parameters to get the key and value. But if you try it, it actually works. Why does it compile? This is because what's required here is a `BiCollector`, which is a functional interface with compatible method signature as `toImmutableMap(Function, Function)`.

> **_TIP:_**  In general, one can method reference any such Collector factory method as BiCollector. Examples include `ImmutableBiMap::toImmutableBiMap`, `ImmutableListMiltimap::toImmutableListMultimap`, `Collectors::toConcurrentMap` etc.

> **_TIP:_** The Google internal library has special BiCollector methods that look like the following to make them more discoverable and also static-import friendly:
```java
class BiCollectors {
  public static <K, V> BiCollector<K, V> toImmutableMap() {
    return ImmutableMap::toImmutableMap;
  }
}
```
> This is useful because in the same .java file you can static import both `ImmutableMap.toImmutableMap` and `BiCollectors.toImmutableMap`. And when you call `toImmutableMap()` in a context that requires BiCollector, or when you call `toImmutableMap(Foo::id, Foo::name)` in a context that requires Collector, the compiler will seamlessly figure out which one to use as if both imports were overloads in the same class.

> To avoid dependency, Mug doesn't include these Guava-specific utilities. But it's trivial to create them when you need them. Eventually when BiStream is consolidated into Guava, these internal Guava-specific BiCollector utilities such as `toImmutableTable()` will become available.

And more...

As implied above, you can also collect to a Guava `ImmutableSetMultimap`:
```java
ImmutableSetMultimap<K, V> all = BiStream.concat(cached, onDemand)
     .collect(ImmutableSetMultimap::toImmutableSetMultimap);
```

to a custom data structure:
```java
// compatible with BiCollector signature
<T, V> Collector<T, ?, Ledger<V>> toLedger(
    Function<T, Instant> toTime, Function<T, V> toValue) {...}

Ledger<V> ledger = ledger.timeseries()
    .filterKeys(...)
    .collect(this::toLedger);  // method-ref as a BiCollector
```


Or do further grouping:
```java
Multimap<Address, PhoneNumber> phoneBook = ...;
ImmutableMap<State, ImmutableSet<AreaCode>> stateAreaCodes =
    BiStream.from(phoneBook)
        .mapValues(PhoneNumber::areaCode)
        .collect(BiCollectors.groupingBy(Address::state, toImmutableSet()))
        .collect(toImmutableMap());
```

## Design Considerations

#### Why doesn't BiStream<K, V> extend `Stream<Map.Entry<K, V>>`?

* We expect that with BiStream users will rarely need to deal with Entry objects.
* The Stream class has a large API surface. Next versions can likely add more methods. It'll be hard to stay clear of accidental conflicts with existing or future JDK methods.
* It's not clear that JDK meant for 3rd-party libraries to extend from Stream directly.

#### Why is BiCollector designed with that weird signature?

This is mainly for the ease of reusing existing Collector factory methods as BiCollector implementations. This way, users can freely collect from BiStream to custom data with ease, like `collect(Collectors::toConcurrentMap)`, `collect(ImmutableSetMultimap::toImmutableSetMultimap)`.

#### Why doesn't BiStream implement Iterable?

We believe it's better to stay consistent with JDK Stream. The reason Stream didn't implement Iterable is likely because there is a quasi-standard that Iterable are expected to be idempotent, while streams are one-use-only. 

#### Why not provide a toPairs() method that returns List<Pair<K, V>>?

See [Perusing Pair](https://github.com/google/mug/wiki/Perusing-Pair-(and-BiStream)), we believe it's better not to add library support for Pair.
