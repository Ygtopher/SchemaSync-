# Command-Line Interface (CLI) & Configuration Guide

**Version:** 1.7.3  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## 1. CLI Usage & System Executables

SchemaSync can be launched directly from your system terminal or command prompt:

### Executable Command by Platform

| Operating System | Command | Installation Target |
| :--- | :--- | :--- |
| **Windows** | `SchemaSync.exe` or `javaw -jar SchemaSync.jar` | `%LOCALAPPDATA%\Programs\SchemaSync` |
| **Ubuntu / Debian** | `schemasync` | `/usr/bin/schemasync` |
| **Fedora / RHEL** | `schemasync` | `/usr/bin/schemasync` |
| **Universal (All OS)** | `java -jar SchemaSync.jar` | Any local folder |

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
