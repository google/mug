# SafeSql: Compile-Time Safe SQL Templates for Java

## Introduction

SafeSql is a compile-time checked SQL template library for Java. Its purpose is to let
developers write direct, readable SQL, compose dynamic SQL, and at the same time make accidental
SQL injection **impossible**. For example:

```java {.good}
// Construct a SafeSql with a template and two placeholder values {id} and {name}.
SafeSql sql = SafeSql.of(
    "select id, name, age from users where id = {id} OR name LIKE '%{name}%'",
    userId, userName);

// convenience method to map `ResultSet` to a list of records or Java beans.
List<User> users = sql.query(connection, User.class);
```

This template-based syntax allows you to parameterize the SQL safely and conveniently,
using the `{placeholder}` syntax.

The template placeholder values are automatically sent through JDBC `PreparedStatement`
when `sql.query()` is called.

---

## Common Pain Points in SQL-First Java Development

### 1. Dynamic `IN` Clauses with Collections

When using an `IN` clause, most frameworks require you to manually construct the right number of
placeholders, which can be cumbersome and error-prone.

**Example (MyBatis XML):**

```xml {.bad}
<!-- Using MyBatis <foreach> to handle dynamic IN lists -->
<select id="findByDepartmentIds" resultType="User">
  SELECT * FROM Sales
  WHERE department_id IN
  <foreach item="id" collection="departmentIds" open="(" separator="," close=")">
    #{id}
  </foreach>
</select>
```
```java {.bad}
// Called with:
sqlSession.selectList("findByDepartmentIds", Map.of("departmentIds", List.of(1, 2, 3)));
```

It's safe from SQL injection (if you correctly used `#{}`, not `${}`). But it requires the special
`<foreach>` XML syntax, which adds visual clutter and complexity compared to writing plain SQL. 

**With SafeSql:**
```java {.good}
List<Integer> departmentIds = List.of(1, 2, 3);
SafeSql.of(
    "SELECT * FROM Sales WHERE department_id IN ({department_ids})",
    departmentIds);
```
SafeSql automatically expands the list parameter `{department_ids}` to the correct number of
placeholders and binds the values, eliminating manual string work and preventing common mistakes.

---

### 2. Conditional Query Fragments

Adding optional filters or groupings usually means string concatenation or awkward branching in your code.

**Example (with manual concatenation):**
```java {.bad}
String sql = "SELECT * FROM Sales";
if (groupByDay) {
    sql += " GROUP BY department_id, date";
} else {
    sql += " GROUP BY department_id";
}
```
Manually stitching together query fragments can quickly become hard to read and maintain,
and makes errors or injection bugs more likely.

**With SafeSql:**
```java {.good}
SafeSql.of(
    """
    SELECT department_id {group_by_day -> , date}, COUNT(*) AS cnt
    FROM Sales
    WHERE department_id IN ({department_ids})
    GROUP BY department_id {group_by_day -> , date},
    """,
    groupByDay, departmentIds, groupByDay);
```
The `->` guard in `{group_by_day -> , date}` tells SafeSql to conditionally include `, date`
wherever needed based on the value of `groupByDay`. This keeps dynamic queries clean and makes
logic explicit at the SQL level, not hidden in string operations.

---

### 3. Injecting Identifiers like Column or Table Names

Most frameworks only allow safe parameterization for values, because the JDBC `PreparedStatement`
only supports value parameterization. When it comes to parameterizing SQL identifiers,
such as table names or column names, developers are forced to fall back to direct string concatenation.

**Example (in MyBatis XML):**
```xml {.bad}
<select id="findMetrics" resultType="Map">
  SELECT department_id, date, ${metricColumns}
  FROM Sales
</select>
```
```java {.bad}
// When called as:
sqlSession.selectList("findMetrics", Map.of("metricColumns", "price, currency, discount"));
// Generates:
SELECT department_id, date, price, currency, discount FROM Sales
```

- In MyBatis, `#{}` is for safe value parameterization—these become JDBC parameters and are properly escaped.
- `${}` is direct string substitution—**raw and unescaped**.
- Using `${metricColumns}` means *whatever string is supplied* is injected as-is into the SQL.
    - This creates a **serious SQL injection risk** if any part of the input comes from user data.
- The similarity between `#{}` (JDBC parameterized) and `${}` (please inject me!) is a frequent source of confusion and mistakes.

**With SafeSql:**

Simply backtick-quote (**or double-quote**, whichever your DB uses to quote identifiers)
the placeholder and SafeSql will recognize the intention to use it as an identifier:
 
```java {.good}
List<String> metricColumns = List.of("price", "currency", "discount");
SafeSql.of("SELECT department_id, date, `{metric_columns}` FROM Sales", metricColumns);
```
SafeSql expands `{metric_columns}` into a comma-separated, properly quoted, and validated list of identifiers.
Unsafe or invalid names are rejected, which removes a whole class of injection risks and makes the query easy to audit.

---

