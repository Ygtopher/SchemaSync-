# SchemaSync System Architecture & Design

**Version:** 1.7.1  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## Architectural Overview

SchemaSync is structured around a decoupled, thread-safe, modular desktop application architecture designed specifically for high-throughput database exploration and schema engineering without external web dependencies.

```mermaid
graph TD
    subgraph UI_Layer ["Presentation Layer (Swing + FlatLaf)"]
        MainFrame["Main Application Window (JFrame)"]
        Dashboard["Dashboard & Telemetry Panel"]
        Browse["Multi-Tab Browse & Search Panel"]
        JoinBuilder["Visual Join Builder & JSqlParser"]
        ERDiagram["Interactive ER Diagram (JGraphX)"]
        DataOps["Data Operations & DDL Workbench"]
        SqlEditor["SQL Editor & Syntax Highlighter (RSyntaxTextArea)"]
        ActivityMon["Activity Monitor & Live Profiler"]
        DiffWorkbench["Table Compare & Schema Drift Studio"]
        SecurityTools["Data Masking & AES-256-GCM Vault"]
        SshPanel["SSH Terminal & SFTP Client (JSch)"]
    end

    subgraph Service_Layer ["Service & Coordination Layer"]
        DBManager["DatabaseManager (Connection Lifecycle & Metadata Cache)"]
        ThemeMgr["ThemeManager (FlatLaf Dark/Light Styling)"]
        VaultService["VaultManager (PBKDF2 + AES-GCM)"]
        UpdateService["UpdateChecker & Self-Restart Engine"]
        SessionSaver["SessionPersistence (Drafts & History)"]
    end

    subgraph JDBC_Layer ["Database Abstraction & Driver Layer"]
        PostgresDriver["PostgreSQL JDBC Driver (v42.7)"]
        MySQLDriver["MySQL Connector/J (v8.3)"]
        SQLiteDriver["SQLite JDBC Driver (v3.45)"]
        UCanAccess["UCanAccess (MS Access .accdb Engine)"]
        H2Driver["H2 In-Memory Embedded Database"]
    end

    subgraph Target_Engines ["Physical Storage & Servers"]
        RDBMS_PG["PostgreSQL Server"]
        RDBMS_MY["MySQL / MariaDB Server"]
        File_SQLITE["Local SQLite DB (.db / .sqlite)"]
        File_ACCESS["MS Access Files (.accdb / .mdb)"]
        Mem_H2["In-Memory Sandbox Engine"]
        Remote_SSH["Remote Linux Servers (SSH/SFTP)"]
    end

    MainFrame --> UI_Layer
    UI_Layer --> Service_Layer
    Service_Layer --> DBManager
    DBManager --> JDBC_Layer
    JDBC_Layer --> Target_Engines
```

---

## Key System Layers

### 1. Presentation Layer (Swing + FlatLaf Modern Dark/Light)
* **Single-Process Lightweight GUI:** Built entirely with native Java Swing, completely eliminating the heavyweight memory overhead of Electron or Chromium runtimes.
* **Responsive Multi-Row Toolbars (`WrapLayout`):** Controls automatically wrap into clean vertical rows on smaller display resolutions and laptops, preventing toolbar clipping without requiring cumbersome submenus.
* **Syntax Highlighting & Code Folding:** Powered by `RSyntaxTextArea` with customized dark SQL themes, keyword autocompletion, block bracket matching, and line numbering.
* **Graph Visualization Engine:** Powered by `JGraphX` (mxGraph), providing GPU-accelerated canvas zooming, node panning, hierarchical tree layouts, and high-DPI export.

### 2. Service & Business Logic Layer
* **[`DatabaseManager.java`](file:///d:/projects/DBTOOL/src/main/java/com/dbtool/DatabaseManager.java):** The centralized nexus for all database interactions. Manages connection state, pooling, metadata discovery, query execution, and cache synchronization.
* **Metadata & Column Cache:** Features thread-safe `ConcurrentHashMap` caches for schema table lists, column types, and foreign key graphs, drastically minimizing expensive round-trips to the database metadata catalogs during interactive querying.
* **Query History & Draft Persistence:** Keystrokes in the SQL editor are debounced and persisted to `Documents/SchemaSyncSaves/sql_editor_draft.sql`, ensuring full crash recovery and seamless state restoration across sessions and app updates.

### 3. Threading Model & UI Concurrency
Database queries and network operations never block the Swing Event Dispatch Thread (EDT):

| Execution Type | Handler | Purpose |
| :--- | :--- | :--- |
| **GUI Events & Rendering** | **Event Dispatch Thread (EDT)** | Handles user clicks, repaints, modal dialogs, and table model updates. |
| **SQL Query Execution** | **Background Worker Threads (`SwingWorker`)** | Long-running `SELECT`, `UPDATE`, or script executions run in isolated worker threads. |
| **Telemetry & Health Polling** | **Scheduled Daemon Executors** | Periodic ping checks, connection keep-alives, and Activity Monitor queries run without UI stutter. |
| **Background Metadata Preload** | **Asynchronous Background Threads** | Tables, column names, and foreign key relations are pre-cached in the background upon connection. |

---

## Transaction Lifecycle & Rollback Traps

SchemaSync enforces stringent transaction safety rules to prevent accidental data corruption:

1. **Auto-Commit Control:**
   - The user can toggle Auto-Commit globally via the top-level toolbar.
   - When Auto-Commit is disabled, visual badges reflect the pending transaction state.
   - Dedicated **Commit** and **Rollback** buttons allow explicit transaction finalization.
2. **Script Builder Transaction Guard:**
   - When a transaction block begins (`BEGIN;` or `START TRANSACTION;`), Auto-Commit is temporarily disabled.
   - If an exception occurs on any statement inside the block, an automatic `ROLLBACK` is issued to protect database integrity, and an error report highlights the exact failing statement.
3. **Dry-Run Migration Testing:**
   - Forward (`UP`) and Rollback (`DOWN`) DDL scripts can be executed in **Dry-Run Mode**.
   - The entire script runs inside a transaction that is guaranteed to rollback before completion, verifying syntax and constraint validity against live data without altering the schema.

---

## Persistence & Directory Structure

All user configurations, query drafts, learned join rules, and encrypted vaults are kept in a dedicated, permission-safe user directory:

```
%USERPROFILE%\Documents\SchemaSyncSaves\     (Windows)
~/Documents/SchemaSyncSaves/                  (Linux)
├── sql_editor_draft.sql                      # Active SQL buffer draft
├── query_history.json                        # 100-entry persistent query log
├── saved_queries.json                        # Starred/favorited SQL snippets
├── learned_joins.properties                  # User-defined and auto-discovered table joins
└── credentials.vault                         # AES-256-GCM encrypted connections
```
