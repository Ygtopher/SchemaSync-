package com.dbtool.core.inspector;

import com.dbtool.core.model.*;
import java.sql.*;
import java.util.*;

public class JdbcSchemaInspector implements SchemaInspector {
    @Override
    public List<String> getTableNames(Connection connection, String schemaPattern) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, schemaPattern, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!name.startsWith("~") && !name.startsWith("MSys")) {
                    tableNames.add(name);
                }
            }
        }
        return tableNames;
    }

    @Override
    public TableMetadata inspectTable(Connection connection, String catalog, String schema, String tableName) throws SQLException {
        TableMetadata table = new TableMetadata(schema, tableName);
        DatabaseMetaData meta = connection.getMetaData();

        Set<String> pkColumns = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME").toUpperCase());
            }
        }

        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnMetadata col = new ColumnMetadata();
                String colName = rs.getString("COLUMN_NAME");
                col.setColumnName(colName);
                col.setTypeName(rs.getString("TYPE_NAME"));
                col.setJdbcType(rs.getInt("DATA_TYPE"));
                col.setColumnSize(rs.getInt("COLUMN_SIZE"));
                col.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
                col.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                col.setDefaultValue(rs.getString("COLUMN_DEF"));
                col.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                col.setPrimaryKey(pkColumns.contains(colName.toUpperCase()));
                table.addColumn(col);
            }
        }
        return table;
    }

    @Override
    public SchemaMetadata inspectSchema(Connection connection, String schemaName) throws SQLException {
        SchemaMetadata schema = new SchemaMetadata(schemaName);
        List<String> tables = getTableNames(connection, schemaName);
        for (String tbl : tables) {
            schema.addTable(inspectTable(connection, null, schemaName, tbl));
        }
        return schema;
    }
}
