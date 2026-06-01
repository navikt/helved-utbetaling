---
license: Apache-2.0
module: http4k-connect-storage-jdbc
---

# http4k-connect-storage-jdbc Reference

JDBC-backed `Storage<T>` — stores JSON-serialized values in a relational database via Exposed ORM.

## Usage

```kotlin
val dataSource: DataSource = /* HikariCP, H2, etc. */

val storage = Storage.Jdbc<MyData>(
    dataSource = dataSource,
    tableName = "my_data",          // optional, defaults to class simple name
    autoMarshalling = Moshi         // optional, default Moshi
)

storage["key"] = MyData("value")   // INSERT or UPDATE
val item = storage["key"]           // SELECT WHERE key = ?
storage.remove("key")              // DELETE WHERE key = ?
storage.keySet("prefix/")          // SELECT WHERE key LIKE 'prefix/%'
storage.removeAll("prefix/")       // DELETE WHERE key LIKE 'prefix/%'
```

## Table Schema

```sql
CREATE TABLE my_data (
    key     VARCHAR(500) NOT NULL PRIMARY KEY,
    contents TEXT NOT NULL
)
```

Table is created automatically on first use.

## Dependencies

Add Exposed and a JDBC driver to your build:

```kotlin
implementation("org.jetbrains.exposed:exposed-core:...")
implementation("org.jetbrains.exposed:exposed-jdbc:...")
implementation("com.h2database:h2:...")  // or postgres, mysql, etc.
```

## Gotchas

- **Keys are limited to 500 characters** (VARCHAR 500)
- Uses insert-or-update (checks existence then inserts/updates — not atomic)
- Table name defaults to the Kotlin class simple name
- Works with any JDBC-compatible database (H2, PostgreSQL, MySQL, etc.)
