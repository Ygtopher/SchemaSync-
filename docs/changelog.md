# SchemaSync Changelog & Version History

**Current Version:** 2.3.0  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## [v2.3.0] - 2026-11-13
### Multi-Connection Database Workspaces & Detachable Floating Windows
* **Multi-Connection Database Session Management (`SessionManager`, `DatabaseSession`):**
  * Concurrent multi-database architecture: manage multiple active database connections concurrently within a single application instance without needing to launch separate OS processes.
  * Full session isolation: each session maintains an independent `DatabaseManager`, HikariCP connection pool, schema metadata cache, and transaction state.
  * Thread-safe session lifecycle: unified event model with `SessionListener` for session additions, activations, detachments, re-attachments, and removals.
* **Modern Workspace Session Tab Bar (`SessionTabBar`):**
  * Chrome/DataGrip-inspired horizontal session tab strip situated directly beneath the header bar.
  * Engine-specific vector badge and live status indicator dot (`🟢 Connected`, `⚪ Disconnected`).
  * Per-session action buttons: 1-click `↗ Detach` into an independent floating window and `✕ Close` session with connection cleanup.
  * Global `+ Connect` action button to open new database connections into new concurrent workspace tabs.
* **Detachable Floating Database Workspaces (`Main`):**
  * Tear off any database tab into an independent top-level floating window for true multi-monitor workflows.
  * Full standalone workbench: each detached window provides complete access to all 13 workspace tabs (`Dashboard`, `SQL Editor`, `Schema Browser`, `Data Operations`, `ER Diagram`, etc.) scoped exclusively to that database.
  * Detached identity badge: header displays a prominent `[ ↗ Detached ]` badge and active connection coordinates.
  * 1-Click Re-attach (`[ ↙ Re-attach to Main ]`): instantly merges the detached workspace back into the main coordinator window.
  * Safe window lifecycle: closing a detached window cleanly returns the session to the main window with zero data or state loss.
* **Vector Icons & UI Enhancements:**
  * Added procedural vector icons `DETACH` (`↗`) and `ATTACH` (`↙`) to `UiIconFactory`.
  * Dynamic panel binding: `SchemaPanel`, `SqlEditorPanel`, `DashboardPanel`, `TableComparePanel`, `DualDatabaseComparePanel`, `ScriptBuilderPanel`, and `DataOpsPanel` updated to seamlessly switch active `DatabaseManager` instances.
  * Automated testing suite: comprehensive unit and visual integration tests (`DatabaseSessionTest`, `SessionManagerTest`, `SessionTabBarTest`, `MultiConnectionWindowRenderTest`).

---

## [v2.2.0] - 2026-11-13
### Complete Dashboard Remake & Smooth Spline Area Chart Component
* **All-New Mission Control Dashboard Architecture (`DashboardPanel`):**
  * Eliminated legacy 12 cramped text-heavy telemetry cards and static table layout.
  * **Live Identity Hero Banner**: Live database engine emblem, database name, connection status pill (`🟢 Live (Read / Write)`), host, port, active user, live ping badge (`⚡ 11 ms`), and interactive **Radial Schema Health Score Dial (`94 / 100`)**.
  * **4 Primary High-Impact KPI Cards**: Catalog Inventory (`48 Tables, 12 Views`), Storage Footprint (`1.42 GB, ~650k Rows`), Query Latency (`14.2 ms Avg, 99.8% Success`), and Pool & Governance (`3 / 10 Active, Safe DML Guard ON`).
  * **Actionable Schema & Performance Insights**: Real-time diagnostic recommendations detecting missing Primary Keys and unindexed foreign keys with 1-click execution shortcuts (`[ Review PKs ]`, `[ Add Index ]`).
  * **Visual Table Storage Footprint**: Top 5 largest tables displayed with proportional gradient progress meters, row count estimates, storage percentages, and double-click navigation into Browse tab.
  * **Executive Studio Launchpad**: 6 modern quick-action tiles with procedural vector icons (`OmniSearch`, `ER Diagram`, `Dual DB Compare`, `Schema Drift`, `Data Masking`, `Mock Data Gen`).
  * **Recent Query Execution Activity**: Live timeline stream displaying recent queries with execution duration badges (`12 ms`), status checkmarks, timestamps, and formatted SQL previews.
* **Smooth Spline Gradient Area Chart Component (`SplineAreaChartPanel`):**
  * Built a custom, pure Java 2D smooth spline area chart component with Catmull-Rom cubic bezier curve interpolation (no jagged lines).
  * Smooth vertical gradient fill (`GradientPaint`) fading down to 0% opacity at baseline, matching modern Cloud Console aesthetics.
  * Multi-metric switcher: Toggle between `Query Volume (Throughput)`, `Avg Latency (ms)`, and `Storage Growth`.
  * Time range filters: Quick toggles for `24h`, `7d`, `30d`, and `12m` timeline.
  * Interactive hover crosshair: Dynamic dashed vertical marker, glowing beacon dot, and floating tooltip showing exact timestamp and metric values.
  * Summary telemetry cards: Peak throughput, lowest latency, 99th percentile, and query error rate.

