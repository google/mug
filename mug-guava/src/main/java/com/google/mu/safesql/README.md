## Safe? Isn't `PreparedStatement` already safe from injection?

Yes (at least mostly, we'll get to where it's not entirely safe). 

If you always write the SQL with literal string, and only parameterize the values, like for `WHERE name = ?`, or `time BETWEEN ? and ?`, then you are almost always safe. For example:

```java
try (PreparedStatement statment =
    connection.prepareStatement("SELECT name FROM Users WHERE id = ?")) {
  statement.setLong(1, id);
  try (ResultSet resultSet = statement.executeQuery()) {
    while (resultSet.next()) {
      String name = resultSet.getString("name");
      ...
    }
  }
}
```

## When would I _not_ use literal SQL strings?

If you have a list of ids to query, you can't just pass the list as a `PreparedStatement`, see https://stackoverflow.com/questions/3107044/preparedstatement-with-list-of-parameters-in-a-in-clause.

You can still try to be safe by adding one `?` character for each list element. But here starts the slippery slope: at some point someone can make a mistake and start building the string directly, because it's so much easier to just do this:

```java {.bad}
String query =
    "SELECT name FROM Users WHERE id IN " + ids.collect(joining(", ", "(", ")");
```

Or, say you have a `UserCriteria` class with a bunch of optional criteria that respresents the application input:

```java
class UserCriteria {
  Optional<String> firstName();
  Optional<String> lastName();
  Optional<String> address();
  Optional<String> email();
}
```

And you want to generate the query that selects from the `User` table based on any combination of these criteria. That is, if `address` is specified, then `address = ?`; if `email` is specified, then `email = ?` etc.

This is also beyond what `PreparedStatement` can provide. You'll be forced to create the query with a dynamic string.

One last example is in SQL Server, you may want to use the `TOP n` operator to limit the page size, but unfortunately the SQL Server JDBC driver doesn't support `TOP ?`.

None of these can't be done safely. Heck, if you are always careful, you can always avoid injection. But who never makes mistakes?

In a large team, a large company with large code base, you are only responsible for one piece of the code base. How do you know that for example the `UserCriteria` object you received from the caller didn't go through some unsafe dynamic SQL construction?

## So how does `SafeSql` make it safe?

Tnere is no rocket science. Any software can be safe by doing the simplest thing with the most basic functionality, like `printf("hello world")`. An offline USB drive holds the data safer than a server on the internet with the most expensive security software.

But of course being uselessly safe is useless. `SafeSql` uses the Java type system and a bit of ErrorProne compile-time plugin to help with dynamic SQL.

The basis of all is the ErrorProne `@CompileTimeConstant` annotation. It requires that you can't just use any `String` as the SQL. It has to be a compile-time constant. That is, `SafeSql.of("SELECT 1")` compiles, but `SafeSql.of(userProvideString)` does not.

On top of that, it uses a templating syntax to allow subqueries to be embedded in, for example:

```java
SafeSql subquery = SafeSql.of("SELECT 1");
SafeSql query = SafeSql.of("SELECT * FROM ({subquery})", subquery);
```
will translate to:

```sql
SELECT * FROM (SELECT 1)
```

What happens if you pass in non-subquery, like a `String`?

```java
String userName = ...;
SafeSql query = SafeSql.of("SELECT * FROM Users WHERE name = {name}", userName);
```

If it isn't itself a `SafeSql`, it's considered a parameter, and `SafeSql` translates it to the `?` placeholder:

```sql
SELECT * FROM Users WHERE name = ?
```

And then when you call `query()` or `prepareStatement()` on the `SafeSql` object, it will call:

```java
preparedStatement.setObject(1, userName);
```

## To support `IN` operator

Just write the most intuitive template:

```java {.good}
SafeSql query = SafeSql.of("SELECT name FROM Users WHERE id IN ({ids})", ids);
```

If the `ids` list has 3 elements, it will translate to:

```sql
SELECT name FROM Users WHERE id IN (?, ?, ?)
```


## Parameterize by identifiers

Sometimes you may need to parameterize by table name. Say, you have a `BetterUsers` table with some experimental indexing that you want to gradually roll out, so you can't just change the application to all the sudden switch to the new table.

