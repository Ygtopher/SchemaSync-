package com.dbtool;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.swing.filechooser.FileSystemView;

public class DatabaseManager {
    private String lastHost, lastPort, lastDbName, lastUser, lastPass;
    private String lastDumpFile;
    private String lastType = "POSTGRESQL";

    public void autoReconnect() throws Exception {
        if ("MYSQL".equalsIgnoreCase(lastType) && lastHost != null) {
            connectMySQL(lastHost, lastPort, lastDbName, lastUser, lastPass);
        } else if ("SQLITE".equalsIgnoreCase(lastType) && (lastDumpFile != null || lastDbName != null)) {
            connectSQLite(lastDumpFile != null ? lastDumpFile : lastDbName);
        } else if (lastHost != null) {
            connectExistingPostgres(lastHost, lastPort, lastDbName, lastUser, lastPass);
        }
    }

    public String getLastType() { return lastType; }
    public String getLastHost() { return lastHost; }
    public String getLastPort() { return lastPort; }
    public String getLastDbName() { return lastDbName; }
    public String getLastUser() { return lastUser; }
    public String getLastPass() { return lastPass; }
    public String getLastDumpFile() { return lastDumpFile; }
    public boolean isSandbox() { return lastDbName != null && lastDbName.startsWith("Sandbox"); }
    public boolean isConnected() { return connection != null; }

    public Connection connection;
    private java.util.Properties learnedJoins = new java.util.Properties();
    private File learnedJoinsFile;
    private java.util.Map<String, java.util.List<String>> columnCache = new java.util.concurrent.ConcurrentHashMap<>();
    void columnCachePutTest(String table, java.util.List<String> cols) { columnCache.put(table, cols); }
    private java.util.Map<String, java.util.Map<String, String[]>> fkCache = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.List<String> tableCache = null;

    private File getSaveFile(String filename) {
        File docsFolder = FileSystemView.getFileSystemView().getDefaultDirectory();
        File savesFolder = new File(docsFolder, "SchemaSyncSaves");
        if (!savesFolder.exists()) {
            savesFolder.mkdirs();
        }
        return new File(savesFolder, filename);
    }

    public DatabaseManager() {
        new Thread(() -> {
            learnedJoinsFile = getSaveFile("learned_joins.properties");
            if (learnedJoinsFile.exists()) {
                try (FileInputStream in = new FileInputStream(learnedJoinsFile)) {
                    learnedJoins.load(in);
                } catch (Exception e) {}
            }
        }).start();
    }


    private volatile long lastValidCheckMs = 0;

    private void ensureConnectionValid() {
        if (connection == null) return;
        long now = System.currentTimeMillis();
        if (now - lastValidCheckMs < 15000) return;
        try {
            if (!connection.isValid(2)) {
                autoReconnect();
            }
            lastValidCheckMs = System.currentTimeMillis();
        } catch (Exception ignored) {}
    }

    public void clearCache() {
        columnCache.clear();
        fkCache.clear();
        tableCache = null;
        lastValidCheckMs = 0;
    }