---

## [v2.2.0] - 2026-11-13
### Visual Design Overhaul ("Best Look Ever") & Modern Developer Experience
* **Procedural High-DPI Vector Icon Suite (`UiIconFactory`):**
  * Built a 100% pure Java 2D vector procedural icon rendering engine producing razor-sharp, anti-aliased icons at any screen resolution or UI scale factor.
  * Vector icons for all 13 workspace tabs (`Dashboard`, `Browse & Search`, `Visual Join Builder`, `Data Operations`, `SQL Editor`, `Activity Monitor`, `Schema Dependencies`, `ER Diagram`, `Script Builder`, `Compare Queries`, `Dual DB Compare`, `SSH / Server`, `AI Assistant`), plus actions (`RUN`, `STOP`, `CONNECT`, `DISCONNECT`, `DATABASE`, `TABLE`, `REFRESH`, `LOCK`, `CHIP_RAM`, `CHART`, etc.).
* **Unified App Header Bar (`Main`):**
  * Replaced legacy top button wraps with a clean 3-section header bar:
    * **West:** Sidebar toggle (`☰`, `Ctrl+B`), high-res app emblem, bold `SchemaSync` brand title, `v2.2.0` badge, and interactive **Connection Status Pill** (`🟢 Engine / Database` with 1-click disconnect `✕`).
    * **Center:** Spotlight Command Search bar (`[ Ctrl K ]`) for rapid table and workbench navigation.
    * **East:** Primary CTA button (`+ Connect`), `Profiles ▾`, `⚡ Sandbox`, `🧰 Studios ▾`, `🎨 Theme ▾`, and `⋮ More ▾` dropdowns.
* **DataGrip / VS Code-Grade Interactive Status Bar (`StatusBar`):**
  * **Left:** Live Engine pill (`🟢 PostgreSQL 17 / my_db` or `⚪ Offline`), Ping latency badge (`⚡ 12 ms` colored green/amber/red), Schema indicator, and Auto-Commit toggle pill (`Auto-Commit: ON` / `MANUAL`).
  * **Center:** Real-time task progress bar and status message label.
  * **Right:** Auto-Save status button (`💾 Auto-Saved`), JVM Heap RAM monitor (`RAM: 142/512 MB` with 1-click Garbage Collection optimization), UI scale zoom indicator (`🔍 100%`), and Query Log drawer toggle (`📜 Log: 0`).
* **New Tokyo Night & Dracula Studio Themes (`ThemeManager`):**
  * Added **`TOKYO_NIGHT`** ("🌃 Tokyo Night") and **`DRACULA`** ("🧛 Dracula Studio") dark themes.
  * Global UI curve styling (`Component.arc=8`, `Button.arc=8`, `TextComponent.arc=8`) and custom window decorations (`JFrame.setDefaultLookAndFeelDecorated(true)`).
* **Glassmorphic Notification Toasts (`NotificationToast`):**
  * Non-intrusive floating toasts docking smoothly at the bottom-right for connection status, clipboard operations, and memory optimizations, replacing disruptive modal dialog popups.
* **Refined Query & Table Toolbars:**
  * Replaced plain text pipe separators (`" | "`) with subtle vertical dividers (`ThemeManager.createToolbarSeparator()`).
  * Styled run and execute buttons with procedural vector play icons and theme-adaptive success green across `BrowseTabPanel`, `VisualJoinTabPanel`, `SqlEditorPanel`, `TableComparePanel`, and `DashboardPanel`.

---

## [v2.0.0] - 2026-11-12
### Major Architecture & Platform Redesign
* **3-Module Maven Reactor Split:**
  * Re-architected SchemaSync from a monolithic structure into a clean, decoupled 3-module reactor:
    * `schemasync-core`: 100% headless engine (zero `javax.swing.*` or `java.awt.*` imports) containing models, crypto, connection management, SQL parsers, diff engines, and SPIs.
    * `schemasync-cli`: Standalone, headless command-line utility powered by Picocli 4.7.6 (`diff`, `drift-check`, `export`) suitable for CI/CD pipelines.
    * `schemasync-ui`: Rich desktop GUI powered by FlatLaf 3.4, MigLayout 11.3, and docking panels.
* **Java 21 LTS Modernization & Virtual Threads:**
  * Bumped `<source>`, `<target>`, and `<release>` across all modules to Java 21 LTS.
  * Replaced heavyweight ThreadPools with `Executors.newVirtualThreadPerTaskExecutor()` in `TaskCoordinator` for high-concurrency database I/O.
  * Modernized data models to Java 21 `record`s (`QueryExecutionMetric`, `DataTypeInfo`, `ConnectionChangedEvent`, `SchemaInvalidatedEvent`, `QueryExecutedEvent`, `TableMutatedEvent`).
