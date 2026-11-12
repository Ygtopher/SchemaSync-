package com.dbtool.core.inspector;

import com.dbtool.core.model.SchemaMetadata;
import com.dbtool.core.model.TableMetadata;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface SchemaInspector {
    List<String> getTableNames(Connection connection, String schemaPattern) throws SQLException;
    TableMetadata inspectTable(Connection connection, String catalog, String schema, String tableName) throws SQLException;
    SchemaMetadata inspectSchema(Connection connection, String schemaName) throws SQLException;
}
