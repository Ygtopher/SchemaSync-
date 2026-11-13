# SchemaSync

**Version:** 2.3.0  
**Created & Developed by:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

SchemaSync is an enterprise-grade, high-performance Java 21 database synchronization, management, and schema engineering platform for **PostgreSQL, MySQL, SQLite, MS Access, and H2**. Re-architected in **v2.0.0** into a modular reactor (`schemasync-core`, `schemasync-cli`, `schemasync-ui`), styled in **v2.1.0** with an IDE-grade visual redesign, elevated in **v2.2.0** with an all-new **Mission Control Dashboard** featuring a **Smooth Spline Gradient Area Chart** (`SplineAreaChartPanel`), Radial Schema Health Score dial, and Actionable Insights feed, and empowered in **v2.3.0** with **Multi-Connection Database Workspaces & Detachable Floating Windows** (`SessionManager`, `SessionTabBar`, dual-mode coordinator & detached frames), it features a unified App Header Bar, high-DPI procedural vector icon suite, Tokyo Night and Dracula themes, interactive bottom status bar with 1-click GC RAM optimizer, floating toast notifications, 100% headless core engine, high-throughput HikariCP connection pooling, 2-phase chunked & canonical SHA-256 diff engine, ServiceLoader SPI dialects, dynamic JDBC driver loading, pluggable AI SQL assistant (Ollama, Gemini, OpenAI, Anthropic, OpenRouter), Vault V2 OS Keyring integration, ECDSA-verified releases, headless CLI, and native packaging via `jpackage`, Windows portable executables, Ubuntu (`.deb`), and Fedora (`.rpm`).

---

## 📚 Complete System Documentation (`docs/`)

Explore the comprehensive documentation suite located in the [`docs/`](docs/README.md) directory:

* **[Codebase, Module & Function Map](docs/codebase-map.md):** Complete code, class, and method inventory across `schemasync-core`, `schemasync-cli`, and `schemasync-ui`.
* **[Technology Stack & Integration Guide](docs/tech-stack-integration.md):** Full breakdown of all 20+ libraries and end-to-end synergy workflows.
* **[Architecture & Design](docs/architecture.md):** 3-module reactor, headless core, Virtual Threads, HikariCP pooling, and transaction safety.
* **[Supported Databases](docs/supported-databases.md):** Complete guide for PostgreSQL, MySQL, SQLite, MS Access, embedded H2, and custom JDBC drivers.
* **[Comprehensive Features](docs/features.md):** Deep-dive into all 18+ workbench tools, 2-phase chunked diff, AI Assistant, telemetry, and editors.
* **[Security & Cryptography](docs/security.md):** Vault V2 (OS Keyring + AES-256-GCM), ECDSA update verification, and PII masking.
* **[Installation & Packaging](docs/installation-and-packaging.md):** Guides for Windows (`.exe` / `jpackage`), Ubuntu (`.deb`), Fedora (`.rpm`), Fat JAR, and CLI.
* **[Troubleshooting & FAQ](docs/troubleshooting.md):** Solutions for Smart App Control, Java 21 runtime, and Wayland dock icons.
* **[Changelog & History](docs/changelog.md):** Full version history from v1.0.0 through v2.3.0.
* **[CLI & Configuration Guide](docs/api-and-cli.md):** Dedicated headless CLI (`diff`, `drift-check`, `export`), JVM runtime tuning, and psql meta-commands.

---

## Key Features

* **🗂️ Multi-Connection Database Workspaces & Detachable Floating Windows (`SessionManager`, `SessionTabBar`, `DatabaseSession`):**
  * **Simultaneous Concurrent Connections:** Connect to multiple databases (PostgreSQL, MySQL, SQLite, etc.) at the same time in a single application window without launching multiple instances.
  * **Dynamic Session Tab Bar:** Chrome/DataGrip-style tab strip with engine vector logos, live status indicator dots (`🟢 Live`, `⚪ Offline`), and active session highlight.
  * **↗ Detachable Floating Windows:** Tear off any database session into an independent top-level IDE window for multi-monitor workflows with full access to all 13 workbench tabs scoped to that database.
  * **↙ 1-Click Re-Attach:** Effortlessly re-dock detached windows back into the primary coordinator window at any time.
  * **Full Session Isolation:** Independent HikariCP connection pools, schema caches, and execution contexts per session.
