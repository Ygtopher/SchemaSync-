package com.dbtool.core.inspector;

import com.dbtool.core.model.ColumnMetadata;
import com.dbtool.core.model.SchemaMetadata;
import com.dbtool.core.model.TableMetadata;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcSchemaInspector implements SchemaInspector {
    @Override
    public List<String> getTableNames(Connection connection, String schemaPattern) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
        }
        return tableNames;
    }

    @Override
    public TableMetadata inspectTable(Connection connection, String catalog, String schema, String tableName) throws SQLException {
        TableMetadata table = new TableMetadata(schema, tableName);
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnMetadata col = new ColumnMetadata();
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setTypeName(rs.getString("TYPE_NAME"));
                col.setJdbcType(rs.getInt("DATA_TYPE"));
                col.setColumnSize(rs.getInt("COLUMN_SIZE"));
                col.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
                col.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                col.setDefaultValue(rs.getString("COLUMN_DEF"));
                col.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
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
