## Tl;dr

What are the occasions you need to reach out to `java.util.time.DateTimeFormatter` or `SimpleDateTimeFormatter`? I rarely have a prescribed format that I want to descibe in these pattern strings. What usually happens is that there are some date time strings (likely in one of the standard formats) waiting to be parsed.

One time I got some timestamp strings from db (like `2023-12-30 10:30:00-07`) and needed to read and parse them from a file. Having to look up the DateTimeFormatter pattern syntax and then the right API to parse them felt like: 

> **"Human beings were not supposed to sit in little cubicles looking up bizarre datetime pattern syntaxes all day..."**

The [`DateTimeFormats`](https://google.github.io/mug/apidocs/com/google/mu/time/DateTimeFormats.html) class provides convenience methods to help make such task as easy as possible. You can call `parseZonedDateTime()` to get a `ZonedDateTime`. Or call `parseToInstant()` to get an `Instant`. For example:

```java {.good}
Instant timestamp = DateTimeFormats.parseToInstant(timestampString);
```

These methods are slower because they have to infer the formatter each time called. But they are good fit for performance insensitive code (such as tests).


## Background

The Java `DateTimeFormatter` class operates on a pattern string with format specifiers to control the date time format.

For example:

```java
DateTimeFormatter.ofPattern("EEEE, LLLL dd MM HH:mm:ss.SSSa VV");
DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd hh:mm:ss.SSSa ZZZZZ");
```

Some of the format specifiers are relatively intutive, such as the `HH:mm:ss` part (you still need to pay attention to use the upper case `HH` and not the lower caes `hh`), and the `yyyy-MM-dd` part (again, need to remember to use lower case `yyyy` and upper case `MM`).

But other specifiers (like the timezone or offset specifiers, the weekday etc.) require carefully reading and memorizing the details in the [`DateTimeFormatter`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html) javadoc.

Even after you've memorized all the details about when to use `VV`, how many `Z`, `E`, `L` letters you need, your code readers may still be illiterate when they have to read the cryptic specifiers.

## Prior Arts

The golang's [time library](https://go.dev/src/time/format.go) uses the `2006 Jan 2 15:04:05` as the reference time to specify date time formats. The benefit of such approach is that users don't need to learn the non-intuitive format specifiers.


## DateTimeFormats

As a similar solution, the `DateTimeFormats` library uses date time _examples_ to specify the date time foramt. Let's see some examples first:

```java {.good}
import static com.google.mu.time.DateTimeFormats.formatOf;

// Equivalent to DateTimeFormatter.ofPattern("EEEE, LLLL dd yyyy HH:mm:ss.SSS VV")
private static final DateTimeFormatter FORMATTER = formatOf(
    "Friday, October 20 2023 10:30:05.000 America/New_York");
```

```java {.good}
// Equivalent to ofPattern("E, yyyy LLL dd HH:mm:ssZZZZZ")
private static final DateTimeFormatter FORMATTER = formatOf(
    "Fri, 2023 Oct 20 10:30-08:00");
```

```java {.good}
// Equivalent to DateTimeFormatter.ISO_DATE_TIME
private static final DateTimeFormatter FORMATTER = formatOf(
    "2023-10-09T11:12:13.1+01:00[Europe/Paris]");
```

```java {.good}
// Equivalent to DateTimeFormatter.RFC_1123_DATE_TIME
private static final DateTimeFormatter FORMATTER = formatOf(
    "Tue, 10 Jun 2008 11:05:30 GMT");
```

```java {.good}
// Equivalent to ofPattern("yyyy-MM-dd HH:mm:ss zzz")
private static final DateTimeFormatter FORMATTER = formatOf(
    "2023-10-30 11:05:30 PST");
```


These example strings focus on what _result_ you want the date time to be formatted to. And the library figures out the specifiers.

If you haven't noticed, any arbitrary date and time works. You don't need to remember a reference date and time. In fact, you can often copy paste from date time strings in the wild (from debug log, from database query result or from any document) and not have to reverse-engineer the date time format.

The benefits of this approach are two-fold:

1. Better authorability. Don't need to learn the date/time pattern syntax.
2. Better readability. The formats are mostly WYSIWYG.

## Avoiding Ambiguity

By default, the library can successfully infer from `2023/10/01`, `2023-10-01`, `2023 Oct 01`, `Oct 01 2023`, `01 Oct 2023` etc. What it can't figure out is `01/02/2023`. Is that January 2nd or Febuary 1st? Both interpretations are valid in certain regions of the world.

So if you need `01/02/2023` to mean January 2nd, use a day-of-month that's unambiguous (such as 30):

```java {.good}
formatOf("01/30/2023")
```

Similarly, to interpret it as Febuary 1st:

```java {.good}
formatOf("30/01/2023")
```


## Customization

The Java `DateTimeFormatter` API supports flexible specifiers. For example, you might want to make the microsecond part optional (only printed if non-zero). It's accomplished using square brackets in the format pattern, but `DateTimeFormats` has no way to infer this from the example date time string.

So when you need flexibilities like this, you can mix explict pattern specifiers with example strings (quoted inside pointy brackets):

```java {.good}
// Equivalent to DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm:ss[.SSS] VV")
private static final DateTimeFormatter FORMATTER = formatOf(
    "<Friday>, <30/01/2014 10:30:05>[.SSS] <Europe/Paris>");
```

The idea is to put _examples_ you want to be inferred inside the pointy-bracketed placeholders, along with the explicit specifiers.


## Compile-time check

An ErrorProne plugin is provided (out-of-box if you use bazel or confurable in the `mug-errorprone` annotation processor if you use Maven).

So if you try to call `formatOf("11/12/2023")`, compilation error will tell you the problem immediately.



