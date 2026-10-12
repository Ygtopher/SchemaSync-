package com.dbtool.util;

import javax.swing.*;
import java.awt.Component;
import java.util.*;
import java.util.function.Consumer;

/**
 * Comprehensive SQL Snippets and Templates Library.
 * Contains both foundational SQL (DQL, DML, DDL, DCL, TCL, Procedures)
 * and the advanced "Real Map" SQL Mastery modules:
 * 1. The SELECT Machinery
 * 2. Frame-Aware Window Functions
 * 3. Recursive CTEs in Production
 * 4. Production Concurrency & Control
 * 5. Engine-Specific Power Features (PostgreSQL, MySQL, Oracle, SQL Server, Snowflake/BigQuery)
 * 6. Index Architectures
 * 7. Planner & Optimization Reality
 * 8. Set-Theoretic & Analytic Monsters
 * 9. Security & Correctness
 * 10. Beyond Standard SQL (PL/SQL, JSON Path, SQL/MED FDW, MATCH_RECOGNIZE, SQL/PGQ)
 */
public class SqlSnippetsLibrary {

    public static class Snippet {
        private final String category;
        private final String title;
        private final String sql;
        private final String description;
        private final String dialect;

        public Snippet(String category, String title, String sql, String description, String dialect) {
            this.category = category;
            this.title = title;
            this.sql = sql;
            this.description = description;
            this.dialect = dialect;
        }

        public String getCategory() { return category; }
        public String getTitle() { return title; }
        public String getSql() { return sql; }
        public String getDescription() { return description; }
        public String getDialect() { return dialect; }

        @Override
        public String toString() {
            return title + (dialect != null && !dialect.equalsIgnoreCase("Standard / ANSI") ? " [" + dialect + "]" : "");
        }
    }

    private static final List<Snippet> ALL_SNIPPETS = new ArrayList<>();
    private static final Map<String, List<Snippet>> CATEGORY_MAP = new LinkedHashMap<>();

