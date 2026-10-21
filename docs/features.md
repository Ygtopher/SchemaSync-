# SchemaSync Comprehensive Feature Guide

**Version:** 1.6.9  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## 1. Executive Telemetry & Live Health Dashboard (`🏠 Dashboard`)
The primary overview panel providing real-time infrastructure intelligence immediately upon connecting:
* **Engine & Connection Telemetry:** Displays connected RDBMS type, host, port, active database catalog, authenticated user, and real-time millisecond ping latency.
* **Proportional Table Storage Meters:** Visual bars illustrating table sizes and disk allocation for the Top 10 largest tables.
* **Catalog Inventory Metrics:** Total count of user tables, foreign keys, secondary indexes, and total rows across the entire database.
* **1-Click Executive Launchpad:** Instant shortcut buttons to the Index Advisor, ER Diagram, Schema Drift Studio, Data Masking, Credential Vault, and Mock Data Generator.

---

## 2. Multi-Tab Browse & Search Studio (`Browse & Search`)
A high-productivity data exploration workbench featuring:
* **Multi-Tab Workspace (`MultiTabContainer`):** Open multiple tables simultaneously in independent sub-tabs.
* **Deduplicated Column Checkbox Selector:** Select specific columns to view via an interactive checkbox dialog without loading redundant column headers or building duplicate `SELECT` clauses.
* **Dynamic WHERE Filter Builder:** Add multiple filter rows with operators (`=`, `!=`, `>`, `<`, `LIKE`, `ILIKE`, `IN`, `IS NULL`, `BETWEEN`).
* **Advanced SQL Clauses:**
  * **ORDER BY:** Multiple columns with ASC/DESC direction.
  * **GROUP BY & HAVING:** Real-time aggregation clauses (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`).
  * **WINDOW FUNCTIONS:** `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()` over custom partitions.
  * **Concurrency Locking:** Add `FOR UPDATE`, `FOR UPDATE SKIP LOCKED`, or `FOR SHARE` clauses.
  * **Table Sampling:** Inspect large tables using `TABLESAMPLE BERNOULLI` or `SYSTEM` percentages.
* **Inline Table Editing:** Double-click any cell to edit data directly with automatic `UPDATE ... WHERE pk = ?` execution.

---

## 3. Visual Join Builder & Bidirectional SQL Parser (`Visual Join Builder`)
Construct complex relational queries visually without typing raw SQL:
* **Base Table & Dynamic Joins:** Select an initial table, then add multiple joined tables with `INNER JOIN`, `LEFT JOIN`, `RIGHT JOIN`, `FULL OUTER JOIN`, or `CROSS JOIN`.
* **Smart Auto-Discovered Join Conditions:** SchemaSync automatically inspects foreign key relationships and pre-fills `ON` clauses (`users.id = orders.user_id`).
* **Bidirectional JSqlParser Engine:** 
  * As you build the visual tree, valid SQL updates in real-time.
  * Click **"Load from SQL"** to paste any raw `SELECT ... JOIN` query, and SchemaSync parses it back into visual dropdowns and condition rows.
* **Exporting:** 1-click **"Copy SQL"** or run immediately with results rendered in a virtualized JTable.

---

## 4. Interactive ER Diagram & Architecture Visualizer (`ER Diagram`)
A high-performance visual schema design and relationship canvas:
* **Interactive Canvas:** Zoom, pan, and drag table cards rendered with `JGraphX`.
* **Rich Table Cards:** Visual display of column names, data types, `[PK]` badges, `[FK]` indicators, and referenced target tables.
* **Automatic Layout Algorithms:** Toggle between **Hierarchical Tree**, **Organic / Force-Directed**, or **Circular** layouts.
* **Export Options:**
  * **High-Resolution PNG:** Export the entire diagram canvas to high-DPI image files.
  * **Mermaid.js Markdown:** Generate standard Mermaid `erDiagram` syntax for GitHub READMEs and team documentation.

---

## 5. Schema Drift & Migration Studio (`📐 Schema Drift`)
Safely track and automate database schema evolution across environments:
* **Portable Snapshots (`.schema.json`):** Save complete structural definitions of your database schema.
* **Live Drift Comparison:** Compare the current live database against a saved snapshot or compare two distinct databases to detect added, modified, or dropped tables, columns, constraints, and indexes.
* **Reversible Migration Generation:**
  * **UP Script (Forward Migration):** Generates atomic `CREATE TABLE`, `ALTER TABLE ADD COLUMN`, `ADD CONSTRAINT` scripts.
  * **DOWN Script (Rollback):** Generates the exact inverse statements (`DROP TABLE`, `ALTER TABLE DROP COLUMN`) to undo changes cleanly.
* **Dry-Run Validation:** Execute the generated migration inside an isolated transaction that tests every DDL statement against live data before issuing an automatic rollback, verifying validity without modifying production schemas.

---

## 6. Database Performance & Index Advisor (`⚡ Index Advisor`)
Proactively optimizes database query performance and storage efficiency:
* **🔴 Unindexed Foreign Keys:** Scans for foreign keys lacking a corresponding secondary index, preventing full-table sequential scans during multi-table `JOIN` operations and eliminating row locks during parent table deletions.
* **🟡 Redundant Composite Indexes:** Identifies secondary indexes that are left-prefix duplicates of existing composite indexes, saving disk storage and buffer pool memory.
* **🟣 Missing Primary Keys:** Identifies tables lacking primary keys that risk duplicate rows and replication failures.
* **🔵 High-Cardinality Search Candidates:** Recommends indexing candidate search columns frequently targeted in `WHERE` clauses.
* **1-Click Execution & DDL Export:** Review generated `CREATE INDEX CONCURRENTLY` or standard index scripts and execute them with a single click.

---

## 7. Sensitive Data Masking & Anonymization Studio (`🔒 Data Masking`)
GDPR and HIPAA-compliant data sanitization workbench:
* **Smart PII Detection:** Automatically detects Personally Identifiable Information based on column names and sample data patterns (Emails, Phone Numbers, Social Security Numbers, Real Names, Credit Card Numbers, Salaries).
* **Configurable Masking Strategies:**
  * **Email Masking:** `j***e@domain.com`
  * **Phone Masking:** `***-***-1234`
  * **Credit Card Masking:** `****-****-****-5678`
  * **Full Redaction:** `[CONFIDENTIAL]`
  * **Synthetic Pseudonymization:** Replaces real names with realistic fictional identities.
* **Dual-Grid Live Preview:** Side-by-side comparison of original raw data versus masked output.
* **Sanitized Export:** Export masked datasets to CSV, Excel (`.xlsx`), or sanitized SQL `INSERT` statements for developer environments.

---

## 8. Enterprise AES-256-GCM Encrypted Credential Vault (`🛡️ Vault`)
* **Military-Grade Encryption:** Protects stored database connection credentials using **AES-256 in Galois/Counter Mode (GCM)** with **PBKDF2-HMAC-SHA256** key derivation (100,000 rounds).
* **Zero-Knowledge Master Password:** Cryptographic keys are derived on-the-fly and never written to disk in plain text.
* **Auto-Lock Timeout:** Automatically locks the in-memory vault after periods of user inactivity.
* **Encrypted Team Export:** Export and import encrypted `.schemasync.vault` bundles with password protection for secure developer onboarding.

---

## 9. Query Profiler & Latency Bottleneck Analyzer (`⏱️ Profiler`)
* **Real-Time Latency Tracking:** Logs execution durations down to the millisecond.
* **Color-Coded Bottleneck Alerts:**
  * Green: `< 100 ms` (Fast)
  * Yellow: `100 ms – 500 ms` (Warning)
  * Red: `> 500 ms` (Critical bottleneck)
* **1-Click EXPLAIN & EXPLAIN ANALYZE:** Automatically generates execution plans detailing sequential scans, index scans, hash joins, and cost distributions.

---

## 10. Instant ORM & Entity Code Generator
Select any table to generate production-ready backend code:
* **Spring Boot JPA (`@Entity`):** Complete Java class with `@Id`, `@GeneratedValue`, `@Column`, getters, and setters.
* **Java Records (Java 17/21):** Immutable modern Java data carriers.
* **TypeScript Prisma Schema:** `model TableName { id Int @id ... }`
* **TypeScript Drizzle ORM:** `export const users = pgTable('users', ...)`
* **Python SQLAlchemy:** Declarative models with typed columns.
* **Python Pydantic (v2):** Strongly typed data validation schemas.
* **C# .NET EF Core:** Entity Framework Core class models.

---

## 11. Synthetic Realistic Mock Data Generator
* Generate between **10 and 10,000 realistic records** for testing.
* Generates realistic First Names, Last Names, Emails, Phone Numbers, Dates, Monetary Balances, and UUIDs.
* **Referential Foreign-Key Sampling:** When populating child tables, the generator automatically queries existing parent primary keys to ensure all foreign keys are valid, completely preventing foreign key constraint violations.

---

## 12. Recursive Foreign Key Dependency Inspector & Safe Cascade Delete
* **Visual Relationship Hierarchy:** Inspect all child, grandchild, and descendant tables referencing a target table.
* **Ordered Deletion Plan:** Computes the reverse-topological order of tables required to safely delete records without constraint errors.
* **Single-Transaction Cascade:** Executes the cascade delete inside an atomic transaction with immediate rollback on error.

---

## 13. Global OmniSearch (`Ctrl+Shift+F`)
* Universal spotlight search across all tables and text columns simultaneously.
* Asynchronous streaming search with real-time match counter.
* Click any search result to immediately navigate to the matching record in the Table Browser.

---

## 14. Interactive Visual Data Charting
* Transform table data and query results into high-contrast visual charts:
  * **Bar Charts:** Categorical comparison across groups.
  * **Line / Trend Charts:** Time-series and sequential data.
  * **Donut / Pie Charts:** Distribution and proportion metrics.
* 1-click **Export to PNG** or **Copy Chart to Clipboard**.

---

## 15. SSH Terminal & Remote SFTP Client (`SSH / Server`)
* Integrated SSH terminal powered by `JSch` for direct database server management.
* Supports password and private key authentication (`RSA`, `ECDSA`, `Ed25519`).
* Built-in SFTP file transfer workbench to upload SQL dumps or download remote backups.
