## When to Use ParameterizedQuery?

### If your Spanner query is simple enough…
If you’re just running a straightforward query with a couple of parameters, the native `Statement` API is fine:
```java
Statement stmt = Statement.newBuilder("SELECT name FROM Users WHERE id = @id")
    .bind("id").to(user.id())
    .build();
```

It gets more difficult when the query grows complex.

---

### 1. **Dynamic subqueries or conditional fragments**

Imagine you have a user-specified boolean expression (like that of Google CEL expression),
and you need to translate to a Spanner where clause.

**Native Spanner:**
```java
StringBuilder sql = new StringBuilder("SELECT e.id, e.name");
if (includeManager) {
    sql.append(", e.manager_id, m.name as manager_name");
}
sql.append(" FROM Employees e");
if (includeManager) {
    sql.append(" LEFT JOIN Employees m ON e.manager_id = m.id");
}
sql.append(" WHERE e.id = @id");
Statement stmt = Statement.newBuilder(sql.toString())
    .bind("id").to(user.id())
    .build();
```
- **Risks:**
  - String concatenation (SQL injection risk)
  - Your SQL is fragmented and error-prone

**With ParameterizedQuery:**
```java
ParameterizedQuery.of(
    """
    SELECT e.id, e.name
           {include_manager? -> , e.manager_id, m.name AS manager_name}
    FROM Employees e
         {include_manager? -> LEFT JOIN Employees m ON e.manager_id = m.id}
    WHERE e.id = {id}
    """,
    includeManager, user.id(), includeManager);
```
- No string concatenation  
- No SQL injection risk  
- **Minimal SQL fragmentation**—the entire query stays in one piece, making it easier to read, reason about, and maintain  

---

### 2. **Managing subquery parameters**

**Native Spanner:**  
You must manually gather and merge parameter maps from every subquery, invent unique names, and ensure consistency—tedious and error-prone.
- **Risks:**
  - Manual parameter map management
  - Name clashes between subquery parameters
  - Loss of type safety and consistency

**With ParameterizedQuery:**  
```java
ParameterizedQuery toSqlFilter(Expr expr) {
  return switch (expr) {
      case MatchExpr(String column, String value) ->
          ParameterizedQuery.of("`{column}` = '{value}'", column, value);
      case AndExpr(Expr left, Expr right) ->
          ParameterizedQuery.of("({left}) AND ({right})", toSqlFilter(left), toSqlFilter(right));
      case OrExpr(Expr left, Expr right) ->
          ParameterizedQuery.of("({left}) OR ({right})", toSqlFilter(left), toSqlFilter(right));
  };
}

ParameterizedQuery query = ParameterizedQuery.of(
    "SELECT * FROM Foo WHERE {filter}", toSqlFilter(expr));
```
- Each subquery manages its own parameters, which are merged automatically. No name clashes, no manual work.

---

### 3. **Parameterizing table/column names**

**Native Spanner:**
```java
List<String> keyColumns = ...;
String sql = "SELECT " + keyColumns + ", COUNT(*) FROM Users GROUP BY " + keyColumns.stream().join(", ");
Statement stmt = Statement.of(sql);
```
- **Risks:**
  - If any of `keyColumns` comes from the user, injection can happen
  - String concatenation

**With ParameterizedQuery:**
```java
List<String> keyColumns = ...;
ParameterizedQuery.of(
    "SELECT `{key_columns}`, COUNT(*) AS cnt FROM Users GROUP BY `{key_columns}`",
    keyColumns);
```
- Backtick-quoted placeholders are strictly validated as identifiers. No string concat or injection risk.

---

### 4. **Semantic error prevention**

Traditional frameworks only check parameter types (e.g., “this parameter is a String”),
so it’s easy to accidentally pass the wrong value if two fields share the same type.
And out-of-order bugs are subtle and hard to catch, even at runtime.

**With ParameterizedQuery, semantic mistakes are caught at compile time:**
```java
// Suppose your template is:
ParameterizedQuery.of("SELECT * FROM Users WHERE ssn = '{ssn}'", user.name());
//                                    ^ placeholder expects ssn, but passed name()

// Compile-time error! The plugin detects the semantic mismatch, even though both are String.
```
- Catches real-world semantic errors that would silently pass in other APIs, making your code safer from human mistakes.