* **High-Throughput Connection Pooling (HikariCP 5.1.0):**
  * Added `SessionPoolManager` delivering high-performance HikariCP connection pools for server databases (PostgreSQL, MySQL).
  * Implemented synchronized lease locks for file-based engines (SQLite, MS Access) preventing file corruption while allowing multi-threaded UI access.
  * Provided dual borrowing semantics: `borrowConnection()` for short pooled queries and `openPinnedSession()` for transaction-controlled workbenches.
* **2-Phase Chunked & Canonical SHA-256 Hash Diff Engine:**
  * Added `CanonicalValueNormalizer`, `CompositeKey`, and 256-bit `RowHasher` producing deterministic cryptographic row fingerprints.
  * Built `ChunkedDataComparator` computing chunk-level Merkle hashes across keyspaces to instantly identify matching blocks and isolate dirty row ranges without full-table memory loading.
  * Implemented `PagedResultWindow` (bounded 2,000-row LRU window) and `VirtualResultTableModel` for streaming keyset pagination.
* **Pluggable Database Dialect & Custom JDBC Driver SPIs:**
  * Extended `DatabaseDialect` interface with standard implementations (`PostgreSqlDialect`, `MySqlDialect`, `SqliteDialect`) loaded via Java's `ServiceLoader`.
  * Implemented `DriverClassLoaderManager` and `DriverShim` for dynamic, on-the-fly loading of external JDBC drivers without restarting.
  * Added interactive `DriverManagerDialog` with connection testing and dynamic registry updates.
* **Pluggable Multi-Provider AI SQL Assistant:**
  * Implemented `LlmProvider` SPI with out-of-the-box providers: Ollama (local private LLM), Google Gemini, OpenAI, Anthropic Claude, and OpenRouter.
  * Added `AiKeyVault` for secure, encrypted API key storage.
  * Added `SchemaContextBuilder` enforcing schema-only privacy (never sends table data or rows to external LLM providers).
* **Enterprise Security Vault V2 & Cryptographic Update Verification:**
  * Upgraded `SecretStore` with OS Keyring integration: Windows DPAPI via JNA, macOS Keychain via `/usr/bin/security`, and Linux Secret Service via `secret-tool`.
  * Added AES-256-GCM authenticated encryption with fail-closed decryption semantics in `CryptoUtil` and `ProfileStoreV2`.
  * Added `ReleaseVerifier` with SHA-256 checksum validation and ECDSA P-256 digital signature verification on GitHub Release artifacts.
* **Responsive Layout Modernization (MigLayout 11.3):**
  * Migrated toolbar and compare card layouts in `DualDatabaseComparePanel` and `DataCompareTabPanel` to fluid, responsive MigLayout constraints.
* **Binary Untracking & Automated CI/CD Release Pipeline:**
  * Untracked legacy release binary blobs from Git history and updated `.gitignore`.
  * Added automated cross-platform GitHub Actions release pipeline (`.github/workflows/release.yml`) producing native `jpackage` app-images, Windows executables, Ubuntu `.deb`, and Fedora `.rpm` packages.

---

## [v1.8.4] - 2026-11-06
### Added & Improved
* **Eliminated Massive Vertical Gaps in Data Diff Configuration Toolbar (`DataCompareTabPanel`):**
  * Re-architected `quickTableContainer` layout into a compact `BorderLayout`: database selector row (`rowDbAndTable`) and clauses toolbar (`tbClauses`) are tightly stacked in `quickTableTopRows` (`BoxLayout.Y_AXIS`) docked at `BorderLayout.NORTH`.
  * Positioned dynamic query clauses (`clausesScrollPane`) in `BorderLayout.CENTER`, eliminating empty voids and expanding only when query clauses are active.
  * Re-configured `dataCompareSplit` with `setResizeWeight(0.0)` so window resizing and maximization allocates 100% of vertical expansion to diff tables and comparison results rather than stretching toolbar gaps.
  * Added dynamic split divider positioning (`updateConfigDivider()`): automatically adjusts divider height between `112px` (compact Quick Table mode), `235px` (when clauses are added), and `260px` (SQL editor modes).
* **Fixed Database Connection Card Button Clipping & Overlapping (`DualDatabaseComparePanel`):**
  * Updated connection card dimensions to `460px × 82px` with zero-margin tight padding (`FlowLayout(FlowLayout.LEFT, 2, 0)`), ensuring all 6 database action buttons (`App DB`, `Sandbox`, `Postgres...`, `Snapshot...`, `Disconnect`, and `✕`) sit comfortably on a single line without wrapping, overlapping, or clipping the card border.
  * Upgraded `dbCardsContainer` to `FlowLayout(FlowLayout.LEFT, 8, 2)` and increased `dbCardsScrollPane` preferred height to `92px` to prevent unexpected wrapping and provide smooth horizontal scrolling across N databases.