Instead, you have a feature flag to control about x% of requests to hit both the new table and the old table (for AvB darklaunch purpose), while the majority traffic still hit the old table. The new table have backward compatible schema so that your existing queries work on both tables (but with different table name).

How do you go about that? `PreparedStatement` doesn't support parameterizing by table names. And you can't just use `SafeQuery.of(featureFlag.getUsersTableName())` because it's not a compile-time constant and will fail to compile.

Using `SafeSql`, you can parameterize the table name by backtick-quoting the placeholder for the table name:

```java {.good}
SafeSql usersQuery(String usersTableName, String userName) {
  return SafeSql.of(
      "SELECT * FROM `{users_table}` WHERE name = {name}",
      usersTablename, userName);
}

SafeSql usersQuery = usersQuery(featureFlag.getUsersTableName());
```

The backticks in the query template tells `SafeSql` that the placeholder value is meant to be an identifier so it will check that the string contains only valid identifier characters (when backtick-quoted). That is, no backtick characters, no backslashes, no ISO control characters etc.


Now assume the `Users` table have many columns, some of which are pretty large (say, "biography" column).

Your application needs to read the table at different places, but to read different fields of the users.

You can use `SELECT *`. But you are trying to optimize the application and would really want to read only the columns you need.

This would require to also parameterize the query by column names, so that you can call `usersQuery(tableName, BASIC_COLUMNS)`, `usersQuery(tableName, BIO_COLUMNS)` etc.

Similarly, you can use backticks to quote the `{columns}` placeholder:


```java {.good}
SafeSql usersQuery(String usersTableName, List<String> columns, String userName) {
  return SafeSql.of(
      "SELECT `{columns}` FROM `{users_table}` WHERE name = {name}",
      usersTablename, userName);
}
```

If the `columns` list contains `[age, name, bio]`, then the SQL will look like:

```sql
SELECT `age`, `name`, `bio` FROM ...
```

That is, all the identifiers in the list will be backtick quoted and then joined by `, `.

## Conditional subqueries

SafeSql template supports conditional operator (`->`) inside the curly brace placeholders.

For example, if there is a snippet that should only be included if enabled, you can use:

```java
SafeSql.of(
    """
    SELECT
        id, name, profile,
        {enable_user_email -> user_email,}
        address
    FROM Users
    """,
    enableUserEmail);
```

The `enable_user_email ->` means that the following SQL snippet "user_email," will only be included if `enableUserEmail` is true.

No if-else or nested conditional are supported, to keep the SQL template readable and free of distractive logic.


## To support the `LIKE` operator

At the beginning I said using `PreparedStatement` is "mostly" safe from injection. What I meant is if you don't use the `LIKE` operator with a parameter. 

Consider the following most native `PreparedStatement` example:

```java {.bad}
String searchBy = ...
try (PreparedStatement stmt =
    connection.prepareStatement("SELECT * FROM Users WHERE bio LIKE '%?%'")) {
  stmt.setString(1, searchBy);
}
```

It won't work, because the JDBC driver will treat the quoted percent sign `%` as a literal character. And then the `setString()` call will fail.

After seeing the failure, you may then correctly realize that the percent sign has to be passed through the `setString()` call:

```java {.bad}
String searchBy = ...
try (PreparedStatement stmt =
    connection.prepareStatement("SELECT * FROM Users WHERE bio LIKE ?")) {
  stmt.setString(1, "%" + searchBy + "%");
}
```

Unfortunately that subjects you to a type of injection: what if the `searchBy` string is `100%`? You may think you are search for "100 percent" but what really happens is that it looks for all strings with 100 in it, such as `100000`.

Or perhaps the string itself includes the backslash character.

It's unlikely any of this will cause disastrous security risk. But nonetheless it's not the expected behavior.

Using `SafeSql`, the most intutive code does the right thing:

```java {.good}
SafeSql.of("SELECT * FROM Users WHERE bio LIKE '%{search_by}%'", searchBy)
```

It translates to the correct `PreparedStatement` call, with special characters in `searchBy` properly escaped.


## Preventing Programming Error

Usually, "SQL Injection" refers to using unsafe string received from the user that could unexpectedly change the semantics of your SQL string.