    static {
        // ==========================================
        // CORE FOUNDATIONS
        // ==========================================
        add("1. DQL — Data Query Language", "Basic SELECT Statements",
            "SELECT * FROM employees;\nSELECT first_name, last_name FROM employees;\nSELECT DISTINCT department FROM employees;\nSELECT first_name AS fname, salary * 12 AS annual_salary FROM employees;\n",
            "Fundamental projection and column selection patterns.", "ANSI");

        add("1. DQL — Data Query Language", "Filtering & Sorting (WHERE, ORDER BY, LIMIT)",
            "SELECT * FROM employees WHERE salary > 50000;\nSELECT * FROM employees WHERE dept = 'IT' AND salary > 60000;\nSELECT * FROM employees WHERE dept IN ('IT', 'HR', 'Finance');\nSELECT * FROM employees WHERE name LIKE 'A%';\nSELECT * FROM employees WHERE salary BETWEEN 40000 AND 80000;\nSELECT * FROM employees WHERE manager_id IS NULL;\nSELECT * FROM employees ORDER BY salary DESC LIMIT 10 OFFSET 5;\n",
            "Filtering operators, pattern matching, null checks, and pagination.", "ANSI");

        add("1. DQL — Data Query Language", "Aggregate Functions (COUNT, SUM, AVG, MIN, MAX)",
            "-- Standard Aggregate Functions\nSELECT \n    COUNT(*) AS total_rows,\n    COUNT(emp_id) AS non_null_count,\n    COUNT(DISTINCT dept) AS distinct_depts,\n    SUM(salary) AS total_payroll,\n    AVG(salary) AS avg_salary,\n    MIN(salary) AS min_salary,\n    MAX(salary) AS max_salary\nFROM employees;\n",
            "Comprehensive summary of all standard relational aggregate functions: COUNT(*), SUM(salary), AVG(salary), MIN, and MAX.", "ANSI");

        add("1. DQL — Data Query Language", "Aggregation & Grouping (GROUP BY, HAVING)",
            "SELECT dept, COUNT(*) AS emp_count, AVG(salary) AS avg_sal, SUM(salary) AS total_sal, MIN(salary) AS min_sal, MAX(salary) AS max_sal\nFROM employees\nGROUP BY dept\nHAVING AVG(salary) > 50000;\n",
            "Aggregate metrics grouped by categorical attributes with post-aggregation filtering.", "ANSI");

        add("1. DQL — Data Query Language", "Joins (INNER, LEFT, RIGHT, FULL, CROSS, SELF)",
            "-- INNER JOIN\nSELECT e.name, d.dept_name\nFROM employees e\nINNER JOIN departments d ON e.dept_id = d.id;\n\n-- LEFT JOIN\nSELECT e.name, d.dept_name\nFROM employees e\nLEFT JOIN departments d ON e.dept_id = d.id;\n\n-- RIGHT JOIN\nSELECT e.name, d.dept_name\nFROM employees e\nRIGHT JOIN departments d ON e.dept_id = d.id;\n\n-- FULL OUTER JOIN\nSELECT e.name, d.dept_name\nFROM employees e\nFULL OUTER JOIN departments d ON e.dept_id = d.id;\n\n-- CROSS JOIN\nSELECT * FROM employees CROSS JOIN departments;\n\n-- SELF JOIN\nSELECT e.name AS employee, m.name AS manager\nFROM employees e\nLEFT JOIN employees m ON e.manager_id = m.id;\n",
            "All fundamental relational join variations.", "ANSI");

        add("1. DQL — Data Query Language", "Subqueries (Scalar, Correlated, IN, EXISTS)",
            "-- Scalar subquery\nSELECT name FROM employees WHERE salary = (SELECT MAX(salary) FROM employees);\n\n-- Correlated subquery\nSELECT name FROM employees e\nWHERE salary > (SELECT AVG(salary) FROM employees WHERE dept_id = e.dept_id);\n\n-- IN / EXISTS\nSELECT * FROM employees WHERE dept_id IN (SELECT id FROM departments WHERE location = 'NYC');\nSELECT * FROM employees e WHERE EXISTS (SELECT 1 FROM projects p WHERE p.emp_id = e.id);\n",
            "Nested scalar and correlated subqueries with existence checks.", "ANSI");

        add("1. DQL — Data Query Language", "Set Operations (UNION, INTERSECT, EXCEPT)",
            "SELECT name FROM employees\nUNION\nSELECT name FROM contractors;\n\nSELECT name FROM employees\nUNION ALL\nSELECT name FROM contractors;\n\nSELECT name FROM employees\nINTERSECT\nSELECT name FROM contractors;\n\nSELECT name FROM employees\nEXCEPT\nSELECT name FROM contractors;\n",
            "Set algebra operators across query results.", "ANSI");

        add("2. DML — Data Manipulation Language", "INSERT Statements",
            "INSERT INTO employees (name, dept, salary) VALUES ('Alice', 'IT', 75000);\nINSERT INTO employees (name, dept) SELECT name, dept FROM new_hires;\nINSERT INTO employees (id, name) VALUES (1, 'A'), (2, 'B'), (3, 'C');\n",
            "Single, batch, and query-driven record insertion.", "ANSI");

        add("2. DML — Data Manipulation Language", "UPDATE Statements",
            "UPDATE employees SET salary = salary * 1.10 WHERE dept = 'IT';\nUPDATE employees e\nSET salary = (SELECT AVG(salary) FROM employees WHERE dept_id = e.dept_id);\n",
            "Direct and correlated row updates.", "ANSI");

        add("2. DML — Data Manipulation Language", "DELETE Statements",
            "DELETE FROM employees WHERE hire_date < '2010-01-01';\nDELETE FROM employees WHERE dept_id IN (SELECT id FROM departments WHERE closed = 1);\n",
            "Conditional record deletion.", "ANSI");

        add("2. DML — Data Manipulation Language", "MERGE & UPSERT Variants",
            "-- PostgreSQL Upsert\nINSERT INTO employees (id, name, salary) VALUES (1, 'Alice', 80000)\nON CONFLICT (id) DO UPDATE SET salary = EXCLUDED.salary;\n\n-- MySQL Upsert\nINSERT INTO employees (id, name, salary) VALUES (1, 'Alice', 80000)\nON DUPLICATE KEY UPDATE salary = VALUES(salary);\n\n-- ANSI MERGE\nMERGE INTO target t\nUSING source s ON t.id = s.id\nWHEN MATCHED THEN UPDATE SET t.val = s.val\nWHEN NOT MATCHED THEN INSERT (id, val) VALUES (s.id, s.val);\n",
            "Cross-engine upsert and merge logic.", "Multi-Dialect");

        add("3. DDL — Data Definition Language", "CREATE TABLE & Constraints",
            "CREATE TABLE employees (\n  id SERIAL PRIMARY KEY,\n  name VARCHAR(100) NOT NULL,\n  email VARCHAR(150) UNIQUE,\n  dept_id INT,\n  salary DECIMAL(10,2) DEFAULT 0,\n  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n  FOREIGN KEY (dept_id) REFERENCES departments(id) ON DELETE SET NULL\n);\n",
            "Table definition with primary keys, foreign keys, and defaults.", "ANSI / PostgreSQL");

        add("3. DDL — Data Definition Language", "CREATE VIEW & MATERIALIZED VIEW",
            "CREATE VIEW high_earners AS\nSELECT id, name, salary FROM employees WHERE salary > 100000;\n\nCREATE MATERIALIZED VIEW dept_stats AS\nSELECT dept_id, COUNT(*) cnt, AVG(salary) avg_sal FROM employees GROUP BY dept_id;\n",
            "Virtual and materialized views.", "ANSI / PostgreSQL");

        add("3. DDL — Data Definition Language", "ALTER TABLE Operations",
            "ALTER TABLE employees ADD COLUMN phone VARCHAR(20);\nALTER TABLE employees DROP COLUMN phone;\nALTER TABLE employees ALTER COLUMN name TYPE VARCHAR(200);\nALTER TABLE employees RENAME TO staff;\nALTER TABLE employees ADD CONSTRAINT fk_dept FOREIGN KEY (dept_id) REFERENCES departments(id);\nALTER TABLE employees DROP CONSTRAINT fk_dept;\n",
            "Schema mutations and constraint additions.", "ANSI");

        add("4. DCL & TCL — Control & Transactions", "GRANT & REVOKE Privileges",
            "GRANT SELECT, INSERT ON employees TO alice;\nGRANT ALL PRIVILEGES ON DATABASE company TO admin_user;\nREVOKE INSERT ON employees FROM alice;\nCREATE ROLE analyst;\nGRANT SELECT ON employees TO analyst;\n",
            "Role and privilege management.", "ANSI / PostgreSQL");

        add("4. DCL & TCL — Control & Transactions", "Transaction Block & Savepoints",
            "BEGIN;\nUPDATE accounts SET balance = balance - 100 WHERE id = 1;\nSAVEPOINT sp1;\nUPDATE accounts SET balance = balance + 100 WHERE id = 2;\n-- If error: ROLLBACK TO sp1;\nCOMMIT;\n",
            "Atomic transaction demarcation and savepoint recovery.", "ANSI");

        // ==========================================
        // 1. THE SELECT MACHINERY
        // ==========================================
        add("🔥 1. The SELECT Machinery", "LATERAL / CROSS APPLY (Correlated Joins)",
            "-- LATERAL / CROSS APPLY (row-by-row correlated joins)\nSELECT e.name, t.top_salary\nFROM employees e\nCROSS JOIN LATERAL (\n  SELECT salary AS top_salary FROM salaries s\n  WHERE s.emp_id = e.id ORDER BY salary DESC LIMIT 1\n) t;\n",
            "Evaluate a subquery for each row of the outer table. Similar to a parameterized for-loop in SQL.", "PostgreSQL / ANSI");

        add("🔥 1. The SELECT Machinery", "TABLESAMPLE (Bernoulli & System)",
            "-- TABLESAMPLE (Bernoulli / System random sampling)\nSELECT * FROM employees TABLESAMPLE BERNOULLI (10);\n-- BERNOULLI samples row-by-row (~10%); SYSTEM samples disk pages for speed.\n",
            "Sample a percentage of table rows efficiently without scanning the entire table.", "PostgreSQL / ANSI / SQL Server");

        add("🔥 1. The SELECT Machinery", "FOR UPDATE SKIP LOCKED (Distributed Job Queues)",
            "-- FOR UPDATE / SKIP LOCKED (distributed job queue lock)\nSELECT * FROM jobs WHERE status = 'pending'\nORDER BY created_at\nLIMIT 1\nFOR UPDATE SKIP LOCKED;\n",
            "Acquire row locks while skipping already locked rows. Essential for multi-worker background job queues.", "PostgreSQL / MySQL 8 / Oracle");

        add("🔥 1. The SELECT Machinery", "Named WINDOW Clause",
            "-- Named WINDOW clause (avoids repeating window specifications)\nSELECT dept, salary,\n  RANK() OVER w AS rnk,\n  AVG(salary) OVER w AS dept_avg\nFROM employees\nWINDOW w AS (PARTITION BY dept ORDER BY salary);\n",
            "Define reusable window specifications once at the end of the query to eliminate redundancy.", "ANSI / PostgreSQL / MySQL 8");

        add("🔥 1. The SELECT Machinery", "FILTER Clause on Aggregates",
            "-- FILTER clause (cleaner, standard alternative to CASE WHEN inside aggregates)\nSELECT COUNT(*) FILTER (WHERE salary > 100000) AS rich,\n       COUNT(*) FILTER (WHERE salary <= 100000) AS poor,\n       AVG(salary) FILTER (WHERE dept = 'IT') AS it_avg\nFROM employees;\n",
            "Standard SQL syntax for conditional aggregation without nested CASE expressions.", "PostgreSQL / SQLite 3.30+");

        add("🔥 1. The SELECT Machinery", "DISTINCT ON (PostgreSQL First-per-Group)",
            "-- DISTINCT ON (Postgres specific: keeps only the first row per group)\nSELECT DISTINCT ON (dept_id) *\nFROM employees\nORDER BY dept_id, salary DESC;\n",
            "Returns the single top row per partition according to the ORDER BY expression.", "PostgreSQL");

        // ==========================================
        // 2. FRAME-AWARE WINDOW FUNCTIONS
        // ==========================================
        add("🔥 2. Frame-Aware Window Functions", "Running Total with Explicit Frame",
            "-- Running total with explicit ROWS frame\nSELECT id, hire_date, salary,\n  SUM(salary) OVER (ORDER BY hire_date\n                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total\nFROM employees;\n",
            "Explicit frame prevents range-based grouping of duplicate order keys and ensures strict row accumulation.", "ANSI Standard");

        add("🔥 2. Frame-Aware Window Functions", "Moving Average (Last 7 Rows)",
            "-- Moving average (last 7 rows: 6 preceding + current)\nSELECT day, salary,\n  AVG(salary) OVER (ORDER BY day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS moving_avg_7d\nFROM daily_metrics;\n",
            "Computes rolling window metrics over a fixed physical number of rows.", "ANSI Standard");

        add("🔥 2. Frame-Aware Window Functions", "Centered Window (Preceding & Following)",
            "-- Centered window (3 preceding and 3 following rows = 7 row window)\nSELECT d, price,\n  AVG(price) OVER (ORDER BY d ROWS BETWEEN 3 PRECEDING AND 3 FOLLOWING) AS centered_avg\nFROM stock_prices;\n",
            "Smooths time-series data using both historical and future surrounding rows.", "ANSI Standard");

        add("🔥 2. Frame-Aware Window Functions", "Default vs Explicit RANGE (Tie Handling)",
            "-- Default RANGE vs explicit ROWS\n-- Note: RANGE treats ties as one collective group; ROWS distinguishes physical rows!\nSELECT d, x,\n  SUM(x) OVER (ORDER BY d RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS range_sum,\n  SUM(x) OVER (ORDER BY d ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS rows_sum\nFROM measurements;\n",
            "Demonstrates the critical difference between logical RANGE and physical ROWS framing when ties exist.", "ANSI Standard");

        add("🔥 2. Frame-Aware Window Functions", "Named Frames + EXCLUDE CURRENT ROW",
            "-- Named frames + EXCLUDE CURRENT ROW\nSELECT d, x,\n  SUM(x) OVER (ORDER BY d ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING EXCLUDE CURRENT ROW) AS peer_sum\nFROM readings;\n",
            "Excludes the current row from the frame computation, calculating metrics purely on neighboring rows.", "PostgreSQL 11+ / SQLite");

        add("🔥 2. Frame-Aware Window Functions", "NTILE, FIRST_VALUE, LAST_VALUE, NTH_VALUE",
            "-- Advanced Window Navigation\nSELECT dept, name, salary,\n  NTILE(4) OVER (ORDER BY salary) AS quartile,\n  FIRST_VALUE(salary) OVER (PARTITION BY dept ORDER BY salary DESC) AS top_sal,\n  LAST_VALUE(salary) OVER (PARTITION BY dept ORDER BY salary DESC\n                          ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS lowest_sal,\n  NTH_VALUE(salary, 2) OVER (PARTITION BY dept ORDER BY salary DESC\n                            RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS second_highest\nFROM employees;\n",
            "Quartile division and absolute positional value extraction within partitions.", "ANSI Standard");

        // ==========================================
        // 3. RECURSIVE CTES IN PRODUCTION
        // ==========================================
        add("🔥 3. Recursive CTEs in Production", "Graph Traversal with Cycle Detection",
            "-- Graph traversal with cycle detection\nWITH RECURSIVE reach AS (\n  SELECT src, dst, ARRAY[src] AS path FROM edges WHERE src = 'A'\n  UNION ALL\n  SELECT e.src, e.dst, path || e.src\n  FROM edges e JOIN reach r ON e.src = r.dst\n  WHERE NOT e.src = ANY(path)     -- cycle guard\n)\nSELECT * FROM reach;\n",
            "Traverse arbitrary directed cyclic graphs safely using an accumulated array path guard.", "PostgreSQL");

        add("🔥 3. Recursive CTEs in Production", "Date Series Generation (No Calendar Table)",
            "-- Generate a date series without a physical calendar table\nWITH RECURSIVE dates(d) AS (\n  SELECT DATE '2024-01-01'\n  UNION ALL\n  SELECT d + INTERVAL '1 day' FROM dates WHERE d < DATE '2024-12-31'\n)\nSELECT * FROM dates;\n",
            "Generates continuous date intervals on the fly for reporting, left joins, and gap filling.", "ANSI / PostgreSQL");

        add("🔥 3. Recursive CTEs in Production", "Hierarchical Org-Chart with Path & Depth",
            "-- Hierarchical Org-Chart with Depth and Breadth Path\nWITH RECURSIVE org_chart AS (\n  SELECT id, name, manager_id, 1 AS depth, CAST(name AS VARCHAR(255)) AS path\n  FROM employees WHERE manager_id IS NULL\n  UNION ALL\n  SELECT e.id, e.name, e.manager_id, oc.depth + 1, CAST(oc.path || ' -> ' || e.name AS VARCHAR(255))\n  FROM employees e JOIN org_chart oc ON e.manager_id = oc.id\n)\nSELECT * FROM org_chart ORDER BY depth, path;\n",
            "Parent-child tree expansion with visual breadcrumbs and tree depth level.", "ANSI Standard");

        // ==========================================
        // 4. PRODUCTION CONCURRENCY & CONTROL
        // ==========================================
        add("🔥 4. Production Concurrency & Control", "Transaction Isolation Levels & Anomalies",
            "-- ISOLATION LEVELS (know the anomalies)\nSET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;  -- dirty reads permitted\nSET TRANSACTION ISOLATION LEVEL READ COMMITTED;    -- non-repeatable reads possible\nSET TRANSACTION ISOLATION LEVEL REPEATABLE READ;   -- phantom reads guarded (PostgreSQL eliminates phantoms here)\nSET TRANSACTION ISOLATION LEVEL SERIALIZABLE;      -- full Serializable Snapshot Isolation (SSI)\n",
            "Controls concurrency anomalies: dirty read, non-repeatable read, phantom read, and serialization failure.", "ANSI / PostgreSQL");

        add("🔥 4. Production Concurrency & Control", "PostgreSQL Advisory Locks",
            "-- ADVISORY LOCKS (Application-level distributed locks in Postgres)\nSELECT pg_advisory_lock(42);          -- Session-level blocking lock\nSELECT pg_try_advisory_xact_lock(42); -- Transaction-level non-blocking attempt (returns bool)\nSELECT pg_advisory_unlock(42);        -- Release session lock\n",
            "User-space locks coordinated directly via the database engine without locking table rows.", "PostgreSQL");

        add("🔥 4. Production Concurrency & Control", "Distributed Job Queue with SKIP LOCKED",
            "-- Distributed Job Queue worker pattern\nBEGIN;\nSELECT id, payload\nFROM job_queue\nWHERE status = 'queued'\nORDER BY priority DESC, created_at ASC\nLIMIT 1\nFOR UPDATE SKIP LOCKED;\n\n-- Mark job as processing\nUPDATE job_queue SET status = 'processing', started_at = CURRENT_TIMESTAMP WHERE id = 123;\nCOMMIT;\n",
            "Guarantees that multiple concurrent workers never process the same job or block on each other.", "PostgreSQL / MySQL 8");

        add("🔥 4. Production Concurrency & Control", "LISTEN / NOTIFY (Pub-Sub Messaging)",
            "-- LISTEN / NOTIFY (Postgres pub-sub mechanism)\n-- In subscriber connection:\nLISTEN orders_changed;\n\n-- In publisher connection or trigger:\nNOTIFY orders_changed, 'order 123 updated';\n-- Or with dynamic JSON payload:\nSELECT pg_notify('orders_changed', json_build_object('order_id', 123, 'status', 'shipped')::text);\n",
            "Low-overhead asynchronous inter-process pub/sub without external message brokers.", "PostgreSQL");

        add("🔥 4. Production Concurrency & Control", "Concurrent Materialized View Refresh",
            "-- Refresh Materialized View Concurrently without locking concurrent readers\n-- Prerequisite: A unique index must exist on the materialized view\nCREATE UNIQUE INDEX IF NOT EXISTS idx_dept_stats_dept ON dept_stats (dept_id);\nREFRESH MATERIALIZED VIEW CONCURRENTLY dept_stats;\n",
            "Updates materialized view contents in place without exclusive read locks.", "PostgreSQL");

        // ==========================================
        // 5. ENGINE-SPECIFIC POWER FEATURES
        // ==========================================
        add("🔥 5. Engine-Specific Features", "PostgreSQL: JSONB, Path Queries & Operators",
            "-- PostgreSQL JSONB Extraction & Path Queries\nSELECT id,\n       data->'user'->>'email' AS email,\n       jsonb_path_query(data, '$.items[*] ? (@.price > 100)') AS expensive_items\nFROM events\nWHERE data @> '{\"status\": \"active\"}';\n",
            "Fast binary JSON querying, indexing, and jsonpath expressions in PostgreSQL.", "PostgreSQL");

        add("🔥 5. Engine-Specific Features", "PostgreSQL: Arrays, Unnest & Full-Text Search",
            "-- PostgreSQL Arrays & Unnest\nSELECT id, unnest(tags) AS tag FROM posts WHERE 'sql' = ANY(tags);\n\n-- PostgreSQL Full-Text Search (tsvector, tsquery, @@)\nSELECT title, ts_rank(to_tsvector('english', body), to_tsquery('english', 'database & performance')) AS rank\nFROM articles\nWHERE to_tsvector('english', body) @@ to_tsquery('english', 'database & performance')\nORDER BY rank DESC;\n",
            "Native array expansion and full-text ranked retrieval.", "PostgreSQL");

        add("🔥 5. Engine-Specific Features", "MySQL: MATCH ... AGAINST & JSON_TABLE",
            "-- MySQL Full-Text Search\nSELECT id, title, MATCH(title, description) AGAINST('database performance' IN NATURAL LANGUAGE MODE) AS score\nFROM products\nWHERE MATCH(title, description) AGAINST('database performance' IN NATURAL LANGUAGE MODE);\n\n-- MySQL 8 JSON_TABLE (convert JSON array to relational rows)\nSELECT j.*\nFROM orders,\nJSON_TABLE(orders.items_json, '$[*]' COLUMNS (\n    item_id INT PATH '$.id',\n    item_name VARCHAR(100) PATH '$.name',\n    price DECIMAL(10,2) PATH '$.price'\n)) AS j;\n",
            "MySQL full-text indexing and JSON relational projection.", "MySQL 8");

        add("🔥 5. Engine-Specific Features", "Oracle: CONNECT BY, MODEL Clause & PIVOT",
            "-- Oracle CONNECT BY PRIOR\nSELECT LEVEL, id, name, manager_id, SYS_CONNECT_BY_PATH(name, '/') AS path\nFROM employees\nSTART WITH manager_id IS NULL\nCONNECT BY PRIOR id = manager_id;\n\n-- Oracle PIVOT\nSELECT * FROM (SELECT dept_id, job, salary FROM employees)\nPIVOT (SUM(salary) FOR job IN ('CLERK' AS clerks, 'MANAGER' AS mgrs, 'ANALYST' AS analysts));\n\n-- Oracle MODEL Clause (Spreadsheet calculations in SQL)\nSELECT * FROM sales_fact\nMODEL\n  PARTITION BY (region)\n  DIMENSION BY (year)\n  MEASURES (sales)\n  RULES (\n    sales[2025] = sales[2024] * 1.15\n  );\n",
            "Oracle-native hierarchical walking, spreadsheet modeling, and pivoting.", "Oracle");

        add("🔥 5. Engine-Specific Features", "SQL Server: CROSS APPLY, OPENJSON & HIERARCHYID",
            "-- SQL Server CROSS APPLY\nSELECT e.name, top_s.salary\nFROM employees e\nCROSS APPLY (\n    SELECT TOP 1 salary FROM salary_history s\n    WHERE s.emp_id = e.id ORDER BY change_date DESC\n) top_s;\n\n-- SQL Server OPENJSON\nSELECT * FROM OPENJSON('{\"name\": \"John\", \"skills\": [\"SQL\", \"Java\"]}')\nWITH (name VARCHAR(50) '$.name', skills NVARCHAR(MAX) '$.skills' AS JSON);\n\n-- SQL Server HIERARCHYID\nSELECT org_node.ToString() AS path, name, org_node.GetLevel() AS lvl\nFROM org_chart\nORDER BY org_node;\n",
            "T-SQL row correlation, JSON parsing, and native hierarchy tree data types.", "SQL Server");

        add("🔥 5. Engine-Specific Features", "Snowflake & BigQuery: QUALIFY, FLATTEN & UNNEST",
            "-- Snowflake & BigQuery QUALIFY clause (filter window functions directly!)\nSELECT dept, name, salary,\n       ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn\nFROM employees\nQUALIFY rn <= 3;\n\n-- Snowflake FLATTEN\nSELECT id, f.value::string AS tag\nFROM posts, LATERAL FLATTEN(input => tags_array) f;\n\n-- BigQuery UNNEST structs/arrays\nSELECT user_id, item.name, item.price\nFROM orders, UNNEST(items) AS item;\n",
            "Cloud warehouse optimizations: QUALIFY avoids nested CTEs; FLATTEN/UNNEST decomposes arrays.", "Snowflake / BigQuery");

        // ==========================================
        // 6. INDEX ARCHITECTURES
        // ==========================================
        add("🔥 6. Index Architectures", "GIN & BRIN Indexes",
            "-- GIN Index (Generalized Inverted Index for Full-Text & JSONB)\nCREATE INDEX idx_docs_gin ON docs USING GIN (to_tsvector('english', content));\nCREATE INDEX idx_payload_gin ON events USING GIN (payload jsonb_path_ops);\n\n-- BRIN Index (Block Range Index for massive append-only/time-series tables)\nCREATE INDEX idx_logs_brin ON logs USING BRIN (created_at) WITH (pages_per_range = 128);\n",
            "GIN accelerates containment queries and full text; BRIN consumes <1% the RAM of B-Trees for sorted series.", "PostgreSQL");

        add("🔥 6. Index Architectures", "Partial & Expression Indexes",
            "-- Partial Index (indexes only active rows, saves 90%+ index space and write overhead)\nCREATE INDEX idx_orders_open ON orders (created_at) WHERE status = 'open';\n\n-- Expression / Functional Index (accelerates deterministic function evaluation)\nCREATE INDEX idx_users_lower_email ON users (LOWER(email));\n",
            "Conditional index filters and functional column transformations.", "PostgreSQL / SQLite");

        add("🔥 6. Index Architectures", "Covering Indexes (INCLUDE) & Composite Ordering",
            "-- Covering Index (Index-Only Scan: payload columns stored in leaf pages without heap lookups)\nCREATE INDEX idx_orders_covering ON orders (customer_id) INCLUDE (total_amount, order_date);\n\n-- Composite Index with distinct column sort directions and NULL placement\nCREATE INDEX idx_composite_audit ON audit_records (tenant_id, created_at DESC, sequence_num NULLS LAST);\n",
            "Allows the query optimizer to answer queries exclusively from the index tree without table heap I/O.", "PostgreSQL / SQL Server");

        add("🔥 6. Index Architectures", "Index-Only Scan Proof (EXPLAIN ANALYZE)",
            "-- Verify that table heap scans are bypassed completely\nEXPLAIN (ANALYZE, BUFFERS)\nSELECT customer_id, total_amount FROM orders WHERE customer_id = 42;\n-- Look for: 'Index Only Scan using idx_orders_covering'\n-- Look for: 'Heap Fetches: 0'\n",
            "Execution plan proof showing zero heap fetches and pure index lookups.", "PostgreSQL");

        // ==========================================
        // 7. PLANNER & OPTIMIZATION REALITY
        // ==========================================
        add("🔥 7. Planner & Optimization Reality", "Postgres EXPLAIN (BUFFERS, FORMAT JSON)",
            "-- Comprehensive execution plan inspection with buffer cache hits\nEXPLAIN (ANALYZE, BUFFERS, TIMING, COSTS, FORMAT JSON)\nSELECT e.name, d.dept_name\nFROM employees e JOIN departments d ON e.dept_id = d.id\nWHERE e.salary > 75000;\n",
            "Outputs actual runtime, cost estimates, and Shared Hit/Read buffer metrics for plan analysis.", "PostgreSQL");

        add("🔥 7. Planner & Optimization Reality", "Session Planner Knobs & Memory Tuning",
            "-- Temporarily disable planner scan mechanisms for troubleshooting\nSET enable_seqscan = off;   -- discourage sequential table scans\nSET enable_nestloop = off;  -- discourage nested loop joins\nSET work_mem = '256MB';     -- allocate more RAM for hashes/sorts before spilling to disk temp files\n",
            "Tune memory and inspect alternative query plans without altering server-wide configuration.", "PostgreSQL");

        add("🔥 7. Planner & Optimization Reality", "Optimizer Hints across Engines",
            "-- Oracle Optimizer Hint\nSELECT /*+ INDEX(emp idx_dept) LEADING(emp dept) USE_NL(dept) */ *\nFROM employees emp JOIN departments dept ON emp.dept_id = dept.id\nWHERE emp.dept = 'IT';\n\n-- SQL Server Table Hint\nSELECT * FROM employees WITH (INDEX(idx_dept), NOLOCK)\nWHERE dept = 'IT';\n\n-- MySQL Index Hint\nSELECT * FROM employees USE INDEX (idx_dept)\nWHERE dept = 'IT';\n",
            "Force specific index paths and join algorithms across Oracle, SQL Server, and MySQL.", "Multi-Engine");

        // ==========================================
        // 8. SET-THEORETIC & ANALYTIC MONSTERS
        // ==========================================
        add("🔥 8. Set-Theoretic & Analytic Monsters", "Gaps and Islands (Contiguous Grouping)",
            "-- Gaps and Islands: Group consecutive login days into unbroken streaks\nWITH numbered AS (\n  SELECT day,\n         day - CAST(ROW_NUMBER() OVER (ORDER BY day) AS INT) * INTERVAL '1 day' AS grp\n  FROM login_days\n)\nSELECT MIN(day) AS streak_start, MAX(day) AS streak_end, COUNT(*) AS streak_length\nFROM numbered\nGROUP BY grp\nORDER BY streak_start;\n",
            "The classic difference-of-row-numbers technique to group adjacent sequences.", "ANSI Standard");

        add("🔥 8. Set-Theoretic & Analytic Monsters", "Deduplication Keeping Latest Record",
            "-- Deduplication: Delete older duplicate rows while preserving the newest record\nDELETE FROM employees e\nUSING employees e2\nWHERE e.id = e2.id\n  AND e.updated_at < e2.updated_at;\n",
            "Clean duplicates efficiently using self-referencing delete joins.", "PostgreSQL");

        add("🔥 8. Set-Theoretic & Analytic Monsters", "Sessionization (30-Minute Inactivity Window)",
            "-- Sessionization: Group event clicks into sessions divided by 30-min idle gaps\nWITH event_gaps AS (\n  SELECT user_id, ts,\n         ts - LAG(ts) OVER (PARTITION BY user_id ORDER BY ts) AS gap\n  FROM user_events\n),\nsession_starts AS (\n  SELECT user_id, ts,\n         CASE WHEN gap > INTERVAL '30 min' OR gap IS NULL THEN 1 ELSE 0 END AS is_new_session\n  FROM event_gaps\n)\nSELECT user_id, ts,\n       SUM(is_new_session) OVER (PARTITION BY user_id ORDER BY ts) AS session_id\nFROM session_starts;\n",
            "Reconstructs user browsing sessions based on inactivity thresholds using LAG and cumulative SUM.", "ANSI Standard");

        add("🔥 8. Set-Theoretic & Analytic Monsters", "Exact Median & Percentiles",
            "-- Exact Median and Percentiles using Inverse Distribution functions\nSELECT\n  PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY salary) AS median_salary_continuous,\n  PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY salary) AS median_salary_discrete,\n  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY salary) AS p95_salary\nFROM employees;\n",
            "Calculates continuous and discrete median percentiles mathematically without approximations.", "ANSI / PostgreSQL / Oracle");

        add("🔥 8. Set-Theoretic & Analytic Monsters", "Top-N Per Group (3 Distinct Approaches)",
            "-- Approach 1: Standard ROW_NUMBER() subquery (fastest, most portable)\nSELECT * FROM (\n  SELECT *, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn\n  FROM employees\n) t WHERE rn <= 3;\n\n-- Approach 2: Correlated Subquery COUNT (works in older engines without window functions)\nSELECT * FROM employees e\nWHERE (\n  SELECT COUNT(*) FROM employees e2\n  WHERE e2.dept = e.dept AND e2.salary > e.salary\n) < 3;\n\n-- Approach 3: DENSE_RANK() (preserves ties for leaderboard scenarios)\nSELECT * FROM (\n  SELECT *, DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS drk\n  FROM employees\n) t WHERE drk <= 3;\n",
            "Three industry-standard ways to solve Top-N per category with trade-off considerations.", "ANSI Standard");

        // ==========================================
        // 9. SECURITY & CORRECTNESS
        // ==========================================
        add("🔥 9. Security & Correctness", "Row-Level Security (PostgreSQL RLS)",
            "-- Row-Level Security (Multi-Tenant isolation at database engine level)\nALTER TABLE orders ENABLE ROW LEVEL SECURITY;\n\nCREATE POLICY user_orders_policy ON orders\n  FOR ALL\n  TO application_user\n  USING (customer_id = current_setting('app.current_user_id')::int)\n  WITH CHECK (customer_id = current_setting('app.current_user_id')::int);\n",
            "Enforces database-level tenant isolation preventing accidental cross-tenant data leaks.", "PostgreSQL");

        add("🔥 9. Security & Correctness", "Column-Level Privileges",
            "-- Fine-grained Column-Level Grants\nGRANT SELECT (id, name, department), UPDATE (department) ON employees TO hr_clerk;\nREVOKE ALL PRIVILEGES ON TABLE employees FROM hr_clerk;\n",
            "Restricts sensitive columns like salaries, SSNs, or hashed passwords from standard roles.", "ANSI / PostgreSQL");

        add("🔥 9. Security & Correctness", "Parameterized Prepared Statements",
            "-- Parameterized Execution Guard against SQL Injection\nPREPARE find_user_by_email (TEXT) AS\n  SELECT id, username, email, created_at FROM users WHERE email = $1;\n\nEXECUTE find_user_by_email('alice@example.com');\nDEALLOCATE find_user_by_email;\n",
            "Separates query parsing from untrusted input values to eliminate injection risks.", "PostgreSQL / ANSI");

        add("🔥 9. Security & Correctness", "Safe Dynamic SQL (format %I in PL/pgSQL)",
            "-- Safe Dynamic SQL preventing SQL injection on identifiers\nDO $$\nDECLARE\n  tbl TEXT := 'audit_log';\n  row_id INT := 42;\nBEGIN\n  -- %I quotes identifier safely; %s quotes string; %L quotes literal\n  EXECUTE format('SELECT * FROM %I WHERE id = $1', tbl) USING row_id;\nEND $$;\n",
            "Safe dynamic construction using %I identifier escaping and parameterized USING clauses.", "PostgreSQL");

        // ==========================================
        // 10. BEYOND STANDARD SQL
        // ==========================================
        add("🔥 10. Beyond Standard SQL", "Procedural Dialects (PL/pgSQL Loop & Cursor)",
            "-- PL/pgSQL Stored Procedure with Record Cursor\nCREATE OR REPLACE FUNCTION process_salary_hikes(dept_name TEXT, hike_pct NUMERIC)\nRETURNS INT LANGUAGE plpgsql AS $$\nDECLARE\n  v_count INT := 0;\n  r RECORD;\nBEGIN\n  FOR r IN SELECT id, salary FROM employees WHERE dept = dept_name LOOP\n    UPDATE employees SET salary = salary * (1 + hike_pct/100) WHERE id = r.id;\n    v_count := v_count + 1;\n  END LOOP;\n  RETURN v_count;\nEND;\n$$;\n\nSELECT process_salary_hikes('IT', 10);\n",
            "Procedural control-flow, loops, and cursors inside stored server-side routines.", "PostgreSQL");

        add("🔥 10. Beyond Standard SQL", "SQL/JSON Path Expressions",
            "-- SQL/JSON Path expressions (JSON query standard)\nSELECT jsonb_path_query(data, '$.store.book[*] ? (@.price < 20).title') AS affordable_titles\nFROM inventory;\n\nSELECT jsonb_path_exists(data, '$.store.book[*] ? (@.author == \"Knuth\")') AS has_knuth\nFROM inventory;\n",
            "Standard JSON path predicate filtering over semi-structured documents.", "PostgreSQL 12+ / Oracle / MySQL 8");

        add("🔥 10. Beyond Standard SQL", "SQL/MED: Foreign Data Wrappers (FDW)",
            "-- SQL/MED: Foreign Data Wrappers (Querying remote foreign databases directly)\nCREATE EXTENSION IF NOT EXISTS postgres_fdw;\n\nCREATE SERVER remote_billing_server\n  FOREIGN DATA WRAPPER postgres_fdw\n  OPTIONS (host 'billing-db.internal', port '5432', dbname 'billing');\n\nCREATE USER MAPPING FOR current_user\n  SERVER remote_billing_server\n  OPTIONS (user 'billing_reader', password 'secret');\n\nCREATE FOREIGN TABLE remote_invoices (\n  invoice_id INT,\n  client_id INT,\n  amount DECIMAL(10,2)\n) SERVER remote_billing_server OPTIONS (table_name 'invoices');\n\nSELECT * FROM remote_invoices WHERE amount > 5000;\n",
            "Query tables across different physical databases seamlessly as if they were local tables.", "PostgreSQL (SQL/MED Standard)");

        add("🔥 10. Beyond Standard SQL", "SQL:2016 MATCH_RECOGNIZE (Stream Pattern Matching)",
            "-- SQL:2016 Row Pattern Recognition (Oracle, Snowflake)\n-- Detect V-shaped price recoveries (downward run followed by an upward rebound)\nSELECT *\nFROM stock_prices\nMATCH_RECOGNIZE (\n  PARTITION BY symbol\n  ORDER BY trade_date\n  MEASURES\n    START_ROW.trade_date AS start_date,\n    BOTTOM.trade_date AS dip_date,\n    FINAL.trade_date AS recovery_date\n  ONE ROW PER MATCH\n  PATTERN (START_ROW DOWN+ UP+)\n  DEFINE\n    DOWN AS DOWN.price < PREV(DOWN.price),\n    UP AS UP.price > PREV(UP.price)\n);\n",
            "Complex event processing and regular expression pattern matching across ordered rows.", "Oracle / Snowflake");

        add("🔥 10. Beyond Standard SQL", "SQL/PGQ (Property Graph Queries / SQL:2023)",
            "-- SQL/PGQ Property Graph Query Pattern (SQL:2023 Standard)\nSELECT graph_summary.*\nFROM GRAPH_TABLE (company_graph\n  MATCH (src:Person WHERE src.name = 'Alice') -[e:KNOWS]-> (friend:Person) -[:WORKS_AT]-> (c:Company)\n  COLUMNS (src.name AS person, friend.name AS friend_name, c.name AS company_name)\n) AS graph_summary;\n",
            "Native graph pattern matching directly within relational SQL queries.", "SQL:2023 / PostgreSQL 16+");
    }