---

## [v1.8.3] - 2026-11-06
### Added & Improved
* **PostgreSQL Dollar-Quoting (`$$ ... $$` & `$tag$ ... $tag$`) Support in Multi-Statement Execution (`DataCompareTabPanel`):**
  * Enhanced statement splitting (`splitSqlStatements`) to natively preserve PostgreSQL dollar-quoted function bodies, procedural blocks, and triggers. Semicolons inside procedural logic (e.g., custom fuzzy matching `CREATE OR REPLACE FUNCTION ... AS $$ ... $$;`) are preserved without premature splitting.
* **Intelligent Dynamic Key Column Auto-Resolution in Custom SQL Diff (`DataCompareTabPanel`):**
  * Custom comparison queries now dynamically resolve identifier key columns (`#`, `AccountNumber`, `id`, or first common column) directly from query result sets and aliases.
  * Eliminates the requirement to pre-select a physical table column when running arbitrary custom SQL, seamlessly displaying cell-level diffs or switching to the side-by-side view.

---

## [v1.8.2] - 2026-11-06
### Added & Improved
* **Dual Database Compare Advanced Feature Suite (`DualDatabaseComparePanel`, `DataCompareTabPanel`, `DataReconciliationDialog`, `DualDbReportExporter`):**
  * **⇄ Quick Swap Source ↔ Target:** Added 1-click bidirectional swapping of Source and Target database connections and selected tables in both Schema Diff and Live Data Diff tabs.
  * **Dynamic Live Counter Badges:** Added live quantitative counters to filter buttons (`All (N)`, `Differences (N)`, `Modified (N)`, `Identical (N)`) in both Schema and Data diff views.
  * **Direct Schema-to-Data Diff Jump:** Added double-click listener and right-click context menu on schema diff table rows to instantly switch to the Data Diff tab and configure the selected table for comparison.
  * **Safe Transactional Data Reconciliation Dialog (`DataReconciliationDialog`):** Interactive modal dialog previewing SQL dry-run statements (`INSERT`, `UPDATE`, `DELETE`) with syntax highlighting, file export, and atomic execution inside an isolated transaction (`setAutoCommit(false)` with automated rollback on error).
  * **Side-by-Side Visual DDL Split Viewer:** Added tabbed DDL inspector displaying both the Target Migration DDL script and side-by-side source vs target `CREATE TABLE` DDL definitions.
  * **Cross-Engine Smart Type Normalization (`SchemaComparator`):** Intelligent type equivalence matching across database engines (`INT4` ↔ `INTEGER`, `BIGINT` ↔ `INT8`, `VARCHAR(255)` ↔ `TEXT`, `BOOLEAN` ↔ `TINYINT(1)`, `TIMESTAMPTZ` ↔ `TIMESTAMP WITH TIME ZONE`, `DOUBLE PRECISION` ↔ `FLOAT8`).
  * **Interactive HTML & Markdown Audit Report Exporter (`DualDbReportExporter`):** Generates self-contained, publication-ready executive comparison audit reports with summary metric cards, colored status badges, column drift tables, and target migration scripts.
  * **Offline Schema Snapshot Support in Dual DB Compare:** Added `Load Snapshot (.json)...` option to compare live databases directly against saved offline schema snapshots without requiring an active database server.
* **Persistent Minimized Query Log State (`Main`):**
  * Fixed window resize and maximize listeners to strictly preserve the minimized state of the Global Query Log without un-minimizing during app layout adjustments.
* **Smart Content-Based Column Auto-Sizing & Theme Grid Lines:**
  * Auto-adjusts column widths based on exact content and header length (with icon padding and monospace metric calculation) to eliminate column clipping and prevent squished columns in Visual Join Builder and Browse & Search tables.

---

## [v1.8.1] - 2026-11-06
### Added & Improved
* **Comprehensive Workspace Session Snapshot Restore & Recovery (`SessionRecoveryManager`, `RecoveryDialog`, `Main`):**
  * Added **`♻️ Restore Latest Autosaved Snapshot...`**, **`📂 Load Snapshot from File...`**, **`💾 Export Current Snapshot to File...`**, and **`📜 Recent / Archived Snapshots ▾`** directly to the Auto-Save toolbar indicator button and **Command Palette (`Ctrl+K`)**.
  * Upgraded `RecoveryDialog` with interactive manual restore mode showing recoverable work summaries, connected databases, browse tabs, visual join tabs, and query draft previews.
  * Re-architected `restoreSessionSnapshot(...)` with asynchronous sequencing: database connections are re-established on a background worker thread, columns and foreign keys are preloaded in bulk, UI tables are populated, and Browse & Visual Join tabs automatically execute queries and render data grids upon restoration.
  * Added full connection restoration support for all database engines: **SQLite** (`.db`, `.sqlite`, `.sqlite3`), **MS Access** (`.accdb`, `.mdb`), **MySQL / MariaDB**, **PostgreSQL**, and in-memory **H2 Sandbox**.
  * Added `SessionRecoveryManager.listArchivedSnapshots()`, `loadSnapshotFromFile()`, and `saveSnapshotToFile()` with unit test coverage.
