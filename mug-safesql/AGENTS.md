# Rules for AI Agents Generating Code

## SafeSql.java

When generating or refactoring code using `SafeSql.java`, follow these rules
to ensure safety against SQL injection and maintain readability.

### 1. Templating Rules

1.  **Placeholder Syntax**: `{placeholder}` is the syntax to interpolate into
    the query template, using the `SafeSql.of()` static factory method.
2.  **Supported Types**: Primitive type numbers, strings, `java.time.Instant`,
    `java.time.LocalDate`, `java.time.ZonedDateTime`, and `List` thereof.
3.  **String Values Should Be Quoted**: String placeholders intended to be values
    should be quoted by single quotes like `'{user_id}'`. While not strictly
    required, it makes the intent more explicit to the code readers.
4.  **Identifiers Must Be Backticked**: String placeholders intended to be
    identifiers should be quoted by backticks. They will be sanity checked to
    ensure no dangerous characters are used.
5.  **Query Composition**: `SafeSql` itself can be passed as placeholder
    values. This allows arbitrarily complex and dynamic query composition.
    All compositions are done through templating.
6.  **Raw SQL and Symbols**: Parameters, except if the placeholder is
    backtick-or-double quoted as identifier, are by default passed as
    `PreparedStatement` parameters. If you must pass a trusted raw string or
    subquery, it must be another `SafeSql`.
7.  **List Expansion**:
    -   **Identifiers**: Backtick-quote the placeholder (e.g., `` `{columns}` ``)
        to expand a list of identifiers.
    -   **Values (Strings in IN clause)**: You can single-quote the placeholder
        inside an `IN` clause (e.g., `IN ('{names}')`) for a list of strings.
    -   **Values (Numbers or general)**: Leave the placeholder unquoted (e.g.,
        `IN ({ids})`) to expand a list of values (like integers) into parameters.

    **Examples**:

    ```java
    // Expanding identifiers
    SafeSql query = SafeSql.of("SELECT `{columns}` FROM Users", asList("id", "name"));
    // Expands to: SELECT `id`, `name` FROM Users

    // Expanding values (unquoted)
    SafeSql query = SafeSql.of("SELECT * FROM Users WHERE id IN ({ids})", asList(1, 2, 3));
    // Expands to: SELECT * FROM Users WHERE id IN (?, ?, ?)
    ```
8.  **Conditional Clause**: `SafeSql.of("select {shows_id? -> id,} name from
    Users", showsId())` will include the `"id,"` only if `showsId()` is true.
9.  **Optional and Nullable Parameters**: `SafeSql.of("select .. where {id? ->
    id = 'id?'}", id())` will generate `"where id = 'foo'"` if `id()` returns
    `Optional.of("foo")` or simply `"foo"`. It will omit the snippet if `id()`
    returns `Optional.empty()` or is `null`. Null values are treated the same
    as `Optional.empty()`, allowing you to use nullable variables directly in
    conditional placeholders without wrapping them in `Optional`.
10. **Collection Parameters in Conditionals**: Collection parameters can also be
    used in conditional queries. For example, `SafeSql.of("... where {ids? ->
    id in 'ids?'}", ids())` will generate `"id in ('a', 'b')"` if `ids()`
    returns `[a, b]`, but if `ids()` returns `[]`, it will be omitted.
11. **Positional Parameters**: The placeholder values are passed in positionally
    like `String.format()`. Their correctness will be checked such that the
    placeholder value expression must contain all the words in the placeholder
    name as a subset (in the same order), except `get`/`is` prefixes.
12. **Placeholder Name Reuse**: If the query expects to use the same identifier
    or same value at multiple places, make sure to use the same placeholder
    name. Then the compiler will ensure the same expression is passed for these
    placeholders.
13. **LIKE Clauses and Wildcards**: When using the `LIKE` operator, use the
    syntax `LIKE '%{placeholder}%'` (or `'%{placeholder}'`, `'{placeholder}%'`).
    The query goes through `PreparedStatement` parameterization (using `?`), so
    no SQL injection is possible. The library automatically handles wildcards by
    prefixing `%`, `_`, and `^` in the parameter value with `^`, and appends
    ` ESCAPE '^'` to the generated SQL. Do not manually append the `ESCAPE`
    clause.

    **Example**:

    ```java
    SafeSql query = SafeSql.of("SELECT * FROM Users WHERE name LIKE '%{name}%'", name);
    // Generated SQL: SELECT * FROM Users WHERE name LIKE ? ESCAPE '^'
    ```

### 2. Conditional Snippets

