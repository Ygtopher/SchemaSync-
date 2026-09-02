# SchemaSync

SchemaSync is a powerful, lightweight Java database utility for PostgreSQL, MS Access, and H2.

## Engine Compatibility Matrix

| Engine | Connect via | Schema Diff | Data Diff | PSQL Scripting |
|---|---|---|---|---|
| PostgreSQL 12-16 | JDBC / Dumps | Supported | Supported | Full Native Emulation |
| MS Access (.accdb/.mdb) | UCanAccess | Supported | Supported | Partial (Transactions) |
| Embedded H2 Engine | In-Memory / File | Supported | Supported | Supported |