* **Schema Drift Snapshot Studio Offline Comparison & Null-Safety (`SchemaDriftDialog`, `SchemaComparator`):**
  * Removed database connection restriction so `SchemaDriftDialog` can be opened at any time.
  * Added offline file-to-file comparison mode: compare **Saved Baseline Snapshot (.schema.json)** against a **Saved Target Snapshot (.schema.json)** without requiring a live database connection.
  * Added **`📂 Load Target Snapshot (.json)`**, **`⇄ Swap Baseline ↔ Target`**, and **`📜 Inspect Tables`** to view schema definitions, tables, and column metadata directly from saved snapshot JSON files.
  * Hardened `SchemaComparator` against `null` types, missing column names, and unexpected metadata to prevent `NullPointerException` during comparison.

---

## [v1.8.0] - 2026-11-06
### Added & Improved
* **Intelligent Content-Based Column Width Auto-Sizing (`SmartTableColumnAdjuster`, `UpdatableTable`, `Main`):**
  * Added `SmartTableColumnAdjuster` to calculate optimal column widths dynamically from cell content lengths and header titles (including `🔑` PK / `🔗` FK icons and sort arrows).
  * Automatically sets `AUTO_RESIZE_OFF` with smooth horizontal scrolling when total column width exceeds the viewport, preventing the "barcode sliver" column squishing in `Visual Join Builder` and data truncation (`2090...`, `GITWA...`) in `Browse & Search`.
  * Added interactive double-click auto-fit on header separators, allowing users to auto-fit individual columns instantly.
  * Added dedicated **`[⚡ Auto-Fit]`** toolbar buttons in both **Browse & Search** and **Visual Join Builder**, and an **`⚡ Auto-Fit All Columns to Content`** action to table context menus.
  * Integrated automatic column width adjustment across `SQL Editor`, `Dual Database Compare`, `Table Compare`, and `Activity Monitor`.
* **Crisp Table Grid & Header Theme Matching (`ThemeManager`, `TableRowUtilities`):**
  * Added preset-specific grid line colors: clean, visible Slate 300 (`#cbd5e1`) for **Solar Daylight (Light Mode)** and coordinated navy slate for all Dark Mode presets (**Midnight Blue**, **Emerald Studio**, **Obsidian Slate**, **Cyber Amethyst**).
  * Explicitly synchronized `setShowGrid(true)`, horizontal/vertical grid lines, cell spacing, and row header borders across live tables and scroll viewports.
* **High-Contrast Data Visibility & Semantic Badges (`SmartTableCellRenderer`):**
  * Tuned cell text foreground to deep Slate 900 (`#0f172a`) in Light Mode and crisp Slate 100 (`#f1f5f9`) in Dark Mode for maximum legibility.
  * Redesigned `NULL` badges with high-contrast text (`#475569` bold on `#f1f5f9` with `#cbd5e1` border in Light Mode, WCAG > 4.8:1 contrast).
  * Enhanced monospaced numeric formatting, boolean badges (`● true` / `○ false`), and date/timestamp highlighting.

---

## [v1.7.9] - 2026-11-06
### Added & Improved
* **Fixed-Width Semantic Clause Badges & Aligned Grid Columns (`ClauseBadgeFactory`, `Main`, `DataCompareTabPanel`):**
  * Added `ClauseBadgeFactory` with theme-aware `96px × 26px` semantic clause badges (`🔗 JOIN`, `↳ ON`, `↳ AND`, `➕ TABLE`, `🔍 WHERE`, `↕ ORDER`, `📂 GROUP`, `🎯 HAVING`, `∑ AGG`, `🪟 OVER`) that adapt automatically to both Dark Mode and Solar Daylight (Light Mode).
  * Standardized fixed column widths across all stacked clause rows (`PREFIX_WIDTH = 96`, `TABLE_WIDTH = 148`, `DOT_WIDTH = 18`, `COLUMN_WIDTH = 168`, `OPERATOR_WIDTH = 94`, `VALUE_WIDTH = 165`) in **Visual Join Builder**, **Browse & Search**, **Subquery Clauses Builder**, and **Dual DB Compare** so table, column, operator, and value inputs align in a clean vertical grid.
  * Fixed `WherePanel` value input visibility when restoring saved session tabs (`restoreWhereState` / `updateInControlsLayout`) so `=` and comparison conditions always display their value text box, and automatically promote the second `WHERE` / `HAVING` row to display the semantic `🔍 WHERE` / `🎯 HAVING` badge when the first row is removed.
  * Unified `ORDER BY` rows into the main clause scroll container in **Visual Join Builder**.

