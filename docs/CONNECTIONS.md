# SchemaSync Connection Management

SchemaSync provides unified access to PostgreSQL, MS Access, and embedded H2.

## UCanAccess Specifics
- MS Access files (.accdb, .mdb) are read via the Jackcess / UCanAccess pure Java driver.
- Supports both reading and executing ALTER / SELECT statements.
