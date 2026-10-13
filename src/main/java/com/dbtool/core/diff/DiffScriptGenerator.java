package com.dbtool.core.diff;

import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.TableMetadata;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DiffScriptGenerator {

    /**
     * Generates a SQL DDL migration script to sync Target DB (Database B) to match Source DB (Database A).
     */
    public static String generateSchemaSyncScript(SchemaDiffResult diffResult, String sourceDbName, String targetDbName) {
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        sb.append("-- ====================================================================\n");
        sb.append("-- SchemaSync Migration DDL Script\n");
        sb.append("-- Generated: ").append(timestamp).append("\n");
        sb.append("-- Source DB (A): ").append(sourceDbName != null ? sourceDbName : "Database A").append("\n");
        sb.append("-- Target DB (B): ").append(targetDbName != null ? targetDbName : "Database B").append("\n");
        sb.append("-- Purpose: Synchronize Target DB (B) to match Source DB (A)\n");
        sb.append("-- ====================================================================\n\n");

        if (diffResult == null || diffResult.getTableDiffs().isEmpty()) {
            sb.append("-- No schema differences detected. Both databases are structurally identical.\n");
            return sb.toString();
        }

        List<TableDiff> missingInTarget = new ArrayList<>();
        List<TableDiff> modifiedTables = new ArrayList<>();
        List<TableDiff> missingInSource = new ArrayList<>();

        for (TableDiff td : diffResult.getTableDiffs()) {
            if (td.getType() == DifferenceType.REMOVED) {
                // Exists in Source (A), missing in Target (B) -> Needs CREATE TABLE in B
                missingInTarget.add(td);
            } else if (td.getType() == DifferenceType.ADDED) {
                // Exists only in Target (B), not in Source (A) -> Extra table in B
                missingInSource.add(td);
            } else if (td.getType() == DifferenceType.MODIFIED) {
                modifiedTables.add(td);
            }
        }

        // 1. Create Missing Tables in Target
        if (!missingInTarget.isEmpty()) {
            sb.append("-- --------------------------------------------------------------------\n");
            sb.append("-- 1. CREATE TABLES (Missing in Target DB)\n");
            sb.append("-- --------------------------------------------------------------------\n\n");
            for (TableDiff td : missingInTarget) {
                TableMetadata tm = td.getSourceTable();
                if (tm == null) continue;
                sb.append(generateCreateTableDdl(tm)).append("\n\n");
            }
        }

        // 2. Alter Modified Tables
        if (!modifiedTables.isEmpty()) {
            sb.append("-- --------------------------------------------------------------------\n");
            sb.append("-- 2. ALTER TABLES (Column Additions, Type Mismatches, Nullability)\n");
            sb.append("-- --------------------------------------------------------------------\n\n");
            for (TableDiff td : modifiedTables) {
                sb.append("-- Table: ").append(td.getTableName()).append("\n");
                for (ColumnDiff cd : td.getColumnDiffs()) {
                    if (cd.getType() == DifferenceType.REMOVED) {
                        // Exists in A, missing in B -> ADD COLUMN to B
                        ColumnMetadata col = cd.getSourceColumn();
                        if (col != null) {
                            sb.append("ALTER TABLE ").append(quote(td.getTableName()))
                              .append(" ADD COLUMN ").append(quote(col.getColumnName()))
                              .append(" ").append(formatColumnType(col));
                            if (!col.isNullable()) sb.append(" NOT NULL");
                            if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
                                sb.append(" DEFAULT ").append(col.getDefaultValue());
                            }
                            sb.append(";\n");
                        }
                    } else if (cd.getType() == DifferenceType.MODIFIED) {
                        ColumnMetadata sCol = cd.getSourceColumn();
                        ColumnMetadata tCol = cd.getTargetColumn();
                        if (sCol != null && tCol != null) {
                            if (!sCol.getTypeName().equalsIgnoreCase(tCol.getTypeName())) {
                                sb.append("ALTER TABLE ").append(quote(td.getTableName()))
                                  .append(" ALTER COLUMN ").append(quote(sCol.getColumnName()))
                                  .append(" TYPE ").append(formatColumnType(sCol)).append(";\n");
                            }
                            if (sCol.isNullable() != tCol.isNullable()) {
                                sb.append("ALTER TABLE ").append(quote(td.getTableName()))
                                  .append(" ALTER COLUMN ").append(quote(sCol.getColumnName()))
                                  .append(sCol.isNullable() ? " DROP NOT NULL;\n" : " SET NOT NULL;\n");
                            }
                        }
                    } else if (cd.getType() == DifferenceType.ADDED) {
                        // Exists in B, but not in A -> Optional DROP COLUMN
                        sb.append("-- Optional: Column '").append(cd.getColumnName())
                          .append("' exists in Target but not in Source\n");
                        sb.append("-- ALTER TABLE ").append(quote(td.getTableName()))
                          .append(" DROP COLUMN ").append(quote(cd.getColumnName())).append(";\n");
                    }
                }
                sb.append("\n");
            }
        }

        // 3. Extra Tables in Target
        if (!missingInSource.isEmpty()) {
            sb.append("-- --------------------------------------------------------------------\n");
            sb.append("-- 3. EXTRA TABLES IN TARGET (Not present in Source DB)\n");
            sb.append("-- --------------------------------------------------------------------\n");
            for (TableDiff td : missingInSource) {
                sb.append("-- Optional: DROP TABLE ").append(quote(td.getTableName())).append(";\n");
            }
            sb.append("\n");
        }

        sb.append("-- ====================================================================\n");
        sb.append("-- End of Migration Script\n");
        sb.append("-- ====================================================================\n");
        return sb.toString();
    }

    /**
     * Generates CREATE TABLE DDL from TableMetadata.
     */
    public static String generateCreateTableDdl(TableMetadata table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quote(table.getTableName())).append(" (\n");

        List<String> pkCols = new ArrayList<>();
        List<String> colDefs = new ArrayList<>();

        for (ColumnMetadata col : table.getColumns()) {
            StringBuilder cDef = new StringBuilder();
            cDef.append("  ").append(quote(col.getColumnName())).append(" ").append(formatColumnType(col));
            if (!col.isNullable()) {
                cDef.append(" NOT NULL");
            }
            if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
                cDef.append(" DEFAULT ").append(col.getDefaultValue());
            }
            colDefs.add(cDef.toString());
            if (col.isPrimaryKey()) {
                pkCols.add(quote(col.getColumnName()));
            }
        }

        sb.append(String.join(",\n", colDefs));

        if (!pkCols.isEmpty()) {
            sb.append(",\n  PRIMARY KEY (").append(String.join(", ", pkCols)).append(")");
        }

        sb.append("\n);");
        return sb.toString();
    }

    /**
     * Generates a SQL Rollback (UNDO) script to revert changes applied by generateSchemaSyncScript.
     */
    public static String generateRollbackScript(SchemaDiffResult diffResult, String sourceDbName, String targetDbName) {
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        sb.append("-- ====================================================================\n");
        sb.append("-- SchemaSync ROLLBACK (UNDO) Script\n");
        sb.append("-- Generated: ").append(timestamp).append("\n");
        sb.append("-- Target DB (B): ").append(targetDbName != null ? targetDbName : "Database B").append("\n");
        sb.append("-- Purpose: Revert changes made during migration back to previous schema state\n");
        sb.append("-- ====================================================================\n\n");

        if (diffResult == null || diffResult.getTableDiffs().isEmpty()) {
            sb.append("-- No schema differences detected. No rollback operations required.\n");
            return sb.toString();
        }

        List<TableDiff> createdInTarget = new ArrayList<>();
        List<TableDiff> modifiedTables = new ArrayList<>();

        for (TableDiff td : diffResult.getTableDiffs()) {
            if (td.getType() == DifferenceType.REMOVED) {
                // Was missing in B, forward script created it -> Rollback DROPS it
                createdInTarget.add(td);
            } else if (td.getType() == DifferenceType.MODIFIED) {
                modifiedTables.add(td);
            }
        }

        // 1. Revert Altered Columns (Restore original Target column definitions)
        if (!modifiedTables.isEmpty()) {
            sb.append("-- --------------------------------------------------------------------\n");
            sb.append("-- 1. REVERT ALTERED TABLES (Drop Added Columns, Restore Original Types)\n");
            sb.append("-- --------------------------------------------------------------------\n\n");
            for (TableDiff td : modifiedTables) {
                sb.append("-- Table: ").append(td.getTableName()).append("\n");
                for (ColumnDiff cd : td.getColumnDiffs()) {
                    if (cd.getType() == DifferenceType.REMOVED) {
                        // Forward script added this column to B -> Rollback DROPS it
                        ColumnMetadata col = cd.getSourceColumn();
                        if (col != null) {
                            sb.append("ALTER TABLE ").append(quote(td.getTableName()))
                              .append(" DROP COLUMN ").append(quote(col.getColumnName())).append(";\n");
                        }
                    } else if (cd.getType() == DifferenceType.MODIFIED) {
                        // Forward script changed type to Source -> Rollback restores Target type
                        ColumnMetadata sCol = cd.getSourceColumn();
                        ColumnMetadata tCol = cd.getTargetColumn();
                        if (sCol != null && tCol != null) {
                            if (!sCol.getTypeName().equalsIgnoreCase(tCol.getTypeName())) {
                                sb.append("ALTER TABLE ").append(quote(td.getTableName()))
                                  .append(" ALTER COLUMN ").append(quote(tCol.getColumnName()))
                                  .append(" TYPE ").append(formatColumnType(tCol)).append(";\n");
                            }
                            if (sCol.isNullable() != tCol.isNullable()) {
                                sb.append("ALTER TABLE ").append(quote(td.getTableName()))
                                  .append(" ALTER COLUMN ").append(quote(tCol.getColumnName()))
                                  .append(tCol.isNullable() ? " DROP NOT NULL;\n" : " SET NOT NULL;\n");
                            }
                        }
                    }
                }
                sb.append("\n");
            }
        }

        // 2. Drop Newly Created Tables
        if (!createdInTarget.isEmpty()) {
            sb.append("-- --------------------------------------------------------------------\n");
            sb.append("-- 2. DROP NEWLY CREATED TABLES\n");
            sb.append("-- --------------------------------------------------------------------\n\n");
            for (TableDiff td : createdInTarget) {
                sb.append("DROP TABLE IF EXISTS ").append(quote(td.getTableName())).append(" CASCADE;\n");
            }
            sb.append("\n");
        }

        sb.append("-- ====================================================================\n");
        sb.append("-- End of Rollback Script\n");
        sb.append("-- ====================================================================\n");
        return sb.toString();
    }

    private static String formatColumnType(ColumnMetadata col) {
        String type = col.getTypeName() != null ? col.getTypeName().toUpperCase() : "VARCHAR";
        if (type.equals("VARCHAR") || type.equals("CHAR") || type.equals("CHARACTER VARYING")) {
            int size = col.getColumnSize() > 0 ? col.getColumnSize() : 255;
            return "VARCHAR(" + size + ")";
        }
        if (type.equals("DECIMAL") || type.equals("NUMERIC")) {
            int size = col.getColumnSize() > 0 ? col.getColumnSize() : 10;
            int digits = col.getDecimalDigits() >= 0 ? col.getDecimalDigits() : 2;
            return type + "(" + size + "," + digits + ")";
        }
        return type;
    }

    private static String quote(String id) {
        if (id == null) return "";
        if (id.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return id;
        }
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }
}