---

## [v1.7.8] - 2026-11-06
### Fixed & Improved
* **Close Every Tab Including the Last Tab Across All Modules (`MultiTabContainer`, `DualDatabaseComparePanel`, `SshTerminalPanel`, `SqlEditorPanel`, `Main`):**
  * Removed restrictions blocking closing the final tab (`Browse 1`, `Join 1`, `Compare Session 1`, `Terminal 1`) so every tab in every module can be closed freely via its `✕` button, middle-click, or right-click menu.
  * Added **`✕ Close All`** toolbar buttons and right-click context menus (**`Close Tab`**, **`Close Other Tabs`**, **`Close All Tabs`**) across **Browse & Search**, **Visual Join Builder**, **Dual DB Compare**, **SSH / Server**, and **SQL Editor Result Tabs**, plus clean empty-state views and automatic tab creation when selecting a table after closing all tabs.
* **Light Mode (`☀️ Solar Daylight`) Data Grid & Viewport Sync (`ThemeManager`, `SmartTableCellRenderer`, `TableRowUtilities`):**
  * Wrapped all `Table.*`, `TableHeader.*`, `Viewport.*`, and `ScrollPane.*` defaults in `ColorUIResource` and added recursive `ThemeManager.syncComponentTreeThemeColors(...)` so `JTable` grids, row number gutters, and scroll viewports immediately switch to crisp white/light-zebra colors in Light Mode.
* **Composite Font Emoji/Symbol Fallback & Release Notes Encoding Sanitizer (`ThemeManager`, `UpdateChecker`):**
  * Preserved OS composite font fallback chains (`FontUtils.getCompositeFont`) during UI scaling and custom button font creation so all emojis and geometric symbols render cleanly on Windows.
  * Added `UpdateChecker.repairMojibake(...)` and `UpdateChecker.formatReleaseNotesForDisplay(...)` to automatically repair UTF-8/Windows-1252 encoding artifacts and format Markdown in the In-App Update Dialog.

---

## [v1.7.7] - 2026-11-05
### Added & Improved
* **TablePlus / DataGrip-Style Modern IDE UI Suite (`ThemeManager`, `SmartTableCellRenderer`, `CommandPaletteDialog`, `RowInspectorDrawer`, `Main`):**
  * **⚡ Command Palette & Quick Table Switcher (`Ctrl+K` / `Ctrl+P`):** Instant multi-token Spotlight search across all database tables, workbench tabs, studio dialogs, theme presets, and interface zoom levels.
  * **📊 Smart Semantic Data Grid Rendering (`SmartTableCellRenderer`):** Renders `NULL` values as rounded italic pill badges, booleans (`true`/`false`) as colored status pills, numbers right-aligned in monospace font, `🔑 PK` / `🔗 FK` icons in column headers, `26px` comfortable row height, zebra row striping, and smooth mouse-hover row highlighting.
  * **📐 Side-by-Side Row Inspector & Quick Editor Drawer (`RowInspectorDrawer`):** Toggleable right-hand sidebar in `Browse & Search` that inspects any clicked row with live column filtering, `NULL` toggles, 1-click field copy, and parameterized `Ctrl+S` in-place saving.
  * **🎛️ Compact Grid Mode & Streamlined Top Header:** Added a 1-click **`▲ Compact Grid`** toggle with live active clause badges (`WHERE (N) | ORDER (N) | JOIN (N)`) in `Browse & Search` and `Visual Join Builder`, plus a grouped **`🧰 Studios & Tools ▾`** menu and **`🎨 Theme ▾`** selector (**Midnight Blue**, **Emerald Studio**, **Obsidian Slate**, **Cyber Amethyst**, **Solar Daylight**, and **90%–125% UI Zoom**).

---

## [v1.7.6] - 2026-11-05
### Added & Improved
* **3-in-1 Instant Update Engine (`UpdateChecker`, `Main`):**
  * **49× Smaller Delta Patch (`SchemaSync-patch.jar`, ~1.17 MB):** Publishes a lightweight classes-only delta JAR alongside the full installer, downloading in under 1 second and patching only `com/dbtool/` classes inside the existing `SchemaSync.jar` or `SchemaSync.exe` while preserving 55 MB of cached third-party libraries.
  * **Silent Background Pre-Download & Staging (`✨ Update Ready — Restart`):** Automatically stages updates in `~/.schemasync/updates/staged/` in the background and illuminates a 1-click **✨ Update Ready (vX.Y.Z) — Restart** toolbar badge with zero download wait.
  * **Direct Binary Swap (No Setup Wizard):** Applies staged updates directly in-place to `SchemaSync.jar` and Launch4j `SchemaSync.exe` (preserving the Windows PE executable header) and relaunches immediately.