### 4. Proper Escaping for LIKE Patterns

When user input is used in `LIKE` queries, forgetting to escape `%` or `_` can cause accidental matches or security issues.

**Example (with manual escaping):**
```java {.bad}
String term = userInput.replace("%", "\%").replace("_", "\_");
String sql = "SELECT * FROM users WHERE name LIKE ?";
jdbcTemplate.query(sql, term);
```
Manual escaping is repetitive and easy to forget, leading to unpredictable results or vulnerabilities.

**With SafeSql:**
```java {.good}
SafeSql.of("SELECT * FROM users WHERE name LIKE '%{name}%'", userName);
```
SafeSql escapes any special characters in parameters used within `LIKE` expressions automatically.
You don’t have to think about escaping rules or risk mistakes—user input is always treated literally.

---

### 5. Query Composition and Parameter Isolation

Composing queries from reusable fragments often leads to parameter name clashes or incorrect bindings,
especially as code grows.

With manual tracking, you have to carefully manage names and argument lists, often by inventing your
own conventions, which makes code hard to read and can break during refactorings.

It’s easy to accidentally overwrite or mismatch parameters, leading to subtle bugs or unexpected results.

**With SafeSql:**
```java {.good}
SafeSql computation = SafeSql.of("CASE ... END as computed, {from_time}", fromTime);
SafeSql whereClause = ...;
SafeSql.of("SELECT department_id, {computation} FROM Sales WHERE {where_clause}", computation, whereClause);
```
Each fragment’s parameters are managed in isolation by SafeSql, so you can safely compose and reuse query parts
without worrying about accidental overwrites or binding mistakes. They are guaranteed to be eventually sent
through `PreparedStatement` in the correct order.

---

### 6. Parameter Name and Order Checking

In traditional frameworks, parameter order or name mismatches can slip by the compiler
and only fail at runtime—or worse, silently produce incorrect results.

**Example (with JDBC):**
```java {.bad}
String sql = "SELECT * FROM users WHERE id = ? AND name = ?";
preparedStatement.setInt(1, userName); // mistake: wrong order
preparedStatement.setString(2, userId);
```
This kind of error won’t always be caught during development, and can be difficult to debug.

**With SafeSql:**
SafeSql integrates with ErrorProne to check at compile time that the template placeholders and the
template arguments match.
If you get the order or number of arguments wrong, you’ll get a clear compilation error.

For example, the following code will fail to compile because the order of the two arguments is wrong:

```java
SafeSql.of(
    "select id, name, age from users where id = {id} OR name LIKE '%{name}%'",
    userName, userId);
```

Such compile-time check is very useful when your SQL is large and you need to change or move a
snippet around, which can cause the placeholders no longer matching the template arguments.

Sometimes you may run into a placeholder name that doesn't match the corresponding argument expression.
If renaming the placeholder isn't an option, consider using an explicit comment like the following:

```java
SafeSql.of(
    "select id, name, age from users where id = {user_id} OR name LIKE '%{user_name}%'",
    /* user_id */ userRequest.getUuid(), userRequest.getUserName());
```

The explicit `/* user_id */` confirms your intention to both SafeSql **and to your readers**
that you do mean to use the uuid as the `user_id`. 

---

### 7. Real, Actual SQL. No DSL; No XML

A common pain point with many Java data frameworks is that you end up writing SQL as a domain-specific language (DSL),
verbose XML, or by assembling fragments with string builders. This creates a barrier when trying to move between
your code and SQL consoles, and often obscures the actual query.

With SafeSql, what you write in the SQL template is real SQL. Everything outside of the `{placeholders}`
are _what-you-see-is-what-you-get_.
There’s no intermediate layer or DSL to learn and two-way translate in your head.
Queries are copy-pasteable between code and database consoles with minimal manual change required.
This keeps the workflow free of friction, simplifies debugging, and helps to make your SQL transparent and maintainable.

---

### 8. Type Safety

SafeSql does not know your database schema so cannot check for column types or column name typos.

But typos and superficial type mismatch (like using int for the `name` column) are the easy kind of errors:

- If you test your SQL (and you should), the DB will instantly catch these errors for you.
  - In fact, it's best if you've copied the SQL from the DB console where the correctness is already verified.
  - And you should have automated tests to ensure the SQL not only type checks, but gives you the right result.
- Most db types are numbers and strings. So the type checking can only provide marginal protection. 

The main safety advantages of SafeSql are in its industry-strength SQL injection prevention, parameter wiring,
and dynamic query construction—areas where mistakes are easy to make and hard to find otherwise.

---

## Security Best Practices

No additional best practices are needed: if your code compiles and runs, your SQL is safe from injection
(and most likely safe from programming errors too).

---

## Conclusion

SafeSql helps keep handwritten SQL expressive and robust, while removing many sources of subtle errors and security risks.
Its template syntax and compile-time checks make query development safer and more maintainable,
so you can focus on business logic—not boilerplate or defensive programming.
