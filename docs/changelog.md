# SchemaSync Changelog & Version History

**Current Version:** 1.7.0  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## [v1.7.0] - 2026-10-21
### Added
* **Complete System Documentation Suite:** Added comprehensive architecture documentation in `docs/`:
  * [`docs/codebase-map.md`](codebase-map.md): Package, class, and method inventory across all modules.
  * [`docs/tech-stack-integration.md`](tech-stack-integration.md): Deep-dive into all 20+ technologies and cross-cutting synergy workflows.
  * [`docs/architecture.md`](architecture.md), [`docs/supported-databases.md`](supported-databases.md), [`docs/features.md`](features.md), [`docs/security.md`](security.md), [`docs/installation-and-packaging.md`](installation-and-packaging.md), [`docs/troubleshooting.md`](troubleshooting.md), and [`docs/api-and-cli.md`](api-and-cli.md).
* **Lightweight 54MB Setup Packaging:** Optimized Inno Setup configuration to eliminate duplicate payload embedding, cutting installer size in half while maintaining full Smart App Control compatibility.

### Fixed
* **Windows 11 Smart App Control (Error 4551):** Seamless execution via signed Adoptium Temurin `javaw.exe` runtime.
* **Column Deduplication:** Resolved multi-schema duplicate column names in browse and join checkbox selectors.
* **Installer Adoptium Auto-Download:** Switched download endpoint to official Adoptium `.msi` URL with `PrepareToInstall` pre-install check.

---

## [v1.6.9] - 2026-10-20
### Fixed
* **Column Deduplication in Selectors & Queries:** Resolved an issue where table column names were duplicated in the column selection checkbox dialog and generated duplicate column names in SQL `SELECT` / `JOIN` clauses. Added schema isolation in metadata caching and converted column collectors to deduplicated `LinkedHashSet` instances.
* **Windows 11 Smart App Control (Error 4551):** Configured the Windows installer to create shortcuts that target Microsoft-trusted, digitally signed `javaw.exe`, providing 100% compliance with Windows 11 Smart App Control while preserving the native window icon and `SchemaSync.DatabaseTool` taskbar grouping.
* **Installer Adoptium Java 21 Auto-Download:** Corrected the Adoptium API endpoint from `/binary/` to `/installer/` so setup downloads the official `.msi` package. Shifted Java detection into `PrepareToInstall` to ensure Java 21 installs before shortcuts are generated.
* **Password-Free 1-Click Update on Linux:** Enabled user-space update caching (`~/.local/share/schemasync/`) so Ubuntu (`.deb`) and Fedora (`.rpm`) users can update in 1 click without root/sudo password prompts.

---

## [v1.6.8] - 2026-10-18
### Added
* **Ubuntu Desktop Linux Support (`.deb`):** Introduced official Debian/Ubuntu `.deb` packages with automatic OpenJDK dependency management, `/usr/bin/schemasync` command-line integration, and GNOME App Grid launchers.
* **Cross-Platform Update Engine:** Unified the 1-Click Update & Restart experience across Windows and Linux.

---

## [v1.6.4] - 2026-10-14
### Added
* **Native Fedora 44 RPM Package (`.rpm`):** Added native RPM packaging installable via `dnf` with `/usr/bin/schemasync` system link.
* **Wayland Dock Icon Binding:** Resolved the generic grey icon issue on Fedora Wayland GNOME sessions via `awtAppClassName` matching and `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED`.
* **Automatic SQL Editor Session Recovery:** Added background draft debouncing to `Documents/SchemaSyncSaves/sql_editor_draft.sql`, recovering work after reboots or updates.
* **Persistent Query History:** Relocated query history to `Documents/SchemaSyncSaves/` with 100-entry capacity.

---

## [v1.5.0] - 2026-10-01
### Added
* **Sensitive Data Masking & Anonymization Studio:** Introduced GDPR and HIPAA-compliant data masking with automatic PII pattern detection (emails, phones, credit cards, names, SSNs) and sanitized SQL/CSV export.
* **Enterprise AES-256-GCM Credential Vault:** Military-grade encrypted connection storage with PBKDF2-HMAC-SHA256 key derivation, zero-knowledge master password protection, and encrypted `.schemasync.vault` team sharing.

---

## [v1.4.0] - 2026-09-24
### Added
* **Interactive ER Diagram & Architecture Visualizer:** High-contrast relationship graphing powered by `JGraphX` with Hierarchical, Organic, and Circular layouts. Added high-DPI PNG export and Mermaid.js Markdown ERD generation.
* **Database Performance & Index Advisor:** Detects unindexed foreign keys causing slow `JOIN` scans, redundant composite indexes, missing primary keys, and high-cardinality candidate search columns with 1-click execution.

---

## [v1.3.0] - 2026-09-20
### Added
* **Schema Drift & Reversible Migration Studio:** Capture portable `.schema.json` snapshots, inspect schema drift across environments, and generate atomic reversible **UP** and **DOWN** migration DDL scripts with **Dry-Run Mode** testing.
* **Instant ORM & Entity Code Generator:** Right-click any table to produce production-ready backend models for Spring Boot JPA (`@Entity`), Java Records, Prisma Schema, Drizzle ORM, SQLAlchemy, Pydantic (v2), and C# EF Core.

---

## [v1.2.0] - 2026-09-17
### Added
* **Visual Data Charting:** Interactive Bar, Line, and Donut charts with image and clipboard export.
* **Smart Drag-and-Drop CSV & Excel Importer:** Drag & drop `.csv`, `.tsv`, or `.xlsx` files with automatic column mapping, first-20-rows preview, batch inserts, and single-transaction rollback safety.
* **Synthetic Realistic Mock Data Generator:** Generate 10 to 10,000 realistic records with relational foreign-key sampling from existing parent tables.
* **Recursive FK Dependency Inspector & Safe Cascade Delete:** Interactive visual tree of child dependencies with reverse-topological deletion plans.

---

## [v1.1.0] - 2026-09-15
### Added
* **Visual Join Builder:** Multi-table relational query builder with automatic foreign-key discovery and bidirectional `JSqlParser` reverse SQL loading.
* **Query Profiler & Latency Bottleneck Analyzer:** Microsecond latency logging, bottleneck threshold warnings (`> 100ms`, `> 500ms`), and execution plan visualization (`EXPLAIN`).
* **Activity Monitor & Live PID Inspector:** Real-time database server activity tracking with 1-click backend PID query cancellation.
* **SSH Terminal & SFTP Transfer Client:** Built-in server management tab powered by `JSch`.

---

## [v1.0.0] - 2026-09-14
### Initial Release
* Multi-database connectivity for PostgreSQL, MySQL, SQLite, MS Access (.accdb), and embedded H2.
* Table Browser with custom WHERE filtering, search, and inline cell editing.
* Transaction control (Commit, Rollback, Auto-Commit toggle).
* Script Builder with psql emulation (`\set`, `\gset`, `\echo`).
* Exporting to CSV, Excel (`.xlsx`), PDF, and SQL `INSERT` statements.
