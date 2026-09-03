# Database Dialect Extension Guide

SchemaSync isolates all RDBMS specific nuances inside `DatabaseDialect` implementations.

## Adding a New Dialect
1. Extend `GenericSqlDialect` or implement `DatabaseDialect`.
2. Override `quoteIdentifier(String)`.
3. Provide catalog queries for table listing and column metadata.
4. Register the new dialect in `DialectRegistry`.