    private static void add(String category, String title, String sql, String description, String dialect) {
        Snippet s = new Snippet(category, title, sql, description, dialect);
        ALL_SNIPPETS.add(s);
        CATEGORY_MAP.computeIfAbsent(category, k -> new ArrayList<>()).add(s);
    }

    public static List<Snippet> getAllSnippets() {
        return Collections.unmodifiableList(ALL_SNIPPETS);
    }

    public static Map<String, List<Snippet>> getCategoryMap() {
        return Collections.unmodifiableMap(CATEGORY_MAP);
    }

    public static List<Snippet> search(String query) {
        if (query == null || query.trim().isEmpty()) return getAllSnippets();
        String q = query.toLowerCase();
        List<Snippet> results = new ArrayList<>();
        for (Snippet s : ALL_SNIPPETS) {
            if (s.getTitle().toLowerCase().contains(q) ||
                s.getCategory().toLowerCase().contains(q) ||
                s.getSql().toLowerCase().contains(q) ||
                s.getDescription().toLowerCase().contains(q) ||
                (s.getDialect() != null && s.getDialect().toLowerCase().contains(q))) {
                results.add(s);
            }
        }
        return results;
    }

    /**
     * Builds a comprehensive JPopupMenu with categorized menus and submenus.
     */
    public static void populatePopupMenu(JPopupMenu menu, Consumer<String> insertAction) {
        populatePopupMenu(menu, insertAction, null);
    }

