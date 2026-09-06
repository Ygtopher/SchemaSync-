# Query Engine Architecture

## Execution Flow
1. User provides SQL statement.
2. `SqlSanitizer` cleans comments and trailing semicolons.
3. `StatementRunner` limits max rows to 10,000 to prevent JVM heap exhaustion.
4. Results are encapsulated in immutable `RowData` and `QueryResult`.
