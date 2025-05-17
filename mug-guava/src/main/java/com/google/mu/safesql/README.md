# Think your SQL is safe from injection? Think again

SQL Injection has long dominated the OWASP Top 10, yet most Java developers are quick to dismiss it:

> “We’ve been using PreparedStatement for years—no way we’re vulnerable.”

That’s half true. And the other half is exactly where things fall apart.

---

## What is Identifier Injection?

PreparedStatement binds **values** safely using `?` placeholders.
But it can’t bind **SQL structure**: table names, column names,
order-by fields, or control keywords.

That means as soon as you write something like this:

```java
String orderBy = request.getParameter("sort");
String sql = "SELECT * FROM users ORDER BY " + orderBy;
```

You've opened the door to:

```http
GET /users?sort=name desc; DROP TABLE users --
```

Which produces:

```sql
SELECT * FROM users ORDER BY name desc; DROP TABLE users --
```

> This is identifier injection: your values are protected, but your structure is not.

---

## How do frameworks fail to protect this?

Here’s a real-world scenario using MyBatis + Spring JDBC.

### Vulnerable MyBatis Example:

```xml
<select id="listUsers" resultType="User">
  SELECT * FROM users
  <where>
    <if test="name != null">
      AND name = #{name}
    </if>
  </where>
  ORDER BY ${sortField}
</select>
```

Java code:

```java
Map<String, Object> params = Map.of("name", name, "sortField", sort);
List<User> users = sqlSession.selectList("listUsers", params);
```

`#{}` safely binds values.

But `${}` directly injects unescaped user input. MyBatis documentation says:

> “The `$` sign is not safe and can lead to SQL injection.”  
> — [MyBatis XML Reference](https://mybatis.org/mybatis-3/sqlmap-xml.html#Dynamic_SQL)

### A Spring JDBC Variant:

```java
String table = request.getParameter("table");
String sql = "SELECT * FROM " + table + " WHERE status = ?";
jdbcTemplate.query(sql, status);
```

Looks like you're using `?`—safe, right? But the **table name** is user-controlled and unvalidated.

## Why is identifier injection so overlooked?

1. **No errors, no warnings, no scans** – Static analyzers rarely catch structure-level risks.
2. **No logs** – Unlike value injection, structure injection happens quietly during string composition.
3. **Frameworks allow it** – MyBatis lets you use `${}`; Spring JDBC doesn’t warn on concatenated identifiers.

## Is it a real threat?

- **OWASP Cheat Sheet**:  
  > "Bind variables cannot be used for identifiers like table or column names... use whitelisting."  
  — [SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)

- **PostgreSQL Documentation**:  
  > "Never concatenate unchecked input into dynamic queries. Use quote_ident() for identifiers."  
  — [PL/pgSQL Docs](https://www.postgresql.org/docs/current/plpgsql-statements.html#PLPGSQL-STATEMENTS-EXECUTING-DYN)

- **SQLAlchemy Docs**:  
  > "Never, under any circumstances, pass untrusted user input to string-concatenated SQL identifiers such as table names or column names."  
  — [SQLAlchemy SQL Expression Language](https://docs.sqlalchemy.org/en/20/core/tutorial.html)

---

## How SafeSql solves this

[SafeSql](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeSql.html) is a 
compile-time-safe SQL templating system that addresses structure injection head-on:

```java
SafeSql query = SafeSql.of(
  "SELECT `{columns}` FROM users WHERE name LIKE '%{keyword}%'",
  /* columns */ List.of("id", "email"), /* keyword */ "100%");
  
SafeSql query = SafeSql.of(
    "SELECT * FROM `{table}` WHERE status = {status}", table, status);
```

Highlights:

- ✅ **Identifier safety**: ``{columns}`` only accepts safe column names (strict validation).
- ✅ **LIKE safety**: auto-escapes `%`, `_`, and `\` for pattern queries.
- ✅ **Compile-time template checks**: mismatched parameters? Compilation fails.

Resulting SQL:

```sql
SELECT `id`, `email` FROM users WHERE name LIKE ?
```

And parameters:

```java
statement.setObject(1, "%100\\%%")
```

What if you forgot to quote the `{columns}` placeholder with backticks?

The template will pass all unquoted placeholders as JDBC parameters and JDBC will reject
them in the place of columns.

---

## Conditional Subqueries: The Underrated Injection Vector

One of the most common sources of dynamic SQL is conditional logic:

- Filtering by optional fields (e.g., keyword, status, type)
- Adding nested subqueries depending on user input
- Switching between JOINs or SELECT fields based on context

These are structurally dynamic — and thus **prone to injection** if not composed carefully.

### ❌ What this looks like in Spring or JDBC:

```java
String sql = "SELECT * FROM users WHERE 1=1";
if (keyword != null) {
  sql += " AND name LIKE '%" + keyword + "%'";
}
```

Even if you bind the "keyword" as parameter,
the `AND ...` clause itself is dynamically generated,
and it opens up the door to the dangerous string concatenation that can be user
influenced, through enough indirections and parameter passing.

### ❌ What this looks like in MyBatis:

```xml
<select id="listUsers">
  SELECT * FROM users WHERE 1=1
  <where>
    <if test="keyword != null">
      AND name LIKE CONCAT('%', #{keyword}, '%')
    </if>
  </where>
</select>
```

While `#{}` safely binds values, **the conditional logic is entangled with markup**, and doesn’t compose well.
The XML-based logic also makes it hard to abstract and reuse subqueries without duplicating or scattering the logic.

### ✅ What this looks like in SafeSql:

The [`SafeSql.optionally()`](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeSql.html#optionally(java.lang.String,java.util.Optional))
utility renders an optional template only when the Java `Optional` parameter is present.

```java
SafeSql query = SafeSql.of(
    "SELECT * FROM Users WHERE 1=1 {optionally_and_name}",
    optionally("AND name LIKE '%{keyword}%'", keyword));
```

This syntax is:
- ✅ Declarative
- ✅ Directly readable as SQL
- ✅ Easy to refactor into reusable subquery `SafeSql` objects

---

## Conclusion: SafeSql is safe, unconditionally, careful or not.

Try to shoot yourself in the foot and `SafeSql` will stop you.

This is because PreparedStatement guards your values,
but `SafeSql` guards the entire SQL query through Java's strong type and SQL template semantic analysis.