This is not the only type of errors though. Imagine if you have a list of comparison expressions and you want to use all of them in the `WHERE` clause, you may be tempted to do:

```java {.bad}
List<String> clauses = ...;
String sql = "SELECT * FROM Users WHERE " + clauses.stream().collect(joining(" AND "));
```

So if `clauses` is `["name = 'Emma'", "age < 20"]`, you'll get:

```sql
SELECT * FROM Users WHERE name = 'Emma' AND age < 20
```

Right? (sure, let's ignore the SQL injection risk for now)

No. If the list happens to be empty, you'll get an invalid SQL with `WHERE` but nothing after it.

That's not the worst: you'll get an error at least.

What if one of the `clauses` is `"name = 'Emma' OR name = 'Jim'"`? The SQL will become:

```sql
SELECT * FROM Users WHERE name = 'Emma' OR name = 'Jim' AND age < 20
```

The operator `AND` binds more tightly than `OR`, so the result SQL is actually looking for either `Emma` or `Jim` who is under 20. This is not someone trying to attack you. Just that you shot youself in the foot with complex dynamic SQL.

`SafeSql` provides built-in `and()` and `or()` methods that handles this by adding necessary parenthesis so the code will do what it looks like it does:


```java {.good}
List<SafeSql> clauses = ...;
SafeSql sql = SafeSql.of(
    "SELECT * FROM Users WHERE {clauses}",
    clauses.stream().collect(SafeSql.and());
```

## Apple to Apple

The `SafeSql.of()` method accepts an varargs for all the placeholders in the SQL template. Sometimes the SQL can be quite complex with many placeholders. 

It would have been easy to make mistake and either not pass in the correct number of parameters, or pass them in the wrong order, which may cause all kinds of problems.

`SafeSql` guards against such human error by checking at compile-time that your parameters must match the placeholders by name. So for example  the following code won't compile because it's passed the two parameters in the wrong order:

```java
SafeSql.of("SELECT * FROM Users WHERE email = {email} OR address = {address}", address, email)
```

This check is relatively flexible. So for example you can use `user.getId()` to match `{user_id}`, or `{user}` or `{id}`.

Sometimes if any placeholder name and the parameter can't be made match, you can always use an inline comment to tell the compile-time check (and code readers) that "it's indeed that". Like:

```java
SafeSql.of(
    "SELECT * FROM Users WHERE email = {email} OR address = {address}",
    /* email */ "me@whatever.com", address)
```


## Convenient JDBC Wrapper

Besides dynamically building the SQL, the `SafeSql` API is a more convenient wrapper. For example, if you are trying to select the user ids, you can use the `query()` convenience method to return a list:

```java {.good}
SafeSql sql = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name);
try {Connection connection = DriverManager.getConnection(...)) {
  List<Long> userIds = sql.query(connection, row -> row.getLong("id"));
}
```

Or if you are trying to insert some rows, you can use the `update()` method:

```java {.good}
SafeSql sql = SafeSql.of("INSERT INTO Users(id, name) VALUES ({id}, '{name}')", id, name);
try {Connection connection = DriverManager.getConnection(...)) {
  sql.update(connection);
}
```

What if you want to insert multiple rows? You can certainly create a new `SafeSql` object each time and then call `update()`, which will create a new `PreparedStatement` each time.

But in JDBC it's usually more efficient to reuse the same `PreparedStatement` object. This can be achived by using the `prepareToUpdate()` method:

```java {.good}
try {Connection connection = DriverManager.getConnection(...)) {
  var insertUser = SafeSql.prepareToUpdate("INSERT INTO Users(id, name) VALUES ({id}, '{name}')");
  for (...) {
    long id = ...;
    String name = ...;
    insertUser.with(id, name);
  }
}
```

Every time `with(id, name)` is called, it reuses the same `PreparedStatement` and just resets the parameters.

The same compile-time check is there to make sure the varargs to the `with()` method are in the right order.

Similarly, consider using the [`prepareToQuery()`](https://google.github.io/mug/apidocs/com/google/mu/safesql/SafeSql.html#prepareToQuery(java.sql.Connection,java.lang.String,com.google.mu.safesql.SqlFunction)) convenience method to reuse the same `PreparedStatement` for multiple query executions.


