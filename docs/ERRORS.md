# SchemaSync Error Code Taxonomy

| Code | Exception Class | Recovery Strategy |
|---|---|---|
| `CONN_FAILED` | `DatabaseConnectionException` | Check host reachability, credentials, and port |
| `QUERY_ERROR` | `QueryExecutionException` | Validate SQL syntax against target dialect |
| `SCHEMA_DIFF_ERROR` | `SchemaDiffException` | Check metadata permissions on catalogs |
| `DATA_DIFF_ERROR` | `DataDiffException` | Verify primary key column definitions |
| `SCRIPT_ERROR` | `ScriptExecutionException` | Inspect psql command line syntax |
| `EXPORT_FAILED` | `ExportException` | Check disk write permissions and target format |