    public static void populatePopupMenu(JPopupMenu menu, Consumer<String> insertAction, Runnable openCheatSheetDialog) {
        if (openCheatSheetDialog != null) {
            JMenuItem cheatSheetItem = new JMenuItem("📚 Open Interactive SQL Mastery Cheat Sheet...");
            cheatSheetItem.setFont(cheatSheetItem.getFont().deriveFont(java.awt.Font.BOLD));
            cheatSheetItem.addActionListener(e -> openCheatSheetDialog.run());
            menu.add(cheatSheetItem);
            menu.addSeparator();
        }

        // Section 1: Core Foundations
        JMenu coreMenu = new JMenu("🌟 Core SQL Foundations (DQL, DML, DDL, DCL, TCL)");
        for (Map.Entry<String, List<Snippet>> entry : CATEGORY_MAP.entrySet()) {
            if (!entry.getKey().startsWith("🔥")) {
                JMenu sub = new JMenu(entry.getKey());
                for (Snippet s : entry.getValue()) {
                    JMenuItem item = new JMenuItem(s.getTitle());
                    item.setToolTipText("<html><b>" + s.getTitle() + "</b><br>" + s.getDescription() + "</html>");
                    item.addActionListener(e -> insertAction.accept(s.getSql()));
                    sub.add(item);
                }
                coreMenu.add(sub);
            }
        }
        menu.add(coreMenu);

        menu.addSeparator();
        JLabel headerLabel = new JLabel("  🔥 ADVANCED SQL MASTERY (THE REAL MAP)");
        headerLabel.setFont(headerLabel.getFont().deriveFont(java.awt.Font.BOLD, 11f));
        headerLabel.setForeground(java.awt.Color.GRAY);
        menu.add(headerLabel);

        // Section 2: Advanced Mastery Categories
        for (Map.Entry<String, List<Snippet>> entry : CATEGORY_MAP.entrySet()) {
            if (entry.getKey().startsWith("🔥")) {
                JMenu catMenu = new JMenu(entry.getKey());
                for (Snippet s : entry.getValue()) {
                    JMenuItem item = new JMenuItem(s.getTitle());
                    item.setToolTipText("<html><b>" + s.getTitle() + "</b> [" + s.getDialect() + "]<br>" + s.getDescription() + "</html>");
                    item.addActionListener(e -> insertAction.accept(s.getSql()));
                    catMenu.add(item);
                }
                menu.add(catMenu);
            }
        }
    }