-   **`SafeSql.when(boolean, String, Object...)`**: Use this method when an
    entire query snippet should be guarded on a condition.
-   It can be chained with `.orElse(String, Object...)` or `.orElse(SafeSql)`.
-   **Example**:

    ```java
    SafeSql.when(useId(), "where id = '{id}'", id)
        .orElse("where id is null")
    ```

-   Both `when()` and `orElse()` support the exact same template interpolation
    syntax as `SafeSql.of()`.

-   **Instance `when(boolean)`**: Alternatively, if you already have a `SafeSql`
    instance, you can use the instance method `.when(boolean)` to conditionally
    include it. It returns the instance if true, or `SafeSql.EMPTY` if false.
    This allows for fluent chaining:

    ```java
    SafeSql query = SafeSql.of(
        "SELECT * FROM Users {where}",
        SafeSql.of("WHERE id = {id}", userId).when(condition));
    ```

### 3. Subquery Joiners

-   **`SafeSql.and()`** and **`SafeSql.or()`**: These are `Collector`s used
    to combine a stream of subqueries using `AND` and `OR` operators (e.g.,
    `subqueries.stream().collect(SafeSql.and())`). Proper parentheses are
    automatically added to prevent `AND`/`OR` precedence from changing the
    meaning of the query.

-   **`SafeSql.joining(String delimiter)`**: This is also a `Collector` for
    other types of subquery joins with a custom delimiter (e.g.,
    `subqueries.stream().collect(SafeSql.joining(", "))`).

### 4. Best Practices

-   **Prefer Text Blocks**: Prefer using triple-quote `"""` text blocks over
    string concatenation for multi-line queries.

-   **No Loops or If-Else in Templates**: No loops or if-else statements are
    supported in the minimalist templating. The idea is to always do "logic" in
    Java and avoid template spaghetti. However, if you have two snippets that
    you want to use either one or the other depending on a boolean
    `fooEnabled()`, you can use:

    ```java
    SafeSql.of(
        """
          ...
          {foo_enabled? -> foo,}
          {foo_disabled? -> cast(null as string) as foo,}
          ...
        """,
        fooEnabled(),
        fooDisabled());

    // Helper methods in Java
    boolean fooEnabled();
    boolean fooDisabled() {
      return !fooEnabled();
    }
    ```

-   **Line Wrapping in Conditionals**: Newlines are supported to the right of
    the arrow operator (`->`) in conditional clauses. Use line wrapping properly
    to help readability of long conditional snippets.

-   **Naming Convention and Placeholder Matching**: By default, the
    placeholder's expression must contain as a subset the words in the
    placeholder name. This acts as a safety net against mis-ordering varargs.

    -   **Consistent Naming**: Prefer to name placeholders and variables or
        methods consistently so they naturally match the template placeholder
        without extra work.

    -   **Explicit Comments**: If you must force a match, you can use an
        explicit comment like `/* placeholder_name */` in front of the value.
        This also tells code readers that you know what you are doing.

    -   **Formatting**: When using explicit comments to force matching, properly
        format the placeholder values one per line, as the Google Java Formatter
        may otherwise format them poorly.

    -   **Avoid Over-Commenting**: Don't blindly comment for every argument when
        they already match naturally (e.g., `/* user_id */ user.id()` is silly).

### 5. Guardrails

-   **ErrorProne Recommendation**: While not strictly required, it's strongly
    recommended to use `SafeSql` with ErrorProne in the
    `annotationProcessorPath`. This helps to guard against SQL injection and
    ensures the semantic correctness of template parameters at compile time.

-   **Compilation Errors**: Compilation errors will point to the offending
    template argument when the naming convention or type check fails.

-   **Line Numbers in Templates**: The error message will also show the line
    number of the corresponding placeholder in the template, making it easy to
    locate the issue in long queries.

-   **SQL Injection Prevention**: All placeholders, unless backtick-or-double
    quoted, will have their values passed as `PreparedStatement` parameters to
    avoid SQL injection. Identifier placeholders quoted by backtick or double
    quote will have the identifier string sanity checked to ensure there are no
    dangerous characters that can cause injection.

-   **No Nesting in Conditionals**: Within a conditional clause `{placeholder?
    -> ...}`, do NOT use curly braces again for the placeholder expansion on the
    right side of the arrow. There is only one set of curly braces, which
    encloses the entire placeholder. Use `placeholder?` directly (e.g., `{ids?
    -> id in 'ids?'}`). Nesting curly braces will cause parsing errors.

