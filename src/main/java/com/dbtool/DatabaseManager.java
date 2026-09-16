package com.dbtool;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import javax.swing.filechooser.FileSystemView;

public class DatabaseManager {
    public Connection connection;
    private java.util.Properties learnedJoins = new java.util.Properties();
    private File learnedJoinsFile;

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

    public void connect(String dbFilePath) throws Exception {
        // Fallback for old method without credentials
        connectPostgres(dbFilePath, "localhost", "5432", "postgres", "postgres", "analyzer_db");
    }

    public void connectPostgres(String dbFilePath, String host, String port, String user, String pass, String dbName) throws Exception {
        // Close existing connection if any
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }

        if (dbFilePath.toLowerCase().endsWith(".sql.gz") || dbFilePath.toLowerCase().endsWith(".sql")) {
            System.out.println("Connecting to PostgreSQL to create database...");
            
            // Connect to the default 'postgres' database to create the new one
            String baseUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
            try (Connection tempConn = DriverManager.getConnection(baseUrl, user, pass);
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
            String targetUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
            connection = DriverManager.getConnection(targetUrl, user, pass);
            
            importSqlDump(dbFilePath);
            
            // Reconnect AGAIN to clear any session-level variables (like search_path) altered by pg_dump
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = DriverManager.getConnection(targetUrl, user, pass);
            
        } else if (dbFilePath.toLowerCase().endsWith(".accdb") || dbFilePath.toLowerCase().endsWith(".mdb")) {
            System.out.println("Connecting to MS Access Database...");
            String url = "jdbc:ucanaccess://" + dbFilePath;
            connection = DriverManager.getConnection(url);
        } else {
            throw new SQLException("Unsupported file format. Please select .accdb, .mdb, .sql, or .sql.gz");
        }
    }

    public void connectExistingPostgres(String host, String port, String dbName, String user, String pass) throws Exception {
        String targetUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        connection = DriverManager.getConnection(targetUrl, user, pass);
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
        return tables;
    }

    public List<String> getColumnNames(String tableName) {
        List<String> columns = new ArrayList<>();
        if (connection == null || tableName == null) return columns;

        String schema = null;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            schema = parts[0];
            tableName = parts[1];
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getColumns(null, schema, tableName, "%");
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
            if (columns.isEmpty()) {
                rs = metaData.getColumns(null, schema, tableName.toUpperCase(), "%");
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
            if (columns.isEmpty()) {
                rs = metaData.getColumns(null, schema, tableName.toLowerCase(), "%");
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return columns;
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
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            String schemaA = null;
            String nameA = tableA;
            if (tableA.contains(".")) {
                schemaA = tableA.split("\\.")[0];
                nameA = tableA.split("\\.")[1];
            }
            
            String schemaB = null;
            String nameB = tableB;
            if (tableB.contains(".")) {
                schemaB = tableB.split("\\.")[0];
                nameB = tableB.split("\\.")[1];
            }

            // 1. Check if tableA has a foreign key pointing to tableB
            try (ResultSet rs = metaData.getImportedKeys(null, schemaA, nameA)) {
                while (rs.next()) {
                    String pkTable = rs.getString("PKTABLE_NAME");
                    if (nameB.equalsIgnoreCase(pkTable)) {
                        String fkColumn = rs.getString("FKCOLUMN_NAME");
                        String pkColumn = rs.getString("PKCOLUMN_NAME");
                        return new String[]{tableA + "." + fkColumn, tableB + "." + pkColumn};
                    }
                }
            }

            // 2. Check if tableB has a foreign key pointing to tableA
            try (ResultSet rs = metaData.getImportedKeys(null, schemaB, nameB)) {
                while (rs.next()) {
                    String pkTable = rs.getString("PKTABLE_NAME");
                    if (nameA.equalsIgnoreCase(pkTable)) {
                        String fkColumn = rs.getString("FKCOLUMN_NAME");
                        String pkColumn = rs.getString("PKCOLUMN_NAME");
                        return new String[]{tableA + "." + pkColumn, tableB + "." + fkColumn};
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        if (identifier.matches("^[a-z_][a-z0-9_]*$") && !SQL_KEYWORDS.contains(identifier)) {
            return identifier;
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public String quoteTableName(String tableName) {
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            return quoteIfNecessary(parts[0]) + "." + quoteIfNecessary(parts[1]);
        }
        return quoteIfNecessary(tableName);
    }

    public String quoteColumnName(String fullColumnName) {
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

    public DefaultTableModel executeQuery(String query) throws SQLException {
        if (connection == null) throw new SQLException("Not connected to a database.");

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_schema(), current_database()")) {
             if (rs.next()) {
                 System.out.println("Connection DB: " + rs.getString(2) + ", Schema: " + rs.getString(1));
             }
        }
        System.out.println("Executing Query: [" + query + "]");
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return buildTableModel(rs);
        }
    }

    public DefaultTableModel executeSearch(String tableName, String keyword, String cols, int limit) throws SQLException {
        if (connection == null) throw new SQLException("Not connected to a database.");

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

        try (PreparedStatement pstmt = connection.prepareStatement(query.toString())) {
            String searchPattern = "%" + keyword + "%";
            for (int i = 0; i < columns.size(); i++) {
                pstmt.setString(i + 1, searchPattern);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return buildTableModel(rs);
            }
        }
    }

    private DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();

        // names of columns
        Vector<String> columnNames = new Vector<>();
        int columnCount = metaData.getColumnCount();
        for (int column = 1; column <= columnCount; column++) {
            columnNames.add(metaData.getColumnLabel(column));
        }

        // data of the table
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> vector = new Vector<>();
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                vector.add(rs.getObject(columnIndex));
            }
            data.add(vector);
        }

        return new DefaultTableModel(data, columnNames);
    }
    public void saveConnectionProfile(String profileName, String host, String port, String user, String pass, String dbName) {
        java.util.Properties props = new java.util.Properties();
        java.io.File propFile = getSaveFile("saved_connections.properties");
        if (propFile.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {}
        }
        
        String encodedPass = java.util.Base64.getEncoder().encodeToString(pass.getBytes());
        String val = host + ";" + port + ";" + user + ";" + encodedPass + ";" + dbName;
        props.setProperty(profileName, val);
        
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(propFile)) {
            props.store(out, "Saved Connection Profiles");
        } catch (Exception e) {
            e.printStackTrace();
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
}
