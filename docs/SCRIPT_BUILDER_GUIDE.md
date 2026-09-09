# SchemaSync Script Builder & PSQL Emulation

## Supported Meta-Commands
- `\set variable_name value`: Assigns a script variable.
- `SELECT val FROM ... \gset [prefix_]`: Binds query columns to variables.
- `\echo message`: Prints messages with variable interpolation.
- `BEGIN; ... COMMIT;`: Safe transactional blocks with automatic rollback on error.