* **Live Search Bar in All Column Selector Dialogs (`Main.BrowseTab`, `Main.VisualJoinTab`, `DataCompareTabPanel`):**
  * Added an instant **🔍 Search** filter bar with a `✕` Clear button and auto-focus to **Select Columns** (`Browse & Search`), **Select Join Columns** (`Visual Join Builder`), and **Select Columns to Compare** (`Dual DB Compare`).
  * In `Select Join Columns`, filters across all joined table tabs simultaneously, shows live match count badges on each tab (`table_name (N)`), and auto-switches to the first matching tab.
  * Made `Select All`, `Unselect All`, `Select All (Tab)`, and `Unselect All (Tab)` filter-aware (toggling only visible matching columns when a search filter is active) and added a live `Selected: X / Y` footer counter.

---

## [v1.7.5] - 2026-11-05
### Fixed & Improved
* **Resizable & Scrollable Update Row / Insert Row Dialogs (`Main.BrowseTab`):**
  * Replaced the non-resizable `JOptionPane.showConfirmDialog` with a fully resizable modal `JDialog` (`showResizableRowFormDialog`) so wide tables (30+ columns) never overflow off the bottom of the screen behind the taskbar.
  * Added smooth vertical and horizontal scrolling (`JScrollPane` with 18px mouse-wheel unit increments) and horizontally stretching `GridBagLayout` fields when resizing the dialog.
  * Added an instant **🔍 Filter columns** search bar at the top of the dialog to filter form fields by column name on the fly.
  * Pinned the **Update Row / Insert Row** (`Enter`) and **Cancel** (`Esc`) action buttons in the bottom bar so they remain permanently visible without scrolling.

---

## [v1.7.4] - 2026-10-28
### Added
* **Executive Application Usage & Telemetry Statistics Suite (`DashboardPanel`, `AppUsageTracker`):**
  * **12 Live Telemetry Cards (3×4 Grid):**
    * **🚀 Sessions & Uptime:** Total sessions launched, active usage days, consecutive-day streak (`🔥 Xd`), live session timer, and cumulative usage duration.
    * **📈 Queries Executed & Daily Volume:** All-time queries, current session count, and daily breakdown (`Today`, `Yesterday`, `Last 7 Days`).
    * **✅ Query Success Rate:** Percentage of successful executions vs. errors.
    * **⏱️ Latency & Percentiles:** Average, Median (`P50`), 95th-Percentile (`P95`), and Peak (`Max`) query execution latency (`ms`).
    * **🐢 Slow Queries & Row Volume:** Slow query counters (`>= 500 ms` & `>= 1000 ms` with slowest SQL preview tooltip) plus cumulative **Rows Read (`SELECT`) vs. Rows Mutated (`INSERT`/`UPDATE`/`DELETE`)**.
    * **📊 SQL Workload Mix:** Breakdown across `SELECT / DQL`, `DML`, `DDL`, and `EXPLAIN`.
    * **🛡️ Safety & Linter Governance:** Unfiltered destructive `UPDATE`/`DELETE` queries intercepted/previewed, static SQL linter issues detected & auto-fixes applied, PII rows masked, and Schema Drift snapshots/migrations generated.
    * **🔄 Data Movement & CodeGen:** DB connections opened, exports performed, CSV/Excel rows imported, synthetic mock rows generated, Dual-DB comparisons run, and ORM entity models generated.
    * **🔥 Most Queried Table:** Automatic SQL table extraction (`FROM`, `JOIN`, `INTO`, `UPDATE`, `TABLE`) ranking top queried tables.
    * **🏆 Most Used Tool & DB Engine:** Top workbench tool and top database engine (`PostgreSQL`, `MySQL`, `SQLite`, `MS Access`, `H2`).
    * **🔖 Saved & Pinned Library:** Saved query count, history buffer count, and `⭐ Pinned` favorites.
    * **🧠 JVM Memory & Cache Health:** Live JVM Heap RAM (`Used MB / Max MB (%)`) with 1-click **⚡ Optimize Memory (Run GC)** button.
  * **📊 Comprehensive Usage & Telemetry Breakdown Table:** Interactive **Filter Category** dropdown (`All Categories`, `🛠️ Workbench Tools`, `🔥 Hot Queried Tables`, `⚡ SQL Workload`, `📅 Daily Activity (7d)`, `🛡️ Safety & Data Movement`, `🗄️ DB Engines & Column Types`) with percentage progress bars and 1-click **🧹 Reset Usage Stats**.
  * **🗄️ Deep Schema Quality & Column Type Distribution:** Tracks Populated vs. Empty tables, Tables Missing Primary Keys, Orphan/Isolated tables (`0` incoming & outgoing FKs), In-Memory Metadata Cache counts (`Tables | Columns | FKs`), and Column Data-Type Distribution (`Text/VARCHAR`, `Numeric`, `Date/Time`, `Boolean`, `JSON/Binary/Other`).