    /**
     * Builds a JMenu structure with categorized submenus for integration into existing menus.
     */
    public static void populateSubMenu(JMenu menu, Consumer<String> insertAction) {
        JMenu coreMenu = new JMenu("🌟 Core SQL Foundations");
        for (Map.Entry<String, List<Snippet>> entry : CATEGORY_MAP.entrySet()) {
            if (!entry.getKey().startsWith("🔥")) {
                JMenu sub = new JMenu(entry.getKey());
                for (Snippet s : entry.getValue()) {
                    JMenuItem item = new JMenuItem(s.getTitle());
                    item.setToolTipText(s.getDescription());
                    item.addActionListener(e -> insertAction.accept(s.getSql()));
                    sub.add(item);
                }
                coreMenu.add(sub);
            }
        }
        menu.add(coreMenu);
        menu.addSeparator();

        for (Map.Entry<String, List<Snippet>> entry : CATEGORY_MAP.entrySet()) {
            if (entry.getKey().startsWith("🔥")) {
                JMenu catMenu = new JMenu(entry.getKey());
                for (Snippet s : entry.getValue()) {
                    JMenuItem item = new JMenuItem(s.getTitle());
                    item.setToolTipText(s.getDescription());
                    item.addActionListener(e -> insertAction.accept(s.getSql()));
                    catMenu.add(item);
                }
                menu.add(catMenu);
            }
        }
    }
}