    public void preloadAllColumns() {
        if (connection == null) return;
        try {
            String currentSchema = null;
            try {
                currentSchema = connection.getSchema();
            } catch (Throwable ignored) {}
            if (currentSchema == null || currentSchema.trim().isEmpty()) {
                currentSchema = "public";
            }

            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    String sch = rs.getString("TABLE_SCHEM");
                    String tbl = rs.getString("TABLE_NAME");
                    String col = rs.getString("COLUMN_NAME");
                    if (tbl != null && col != null && !col.trim().isEmpty()) {
                        String cleanCol = col.trim();
                        // Only cache under unadorned table name if it matches the current active schema or schema is empty/null
                        if (sch == null || sch.isEmpty() || sch.equalsIgnoreCase(currentSchema)) {
                            List<String> list = columnCache.computeIfAbsent(tbl, k -> new ArrayList<>());
                            if (!list.contains(cleanCol)) {
                                list.add(cleanCol);
                            }
                        }
                        if (sch != null && !sch.isEmpty()) {
                            List<String> listWithSchema = columnCache.computeIfAbsent(sch + "." + tbl, k -> new ArrayList<>());
                            if (!listWithSchema.contains(cleanCol)) {
                                listWithSchema.add(cleanCol);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-fatal, getColumnNames will fall back to per-table metadata query
        }
    }

    public void preloadAllForeignKeys() {
        if (connection == null) return;
        try {
            List<String> allTables = getTableNames();
            if (allTables != null) {
                for (String t : allTables) {
                    fkCache.computeIfAbsent(t, k -> new java.util.HashMap<>());
                }
            }
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getImportedKeys(null, null, null)) {
                while (rs.next()) {
                    String fkSchema = rs.getString("FKTABLE_SCHEM");
                    String fkTable = rs.getString("FKTABLE_NAME");
                    String fkCol = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    String pkCol = rs.getString("PKCOLUMN_NAME");
                    if (fkTable != null && fkCol != null && pkTable != null && pkCol != null) {
                        fkCache.computeIfAbsent(fkTable, k -> new java.util.HashMap<>()).put(fkCol, new String[]{pkTable, pkCol});
                        if (fkSchema != null && !fkSchema.equalsIgnoreCase("public") && !fkSchema.isEmpty()) {
                            fkCache.computeIfAbsent(fkSchema + "." + fkTable, k -> new java.util.HashMap<>()).put(fkCol, new String[]{pkTable, pkCol});
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-fatal, getForeignKeys will fall back to per-table query if needed
        }
    }

    public java.util.Map<String, String[]> getCachedForeignKeys(String table) {
        if (table == null) return java.util.Collections.emptyMap();
        java.util.Map<String, String[]> cached = fkCache.get(table);
        return cached != null ? cached : java.util.Collections.emptyMap();
    }
    public void connect(String dbFilePath) throws Exception {
        // Fallback for old method without credentials
        connectPostgres(dbFilePath, "localhost", "5432", "postgres", "postgres", "analyzer_db");
    }

    public void connectPostgres(String dbFilePath, String host, String port, String user, String pass, String dbName) throws Exception {
        this.lastHost = host; this.lastPort = port; this.lastDbName = dbName; this.lastUser = user; this.lastPass = pass; this.lastDumpFile = dbFilePath;
        columnCache.clear();
        tableCache = null;
        // Close existing connection if any
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }

        if (dbFilePath.toLowerCase().endsWith(".sql.gz") || dbFilePath.toLowerCase().endsWith(".sql")) {
            System.out.println("Connecting to PostgreSQL to create database...");
            
            // Connect to the default 'postgres' database to create the new one
            String baseUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres?tcpKeepAlive=true&gssEncMode=disable";
            try (Connection tempConn = com.dbtool.core.connection.ConnectionUtil.getConnection(baseUrl, user, pass);
                 Statement stmt = tempConn.createStatement()) {
                
                // Drop database if exists (safe for our analyzer_db)
                // Postgres requires no active connections to drop, so this might fail if in use,
                // but we try our best.
                try {
                    stmt.execute("DROP DATABASE IF EXISTS " + dbName);
                } catch (Exception e) {
                    System.err.println("Could not drop database, it might not exist or is in use.");
                }
                
                stmt.execute("CREATE DATABASE " + dbName);
            }
            
            // Now connect to the new database
            String targetUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?tcpKeepAlive=true&gssEncMode=disable&connectTimeout=10&socketTimeout=30";
            connection = com.dbtool.core.connection.ConnectionUtil.getConnection(targetUrl, user, pass);
            
            importSqlDump(dbFilePath);
            
            // Reconnect AGAIN to clear any session-level variables (like search_path) altered by pg_dump
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = com.dbtool.core.connection.ConnectionUtil.getConnection(targetUrl, user, pass);
            
        } else if (dbFilePath.toLowerCase().endsWith(".accdb") || dbFilePath.toLowerCase().endsWith(".mdb")) {
            System.out.println("Connecting to MS Access Database...");
            String url = "jdbc:ucanaccess://" + dbFilePath;
            connection = DriverManager.getConnection(url);
        } else if (dbFilePath.toLowerCase().endsWith(".sqlite") || dbFilePath.toLowerCase().endsWith(".db") || dbFilePath.toLowerCase().endsWith(".sqlite3")) {
            System.out.println("Connecting to SQLite Database...");
            connectSQLite(dbFilePath);
        } else {
            throw new SQLException("Unsupported file format. Please select .accdb, .mdb, .sqlite, .db, .sql, or .sql.gz");
        }
    }

    public void connectExistingPostgres(String host, String port, String dbName, String user, String pass) throws Exception {
        this.lastType = "POSTGRESQL";
        this.lastHost = host; this.lastPort = port; this.lastDbName = dbName; this.lastUser = user; this.lastPass = pass; this.lastDumpFile = null;
        clearCache();
        String targetUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?tcpKeepAlive=true&gssEncMode=disable&connectTimeout=10&socketTimeout=30";
        connection = com.dbtool.core.connection.ConnectionUtil.getConnection(targetUrl, user, pass);
    }

    public void connectMySQL(String host, String port, String dbName, String user, String pass) throws Exception {
        this.lastType = "MYSQL";
        this.lastHost = host; this.lastPort = port; this.lastDbName = dbName; this.lastUser = user; this.lastPass = pass; this.lastDumpFile = null;
        clearCache();
        String targetUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000";
        connection = DriverManager.getConnection(targetUrl, user, pass);
    }

    public void connectSQLite(String dbFilePath) throws Exception {
        this.lastType = "SQLITE";
        this.lastHost = null; this.lastPort = null; this.lastDbName = new File(dbFilePath).getName(); this.lastUser = null; this.lastPass = null; this.lastDumpFile = dbFilePath;
        clearCache();
        String targetUrl = "jdbc:sqlite:" + dbFilePath;
        connection = DriverManager.getConnection(targetUrl);
    }

    public boolean isPostgres() {
        if (connection == null) return false;
        try {
            String prod = connection.getMetaData().getDatabaseProductName();
            return prod != null && prod.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isMySQL() {
        if (connection == null) return false;
        try {
            String prod = connection.getMetaData().getDatabaseProductName();
            return prod != null && (prod.toLowerCase().contains("mysql") || prod.toLowerCase().contains("mariadb"));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSQLite() {
        if (connection == null) return false;
        try {
            String prod = connection.getMetaData().getDatabaseProductName();
            return prod != null && prod.toLowerCase().contains("sqlite");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccess() {
        if (connection == null) return false;
        try {
            String prod = connection.getMetaData().getDatabaseProductName();
            return prod != null && (prod.toLowerCase().contains("access") || prod.toLowerCase().contains("ucanaccess"));
        } catch (Exception e) {
            return false;
        }
    }

    public String getDatabaseType() {
        if (connection == null) return "NONE";
        if (isPostgres()) return "POSTGRESQL";
        if (isMySQL()) return "MYSQL";
        if (isSQLite()) return "SQLITE";
        if (isAccess()) return "ACCESS";
        if (isH2()) return "H2";
        return "GENERIC";
    }

    public boolean isH2() {
        if (connection == null) return false;
        try {
            String prod = connection.getMetaData().getDatabaseProductName();
            return prod != null && prod.toLowerCase().contains("h2");
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getPrimaryKeys(String tableName) {
        List<String> pks = new ArrayList<>();
        if (connection == null || tableName == null) return pks;
        try {
            DatabaseMetaData meta = connection.getMetaData();
            String rawTable = tableName;
            String schema = null;
            if (tableName.contains(".")) {
                String[] parts = tableName.split("\\.", 2);
                schema = parts[0];
                rawTable = parts[1];
            }
            // 1. Try with schema
            try (ResultSet rs = meta.getPrimaryKeys(null, schema, rawTable)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null && !pks.contains(col)) pks.add(col);
                }
            }
            // 2. Try without schema
            if (pks.isEmpty()) {
                try (ResultSet rs = meta.getPrimaryKeys(null, null, rawTable)) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null && !pks.contains(col)) pks.add(col);
                    }
                }
            }
            // 3. Try uppercase
            if (pks.isEmpty()) {
                try (ResultSet rs = meta.getPrimaryKeys(null, null, rawTable.toUpperCase())) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null && !pks.contains(col)) pks.add(col);
                    }
                }
            }
            // 4. Try lowercase
            if (pks.isEmpty()) {
                try (ResultSet rs = meta.getPrimaryKeys(null, null, rawTable.toLowerCase())) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null && !pks.contains(col)) pks.add(col);
                    }
                }
            }
            // 5. Special check for SQLite: PRAGMA table_info
            if (pks.isEmpty() && isSQLite()) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quoteTableName(tableName) + ")")) {
                    while (rs.next()) {
                        if (rs.getInt("pk") > 0) {
                            String col = rs.getString("name");
                            if (col != null && !pks.contains(col)) pks.add(col);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return pks;
    }

    public void connectSandbox() throws Exception {
        connectSandbox("schemasync_sandbox");
    }

    public void connectSandbox(String memDbName) throws Exception {
        String dbLabel = "schemasync_sandbox".equals(memDbName) ? "Sandbox (In-Memory)" : "Sandbox (" + memDbName + ")";
        this.lastHost = null; this.lastPort = null; this.lastDbName = dbLabel; this.lastUser = "sa"; this.lastPass = ""; this.lastDumpFile = null;
        clearCache();
        if (connection != null && !connection.isClosed()) {
            try { connection.close(); } catch (Exception ignored) {}
        }
        String url = "jdbc:h2:mem:" + memDbName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        connection = DriverManager.getConnection(url, "sa", "");
        seedSampleSchema();
    }

    public void useExistingConnection(DatabaseManager source) {
        if (source != null && source.connection != null) {
            this.connection = source.connection;
            this.lastHost = source.getLastHost();
            this.lastPort = source.getLastPort();
            this.lastDbName = source.getLastDbName();
            this.lastUser = source.getLastUser();
            this.lastPass = source.getLastPass();
            this.lastDumpFile = source.getLastDumpFile();
            clearCache();
        }
    }

    public void seedSampleSchema() throws SQLException {
        if (connection == null) return;
        boolean isPg = isPostgres();
        try (Statement stmt = connection.createStatement()) {
            // 1. Departments
            stmt.execute("CREATE TABLE IF NOT EXISTS departments (\n" +
                         "  id INT PRIMARY KEY,\n" +
                         "  dept_name VARCHAR(100) NOT NULL,\n" +
                         "  location VARCHAR(100),\n" +
                         "  closed INT DEFAULT 0\n" +
                         ");");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM departments")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO departments (id, dept_name, location, closed) VALUES\n" +
                                 "(1, 'IT', 'NYC', 0),\n" +
                                 "(2, 'HR', 'Chicago', 0),\n" +
                                 "(3, 'Finance', 'NYC', 0),\n" +
                                 "(4, 'Marketing', 'San Francisco', 0),\n" +
                                 "(5, 'Legal', 'Boston', 1);");
                }
            }

            // 2. Employees (Supports both 'dept' and 'department', 'name' and 'first_name'/'last_name')
            String idCol = "id SERIAL PRIMARY KEY";
            stmt.execute("CREATE TABLE IF NOT EXISTS employees (\n" +
                         "  " + idCol + ",\n" +
                         "  first_name VARCHAR(100),\n" +
                         "  last_name VARCHAR(100),\n" +
                         "  name VARCHAR(100),\n" +
                         "  email VARCHAR(150) UNIQUE,\n" +
                         "  dept VARCHAR(50),\n" +
                         "  department VARCHAR(50),\n" +
                         "  dept_id INT,\n" +
                         "  salary DECIMAL(10,2) DEFAULT 0,\n" +
                         "  hire_date DATE,\n" +
                         "  manager_id INT,\n" +
                         "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                         ");");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM employees")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO employees (first_name, last_name, name, email, dept, department, dept_id, salary, hire_date, manager_id) VALUES\n" +
                                 "('Alice', 'Smith', 'Alice Smith', 'alice@company.com', 'IT', 'IT', 1, 75000.00, '2015-03-12', NULL),\n" +
                                 "('Bob', 'Jones', 'Bob Jones', 'bob@company.com', 'IT', 'IT', 1, 62000.00, '2018-06-01', 1),\n" +
                                 "('Charlie', 'Brown', 'Charlie Brown', 'charlie@company.com', 'HR', 'HR', 2, 54000.00, '2019-09-15', NULL),\n" +
                                 "('Diana', 'Prince', 'Diana Prince', 'diana@company.com', 'Finance', 'Finance', 3, 95000.00, '2012-01-10', NULL),\n" +
                                 "('Evan', 'Wright', 'Evan Wright', 'evan@company.com', 'IT', 'IT', 1, 82000.00, '2020-04-20', 1),\n" +
                                 "('Fiona', 'Gallagher', 'Fiona Gallagher', 'fiona@company.com', 'Finance', 'Finance', 3, 48000.00, '2021-11-05', 4),\n" +
                                 "('George', 'Clark', 'George Clark', 'george@company.com', 'HR', 'HR', 2, 45000.00, '2022-02-18', 3),\n" +
                                 "('Hannah', 'Abbott', 'Hannah Abbott', 'hannah@company.com', 'IT', 'IT', 1, 105000.00, '2010-08-25', 1),\n" +
                                 "('Ian', 'Malcolm', 'Ian Malcolm', 'ian@company.com', 'Finance', 'Finance', 3, 115000.00, '2008-05-14', 4),\n" +
                                 "('Julia', 'Roberts', 'Julia Roberts', 'julia@company.com', 'Marketing', 'Marketing', 4, 68000.00, '2023-01-09', NULL);");
                }
            }

            // 3. Contractors
            stmt.execute("CREATE TABLE IF NOT EXISTS contractors (name VARCHAR(100));");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM contractors")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO contractors VALUES ('Dave Miller'), ('Eve Torres'), ('Frank Castle'), ('Alice Smith');");
                }
            }

            // 4. New Hires
            stmt.execute("CREATE TABLE IF NOT EXISTS new_hires (name VARCHAR(100), dept VARCHAR(50));");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM new_hires")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO new_hires VALUES ('Grace Hopper', 'IT'), ('Alan Turing', 'IT');");
                }
            }

            // 5. Projects
            stmt.execute("CREATE TABLE IF NOT EXISTS projects (id INT PRIMARY KEY, emp_id INT, name VARCHAR(100));");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM projects")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO projects VALUES (1, 1, 'Cloud Migration'), (2, 2, 'Auth System'), (3, 4, 'Financial Audit');");
                }
            }

            // 6. Accounts
            stmt.execute("CREATE TABLE IF NOT EXISTS accounts (id INT PRIMARY KEY, balance DECIMAL(10,2));");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM accounts")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO accounts VALUES (1, 5000.00), (2, 2500.00);");
                }
            }

            // 7. Orders
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (\n" +
                         "  id INT PRIMARY KEY,\n" +
                         "  dept VARCHAR(50),\n" +
                         "  region VARCHAR(50),\n" +
                         "  amount DECIMAL(10,2),\n" +
                         "  sales DECIMAL(10,2),\n" +
                         "  status VARCHAR(20) DEFAULT 'pending'\n" +
                         ");");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM orders")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO orders VALUES\n" +
                                 "(1, 'IT', 'North', 1500.00, 1500.00, 'completed'),\n" +
                                 "(2, 'IT', 'South', 2200.00, 2200.00, 'completed'),\n" +
                                 "(3, 'Finance', 'North', 3100.00, 3100.00, 'pending'),\n" +
                                 "(4, 'Finance', 'West', 4500.00, 4500.00, 'completed'),\n" +
                                 "(5, 'HR', 'East', 800.00, 800.00, 'pending');");
                }
            }

            // 8. Sales
            stmt.execute("CREATE TABLE IF NOT EXISTS sales (quarter VARCHAR(10), amount DECIMAL(10,2));");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sales")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO sales VALUES ('Q1', 45000.00), ('Q2', 52000.00), ('Q3', 61000.00), ('Q4', 78000.00);");
                }
            }

            // 9. Audit Log
            stmt.execute("CREATE TABLE IF NOT EXISTS audit_log (\n" +
                         "  emp_id INT,\n" +
                         "  old_sal DECIMAL(10,2),\n" +
                         "  new_sal DECIMAL(10,2),\n" +
                         "  changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                         ");");
        }
        clearCache();
    }

    private void importSqlDump(String dbFilePath) throws Exception {
        InputStream in = new FileInputStream(dbFilePath);
        if (dbFilePath.toLowerCase().endsWith(".gz")) {
            in = new GZIPInputStream(in);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder currentStatement = new StringBuilder();
        
        boolean inDollarQuote = false;
        String dollarMarker = "";
        
        try (Statement stmt = connection.createStatement()) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                
                // Skip comments outside of quotes
                if (!inDollarQuote && (trimmed.startsWith("--") || (trimmed.startsWith("/*") && !trimmed.contains("*/")) || trimmed.isEmpty())) {
                    continue;
                }

                // Check for COPY FROM STDIN
                if (!inDollarQuote && trimmed.toUpperCase().startsWith("COPY ") && trimmed.toUpperCase().contains(" FROM STDIN;")) {
                    String copySql = trimmed;
                    
                    // Use Postgres CopyManager to stream the data efficiently
                    org.postgresql.copy.CopyManager copyManager = new org.postgresql.copy.CopyManager(connection.unwrap(org.postgresql.core.BaseConnection.class));
                    try {
                        copyManager.copyIn(copySql, new Reader() {
                            private String currentLine = null;
                            private int index = 0;
                            @Override
                            public int read(char[] cbuf, int off, int len) throws IOException {
                                if (currentLine == null) {
                                    currentLine = reader.readLine();
                                    if (currentLine == null || currentLine.equals("\\.")) {
                                        return -1; // EOF for this COPY block
                                    }
                                    currentLine += "\n";
                                    index = 0;
                                }
                                int available = currentLine.length() - index;
                                if (available == 0) {
                                    currentLine = null;
                                    return read(cbuf, off, len);
                                }
                                int toRead = Math.min(len, available);
                                currentLine.getChars(index, index + toRead, cbuf, off);
                                index += toRead;
                                return toRead;
                            }
                            @Override
                            public void close() {}
                        });
                    } catch (Exception e) {
                        System.err.println("Warning: COPY data failed: " + e.getMessage());
                    }
                    
                    currentStatement.setLength(0);
                    continue;
                }

                currentStatement.append(line).append("\n");

                // Track PostgreSQL dollar quotes (e.g. $$ or $BODY$)
                if (!inDollarQuote) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$([a-zA-Z0-9_]*)\\$").matcher(line);
                    if (m.find()) {
                        inDollarQuote = true;
                        dollarMarker = m.group();
                        // Check if it closes on the very same line
                        if (line.indexOf(dollarMarker, m.end()) != -1) {
                            inDollarQuote = false;
                        }
                    }
                } else {
                    if (line.contains(dollarMarker)) {
                        inDollarQuote = false;
                    }
                }

                // Execute when we hit a semicolon and we are NOT inside a dollar quote block
                if (trimmed.endsWith(";") && !inDollarQuote) {
                    String sql = currentStatement.toString();
                    currentStatement.setLength(0);
                    
                    try {
                        stmt.execute(sql);
                    } catch (SQLException e) {
                        System.err.println("Warning: Statement failed (ignored): " + e.getMessage());
                    }
                }
            }
        }
        reader.close();
        System.out.println("SQL Dump imported successfully to PostgreSQL.");
    }

    public List<String> getTableNames() {
        if (tableCache != null) return tableCache;
        List<String> tables = new ArrayList<>();
        if (connection == null) return tables;

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"});
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String table = rs.getString("TABLE_NAME");
                
                // Skip internal Postgres system schemas
                if (schema != null && (schema.equalsIgnoreCase("information_schema") || schema.equalsIgnoreCase("pg_catalog"))) {
                    continue;
                }
                
                if (schema != null && !schema.equalsIgnoreCase("public") && !schema.isEmpty()) {
                    tables.add(schema + "." + table);
                } else {
                    tables.add(table);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        tableCache = tables;
        return tables;
    }

    public List<String> getColumnNames(String tableName) {
        if (columnCache.containsKey(tableName)) {
            return new ArrayList<>(new LinkedHashSet<>(columnCache.get(tableName)));
        }
        if (connection == null || tableName == null) return new ArrayList<>();

        String schema = null;
        String rawTable = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            schema = parts[0];
            rawTable = parts[1];
        } else {
            try {
                schema = connection.getSchema();
            } catch (Throwable ignored) {}
        }

        Set<String> columnSet = new LinkedHashSet<>();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String escape = metaData.getSearchStringEscape();
            String escapedTableName = rawTable;
            if (escape != null && !escape.isEmpty()) {
                escapedTableName = escapedTableName.replace("_", escape + "_").replace("%", escape + "%");
            }

            final String targetTable = rawTable;
            java.util.function.Consumer<ResultSet> processor = (rs) -> {
                try {
                    while (rs.next()) {
                        String tName = rs.getString("TABLE_NAME");
                        if (targetTable.equalsIgnoreCase(tName)) {
                            String col = rs.getString("COLUMN_NAME");
                            if (col != null && !col.trim().isEmpty()) {
                                columnSet.add(col.trim());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            };

            // 1. Specific schema query
            try (ResultSet rs = metaData.getColumns(null, schema, escapedTableName, "%")) {
                processor.accept(rs);
            }

            // 2. If nothing found and schema was non-null, try fallback with schema = null
            if (columnSet.isEmpty() && schema != null) {
                try (ResultSet rs = metaData.getColumns(null, null, escapedTableName, "%")) {
                    processor.accept(rs);
                }
            }

            // 3. Fallback uppercase
            if (columnSet.isEmpty()) {
                String upper = rawTable.toUpperCase();
                if (escape != null && !escape.isEmpty()) upper = upper.replace("_", escape + "_").replace("%", escape + "%");
                try (ResultSet rs = metaData.getColumns(null, schema, upper, "%")) {
                    processor.accept(rs);
                }
                if (columnSet.isEmpty() && schema != null) {
                    try (ResultSet rs = metaData.getColumns(null, null, upper, "%")) {
                        processor.accept(rs);
                    }
                }
            }

            // 4. Fallback lowercase
            if (columnSet.isEmpty()) {
                String lower = rawTable.toLowerCase();
                if (escape != null && !escape.isEmpty()) lower = lower.replace("_", escape + "_").replace("%", escape + "%");
                try (ResultSet rs = metaData.getColumns(null, schema, lower, "%")) {
                    processor.accept(rs);
                }
                if (columnSet.isEmpty() && schema != null) {
                    try (ResultSet rs = metaData.getColumns(null, null, lower, "%")) {
                        processor.accept(rs);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        List<String> columns = new ArrayList<>(columnSet);
        columnCache.put(tableName, columns);
        return columns;
    }

    public static class ColumnDefinition {
        public String name;
        public int dataType;
        public String typeName;
        public int size;
        public int decimalDigits;
        public boolean isNullable;
        public boolean isPrimaryKey;
        public boolean isAutoIncrement;
        public String remarks;
        public String foreignKeyRef;

        public String getFriendlyType() {
            if (typeName != null) {
                String t = typeName.toLowerCase();
                if (t.contains("int")) return "Integer";
                if (t.contains("char") || t.contains("text")) return "String";
                if (t.contains("numeric") || t.contains("decimal")) return "Decimal";
                if (t.contains("bool")) return "Boolean";
                if (t.contains("date")) return "Date";
                if (t.contains("time")) return "DateTime";
                if (t.contains("uuid")) return "UUID";
            }
            return typeName != null ? typeName : "Object";
        }
    }

    public List<ColumnDefinition> getDetailedColumns(String tableName) {
        List<ColumnDefinition> list = new ArrayList<>();
        if (connection == null || tableName == null) return list;

        String schema = null;
        String rawTable = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            schema = parts[0];
            rawTable = parts[1];
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            List<String> pks = getPrimaryKeys(tableName);
            Map<String, String[]> fks = getForeignKeys(tableName);

            String escape = metaData.getSearchStringEscape();
            String escapedTableName = rawTable;
            if (escape != null && !escape.isEmpty()) {
                escapedTableName = escapedTableName.replace("_", escape + "_").replace("%", escape + "%");
            }

            final String finalRawTable = rawTable;
            Set<String> seenCols = new HashSet<>();
            java.util.function.Consumer<ResultSet> processor = (rs) -> {
                try {
                    while (rs.next()) {
                        String tblName = rs.getString("TABLE_NAME");
                        if (finalRawTable.equalsIgnoreCase(tblName)) {
                            String colName = rs.getString("COLUMN_NAME");
                            if (colName == null || colName.trim().isEmpty() || !seenCols.add(colName.trim().toLowerCase())) {
                                continue;
                            }
                            ColumnDefinition cd = new ColumnDefinition();
                            cd.name = colName.trim();
                            cd.dataType = rs.getInt("DATA_TYPE");
                            cd.typeName = rs.getString("TYPE_NAME");
                            cd.size = rs.getInt("COLUMN_SIZE");
                            cd.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                            cd.isNullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                            try {
                                String autoInc = rs.getString("IS_AUTOINCREMENT");
                                cd.isAutoIncrement = "YES".equalsIgnoreCase(autoInc);
                            } catch (Exception ignored) {}
                            try {
                                cd.remarks = rs.getString("REMARKS");
                            } catch (Exception ignored) {}

                            for (String pk : pks) {
                                if (pk.equalsIgnoreCase(cd.name)) {
                                    cd.isPrimaryKey = true;
                                    break;
                                }
                            }

                            for (Map.Entry<String, String[]> fkEntry : fks.entrySet()) {
                                if (fkEntry.getKey().equalsIgnoreCase(cd.name)) {
                                    String[] ref = fkEntry.getValue();
                                    cd.foreignKeyRef = ref[0] + "." + ref[1];
                                    break;
                                }
                            }

                            list.add(cd);
                        }
                    }
                } catch (Exception e) {}
            };

            // 1. Standard search
            try (ResultSet rs = metaData.getColumns(null, schema, escapedTableName, "%")) {
                processor.accept(rs);
            }

            // 2. Fallback uppercase
            if (list.isEmpty()) {
                String upper = rawTable.toUpperCase();
                if (escape != null && !escape.isEmpty()) upper = upper.replace("_", escape + "_").replace("%", escape + "%");
                try (ResultSet rs = metaData.getColumns(null, schema, upper, "%")) {
                    processor.accept(rs);
                }
            }

            // 3. Fallback lowercase
            if (list.isEmpty()) {
                String lower = rawTable.toLowerCase();
                if (escape != null && !escape.isEmpty()) lower = lower.replace("_", escape + "_").replace("%", escape + "%");
                try (ResultSet rs = metaData.getColumns(null, schema, lower, "%")) {
                    processor.accept(rs);
                }
            }

            // 4. Fallback SQLite via PRAGMA table_info
            if (list.isEmpty() && isSQLite()) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quoteTableName(tableName) + ")")) {
                    while (rs.next()) {
                        ColumnDefinition cd = new ColumnDefinition();
                        cd.name = rs.getString("name");
                        cd.typeName = rs.getString("type");
                        cd.isPrimaryKey = rs.getInt("pk") > 0;
                        cd.isNullable = rs.getInt("notnull") == 0;
                        list.add(cd);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void learnJoin(String tableA, String colA, String tableB, String colB) {
        if (tableA == null || colA == null || tableB == null || colB == null) return;
        
        // Strip schema just to be safe for matching
        String cleanA = tableA.contains(".") ? tableA.split("\\.")[1] : tableA;
        String cleanB = tableB.contains(".") ? tableB.split("\\.")[1] : tableB;
        
        String key1 = cleanA + ":" + cleanB;
        String val1 = colA + ":" + colB;
        String key2 = cleanB + ":" + cleanA;
        String val2 = colB + ":" + colA;
        
        learnedJoins.setProperty(key1, val1);
        learnedJoins.setProperty(key2, val2);
        
        try (FileOutputStream out = new FileOutputStream(learnedJoinsFile)) {
            learnedJoins.store(out, "Auto-learned Foreign Key Joins");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String[] getLearnedJoin(String tableA, String tableB) {
        if (tableA == null || tableB == null) return null;
        String cleanA = tableA.contains(".") ? tableA.split("\\.")[1] : tableA;
        String cleanB = tableB.contains(".") ? tableB.split("\\.")[1] : tableB;
        
        String key = cleanA + ":" + cleanB;
        String val = learnedJoins.getProperty(key);
        if (val != null) {
            String[] parts = val.split(":");
            if (parts.length == 2) {
                return new String[]{parts[0], parts[1]};
            }
        }
        return null;
    }

    public String[] getForeignKeyMatch(String tableA, String tableB) {
        if (connection == null || tableA == null || tableB == null) return null;
        String nameA = tableA.contains(".") ? tableA.split("\\.")[1] : tableA;
        String nameB = tableB.contains(".") ? tableB.split("\\.")[1] : tableB;

        // 1. Check if tableA has a foreign key pointing to tableB (uses fkCache)
        java.util.Map<String, String[]> fksA = getForeignKeys(tableA);
        for (java.util.Map.Entry<String, String[]> entry : fksA.entrySet()) {
            String fkColumn = entry.getKey();
            String[] pkInfo = entry.getValue();
            if (pkInfo != null && pkInfo.length >= 2 && nameB.equalsIgnoreCase(pkInfo[0])) {
                return new String[]{tableA + "." + fkColumn, tableB + "." + pkInfo[1]};
            }
        }

        // 2. Check if tableB has a foreign key pointing to tableA (uses fkCache)
        java.util.Map<String, String[]> fksB = getForeignKeys(tableB);
        for (java.util.Map.Entry<String, String[]> entry : fksB.entrySet()) {
            String fkColumn = entry.getKey();
            String[] pkInfo = entry.getValue();
            if (pkInfo != null && pkInfo.length >= 2 && nameA.equalsIgnoreCase(pkInfo[0])) {
                return new String[]{tableA + "." + pkInfo[1], tableB + "." + fkColumn};
            }
        }

        return null; // No formal foreign key found
    }

    public String[] getHeuristicMatch(String tableA, String tableB) {
        List<String> colsA = getColumnNames(tableA);
        List<String> colsB = getColumnNames(tableB);
        
        String cleanA = tableA.contains(".") ? tableA.split("\\.")[1] : tableA;
        String cleanB = tableB.contains(".") ? tableB.split("\\.")[1] : tableB;
        
        // 1. Check if tableA has 'tableB_id' and tableB has 'id'
        if (colsA.contains(cleanB + "_id") && colsB.contains("id")) {
            return new String[]{tableA + "." + cleanB + "_id", tableB + ".id"};
        }
        
        // 2. Check if tableB has 'tableA_id' and tableA has 'id'
        if (colsB.contains(cleanA + "_id") && colsA.contains("id")) {
            return new String[]{tableA + ".id", tableB + "." + cleanA + "_id"};
        }
        
        // 3. Check for matching exact column names (excluding standard 'id', 'created_at', etc)
        for (String colA : colsA) {
            if (colA.equalsIgnoreCase("id") || colA.equalsIgnoreCase("created_at") || colA.equalsIgnoreCase("updated_at")) continue;
            if (colsB.contains(colA)) {
                return new String[]{tableA + "." + colA, tableB + "." + colA};
            }
        }
        
        return null;
    }

    private static final java.util.Set<String> SQL_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "user", "order", "group", "select", "where", "from", "table", "update", "delete", "insert", "into", "values", "set", "limit", "offset", "all", "any", "as", "asc", "desc", "between", "case", "cast", "check", "column", "constraint", "create", "cross", "current_date", "current_time", "current_timestamp", "current_user", "default", "distinct", "drop", "else", "end", "except", "false", "for", "foreign", "full", "grant", "having", "in", "inner", "intersect", "is", "join", "left", "like", "natural", "not", "null", "on", "or", "outer", "primary", "references", "right", "table", "then", "to", "true", "union", "unique", "using", "when", "with"
    ));

    private String quoteIfNecessary(String identifier) {
        if (identifier == null) return "";
        if (identifier.matches("^[a-z_][a-z0-9_]*$") && !SQL_KEYWORDS.contains(identifier.toLowerCase())) {
            return identifier;
        }
        if (isMySQL()) {
            return "`" + identifier.replace("`", "``") + "`";
        }
        if (isAccess()) {
            return "[" + identifier.replace("]", "]]") + "]";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public String quoteTableName(String tableName) {
        if (tableName == null) return "";
        if (tableName.contains("(") || tableName.contains(")")) {
            return tableName;
        }
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            return quoteIfNecessary(parts[0]) + "." + quoteIfNecessary(parts[1]);
        }
        return quoteIfNecessary(tableName);
    }

    public String quoteColumnName(String fullColumnName) {
        if (fullColumnName == null) return "";
        // Do not wrap functions/expressions like COUNT(*), SUM(col), AVG(col), etc. in quotes
        if (fullColumnName.equals("*") || fullColumnName.contains("(") || fullColumnName.contains(")")) {
            return fullColumnName;
        }
        String[] parts = fullColumnName.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(quoteIfNecessary(parts[i]));
            if (i < parts.length - 1) {
                sb.append(".");
            }
        }
        return sb.toString();
    }

    public void executeUpdate(String query, java.util.List<Object> params) throws java.sql.SQLException {
        if (connection == null) throw new java.sql.SQLException("Not connected to a database.");
        ensureConnectionValid();

        long start = System.currentTimeMillis();
        boolean success = false;
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(query)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            pstmt.executeUpdate();
            success = true;
            lastValidCheckMs = System.currentTimeMillis();
        } catch (java.sql.SQLException e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("closed") || msg.contains("i/o") || msg.contains("broken pipe") || msg.contains("timeout") || msg.contains("connection")) {
                try {
                    autoReconnect();
                    try (java.sql.PreparedStatement pstmt2 = connection.prepareStatement(query)) {
                        for (int i = 0; i < params.size(); i++) {
                            pstmt2.setObject(i + 1, params.get(i));
                        }
                        pstmt2.executeUpdate();
                        success = true;
                        lastValidCheckMs = System.currentTimeMillis();
                    }
                } catch (Exception ex) {
                    throw e;
                }
            } else {
                throw e;
            }
        } finally {
            com.dbtool.util.QueryLogger.log(query, System.currentTimeMillis() - start, success);
        }
    }

    public int executeUpdate(String query) throws java.sql.SQLException {
        if (connection == null) throw new java.sql.SQLException("Not connected to a database.");
        ensureConnectionValid();

        long start = System.currentTimeMillis();
        boolean success = false;
        try (java.sql.Statement stmt = connection.createStatement()) {
            int count = stmt.executeUpdate(query);
            success = true;
            lastValidCheckMs = System.currentTimeMillis();
            return count;
        } catch (java.sql.SQLException e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("closed") || msg.contains("i/o") || msg.contains("broken pipe") || msg.contains("timeout") || msg.contains("connection")) {
                try {
                    autoReconnect();
                    try (java.sql.Statement stmt2 = connection.createStatement()) {
                        int count = stmt2.executeUpdate(query);
                        success = true;
                        lastValidCheckMs = System.currentTimeMillis();
                        return count;
                    }
                } catch (Exception ex) {
                    throw e;
                }
            } else {
                throw e;
            }
        } finally {
            com.dbtool.util.QueryLogger.log(query, System.currentTimeMillis() - start, success);
        }
    }

    public java.util.Map<String, String[]> getForeignKeys(String table) {
        if (connection == null || table == null) return new java.util.HashMap<>();
        if (fkCache.containsKey(table)) {
            return fkCache.get(table);
        }
        java.util.Map<String, String[]> fkMap = new java.util.HashMap<>();
        try {
            java.sql.DatabaseMetaData metaData = connection.getMetaData();
            String schema = null;
            String name = table;
            if (table.contains(".")) {
                schema = table.split("\\.")[0];
                name = table.split("\\.")[1];
            }
            try (java.sql.ResultSet rs = metaData.getImportedKeys(null, schema, name)) {
                while (rs.next()) {
                    String fkCol = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    String pkCol = rs.getString("PKCOLUMN_NAME");
                    fkMap.put(fkCol, new String[]{pkTable, pkCol});
                }
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        fkCache.put(table, fkMap);
        return fkMap;
    }

    public DefaultTableModel executeQuery(String query) throws java.sql.SQLException {
        if (connection == null) throw new java.sql.SQLException("Not connected to a database.");
        ensureConnectionValid();

        long start = System.currentTimeMillis();
        boolean success = false;
        try (java.sql.Statement stmt = connection.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(query)) {
            DefaultTableModel model = buildTableModel(rs);
            success = true;
            lastValidCheckMs = System.currentTimeMillis();
            return model;
        } catch (java.sql.SQLException e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("closed") || msg.contains("i/o") || msg.contains("broken pipe") || msg.contains("timeout") || msg.contains("connection")) {
                try {
                    autoReconnect();
                    try (java.sql.Statement stmt2 = connection.createStatement();
                         java.sql.ResultSet rs2 = stmt2.executeQuery(query)) {
                        DefaultTableModel model = buildTableModel(rs2);
                        success = true;
                        lastValidCheckMs = System.currentTimeMillis();
                        return model;
                    }
                } catch (Exception ex) {
                    throw e; // throw original
                }
            } else {
                throw e;
            }
        } finally {
            com.dbtool.util.QueryLogger.log(query, System.currentTimeMillis() - start, success);
        }
    }

    public DefaultTableModel executeSearch(String tableName, String keyword, String cols, int limit) throws SQLException {
        if (connection == null) throw new SQLException("Not connected to a database.");
        ensureConnectionValid();

        List<String> columns = getColumnNames(tableName);
        if (columns.isEmpty()) return new DefaultTableModel();

        StringBuilder query = new StringBuilder("SELECT ").append(cols).append(" FROM ").append(quoteTableName(tableName)).append(" WHERE ");
        for (int i = 0; i < columns.size(); i++) {
            // Cast to text to allow LIKE searching on non-text columns in Postgres
            query.append("CAST(").append(quoteColumnName(columns.get(i))).append(" AS TEXT) LIKE ? ");
            if (i < columns.size() - 1) {
                query.append(" OR ");
            }
        }
        query.append(" LIMIT ").append(limit);

        long start = System.currentTimeMillis();
        boolean success = false;
        try (PreparedStatement pstmt = connection.prepareStatement(query.toString())) {
            String searchPattern = "%" + keyword + "%";
            for (int i = 0; i < columns.size(); i++) {
                pstmt.setString(i + 1, searchPattern);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                DefaultTableModel model = buildTableModel(rs);
                success = true;
                return model;
            }
        } catch (SQLException e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("closed") || msg.contains("i/o") || msg.contains("broken pipe") || msg.contains("timeout") || msg.contains("connection")) {
                try {
                    autoReconnect();
                    try (PreparedStatement pstmt2 = connection.prepareStatement(query.toString())) {
                        String searchPattern = "%" + keyword + "%";
                        for (int i = 0; i < columns.size(); i++) {
                            pstmt2.setString(i + 1, searchPattern);
                        }
                        try (ResultSet rs2 = pstmt2.executeQuery()) {
                            DefaultTableModel model = buildTableModel(rs2);
                            success = true;
                            return model;
                        }
                    }
                } catch (Exception ex) {
                    throw e;
                }
            } else {
                throw e;
            }
        } finally {
            com.dbtool.util.QueryLogger.log("SEARCH " + tableName + " FOR " + keyword, System.currentTimeMillis() - start, success);
        }
    }

    private com.dbtool.util.TypedTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        Vector<String> columnNames = new Vector<>();
        int columnCount = metaData.getColumnCount();
        String[] columnTypes = new String[columnCount];
        for (int column = 1; column <= columnCount; column++) {
            columnNames.add(metaData.getColumnName(column));
            columnTypes[column - 1] = metaData.getColumnTypeName(column);
        }
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> vector = new Vector<>();
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                vector.add(rs.getObject(columnIndex));
            }
            data.add(vector);
        }
        return new com.dbtool.util.TypedTableModel(data, columnNames, columnTypes);
    }
    public void saveConnectionProfile(String profileName, String host, String port, String user, String pass, String dbName) {
        java.util.Properties props = new java.util.Properties();
        java.io.File propFile = getSaveFile("saved_connections.properties");
        if (propFile.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {}
        }
        
        String encodedPass = com.dbtool.util.CryptoUtil.encryptPassword(pass);
        String val = host + ";" + port + ";" + user + ";" + encodedPass + ";" + dbName;
        props.setProperty(profileName, val);
        
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(propFile)) {
            props.store(out, "Saved Connection Profiles");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public void deleteConnectionProfile(String profileName) {
        java.util.Properties props = getSavedConnections();
        if (props.containsKey(profileName)) {
            props.remove(profileName);
            java.io.File propFile = getSaveFile("saved_connections.properties");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(propFile)) {
                props.store(out, "Saved Connection Profiles");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public java.util.Properties getSavedConnections() {
        java.util.Properties props = new java.util.Properties();
        java.io.File propFile = getSaveFile("saved_connections.properties");
        if (propFile.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {}
        }
        return props;
    }

    public void saveSshProfile(String profileName, String host, String port, String user, String pass) {
        java.util.Properties props = new java.util.Properties();
        java.io.File propFile = getSaveFile("saved_ssh_connections.properties");
        if (propFile.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {}
        }
        
        String encodedPass = java.util.Base64.getEncoder().encodeToString(pass.getBytes());
        String val = host + ";" + port + ";" + user + ";" + encodedPass;
        props.setProperty(profileName, val);
        
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(propFile)) {
            props.store(out, "Saved SSH Profiles");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public java.util.Properties getSavedSshProfiles() {
        java.util.Properties props = new java.util.Properties();
        java.io.File propFile = getSaveFile("saved_ssh_connections.properties");
        if (propFile.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {}
        }
        return props;
    }

    // =========================================================================
    // Tier 1: Recursive Foreign Key Dependency & Cascade Deletion Engine
    // =========================================================================

    public static class ExportedForeignKey {
        public String fkName;
        public String childSchema;
        public String childTable;
        public List<String> childColumns = new ArrayList<>();
        public String parentSchema;
        public String parentTable;
        public List<String> parentColumns = new ArrayList<>();

        @Override
        public String toString() {
            return childTable + "(" + String.join(", ", childColumns) + ") -> " + parentTable + "(" + String.join(", ", parentColumns) + ")";
        }
    }

    public static class CascadeRowNode {
        public String tableName;
        public Map<String, Object> keyValues = new LinkedHashMap<>();
        public Map<String, Object> allRowValues = new LinkedHashMap<>();
        public String relationReason;
        public List<CascadeRowNode> children = new ArrayList<>();
        public int depth = 0;

        public String getDisplayLabel() {
            StringBuilder sb = new StringBuilder();
            sb.append(tableName).append(" (");
            if (!keyValues.isEmpty()) {
                boolean first = true;
                for (Map.Entry<String, Object> e : keyValues.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
            } else {
                sb.append("Row");
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static class PreparedDelete {
        public String tableName;
        public String sql;
        public List<Object> params;
        public String previewSql;

        public PreparedDelete(String tableName, String sql, List<Object> params, String previewSql) {
            this.tableName = tableName;
            this.sql = sql;
            this.params = params;
            this.previewSql = previewSql;
        }
    }

    public static class CascadePlan {
        public String rootTable;
        public List<CascadeRowNode> rootNodes = new ArrayList<>();
        public int totalRows = 0;
        public Map<String, Integer> tableCounts = new LinkedHashMap<>();
        public List<PreparedDelete> executableDeletes = new ArrayList<>();
        public String fullScript = "";
    }

    public List<ExportedForeignKey> getExportedForeignKeys(String parentTable) {
        List<ExportedForeignKey> result = new ArrayList<>();
        if (connection == null || parentTable == null) return result;

        String rawTable = parentTable;
        String schema = null;
        if (parentTable.contains(".")) {
            String[] parts = parentTable.split("\\.", 2);
            schema = parts[0];
            rawTable = parts[1];
        }

        try {
            DatabaseMetaData meta = connection.getMetaData();
            Map<String, ExportedForeignKey> fkMap = new LinkedHashMap<>();

            java.util.function.Consumer<ResultSet> collector = (rs) -> {
                try {
                    while (rs.next()) {
                        String fkTableSchem = rs.getString("FKTABLE_SCHEM");
                        String fkTableName = rs.getString("FKTABLE_NAME");
                        String fkCol = rs.getString("FKCOLUMN_NAME");
                        String pkCol = rs.getString("PKCOLUMN_NAME");
                        String fkName = rs.getString("FK_NAME");

                        String childFullTable = (fkTableSchem != null && !fkTableSchem.equalsIgnoreCase("public") && !fkTableSchem.isEmpty())
                                ? fkTableSchem + "." + fkTableName
                                : fkTableName;

                        String key = childFullTable + "::" + (fkName != null ? fkName : "fk_" + childFullTable);
                        ExportedForeignKey efk = fkMap.computeIfAbsent(key, k -> {
                            ExportedForeignKey obj = new ExportedForeignKey();
                            obj.fkName = fkName;
                            obj.childSchema = fkTableSchem;
                            obj.childTable = childFullTable;
                            obj.parentTable = parentTable;
                            return obj;
                        });
                        if (!efk.childColumns.contains(fkCol)) {
                            efk.childColumns.add(fkCol);
                            efk.parentColumns.add(pkCol);
                        }
                    }
                } catch (Exception e) {}
            };

            // 1. Try with schema
            if (schema != null) {
                try (ResultSet rs = meta.getExportedKeys(null, schema, rawTable)) {
                    collector.accept(rs);
                }
            }

            // 2. Try without schema
            if (fkMap.isEmpty()) {
                try (ResultSet rs = meta.getExportedKeys(null, null, rawTable)) {
                    collector.accept(rs);
                }
            }

            // 3. Try uppercase
            if (fkMap.isEmpty()) {
                try (ResultSet rs = meta.getExportedKeys(null, null, rawTable.toUpperCase())) {
                    collector.accept(rs);
                }
            }

            // 4. Try lowercase
            if (fkMap.isEmpty()) {
                try (ResultSet rs = meta.getExportedKeys(null, null, rawTable.toLowerCase())) {
                    collector.accept(rs);
                }
            }

            // 5. Fallback for SQLite via PRAGMA foreign_key_list
            if (fkMap.isEmpty() && isSQLite()) {
                List<String> allTables = getTableNames();
                for (String t : allTables) {
                    try (Statement stmt = connection.createStatement();
                         ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(" + quoteTableName(t) + ")")) {
                        while (rs.next()) {
                            String targetTbl = rs.getString("table");
                            if (targetTbl != null && targetTbl.equalsIgnoreCase(rawTable)) {
                                String fkId = rs.getString("id");
                                String fromCol = rs.getString("from");
                                String toCol = rs.getString("to");
                                String key = t + "::fk_" + fkId;
                                ExportedForeignKey efk = fkMap.computeIfAbsent(key, k -> {
                                    ExportedForeignKey obj = new ExportedForeignKey();
                                    obj.fkName = "fk_" + t + "_" + fkId;
                                    obj.childTable = t;
                                    obj.parentTable = parentTable;
                                    return obj;
                                });
                                efk.childColumns.add(fromCol);
                                efk.parentColumns.add(toCol);
                            }
                        }
                    } catch (Exception ex) {}
                }
            }

            result.addAll(fkMap.values());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public CascadePlan buildCascadePlan(String rootTable, List<Map<String, Object>> targetRows) {
        CascadePlan plan = new CascadePlan();
        plan.rootTable = rootTable;
        if (connection == null || rootTable == null || targetRows == null || targetRows.isEmpty()) {
            return plan;
        }

        Set<String> visitedRows = new HashSet<>();
        List<String> rootPks = getPrimaryKeys(rootTable);

        for (Map<String, Object> targetRow : targetRows) {
            CascadeRowNode rootNode = new CascadeRowNode();
            rootNode.tableName = rootTable;
            rootNode.allRowValues.putAll(targetRow);
            rootNode.depth = 0;
            rootNode.relationReason = "Target record to delete";

            // Extract PK values if available, else all values
            if (!rootPks.isEmpty()) {
                for (String pk : rootPks) {
                    for (Map.Entry<String, Object> entry : targetRow.entrySet()) {
                        String clean = entry.getKey().contains(".") ? entry.getKey().split("\\.", 2)[1] : entry.getKey();
                        if (clean.equalsIgnoreCase(pk)) {
                            rootNode.keyValues.put(pk, entry.getValue());
                            break;
                        }
                    }
                }
            }
            if (rootNode.keyValues.isEmpty()) {
                rootNode.keyValues.putAll(targetRow);
            }

            String fingerprint = rootTable + "::" + rootNode.keyValues.toString();
            visitedRows.add(fingerprint);
            plan.rootNodes.add(rootNode);

            // Recursively discover referencing child rows
            inspectChildDependencies(rootNode, visitedRows, 1);
        }

        // Collect in bottom-up (reverse topological) order:
        // Children must be deleted BEFORE parents.
        List<CascadeRowNode> deletionOrder = new ArrayList<>();
        Set<String> collectedRows = new HashSet<>();
        for (CascadeRowNode root : plan.rootNodes) {
            collectBottomUpNodes(root, deletionOrder, collectedRows);
        }

        plan.totalRows = deletionOrder.size();
        for (CascadeRowNode node : deletionOrder) {
            plan.tableCounts.put(node.tableName, plan.tableCounts.getOrDefault(node.tableName, 0) + 1);
        }

        // Build executable prepared deletes and readable preview script
        StringBuilder script = new StringBuilder();
        script.append("-- ==========================================================\n");
        script.append("-- SchemaSync Safe Cascade Deletion Transaction Script\n");
        script.append("-- Generated: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())).append("\n");
        script.append("-- Total Records Affected: ").append(plan.totalRows).append(" across ").append(plan.tableCounts.size()).append(" table(s)\n");
        script.append("-- Root Target Table: ").append(rootTable).append("\n");
        script.append("-- Execution Order: Strict Reverse-Dependency (Children First)\n");
        script.append("-- ==========================================================\n\n");

        if (isPostgres() || isSQLite()) {
            script.append("BEGIN TRANSACTION;\n\n");
        } else if (isMySQL()) {
            script.append("START TRANSACTION;\n\n");
        }

        int step = 1;
        for (CascadeRowNode node : deletionOrder) {
            StringBuilder sql = new StringBuilder("DELETE FROM ").append(quoteTableName(node.tableName)).append(" WHERE ");
            StringBuilder preview = new StringBuilder("DELETE FROM ").append(quoteTableName(node.tableName)).append(" WHERE ");
            List<Object> params = new ArrayList<>();

            boolean first = true;
            for (Map.Entry<String, Object> entry : node.keyValues.entrySet()) {
                if (!first) {
                    sql.append(" AND ");
                    preview.append(" AND ");
                }
                first = false;
                sql.append(quoteColumnName(entry.getKey()));
                preview.append(quoteColumnName(entry.getKey()));

                Object val = entry.getValue();
                if (val == null) {
                    sql.append(" IS NULL");
                    preview.append(" IS NULL");
                } else {
                    sql.append(" = ?");
                    params.add(val);
                    if (val instanceof Number) {
                        preview.append(" = ").append(val);
                    } else {
                        preview.append(" = '").append(val.toString().replace("'", "''")).append("'");
                    }
                }
            }
            sql.append(";");
            preview.append(";");

            script.append("-- Step ").append(step++).append(": ").append(node.relationReason).append("\n");
            script.append(preview.toString()).append("\n\n");

            plan.executableDeletes.add(new PreparedDelete(node.tableName, sql.toString().replace(";", ""), params, preview.toString()));
        }

        script.append("COMMIT;\n");
        plan.fullScript = script.toString();

        return plan;
    }

    private void inspectChildDependencies(CascadeRowNode parentNode, Set<String> visitedRows, int depth) {
        if (depth > 10) return; // Guard against pathological depth
        List<ExportedForeignKey> exportedFks = getExportedForeignKeys(parentNode.tableName);

        for (ExportedForeignKey efk : exportedFks) {
            boolean matchable = true;
            List<Object> matchParams = new ArrayList<>();
            StringBuilder where = new StringBuilder();

            for (int i = 0; i < efk.parentColumns.size(); i++) {
                String parentCol = efk.parentColumns.get(i);
                String childCol = efk.childColumns.get(i);

                Object parentVal = null;
                for (Map.Entry<String, Object> e : parentNode.allRowValues.entrySet()) {
                    String cleanKey = e.getKey().contains(".") ? e.getKey().split("\\.", 2)[1] : e.getKey();
                    if (cleanKey.equalsIgnoreCase(parentCol)) {
                        parentVal = e.getValue();
                        break;
                    }
                }
                if (parentVal == null) {
                    matchable = false;
                    break;
                }

                if (i > 0) where.append(" AND ");
                where.append(quoteColumnName(childCol)).append(" = ?");
                matchParams.add(parentVal);
            }

            if (!matchable || matchParams.isEmpty()) continue;

            String query = "SELECT * FROM " + quoteTableName(efk.childTable) + " WHERE " + where.toString();
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                for (int p = 0; p < matchParams.size(); p++) {
                    pstmt.setObject(p + 1, matchParams.get(p));
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    List<String> childPks = getPrimaryKeys(efk.childTable);

                    while (rs.next()) {
                        CascadeRowNode childNode = new CascadeRowNode();
                        childNode.tableName = efk.childTable;
                        childNode.depth = depth;
                        childNode.relationReason = "Referenced by " + efk.childTable + "(" + String.join(", ", efk.childColumns) + ") -> " + parentNode.tableName;

                        for (int c = 1; c <= colCount; c++) {
                            String colName = md.getColumnName(c);
                            Object colVal = rs.getObject(c);
                            childNode.allRowValues.put(colName, colVal);
                        }

                        if (!childPks.isEmpty()) {
                            for (String pk : childPks) {
                                for (Map.Entry<String, Object> entry : childNode.allRowValues.entrySet()) {
                                    if (entry.getKey().equalsIgnoreCase(pk)) {
                                        childNode.keyValues.put(pk, entry.getValue());
                                        break;
                                    }
                                }
                            }
                        }
                        if (childNode.keyValues.isEmpty()) {
                            childNode.keyValues.putAll(childNode.allRowValues);
                        }

                        String fingerprint = childNode.tableName + "::" + childNode.keyValues.toString();
                        if (!visitedRows.contains(fingerprint)) {
                            visitedRows.add(fingerprint);
                            parentNode.children.add(childNode);
                            // Recurse to find grandchildren
                            inspectChildDependencies(childNode, visitedRows, depth + 1);
                        }
                    }
                }
            } catch (Exception ex) {
                // If child query fails, skip gracefully
            }
        }
    }

    private void collectBottomUpNodes(CascadeRowNode node, List<CascadeRowNode> list, Set<String> collected) {
        for (CascadeRowNode child : node.children) {
            collectBottomUpNodes(child, list, collected);
        }
        String fingerprint = node.tableName + "::" + node.keyValues.toString();
        if (!collected.contains(fingerprint)) {
            collected.add(fingerprint);
            list.add(node);
        }
    }

    public void executeCascadePlan(CascadePlan plan) throws SQLException {
        if (connection == null) throw new SQLException("Not connected to a database.");
        boolean origAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (PreparedDelete pd : plan.executableDeletes) {
                try (PreparedStatement pstmt = connection.prepareStatement(pd.sql)) {
                    for (int i = 0; i < pd.params.size(); i++) {
                        pstmt.setObject(i + 1, pd.params.get(i));
                    }
                    pstmt.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            try {
                connection.setAutoCommit(origAutoCommit);
            } catch (Exception e) {}
        }
    }
}

