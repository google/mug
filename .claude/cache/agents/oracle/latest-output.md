# Research Report: JUnit 4 in Java Projects

Generated: 2026-01-21

## Summary

JUnit 4 is an external dependency that must be explicitly declared in build configurations. It is NOT part of the JDK. When projects claim "zero dependencies," they typically refer to production/compile-time dependencies only—test dependencies like JUnit are almost always excluded from such claims. JUnit 4 is considered legacy in 2024-2025, with most new projects using JUnit 5, but it remains widely used in existing codebases.

---

## Questions Answered

### Q1: Is JUnit 4 considered an "external dependency"?

**Answer:** Yes, JUnit 4 is absolutely an external dependency. It is NOT part of the JDK or Java standard library.

**Key Facts:**
- JUnit 4 must be explicitly added to `pom.xml` (Maven) or `build.gradle` (Gradle)
- It is hosted on Maven Central as a third-party library
- Maven coordinates: `junit:junit:4.13.2`

**When people say "0 dependencies":**
- They typically refer to **production/compile-time dependencies** only
- Test-scoped dependencies are conventionally excluded from "zero dependency" claims
- A "zero dependency" Java project almost always still has JUnit (or JUnit 5) for testing

**Confidence:** High

---

### Q2: How common is JUnit 4 in 2024-2025?

**Answer:** JUnit 4 is still widely used but is considered legacy. JUnit 5 (Jupiter) has been the recommended version since 2017.

**Status as of 2024-2025:**
- JUnit 4 reached end-of-life for new features; only bug fixes and maintenance
- JUnit 5 (released 2017) is the modern standard
- Many enterprise projects still use JUnit 4 due to:
  - Large existing test suites
  - Migration costs and risks
  - Third-party libraries that depend on JUnit 4
- New projects in 2024-2025 should use JUnit 5

**Migration trend:**
- Gradual migration from JUnit 4 to JUnit 5 is ongoing
- JUnit 5 offers a "JUnit Vintage" engine to run JUnit 4 tests during migration
- Package changes: `org.junit` (JUnit 4) → `org.junit.jupiter.api` (JUnit 5)

**Confidence:** High

---

### Q3: Is it normal to use JUnit classes in test files?

**Answer:** Yes, it is completely standard and expected. This is the entire purpose of JUnit.

**Standard practice:**
- Test files import JUnit classes (`@Test`, `Assert`, `@Before`, etc.)
- Tests are placed in `src/test/java` (Maven/Gradle standard)
- JUnit dependency is declared with `test` scope, meaning:
  - Available for compiling and running tests
  - NOT included in the final production JAR/WAR
  - NOT a runtime dependency for production code

**Maven example:**
```xml
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>  <!-- This is key -->
</dependency>
```

**Gradle example:**
```groovy
testImplementation 'junit:junit:4.13.2'
```

**Confidence:** High

---

## Detailed Findings

### Finding 1: JUnit is NOT part of JDK

JUnit has always been a third-party library, separate from Java itself. This is true for all versions (3, 4, and 5). Every Java project must explicitly declare JUnit as a dependency.

### Finding 2: Test Scope Exclusion from Dependency Counts

The Maven/Gradle `test` scope exists specifically to separate test dependencies from production dependencies. When projects advertise "zero dependencies," they are referring to the number of dependencies required to **run** their production code—not the number needed to **test** it.

This is standard industry practice:
- Production code: 0 external dependencies
- Test code: JUnit (and possibly test utilities like Mockito, AssertJ)

### Finding 3: JUnit 4 vs JUnit 5

| Aspect | JUnit 4 | JUnit 5 (Jupiter) |
|--------|---------|-------------------|
| Release | 2005 | 2017 |
| Package | `org.junit` | `org.junit.jupiter.api` |
| Status | Legacy (maintenance only) | Active development |
| Java support | Java 5+ | Java 8+ |
| Features | Basic testing | Parameterized tests, dynamic tests, extensions |

---

## Recommendations

### For This Codebase

1. **If using JUnit 4:** It's acceptable for existing tests, but consider planning migration to JUnit 5
2. **If claiming "zero dependencies":** Clarify this refers to production dependencies only
3. **When documenting:** Explicitly state "0 production dependencies" to avoid confusion

### Implementation Notes

- JUnit 4.13.2 (released 2021) was the final JUnit 4 release
- JUnit 5 uses different Maven artifacts: `junit-jupiter-api`, `junit-jupiter-engine`, etc.
- To migrate gradually, use JUnit 5's `junit-vintage-engine` to run JUnit 4 tests

---

## Sources

**Note:** The web search service was unavailable during this research session. The information above is based on standard Java ecosystem knowledge that is well-established and documented in:

1. [JUnit 4 GitHub Repository](https://github.com/junit-team/junit4) - Official JUnit 4 source
2. [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) - Official documentation
3. [Maven Central - junit:junit](https://mvnrepository.com/artifact/junit/junit) - Dependency information
4. [Maven Dependency Scope Guide](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope) - Test scope explanation

---

## Open Questions

None. The questions about JUnit 4's dependency status, commonality, and usage in test files are well-established facts in the Java ecosystem.