---

## [v1.7.3] - 2026-10-27
### Added
* **Smart SQL Authoring & Editor Intelligence (`SqlEditorPanel`, `SqlFormatter`, `SqlQueryAnalyzer`):**
  * **Multi-Tab SQL Scratchpads:** Added a scratchpad tab bar (`Query 1`, `Query 2`, `+ New Tab`) inside the SQL Editor for working across multiple concurrent SQL scripts.
  * **Context-Aware SQL Autocomplete (`Ctrl+Space`):** Resolves table aliases (`FROM employees e`), suggests table-specific columns (`e.first_name`), and auto-completes Foreign Key `JOIN ... ON` conditions.
  * **Parameterized Query Variables (`:param` / `${param}`):** Automatically prompts for named variables on query run and remembers previous values while preserving PostgreSQL `::type` casts.
  * **1-Click Format (`✨ Format`, `Ctrl+Shift+F`) & `⭐ Expand *`:** Formats complex queries cleanly and expands `SELECT *` into explicit table column names.
* **Static Query Linter & Destructive DML Safety (`SqlQueryAnalyzer`):**
  * **🔍 Lint & Analyze:** Detects unfiltered `UPDATE`/`DELETE` queries, `SELECT *`, non-sargable `WHERE` functions (`UPPER(col) = ...`), leading wildcard `LIKE '%...'`, Cartesian joins, and missing `LIMIT` clauses with 1-click auto-fixes.
  * **🛡️ Dry-Run Preview & Unfiltered DML Guardrail:** Intercepts `UPDATE`/`DELETE` statements without a `WHERE` clause before execution and converts DML into safe `SELECT *` preview queries to inspect affected rows first.
* **Interactive Result Grid Power Tools (`SqlEditorPanel`, `SqlInsertGenerator`):**
  * **Instant Client-Side Filter Bar:** Live substring/regex filtering above every result tab without re-querying the database.
  * **Live Selection Statistics Footer:** Real-time calculation of `Selected Rows`, `Cells`, `Nulls`, `Numeric Count`, `Sum`, `Avg`, `Min`, and `Max` as cells/rows are highlighted.
  * **Extended Row Copying (`UPDATE`, `JSON`, `Markdown`):** Context menu in Browse, Join, and SQL Editor result tables now supports **Copy Row as SQL UPDATE**, **Copy Selected as JSON Array**, and **Copy Selected as Markdown Table** alongside **Copy Row as SQL INSERT** (omitting Primary Key by default).
* **Upgraded Query History & Favorites (`QueryHistory`):**
  * Live search filter box, `⭐ Pinned` favorites filter, execution telemetry badges (`[ms, rows]`), and context menu to Pin/Unpin or promote history items to Named Saved Queries.

---

## [v1.7.2] - 2026-10-27
### Added
* **Smart SQL INSERT Row Copying (`SqlInsertGenerator`):**
  * When using **Copy Row as SQL INSERT** on table rows in Browse, Join, or SQL Editor results, primary key / identity / auto-increment columns (e.g. `id`, `SERIAL`, `AUTO_INCREMENT`) are automatically omitted from the generated `INSERT INTO ...` statement.
  * Eliminates unique constraint violations and duplicate key errors when duplicating existing rows or copying test records.
  * Added secondary option **Copy Row as SQL INSERT (Include Primary Key)** in the context menu when explicit primary key insertion is required.

---

## [v1.7.1] - 2026-10-22
### Added
* **Dual DB Compare Unified SQL Mode ("One Query Affects All Databases"):** Added a unified query mode to `DualDatabaseComparePanel` and `DataCompareTabPanel` alongside Quick Table Match and Custom SQL Query modes:
  * Allows writing ONE SQL statement that executes simultaneously across all connected databases in the pool.
  * **SELECT Data Diff:** Concurrently queries all target databases, auto-detects primary/key columns, and displays side-by-side tables and cell-level unified diffs.
  * **Multi-DB DML/DDL Execution:** Safely executes `UPDATE`, `INSERT`, `DELETE`, `CREATE`, `ALTER`, and `DROP` statements across all databases with safety confirmation prompts, transaction-safe multi-statement script splitting, and automatic catalog cache invalidation.
  * **Execution Results & Audit Tab (`⚡ Execution Results (All DBs)`):** Dedicated results log displaying per-database status, rows affected, latency (ms), and diagnostic messages.
  * **Shortcut:** `Ctrl+Enter` directly triggers execution across all connected databases from the query editor.

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
