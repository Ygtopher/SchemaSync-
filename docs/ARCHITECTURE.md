# SchemaSync Architecture

```mermaid
graph TD
    UI[Swing FlatLaf UI] --> Core[Core Service Layer]
    Core --> Dialects[Dialect Engine]
    Core --> Diff[Diff & Parity Engine]
    Core --> Script[PSQL Emulation Engine]
    Core --> Exporters[Export Subsystem]
    Dialects --> JDBC[JDBC Drivers: PG / H2 / Access]
```

## Layer Responsibilities
- `com.dbtool.core.dialect`: Abstracts dialect variations (quoting, catalogs, paging).
- `com.dbtool.core.diff`: High performance schema and row parity comparisons.
- `com.dbtool.core.script`: Lexical parsing and execution of SQL scripts with meta-commands.
