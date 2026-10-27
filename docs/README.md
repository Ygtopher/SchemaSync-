# SchemaSync Documentation Hub

**Version:** 1.7.3  
**Creator & Lead Architect:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## Welcome to SchemaSync

**SchemaSync** is an enterprise-grade, high-performance, lightweight database synchronization, management, and developer productivity studio built with **Java 11+ and modern FlatLaf UI**. 

It provides seamless multi-engine connectivity across **PostgreSQL, MySQL, SQLite, Microsoft Access (.accdb / .mdb), and embedded H2**, delivering visual schema design, real-time query profiling, data anonymization, automated migrations, visual SQL charting, and full native cross-platform deployment on **Windows 11 (Smart App Control compliant), Ubuntu Desktop Linux (.deb), and Fedora Linux (.rpm)**.

---

## System Requirements

| Specification | Minimum Requirement | Recommended Requirement |
| :--- | :--- | :--- |
| **Java Runtime (JRE/JDK)** | Java 11 (LTS) | Java 21 (LTS) — Eclipse Temurin / Oracle |
| **Operating System** | Windows 10/11 (64-bit), Ubuntu 20.04+, Fedora 38+ | Windows 11 (23H2+), Ubuntu 24.04 LTS, Fedora 44 |
| **RAM** | 512 MB free RAM | 2 GB+ free RAM |
| **Disk Space** | 150 MB for installation | 500 MB for local database caching & backups |
| **Display Resolution** | 1280 × 720 (HD) | 1920 × 1080 (Full HD) or 4K with UI scaling |
| **Architecture** | x86_64 / amd64 | x86_64 / amd64 |

---

## Documentation Index

Explore the complete system documentation categorized by domain:

### 1. [Codebase, Module & Function Map](file:///d:/projects/DBTOOL/docs/codebase-map.md)
* Complete package-by-package, class-by-class, and method-by-method inventory for the entire repository.
* Method signatures, line-by-line mappings, and component responsibility definitions.
* Core facade (`Main`, `DatabaseManager`), all 20+ UI panels and studios, packaging engines, and utilities.

### 2. [Technology Stack & System Integration Guide](file:///d:/projects/DBTOOL/docs/tech-stack-integration.md)
* Comprehensive breakdown of all 20+ technologies, libraries, drivers, and frameworks used in SchemaSync.
* Selection rationale: why each library was chosen over alternatives.
* **End-to-End Synergy Workflows:** Detailed architectural diagrams and explanations showing **how technologies work together** (e.g. Query Profiling, AST Join Builder, ER Diagramming, PII Masking, Credential Vault, and Windows 11 Smart App Control compliance).

### 3. [System Architecture & Design](file:///d:/projects/DBTOOL/docs/architecture.md)
* Architectural layers (Swing UI, Service/Manager layer, JDBC Driver abstraction).
* Threading model (`SwingWorker`, async background execution, event dispatch safety).
* Transaction lifecycle management (auto-commit toggling, atomic rollback traps, manual commit).
* Modular panel and dialog lifecycle.

### 2. [Supported Databases & Dialects](file:///d:/projects/DBTOOL/docs/supported-databases.md)
* Comprehensive breakdown for PostgreSQL, MySQL, SQLite, MS Access, and embedded H2.
* JDBC driver configurations, connection pooling, and connection strings.
* Type mappings, quote identifiers, and dialect-specific SQL optimizations.
* In-memory Sandbox database environment.

### 3. [Workbench Tools & Features](file:///d:/projects/DBTOOL/docs/features.md)
* **Executive Telemetry & Dashboard:** Real-time health gauges, storage distribution, and inventory.
* **Browse & Search Studio:** Multi-column selection, dynamic WHERE/HAVING filters, order-by clauses.
* **Smart SQL INSERT Row Copying:** Context-menu `INSERT INTO` generation that automatically omits primary key / auto-increment columns (with optional explicit PK inclusion).
* **Dual DB Compare Unified SQL Mode:** Execute a single query (`SELECT` data diff or multi-DB `UPDATE`/`INSERT`/`DELETE`/`ALTER`) across all connected databases simultaneously.
* **Visual Join Builder & Reverse Parser:** Drag-free join constructor with JSqlParser bidirectional SQL sync.
* **Interactive ER Diagram Canvas:** High-contrast relationship graphing with PNG and Mermaid.js export.
* **Schema Drift & Migration Studio:** Forward (UP) and Rollback (DOWN) DDL with Dry-Run validation.
* **Database Performance & Index Advisor:** Unindexed FK detection, redundant index alerts, and cardinality scanning.
* **Sensitive Data Masking Studio:** GDPR/HIPAA PII masking (names, emails, phones, SSNs, credit cards).
* **Enterprise AES-256-GCM Vault:** Military-grade encrypted credential storage with zero-knowledge master password.
* **Query Profiler & Latency Bottleneck Analyzer:** Microsecond latency logging, bottleneck threshold warnings.
* **Instant ORM & Entity Code Generator:** JPA, Java Records, Prisma, Drizzle, SQLAlchemy, Pydantic, EF Core.
* **Synthetic Mock Data Generator:** Relational mock data generator with foreign key constraint sampling.
* **Recursive FK Dependency Inspector:** Cascade delete safety visualizer with topological sorting.
* **SSH Terminal & SFTP Transfer Client:** Integrated remote server management.
* **Global OmniSearch (`Ctrl+Shift+F`):** Full-catalog background text scanning.
* **Visual Data Charting:** High-resolution Bar, Line, and Donut charts with clipboard and image export.

### 4. [Security & Cryptography](file:///d:/projects/DBTOOL/docs/security.md)
* AES-256-GCM Authenticated Encryption with PBKDF2-HMAC-SHA256 (100,000 iterations).
* Zero-Knowledge Master Password architecture & in-memory memory wiping.
* Windows 11 Smart App Control (SAC) compliance (trusted signed runtime execution).
* Safe SQL execution (parameterized queries, transaction rollback guards).

### 5. [Installation, Deployment & Packaging](file:///d:/projects/DBTOOL/docs/installation-and-packaging.md)
* **Windows Installer (`SchemaSync-Setup.exe`):** Inno Setup 6, signed runtime resolution, Adoptium JRE 21 setup.
* **Portable Windows Wrapper (`SchemaSync.exe`):** Launch4j configuration and executable headers.
* **Ubuntu Linux Package (`.deb`):** Debian package layout, desktop menu entries, and `/usr/bin` wrapper.
* **Fedora Linux Package (`.rpm`):** RPM packaging, Wayland dock icon binding, and desktop launcher.
* **Universal JAR (`SchemaSync.jar`):** Fat JAR bundling all JDBC drivers and UI libraries.
* **1-Click Self-Updater:** Unified cross-platform update detection and silent restart mechanism.

### 6. [Troubleshooting & FAQ](file:///d:/projects/DBTOOL/docs/troubleshooting.md)
* Resolving Windows 11 Smart App Control (Error 4551).
* Java version mismatch (`UnsupportedClassVersionError`).
* Resolving `"Windows cannot find 'javaw.exe'"`.
* Linux dock icon binding in Wayland vs. X11.
* SSH & SFTP host key and authentication diagnostics.
* MS Access lock file (`.laccdb`) handling.

---

## Repository & Project Information

* **Repository:** [GitHub - Ygtopher/SchemaSync-](https://github.com/Ygtopher/SchemaSync-)
* **Primary Language:** Java (100% Core Java Swing + Modern FlatLaf)
* **Build System:** Apache Maven (`pom.xml`)
* **Author:** NSENGIYUMVA Christophe
* **License:** MIT License
