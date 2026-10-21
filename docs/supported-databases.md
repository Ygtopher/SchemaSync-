# Supported Databases & Dialect Matrix

**Version:** 1.6.9  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## Engine Support Matrix

SchemaSync provides first-class support for the following relational databases:

| Database Engine | Driver Class | Default Port | Storage Type | Minimum Engine Version |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL** | `org.postgresql.Driver` | `5432` | Network Server | PostgreSQL 9.6 – 17+ |
| **MySQL / MariaDB** | `com.mysql.cj.jdbc.Driver` | `3306` | Network Server | MySQL 5.7 – 8.4+, MariaDB 10.3+ |
| **SQLite** | `org.sqlite.JDBC` | N/A | Local File (`.db`, `.sqlite`) | SQLite 3.x |
| **Microsoft Access** | `net.ucanaccess.jdbc.UcanaccessDriver` | N/A | Local File (`.accdb`, `.mdb`) | Access 2000, 2003, 2007, 2010, 2016, 365 |
| **H2 Database (Sandbox)** | `org.h2.Driver` | N/A | In-Memory / File | H2 2.2.x |

---

## Engine Details & Capabilities

### 1. PostgreSQL
* **Driver:** PostgreSQL JDBC Driver `42.7.2`
* **Connection String:** `jdbc:postgresql://<host>:<port>/<database>?sslmode=prefer`
* **Features Supported:**
  * Multi-schema navigation (`public`, custom schemas, catalog separation).
  * `EXPLAIN (ANALYZE, BUFFERS)` execution plan inspection with runtime profiling.
  * Native UUID, JSONB, Array, and Geometric column types.
  * Server activity monitoring (`pg_stat_activity`), connection states, and cancellation of blocking backend PIDs.
  * Table storage telemetry via `pg_total_relation_size()`.

### 2. MySQL & MariaDB
* **Driver:** MySQL Connector/J `8.3.0`
* **Connection String:** `jdbc:mysql://<host>:<port>/<database>?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true`
* **Features Supported:**
  * Information Schema catalog reflection for tables, columns, indexes, and constraints.
  * Visual index advisors detecting unindexed foreign keys and redundant composite keys.
  * `EXPLAIN` query plan analysis.
  * Server process list monitoring via `SHOW FULL PROCESSLIST`.

### 3. SQLite
* **Driver:** SQLite JDBC `3.45.1.0`
* **Connection String:** `jdbc:sqlite:<path_to_file>`
* **Features Supported:**
  * Zero-configuration standalone local database exploration.
  * Automatic `PRAGMA foreign_keys = ON;` initialization to ensure referential integrity.
  * Schema reflection using `PRAGMA table_info()` and `PRAGMA foreign_key_list()`.
  * In-memory fast prototyping using `jdbc:sqlite::memory:`.

### 4. Microsoft Access (`.accdb`, `.mdb`)
* **Driver:** UCanAccess `5.0.1` (backed by Jackcess and HSQLDB)
* **Connection String:** `jdbc:ucanaccess://<path_to_file>;memory=true;immediatelyReleaseResources=true`
* **Features Supported:**
  * Reads and writes Microsoft Access 2000 through Access 2016/Office 365 database files without requiring Microsoft Office or Windows ODBC drivers installed.
  * Full cross-platform compatibility: works seamlessly on **Windows, macOS, and Linux**.
  * Auto-release of file locks upon disconnect, avoiding lingering `.laccdb` locking files.
  * Password-protected `.accdb` file decryption.

### 5. Embedded Sandbox Database (H2 Engine)
* **Driver:** H2 Database `2.2.224`
* **Connection String:** `jdbc:h2:mem:sandbox;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`
* **Features Supported:**
  * **Instant 1-Click Prototyping:** Click **⚡ Sample Sandbox DB** on the top toolbar to instantly spin up a pre-populated in-memory database with realistic relational schemas:
    * `departments`, `employees`, `contractors`, `projects`, `accounts`, `orders`, `sales`, and `audit_log`.
  * **SQL Dump File Import:** Open standard `.sql` or `.sql.gz` dump files directly into an in-memory database to inspect, query, diff, and visualize legacy database dumps without running a local database server.

---

## Feature Comparison Matrix Across Engines

| Feature | PostgreSQL | MySQL | SQLite | MS Access | H2 Sandbox |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Live Telemetry & Dashboard** | Full | Full | Basic | Basic | Full |
| **Activity Monitor (Live PIDs)** | Yes | Yes | N/A | N/A | N/A |
| **Visual Join Builder** | Yes | Yes | Yes | Yes | Yes |
| **Interactive ER Diagrams** | Yes | Yes | Yes | Yes | Yes |
| **Schema Drift & DDL Diff** | Yes | Yes | Yes | Yes | Yes |
| **Index Performance Advisor** | Full | Full | Basic | Basic | Full |
| **Sensitive Data Masking** | Yes | Yes | Yes | Yes | Yes |
| **Synthetic Mock Data Generation**| Yes | Yes | Yes | Yes | Yes |
| **ORM Code Generation** | Yes | Yes | Yes | Yes | Yes |
| **Visual Data Charting** | Yes | Yes | Yes | Yes | Yes |
| **SSH Tunneling** | Yes | Yes | N/A | N/A | N/A |