* **🎨 Modern IDE Experience & Smart Data Grids (`CommandPaletteDialog`, `SmartTableCellRenderer`, `RowInspectorDrawer`, `ThemeManager`):**
  * **⚡ `Ctrl+K` Command Palette & Quick Table Switcher:** Press `Ctrl+K` (or `Ctrl+P`) from anywhere to fuzzy-search and jump to any database table, workbench tab, studio tool, accent theme, or UI zoom level.
  * **📊 TablePlus-Style Semantic Data Grids:** Renders `NULL` pill badges, colored `● true` / `○ false` boolean indicators, right-aligned monospace numbers, `🔑 PK` / `🔗 FK` column header badges, zebra striping, and live row hover highlighting.
  * **📐 Side-by-Side Row Inspector Drawer:** Click any row in `Browse & Search` to inspect and edit all columns in a searchable right-hand sidebar with `NULL` toggles and `Ctrl+S` parameterized saving.
  * **🎛️ `▲ Compact Grid` Toggle & 7 Accent Theme Presets:** Collapse query builder rows into a single compact header bar with live clause badges (`WHERE (2) | ORDER (1)`), and switch between **Tokyo Night**, **Dracula Studio**, **Midnight Blue**, **Emerald Studio**, **Obsidian Slate**, **Cyber Amethyst**, and **Solar Daylight** with **90%–125% UI Zoom**.
* **🏠 Executive Telemetry, Live Health & Application Usage Dashboard (`DashboardPanel`, `AppUsageTracker`):**
  * **12 Live Telemetry Cards (3×4 Grid):** Tracks **Sessions & Activity Streak** (`🔥 Xd`), **Queries Executed & Daily Volume** (`Today | 7d`), **Query Success Rate**, **Latency Percentiles** (`Avg`, `P50`, `P95`, `Max ms`), **Slow Queries (`> 500 ms` / `> 1s`)**, **Rows Read vs. Mutated**, **Safety & Linter Governance** (Dry-Run previews, auto-fixes, PII rows masked), **Data Movement & CodeGen** (Connections, Exports, Imports, Mock Rows, Dual-DB Compares, ORM Models), **Most Queried Table (`🔥`)**, **Most Used Tool & DB Engine**, **Saved Queries & Pinned History**, and **JVM Heap RAM (`Used / Max MB`)** with 1-click **⚡ Optimize Memory (GC)**.
  * **Comprehensive Usage & Telemetry Breakdown Table:** Interactive category filter (`All Categories`, `🛠️ Workbench Tools`, `🔥 Hot Queried Tables`, `⚡ SQL Workload`, `📅 Daily Activity (7d)`, `🛡️ Safety & Data Movement`, `🗄️ DB Engines & Column Types`) with percentage bars and 1-click **🧹 Reset Usage Stats**.
  * **Deep Schema Quality & Column Type Distribution:** Inspects Populated vs. Empty tables, Missing Primary Keys, Orphan/Isolated tables (`0` FKs), In-Memory Metadata Cache counts, and Column Data-Type Distribution (`Text/VARCHAR`, `Numeric`, `Date/Time`, `Boolean`, `JSON/Binary`).
* **🧠 Smart Query Authoring, Linter & Dry-Run Studio (`SqlEditorPanel`, `SqlQueryAnalyzer`):**
  * **Multi-Tab SQL Scratchpads (`Query 1`, `+ New Tab`):** Work across multiple concurrent SQL scratchpad buffers with auto-saved draft persistence.
  * **Context-Aware SQL Autocomplete (`Ctrl+Space`):** Resolves table aliases (`FROM employees e`), suggests table-specific columns (`e.first_name`), and auto-completes Foreign Key `JOIN ... ON` conditions.
  * **Parameterized Query Variables (`:param` / `${param}`):** Automatically prompts for named variables on execution and remembers previous values while preserving PostgreSQL `::type` casts.
  * **Static Query Linter (`🔍 Lint`) & `⭐ Expand *`:** Detects unfiltered `UPDATE`/`DELETE` statements, `SELECT *`, non-sargable `WHERE` functions, leading wildcard `LIKE '%...'`, and missing `LIMIT` clauses with 1-click auto-fixes and `SELECT *` column expansion.
  * **🛡️ Destructive DML Guardrail & Dry-Run Preview:** Intercepts unfiltered `UPDATE`/`DELETE` queries and converts DML into safe read-only `SELECT *` preview tabs so you can inspect the exact rows affected before committing.
* **📊 Interactive Result Grid Power Tools (`SqlInsertGenerator`):**
  * **Instant Client-Side Filter Bar & Live Selection Telemetry:** Filter returned rows in real time as you type, and highlight any cells/rows to view instant **`Selected Rows` | `Cells` | `Nulls` || `Numeric` | `Sum` | `Avg` | `Min` | `Max`** statistics in the footer.
  * **Smart Row Copying (`INSERT` without PK, `UPDATE`, `JSON`, `Markdown`):** Right-click any table row in Browse, Join, or SQL Editor results to copy as **SQL INSERT** (automatically omitting primary keys), **SQL INSERT (Include Primary Key)**, **SQL UPDATE**, **JSON Array**, or **GitHub Markdown Table**.