-   **Quoting Strings and Lists**: **Always** use single or double quotes (e.g.,
    `'{user_id}'`) for string values and backticks (e.g., `` `{column}` ``) for
    string identifiers or lists of strings. Forgetting these quotes is a common
    mistake and will be caught as a compilation error to prevent SQL injection.

### 6. Execution and Integration

While `SafeSql` is primarily focused on safe query construction, it also
provides a simple API for execution and integration with other frameworks.

#### 1. Spring JdbcTemplate Integration

If you are using Spring's `JdbcTemplate`, you can integrate it by using
`toString()` to get the parameterized SQL string and `args()` to get the
parameters:

```java
SafeSql sql = SafeSql.of("SELECT * FROM Users WHERE id = {id}", userId);
List<User> users = jdbcTemplate.query(sql.toString(), sql.args(), rowMapper);
```

#### 2. Direct Execution (via DataSource)

`SafeSql` provides convenience methods to execute queries directly if you have a
`DataSource`. It is recommended to use these overloads to avoid manual resource
management.

-   **Querying for Lists**:

    ```java
    // Map to a Record or Java Bean
    SafeSql query = SafeSql.of("SELECT * FROM Users");
    List<User> users = query.query(dataSource, User.class);

    // Map using a lambda function
    query = SafeSql.of("SELECT name FROM Users");
    List<String> names = query.query(dataSource, row -> row.getString("name"));

    // Single column query (e.g., getting a list of IDs)
    query = SafeSql.of("SELECT id FROM Users");
    List<Long> ids = query.query(dataSource, Long.class);
    ```

-   **Querying for Single Result**:

    ```java
    SafeSql query = SafeSql.of("SELECT * FROM Users WHERE id = {id}", userId);
    Optional<User> user = query.queryForOne(dataSource, User.class);
    ```

-   **Executing Updates**: Use `update` to execute `INSERT`, `UPDATE`, or
    `DELETE` statements. It returns the number of affected rows.

    ```java
    SafeSql query = SafeSql.of("UPDATE Users SET status = 'ACTIVE' WHERE id = {id}", userId);
    int rowsUpdated = query.update(dataSource);
    ```

-   **Lazy Streaming**: Use `queryLazily` to stream results. **Must** be used
    within a try-with-resources block to ensure the connection is closed.

    ```java
    SafeSql query = SafeSql.of("SELECT * FROM Users");
    try (Stream<User> users = query.queryLazily(dataSource, User.class)) {
      users.forEach(System.out::println);
    }

    // Or for a single column
    query = SafeSql.of("SELECT id FROM Users");
    try (Stream<Long> ids = query.queryLazily(dataSource, Long.class)) {
      ids.forEach(System.out::println);
    }
    ```

#### 3. Customizing Statements

You can customize the `Statement` or `PreparedStatement` (e.g., to set fetch
size or max rows) by passing a `StatementSettings` lambda:

```java
SafeSql query = SafeSql.of("SELECT * FROM Users");
List<User> users = query.query(
    dataSource,
    stmt -> stmt.setFetchSize(100),
    User.class);
```

#### 4. Performance & Batching (via Connection)

For cases where you need to reuse statements or perform batch operations,
`SafeSql` provides APIs that take a `Connection` (instead of `DataSource`):

-   **Reusable Templates**:

    ```java
    try (Connection conn = dataSource.getConnection()) {
      var query = SafeSql.prepareToQuery(
          conn, "SELECT * FROM Users WHERE name = {name}", User.class);
      List<User> users1 = query.with("Alice");
      List<User> users2 = query.with("Bob");
    }
    ```

-   **Batching**:

    ```java
    try (Connection conn = dataSource.getConnection()) {
      var insertUser = SafeSql.prepareToBatch(
          conn, "INSERT INTO Users(id, name) VALUES({id}, '{name}')");
      Set<Statement> batches = users.stream()
          .map(user -> insertUser.with(user.id(), user.name()))
          .collect(toUnmodifiableSet());
      for (Statement batch : batches) {
        batch.executeBatch();
      }
    }
    ```

### 7. Avoiding API Hallucination

-   **No Fluent Builder Pattern**: `SafeSql` does **not** support a fluent
    builder pattern for SQL clauses. There are no methods like `select()`,
    `from()`, `where()`, or `orderBy()`.

-   **All Construction via Templating**: All query construction must be done
    through string templates using `SafeSql.of()`, `SafeSql.when()`, and
    composition by passing `SafeSql` objects as placeholder values.

-   **Do Not Guess Methods**: Do not assume methods exist for dynamically
    building queries. If it's not in the small set of methods documented above
    (`of`, `when`, `orElse`, `and`, `or`, `joining`), it does not exist.
