# SafeSql: The Only Injection-Safe SQL Templates for Java

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
List<User> users = sql.query(dataSource, User.class);
```

This template-based syntax allows you to parameterize the SQL safely and conveniently,
using the `{placeholder}` syntax.

The template placeholder values are automatically sent through JDBC `PreparedStatement`
when `sql.query()` is called.

---

## Common Pain Points in SQL-First Java Development

### 1. SQL Injection

While JDBC `PreparedStatement` is a vital defense against SQL Injection (SQLi),
relying on it alone is often insufficient thanks to **dynamic SQL construction**.

When query components like table names or `ORDER BY` clauses are built from user input using raw string concatenation
(or `JdbcTemplate`, or MyBatis `${}` interpolation, jooq's escape hatch etc.) a wide-open door for injection is created.

* **NotPetya (2017) – Up to $10 Billion:** This ransomware attack, which exploited initial vulnerabilities that could include web application flaws, caused unprecedented global disruption and staggering financial losses for giants like Maersk and FedEx.
* **Equifax (2017) – Over $1.4 Billion:** A failure to patch a known web application vulnerability exposed sensitive data for 147 million people, leading to massive settlements, fines, and enduring reputational damage.

These represent company-altering disasters. When large, complex systems rely on *programmer caution and code reviews* for dynamic SQL string concatenation, the risk is a timed bomb. Human errors, vast codebase, developer turnover, and rushed reviews make it impossible to manually prevent every subtle SQLi vulnerability. Humans make mistake, they always do.

#### How Does SafeSql Prevent SQLi?

`SafeSql` delivers **100% strong SQL injection safety**, eliminating human errors as a cause of injection.
It achieves this through an easily enforceable, "safe by construction" approach:

1.  **Forbid Unsafe APIs:** Change your database access layer to only accept `SafeSql` as queries, never raw `String`s.
    * This closes all other doors to SQLi. If the `SafeSql` library is safe, your entire codebase is safe.

2.  **Provably Safe by Construction Guarantees:**
    * The SQL template string is required to be a `@CompileTimeConstant`, enforced by ErrorProne.
        * Use dynamic `String` as SQL template $\to$ **Compilation Error.**
    * By default, all parameters passed to the template are automatically sent as JDBC `PreparedStatement` parameters.
        * Pass untrusted `String` where identifier/dynamic SQL needed $\to$ **JDBC Runtime Error.**
    * SafeSql performs strict validation for `String` placeholders used as table/column names (backtick-quoted or double-quoted as identifiers).
        * Pass a string with malicious characters $\to$ **Immediate Runtime Exception.**
    * Subqueries are only embedded from other `SafeSql` objects or enums that are already provably safe from injection.

There is simply no way to accidentally inject malicious code. If `SafeSql` compiles and runs, it's provably safe from SQLi.

No other SQL framework offers **100% guaranteed safety**.


### 2. Dynamic `IN` Clauses with Collections

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

### 3. Conditional Query Fragments

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
    GROUP BY department_id {group_by_day -> , date}
    """,
    groupByDay, departmentIds, groupByDay);
```
The `->` guard in `{group_by_day -> , date}` tells SafeSql to conditionally include `, date`
wherever needed based on the value of `groupByDay`. This keeps dynamic queries clean and makes
logic explicit at the SQL level, not hidden in string operations.

---

### 4. Injecting Identifiers like Column or Table Names

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

### 5. Proper Escaping for LIKE Patterns

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

### 6. Query Composition and Parameter Isolation

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

### 7. Parameter Name and Order Checking

In traditional frameworks, parameter order or name mismatches can slip by the compiler
and only fail at runtime—or worse, silently produce incorrect results.

**Example (with JDBC):**
```java {.bad}
String sql = "SELECT * FROM users WHERE id = ? AND name = ?";
preparedStatement.setInt(1, userName); // mistake: wrong order
preparedStatement.setString(2, userId);
```

**Example (with Spring NamedParameterJdbcTemplate):**
```java {.bad}
new NamedParameterJdbcTemplate(dataSource)
    .queryForList(
        "SELECT * FROM users WHERE id = :id AND name = :name",
        Map.of("nmae", userName, "id", userId));  // type!
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

### 8. Real, Actual SQL. No DSL; No XML

A common pain point with many Java data frameworks is that you end up writing SQL as a domain-specific language (DSL),
verbose XML, or by assembling fragments with string builders. This creates a barrier when trying to move between
your code and SQL consoles, and often obscures the actual query.

With SafeSql, what you write in the SQL template is real SQL. Everything outside of the `{placeholders}`
are _what-you-see-is-what-you-get_.
There’s no intermediate layer or DSL to learn and two-way translate in your head.
Queries are copy-pasteable between code and database consoles with minimal manual change required.
This keeps the workflow frictionless, simplifies debugging, and helps to make your SQL transparent and maintainable.

---

### 9. Type Safety

SafeSql doesn't know your database schema so cannot check for column types or column name typos.

But typos and superficial type mismatch (like using int for the `name` column) are the easy kind of errors:

- If you test your SQL (and you should), the DB will instantly catch these errors for you.
  - In fact, it's best if you've copied the SQL from the DB console where the correctness is already verified.
  - And you should have automated tests to ensure the SQL not only type checks, but gives you the right result.
- Most db types are numbers and strings. So the type checking can only provide marginal protection anyways.
- False sense of security is dangerous. If you ever wish to skip testing your SQL because it "type checks",
  then it's a liability.

The main safety advantages of SafeSql are in its **zero-backdoor** SQL injection prevention,
compile-time **semantic check** such that you can't pass `user.name()` in the place of `{ssn}`
despite both being string, as well as automatic parameter wiring, and convenient dynamic query
construction—areas where mistakes are easy to make and hard to find otherwise.

---

## Security Best Practices

No additional best practices are needed: if your code compiles and the `SafeSql` object is succesfully constructed,
your SQL is safe from injection.

But just as a tip to avoid running into safety-related runtime errors during test: remember to identifier-quote
(double-quote or backtick-quote) your `"{table_name}"`.

And if you like explicitness, consider quoting all string-typed placeholders:
either it's a table/column name and needs identifier-quotes, or it's a string value,
which you can single-quote like `'{user_name}'`. It doesn't change runtime behavior,
but makes the SQL read less ambiguous.

---

## Summary

In a nutshell, use `SafeSql` if any of the following applies to you:

* You are a large enterprise. Relying on developer vigilance to avoid SQL injection isn't an option. Instead, you need **systematically enforced safety**.
* You prefer to write actual SQL, and appreciate the ability to directly **copy-paste queries** between your code and the database console for easy debugging and testing.
* A low learning curve and a *what-you-see-is-what-you-get* (WYSIWYG) approach are important to you. No Domain Specific Language (DSL) to learn.
* You need to parameterize by **table names**, **column names**, all while preventing injection risks.
* You have **dynamic and complex subqueries** to compose. And you find it error prone managing the subquery parameters manually.
* You value **compile-time semantic error prevention** so you won't accidentally use `user.name()` in a place intended for `{ssn}`, even if both are strings.
