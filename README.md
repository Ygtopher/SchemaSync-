# SchemaSync

**Version:** 1.6.4  
**Created & Developed by:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

SchemaSync is a high-performance, lightweight Java database synchronization and management tool for **PostgreSQL, MySQL, SQLite, MS Access, and H2**. It features a modern Dark Mode UI, 1-Click Schema Migration & Rollback generation, Database Health Diagnostics, Interactive Visual Data Charting, a Visual Join Builder, live Schema & Data diffing, an SSH Terminal, in-app update checks, and native Fedora 44 RPM packaging with full Wayland dock icon binding.

## Key Features

* **🐧 Native Fedora 44 RPM Package (`.rpm`) & Wayland Dock Integration:** Full native integration for Fedora 44 (and compatible RPM-based Linux distributions). Installs seamlessly via `sudo dnf install ./schemasync-1.6.4-1.fc44.noarch.rpm` with automatic OpenJDK dependency resolution (`java-headless >= 11`), `/usr/bin/schemasync` system command launcher, GNOME/KDE `.desktop` application grid integration, automatic Wayland dock icon binding via `--add-opens` and X11/Wayland `awtAppClassName` matching, system `/usr/share/pixmaps` fallbacks, and multi-resolution hicolor icons.
* **📝 Automatic SQL Editor Session Recovery (Draft Persistence):** Never lose your work across app updates or reboots. Automatically saves the active SQL editor buffer into `Documents/SchemaSyncSaves/sql_editor_draft.sql` with real-time keystroke debouncing, instant execution saving, and graceful shutdown hooks, automatically restoring your draft upon launch.
* **📜 Persistent & Isolated Query History:** Relocates Query History and Saved Queries to `Documents/SchemaSyncSaves/` with automatic legacy migration, expanded 100-query capacity, thread-safe asynchronous execution, and complete test isolation.
* **⚡ 1-Click In-App Auto-Updater & Silent Restarter:** Automatic background update checks on launch, interactive What's New changelog viewer, live-streamed download progress bar, and 1-click automated silent installation & seamless application restart (with permanent options for direct installer downloads and GitHub release navigation).
* **Multi-Database Support:** Connect directly to **PostgreSQL**, **MySQL**, **SQLite** (.sqlite / .db), **MS Access** (.accdb), or standard SQL dump files.
* **🏠 Database Overview & Live Health Dashboard:** Executive telemetry tab providing real-time database engine and connection metrics, ping latency (ms), catalog inventory counts, proportional storage distribution across the Top 10 largest tables with live percentage meters, and a 1-click executive launchpad.
* **🔒 Sensitive Data Masking & Anonymization Studio:** GDPR and HIPAA-compliant data sanitization workbench with smart auto-detection of PII (emails, phone numbers, credit card accounts, real names, salaries). Offers side-by-side live dual-grid preview and multi-format export to CSV, Excel (`.xlsx`), and atomic SQL `INSERT` statements.
* **🛡️ Enterprise AES-256-GCM Encrypted Credential Vault:** Military-grade AES-256-GCM authenticated encryption with PBKDF2-HMAC-SHA256 key derivation (100,000 rounds). Supports optional zero-knowledge Master Password protection, in-memory vault lock/unlock, and secure password-protected `.schemasync.vault` bundle export/import for teams.
* **⏱️ SQL Query Profiler & Bottleneck Analyzer:** Real-time query execution monitoring with millisecond latency tracking, color-coded bottleneck alerts (`> 100ms` warning, `> 500ms` critical), 1-click `EXPLAIN` and `EXPLAIN ANALYZE` execution plan visualization, and CSV export.
* **📐 Interactive ER Diagram & Architecture Visualizer:** High-contrast zoomable and pannable diagram canvas displaying rich table cards with `[PK]`, `[FK]`, data types, and column counts. Includes high-resolution **PNG image export**, **Mermaid.js Markdown ERD generation**, dynamic layout engines (Hierarchical, Organic, Circular), and interactive double-click/right-click table navigation.
* **⚡ Database Performance & Index Advisor:** Detects critical unindexed foreign keys causing full-table scans during JOINs and row locks during parent deletes, detects redundant composite indexes to save disk space and RAM, flags missing primary keys, and recommends high-cardinality candidate search indexes with 1-click execution.
* **📐 Schema Snapshot, Drift & Migration Studio:** Capture portable `.schema.json` snapshots of your live database, compare against baselines to detect schema drift across environments, and generate atomic reversible **UP** and **DOWN** migration DDL scripts with **Dry-Run transaction testing**.
* **💻 Instant ORM & Entity Code Generator:** Right-click any table or click 'Generate Code' to instantly produce production-ready backend models for **Spring Boot JPA (`@Entity`)**, **Java Records (17/21)**, **TypeScript Prisma Schema**, **TypeScript Drizzle ORM**, **Python SQLAlchemy**, **Python Pydantic (v2)**, and **C# .NET EF Core**.
* **📥 Smart Drag-and-Drop CSV & Excel Importer:** Drag & drop or browse `.csv`, `.tsv`, or `.xlsx` files with automatic column mapping, first-20-rows preview, batch inserts, and single-transaction rollback safety.
* **🧪 Synthetic Realistic Mock Data Generator:** Generate 10 to 10,000 realistic sample records (Names, Emails, Phones, Dates, Balances, UUIDs) that automatically sample valid foreign keys from referenced parent tables to prevent constraint violations.
* **⚡ Recursive Foreign Key Dependency Inspector & Safe Cascade Delete:** Inspect child and grandchild referencing records down all relationship depths in an interactive visual tree, preview ordered reverse-topological deletion scripts, and execute safe cascade deletes inside an atomic rollback transaction.
* **🔍 Global Database OmniSearch (Spotlight Search, `Ctrl+Shift+F`):** Rapidly scan all tables and columns across the entire database in background streaming mode, with live match filtering and 1-click navigation to jump directly to any matched table record.
* **⚡ One-Click Schema Migration & Rollback Generator:** Compare any two schemas and automatically generate:
  * **Forward Migration (UP)** DDL scripts (`CREATE TABLE`, `ALTER TABLE ADD COLUMN`, `ALTER COLUMN TYPE`)
  * **Rollback Script (DOWN)** to revert changes safely
  * **Dry-Run Mode** to simulate the entire migration inside an isolated transaction without committing
