# Command-Line Interface (CLI) & Configuration Guide

**Version:** 2.1.0  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## 1. Dedicated Headless CLI (`schemasync-cli.jar`)

SchemaSync v2.1.0 includes a dedicated, headless CLI tool built with Picocli 4.7.6 for automated CI/CD workflows and terminal operations:

```bash
java -jar release/schemasync-cli.jar [COMMAND] [OPTIONS]
```

### Supported Commands

#### 1. `diff` (Schema Comparison)
Compare structural schema definitions between two databases or saved profiles:
```bash
# Compare using saved connection profiles
java -jar release/schemasync-cli.jar diff --source "production" --target "staging"

# Compare using raw JDBC URLs
java -jar release/schemasync-cli.jar diff \
  --source "jdbc:postgresql://localhost:5432/prod_db" \
  --target "jdbc:postgresql://localhost:5432/staging_db" \
  --format json --output diff_report.json
```

#### 2. `drift-check` (CI/CD Schema Drift Gate)
Verify that a live database matches an expected baseline snapshot or reference database. Exits with code `1` if schema drift is detected, failing pull requests automatically:
```bash
java -jar release/schemasync-cli.jar drift-check \
  --source "production_profile" \
  --target "migrations_baseline" \
  --fail-on-drift
```

#### 3. `export` (Headless Data Export)
Extract table rows directly into structured files without launching a graphical environment:
```bash
# Export table to CSV
java -jar release/schemasync-cli.jar export --source "production" --table "orders" --format csv --output orders.csv

# Export table to JSON array
java -jar release/schemasync-cli.jar export --source "production" --table "users" --format json --output users.json

# Export table to atomic SQL INSERT statements
java -jar release/schemasync-cli.jar export --source "production" --table "config" --format sql --output config_seed.sql
```

---

## 2. Desktop GUI Executables

SchemaSync desktop can be launched directly from your system terminal or command prompt:

### Executable Command by Platform

| Operating System | Command | Installation Target |
| :--- | :--- | :--- |
| **Windows** | `SchemaSync.exe` or `javaw -jar SchemaSync.jar` | `%LOCALAPPDATA%\Programs\SchemaSync` |
| **Ubuntu / Debian** | `schemasync` | `/usr/bin/schemasync` |
| **Fedora / RHEL** | `schemasync` | `/usr/bin/schemasync` |
| **Universal (All OS)** | `java -jar release/SchemaSync.jar` | Any local folder |

---

## 2. JVM System Properties & Tuning Flags

You can customize the JVM runtime environment and appearance by passing standard `-D` system properties:

### Display & Font Antialiasing
```bash
java \
  -Dawt.useSystemAAFontSettings=on \
  -Dswing.aatext=true \
  -Dsun.java2d.uiScale=1.0 \
  -jar SchemaSync.jar
```
* **`awt.useSystemAAFontSettings=on`**: Enables subpixel font antialiasing matching the native desktop environment.
* **`swing.aatext=true`**: Enforces smooth text rendering across all Swing labels, buttons, and tables.
* **`sun.java2d.uiScale=2.0`**: Manually scales the interface by 200% on high-DPI (4K) displays if OS scaling is disabled.

### Timezone Consistency
```bash
java -Duser.timezone=UTC -jar SchemaSync.jar
```
* **`user.timezone=UTC`**: Ensures consistent `TIMESTAMP` and `DATE` column parsing across all database engines regardless of client locale.

### Memory & Heap Tuning
```bash
java -Xms256m -Xmx2048m -jar SchemaSync.jar
```
* **`-Xms256m`**: Initial heap allocation (256 MB).
* **`-Xmx2048m`**: Maximum heap ceiling (2 GB), recommended when importing large `.csv` / `.xlsx` files or diffing tables with millions of records.

### Linux Wayland Dock Icon Reflection Flag
```bash
java --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -jar SchemaSync.jar
```
* Allows SchemaSync on Linux to assign the `awtAppClassName` to `"schemasync"`, enabling GNOME Shell and KDE Plasma Wayland compositors to bind the application window to the dock.

---

## 3. Script Builder & PSQL Command Emulation

The **Script Builder** tab includes built-in emulation of standard PostgreSQL `psql` meta-commands, allowing migration and deployment scripts written for CLI pipelines to run interactively:

| Meta-Command | Syntax | Description |
| :--- | :--- | :--- |
| **`\echo`** | `\echo "Starting migration..."` | Prints a message directly to the execution log output. |
| **`\set`** | `\set schema_name 'public'` | Defines a session variable for substitution in subsequent queries. |
| **`\gset`** | `SELECT max(id) FROM users \gset max_` | Stores column results from the preceding query into session variables. |
| **`\timing`** | `\timing on` | Enables or disables millisecond execution timer logging per statement. |
| **`\i`** | `\i /path/to/subscript.sql` | Includes and executes an external SQL file inside the current transaction block. |

### Transaction Example in Script Builder
```sql
\echo "Beginning atomic schema migration"
BEGIN;

ALTER TABLE users ADD COLUMN phone VARCHAR(20);
UPDATE users SET phone = '000-000-0000' WHERE phone IS NULL;
ALTER TABLE users ALTER COLUMN phone SET NOT NULL;

COMMIT;
\echo "Migration successfully completed and committed!"
```
If any statement within the block triggers a SQL exception, SchemaSync automatically issues a `ROLLBACK` and highlights the failing line in the execution report.