* **📜 Upgraded Query History & Favorites:** Searchable Query History sidebar with `⭐ Pinned` favorites filter, execution latency/row count telemetry badges (`[14ms, 25r]`), and 1-click promotion to Named Saved Queries.
* **⚡ Dual DB Compare Unified SQL Mode ("One Query Affects All Databases"):** Write a single SQL query that executes simultaneously across all connected databases in the pool alongside **Quick Table Match** and **Custom SQL Query** modes. Supports concurrent `SELECT` data diffs (side-by-side grids + cell-level unified diffs) and multi-DB DML/DDL execution (`UPDATE`, `INSERT`, `DELETE`, `CREATE`, `ALTER`, `DROP`) with confirmation safeguards and per-database telemetry logs.
* **🐧 Native Fedora 44 RPM Package (`.rpm`) & Wayland Dock Integration:** Full native integration for Fedora 44 (and compatible RPM-based Linux distributions). Installs seamlessly via `sudo dnf install ./schemasync-2.3.0-1.fc44.noarch.rpm` with automatic OpenJDK dependency resolution (`java-headless >= 11`), `/usr/bin/schemasync` system command launcher, GNOME/KDE `.desktop` application grid integration, automatic Wayland dock icon binding via `--add-opens` and X11/Wayland `awtAppClassName` matching, system `/usr/share/pixmaps` fallbacks, and multi-resolution hicolor icons.
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

* **Java 21 LTS** (Records, Virtual Threads, Pattern Matching)
* **Maven 3.8+** (3-Module Reactor: `schemasync-core`, `schemasync-cli`, `schemasync-ui`)
* **HikariCP 5.1.0** (High-throughput connection pooling & pinned sessions)
* **FlatLaf 3.4** (Modern Dark/Light Look and Feel)
* **MigLayout 11.3** (Fluid responsive grid forms and diff layouts)
* **Picocli 4.7.6** (Type-safe, modern command-line interface)
* **JSqlParser 4.6** (SQL AST parsing, dialect validation, statement splitting)
* **PostgreSQL JDBC Driver** (`42.7.2`)
* **MySQL Connector/J** (`8.3.0`)
* **SQLite JDBC Driver** (`3.45.2.0`)
* **UCanAccess & Jackcess** (`5.0.1`, MS Access bridge)
* **H2 Database Engine** (`2.2.224`)
* **Apache POI OOXML** (`5.2.5`, Excel export)
* **OpenPDF** (`1.3.30`, Paginated PDF reports)
* **RSyntaxTextArea** (`3.4.0`, SQL syntax highlighting)
* **JSch** (`0.2.16`, SSH Terminal & SFTP)
* **JNA** (`5.14.0`, Windows Taskbar & DPAPI Keyring integration)

## Building from Source

This project uses a 3-module Maven reactor:
- `schemasync-core`: 100% headless engine (zero Swing/AWT imports).
- `schemasync-cli`: Headless command-line tool with picocli.
- `schemasync-ui`: Rich desktop UI with FlatLaf and MigLayout.

1. Clone the repository:
   ```bash
   git clone https://github.com/Ygtopher/SchemaSync-.git
   cd SchemaSync-
   ```
2. Build all modules and test suite (Java 21 required):
   ```bash
   mvn clean package -DskipTests
   ```
   This compiles all three modules and packages:
   - `release/SchemaSync.jar` (UI Fat JAR)
   - `release/schemasync-cli.jar` (Headless CLI Fat JAR)
   - `release/SchemaSync.exe` (Windows portable executable)

3. (Optional) Build self-contained native `jpackage` app-image (bundled JRE):
   ```bash
   mvn clean package -Pjpackage -DskipTests
   ```
   Outputs to `release/jpackage/SchemaSync/`.

## Running & Installing

### Headless CLI (`schemasync-cli.jar`)
Automate diffing, drift detection, and data exports directly in CI/CD pipelines without graphical dependencies:
```bash
# Compare schemas between two databases
java -jar release/schemasync-cli.jar diff --source "jdbc:postgresql://localhost:5432/db1" --target "jdbc:postgresql://localhost:5432/db2"

# Detect schema drift against a baseline
java -jar release/schemasync-cli.jar drift-check --source "prod_db" --target "staging_db" --fail-on-drift

# Headless data export to CSV / JSON / SQL
java -jar release/schemasync-cli.jar export --source "prod_db" --table employees --format csv --output employees.csv
```

### Windows Desktop
- **Portable Executable:** Run `release/SchemaSync.exe`.
- **Bundled App-Image:** Run `release/jpackage/SchemaSync/SchemaSync.exe` (requires no external Java installed).
- **Installer:** Run `release/SchemaSync-Setup.exe` for Start Menu and Desktop shortcuts.

### Linux (Ubuntu / Debian & Fedora)
- **Ubuntu / Debian (.deb):**
  ```bash
  sudo apt install ./schemasync_2.3.0_all.deb
  ```
- **Fedora / RHEL (.rpm):**
  ```bash
  sudo dnf install ./schemasync-2.3.0-1.fc44.noarch.rpm
  ```
- Launch directly from terminal or application launcher:
  ```bash
  schemasync
  ```

### Cross-Platform JAR
Run directly with Java 21+ on any operating system:
```bash
java -jar release/SchemaSync.jar
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