* **🛡 Database Health & Performance Auditor:** 1-click diagnostic scan that detects missing primary keys, unindexed foreign keys causing slow JOINs, wide tables, and orphaned tables, generating instant remediation fix scripts.
* **📊 Visual Data Charting & Dashboards:** Turn raw table data and SQL query results into interactive **Bar Charts**, **Line / Trend Charts**, and **Donut / Pie Charts** with 1-click PNG image export and clipboard copying.
* **🔄 In-App Auto-Update Checker:** Seamlessly checks GitHub Releases for new updates and lets users download the latest installer with a single click.
* **📱 Responsive Multi-Row Toolbars & Overflow-Free Layouts:** Responsive layout engine with `WrapLayout` and viewport-tracking `ScrollablePanel` preventing control clipping on narrow viewports; separates record mutations (`tbRecordActions`) and data export/clipboard utilities (`tbDataTools`) across clean auto-wrapping rows with horizontal scroll fallback.
* **Browse & Search:** Instantly view tables, select specific columns, filter data with a custom WHERE builder, and execute rapid text searches across entire tables.
* **Data Operations:** A dedicated interface to safely build and execute structural and data changes:
  * Multi-column **UPDATE** and **UPDATE + JOIN** builder
  * **DELETE** record builder
  * **ALTER TABLE** (Add, Drop, Rename columns, Change data types)
  * **CREATE INDEX** (Standard and Unique)
  * **TRUNCATE** tables safely
