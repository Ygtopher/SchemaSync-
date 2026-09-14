package com.dbtool;
import java.sql.*;
import com.dbtool.*;

public class test_main {
    public static void main(String[] args) {
        try {
            DatabaseManager db = new DatabaseManager();
            String url = "jdbc:postgresql://localhost:5432/fisa";
            db.connection = DriverManager.getConnection(url, "postgres", "postgres");
            
            java.util.List<String> tables = db.getTableNames();
            System.out.println("Tables found: " + tables.size());
            if (tables.size() > 0) {
                String tbl = tables.get(0);
                System.out.println("First table: [" + tbl + "]");
                String query = "SELECT * FROM " + db.quoteTableName(tbl) + " LIMIT 1000";
                System.out.println("Query: " + query);
                db.executeQuery(query);
                System.out.println("Success!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
