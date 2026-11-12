package com.dbtool.core.export;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Deflater;

public class FullDatabaseExporter {
    
    public static void exportDatabase(Connection sourceConn, String dbType, File targetFile, String format, ExportProgressMonitor monitor) throws Exception {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = sourceConn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String schema = null;
                try { schema = rs.getString("TABLE_SCHEM"); } catch (Exception e) {}
                
                if (schema != null && (schema.equalsIgnoreCase("information_schema") || schema.equalsIgnoreCase("pg_catalog"))) {
                    continue;
                }
                
                if (tableName != null && !tableName.toLowerCase().startsWith("pg_") && !tableName.toLowerCase().startsWith("sql_")) {
                    tables.add(tableName);
                }
            }
        }
        
        int totalRows = 0;
        try (Statement stmt = sourceConn.createStatement()) {
            for (String table : tables) {
                try (ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
                    if (rs2.next()) totalRows += rs2.getInt(1);
                } catch (Exception e) {}
            }
        }
        final int finalTotalRows = totalRows;
        
        if (format.equalsIgnoreCase("accdb")) {
            exportToAccess(sourceConn, tables, targetFile, monitor, finalTotalRows);
        } else {
            exportToSql(sourceConn, tables, targetFile, format.equalsIgnoreCase("sql.gz") || format.equalsIgnoreCase("sqlgz"), monitor, finalTotalRows);
        }
    }
    
    private static void exportToSql(Connection sourceConn, List<String> tables, File targetFile, boolean gzip, ExportProgressMonitor monitor, int totalRows) throws Exception {
        OutputStream os = new FileOutputStream(targetFile);
        if (gzip) {
            os = new GZIPOutputStream(os) {
                { def.setLevel(Deflater.BEST_COMPRESSION); }
            };
        }
        
        boolean origAutoCommit = sourceConn.getAutoCommit();
        try {
            sourceConn.setAutoCommit(false); // Required for setFetchSize to work in PostgreSQL
        } catch (Exception e) {}
        
        int processedRows = 0;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"), 1024 * 1024)) {
            for (String table : tables) {
                if (monitor != null && monitor.isCancelled()) return;
                writer.write("-- Table: " + table + "\n");
                writer.write("DROP TABLE IF EXISTS \"" + table + "\";\n");
                
                // Get Schema
                try (Statement stmt = sourceConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + table + "\" LIMIT 0")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    StringBuilder create = new StringBuilder("CREATE TABLE \"" + table + "\" (\n");
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        create.append("    \"").append(rsmd.getColumnName(i)).append("\" ")
                              .append(rsmd.getColumnTypeName(i));
                        if (i < rsmd.getColumnCount()) create.append(",\n");
                    }
                    create.append("\n);\n\n");
                    writer.write(create.toString());
                } catch (Exception e) {
                    System.err.println("Skipping schema for table " + table + ": " + e.getMessage());
                    continue;
                }
                
                // Get Data with Fetch Size and Multi-row insert
                try (Statement stmt = sourceConn.createStatement()) {
                    try { stmt.setFetchSize(10000); } catch (Exception e) {} // Speed up fetches
                    try (ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + table + "\"")) {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        int colCount = rsmd.getColumnCount();
                        
                        int batchCount = 0;
                        boolean first = true;
                        
                        while (rs.next()) {
                            if (monitor != null && monitor.isCancelled()) return;
                            
                            if (batchCount == 0) {
                                writer.write("INSERT INTO \"" + table + "\" VALUES\n");
                                first = true;
                            }
                            
                            if (!first) {
                                writer.write(",\n");
                            }
                            
                            StringBuilder row = new StringBuilder("(");
                            for (int i = 1; i <= colCount; i++) {
                                Object obj = rs.getObject(i);
                                if (obj == null) {
                                    row.append("NULL");
                                } else if (obj instanceof Number) {
                                    row.append(obj.toString());
                                } else if (obj instanceof Boolean) {
                                    row.append(((Boolean)obj) ? "true" : "false");
                                } else {
                                    row.append("'").append(obj.toString().replace("'", "''")).append("'");
                                }
                                if (i < colCount) row.append(", ");
                            }
                            row.append(")");
                            writer.write(row.toString());
                            
                            first = false;
                            batchCount++;
                            processedRows++;
                            
                            if (batchCount >= 500) {
                                writer.write(";\n");
                                batchCount = 0;
                            }
                            
                            if (monitor != null && processedRows % 2000 == 0) monitor.onProgress(processedRows, totalRows);
                        }
                        
                        if (batchCount > 0) {
                            writer.write(";\n");
                        }
                    }
                }
                writer.write("\n\n");
            }
            if (monitor != null) monitor.onProgress(processedRows, totalRows);
        } finally {
            try {
                sourceConn.setAutoCommit(origAutoCommit);
            } catch (Exception e) {}
        }
    }
    
    private static void exportToAccess(Connection sourceConn, List<String> tables, File targetFile, ExportProgressMonitor monitor, int totalRows) throws Exception {
        if (targetFile.exists()) {
            targetFile.delete();
        }
        
        boolean origAutoCommit = sourceConn.getAutoCommit();
        try {
            sourceConn.setAutoCommit(false); // Required for setFetchSize to work in PostgreSQL
        } catch (Exception e) {}
        
        String url = "jdbc:ucanaccess://" + targetFile.getAbsolutePath() + ";newDatabaseVersion=V2010";
        int processedRows = 0;
        try (Connection destConn = DriverManager.getConnection(url)) {
            destConn.setAutoCommit(false);
            for (String table : tables) {
                if (monitor != null && monitor.isCancelled()) return;
                // Create table
                try (Statement stmt = sourceConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + table + "\" LIMIT 0")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    StringBuilder create = new StringBuilder("CREATE TABLE \"" + table + "\" (");
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        String colName = rsmd.getColumnName(i);
                        String type = "VARCHAR(255)";
                        int typeCode = rsmd.getColumnType(i);
                        if (typeCode == Types.INTEGER || typeCode == Types.SMALLINT || typeCode == Types.TINYINT) type = "INTEGER";
                        else if (typeCode == Types.BIGINT) type = "BIGINT";
                        else if (typeCode == Types.DOUBLE || typeCode == Types.FLOAT || typeCode == Types.REAL || typeCode == Types.NUMERIC || typeCode == Types.DECIMAL) type = "DOUBLE";
                        else if (typeCode == Types.BOOLEAN || typeCode == Types.BIT) type = "YESNO";
                        else if (typeCode == Types.TIMESTAMP || typeCode == Types.DATE || typeCode == Types.TIME) type = "DATETIME";
                        else if (typeCode == Types.BLOB || typeCode == Types.BINARY || typeCode == Types.VARBINARY || typeCode == Types.LONGVARBINARY) type = "LONGBINARY";
                        else if (typeCode == Types.CLOB || typeCode == Types.LONGVARCHAR) type = "LONGTEXT";
                        
                        create.append("\"").append(colName).append("\" ").append(type);
                        if (i < rsmd.getColumnCount()) create.append(", ");
                    }
                    create.append(")");
                    try (Statement destStmt = destConn.createStatement()) {
                        destStmt.execute(create.toString());
                    }
                } catch (Exception e) {
                    System.err.println("Skipping table " + table + " for access export: " + e.getMessage());
                    continue;
                }
                
                // Insert data
                try (Statement stmt = sourceConn.createStatement()) {
                    try { stmt.setFetchSize(10000); } catch (Exception e) {}
                    try (ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + table + "\"")) {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        int colCount = rsmd.getColumnCount();
                        
                        StringBuilder insert = new StringBuilder("INSERT INTO \"" + table + "\" VALUES (");
                        for (int i = 1; i <= colCount; i++) {
                            insert.append("?");
                            if (i < colCount) insert.append(", ");
                        }
                        insert.append(")");
                        
                        try (PreparedStatement destPs = destConn.prepareStatement(insert.toString())) {
                            int batch = 0;
                            while (rs.next()) {
                                if (monitor != null && monitor.isCancelled()) return;
                                for (int i = 1; i <= colCount; i++) {
                                    destPs.setObject(i, rs.getObject(i));
                                }
                                destPs.addBatch();
                                if (++batch % 2000 == 0) {
                                    destPs.executeBatch();
                                    destConn.commit(); // Periodically commit memory for huge tables
                                }
                                processedRows++;
                                if (monitor != null && processedRows % 2000 == 0) monitor.onProgress(processedRows, totalRows);
                            }
                            if (batch % 2000 != 0) {
                                destPs.executeBatch();
                            }
                        }
                    }
                }
            }
            destConn.commit();
            if (monitor != null) monitor.onProgress(processedRows, totalRows);
        } finally {
            try {
                sourceConn.setAutoCommit(origAutoCommit);
            } catch (Exception e) {}
        }
    }
}