* **Query History & Transaction Control:** Global Query History panel across all tabs. Toggle **Auto-Commit** on the fly and manually **Commit** or **Rollback** transactions with single button clicks.
* **Visual Join Builder:** Construct complex multi-table JOINs through a simple dropdown interface without writing raw SQL.
* **Script Builder (with PSQL Emulation):** Write, save, and execute complex transaction scripts with native psql command emulation (\set, \gset, \echo).
* **Table Compare (Diff):** Select any two tables and a primary key to instantly generate a Schema Diff and a Data Diff.
* **SSH Terminal:** Built-in server management tab with an interactive SSH terminal and SFTP drag-and-drop transfers.
* **Robust Exporting:** Export any queried data or query history directly to CSV, Excel (.xlsx), PDF, or raw SQL INSERT statements.

## Technologies Used

* Java 11 (Swing)
* Maven
* FlatLaf (Modern Dark/Light Look and Feel)
* PostgreSQL JDBC Driver
* MySQL Connector/J
* SQLite JDBC Driver
* UCanAccess (MS Access Driver)
* H2 Database Engine
* Apache POI (Excel Exports)
* OpenPDF (PDF Exports)
* RSyntaxTextArea (SQL Syntax Highlighting)
* JSch (SSH Terminal & SFTP)
* JNA (Windows Native AppUserModelID & Taskbar Integration)

## Building from Source

This project uses Maven for dependency management and packaging.

1. Clone the repository.
2. Open a terminal in the root directory.
3. Run the following command to compile and build the standalone Fat JAR:

```bash
mvn clean package -DskipTests
```

This will generate a self-contained executable JAR in the `target/` directory.

## Running & Installing

### Ubuntu Desktop Linux (DEB Package)
Install SchemaSync natively on Ubuntu Desktop (20.04, 22.04, 24.04 LTS+) with automatic Java dependency resolution:
```bash
sudo apt install ./schemasync_1.6.8_all.deb
```
Or install using `dpkg`:
```bash
sudo dpkg -i ./schemasync_1.6.8_all.deb
sudo apt-get install -f  # resolves dependencies if needed
```
Launch directly from the Ubuntu App Grid, dock, or terminal:
```bash
schemasync
```

### Fedora 44 Linux (RPM Package)
Install SchemaSync natively on Fedora 44 with automatic Java dependency resolution:
```bash
sudo dnf install ./schemasync-1.6.8-1.fc44.noarch.rpm
```
Launch directly from the GNOME/KDE App Menu or from terminal:
```bash
schemasync
```

### Windows
- Run the installer **`SchemaSync-Setup.exe`** for automatic Start Menu & Desktop shortcuts.
- Or use the standalone portable executable **`SchemaSync.exe`**.

### Cross-Platform JAR
Run directly with Java 11+ on any operating system:
```bash
java -jar SchemaSync.jar
```

## Usage Notes

* **Script Builder Transactions:** Using the `BEGIN;` statement in the Script Builder automatically disables JDBC auto-commit. If a script fails midway, SchemaSync will catch the exception and automatically issue a `ROLLBACK` to protect your data.
* **Copying SQL:** Almost all tabs feature a "Copy SQL" button that places the exact SQL string generated by the UI into your clipboard for external use.

## Versioning Strategy
SchemaSync follows Semantic Versioning (`MAJOR.MINOR.PATCH`):
* **Minor Version Bumps (`1.x.0`)**: Every time a new feature, engine integration, or workbench tool is added.
* **Patch Version Bumps (`1.x.y`)**: Bug fixes, performance optimizations, and UI refinements.
* **Major Version Bumps (`x.0.0`)**: Breaking architectural changes or major platform redesigns.

## Author & Creator

* **Author:** NSENGIYUMVA Christophe
* **Copyright:** © 2026 NSENGIYUMVA Christophe. All rights reserved.
* **License:** MIT License


