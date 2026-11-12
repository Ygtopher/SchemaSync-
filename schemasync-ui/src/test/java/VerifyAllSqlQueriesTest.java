import com.dbtool.DatabaseManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class VerifyAllSqlQueriesTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting SQL Query Verification on Sandbox DB...");
        DatabaseManager dm = new DatabaseManager();
        dm.connectSandbox();

        String[] queries = new String[] {
            // 1. Basic SELECT
            "SELECT * FROM employees",
            "SELECT first_name, last_name FROM employees",
            "SELECT DISTINCT department FROM employees",
            "SELECT first_name AS fname, salary * 12 AS annual_salary FROM employees",

            // 2. Filter & Sort
            "SELECT * FROM employees WHERE salary > 50000",
            "SELECT * FROM employees WHERE dept = 'IT' AND salary > 60000",
            "SELECT * FROM employees WHERE dept IN ('IT', 'HR', 'Finance')",
            "SELECT * FROM employees WHERE name LIKE 'A%'",
            "SELECT * FROM employees WHERE salary BETWEEN 40000 AND 80000",
            "SELECT * FROM employees WHERE manager_id IS NULL",
            "SELECT * FROM employees ORDER BY salary DESC LIMIT 10 OFFSET 2",

            // 3. Aggregation & Grouping
            "SELECT dept, COUNT(*), AVG(salary), SUM(salary), MIN(salary), MAX(salary) FROM employees GROUP BY dept HAVING AVG(salary) > 50000",

            // 4. Joins
            "SELECT e.name, d.dept_name FROM employees e INNER JOIN departments d ON e.dept_id = d.id",
            "SELECT e.name, d.dept_name FROM employees e LEFT JOIN departments d ON e.dept_id = d.id",
            "SELECT e.name, d.dept_name FROM employees e RIGHT JOIN departments d ON e.dept_id = d.id",
            "SELECT e.name, d.dept_name FROM employees e FULL OUTER JOIN departments d ON e.dept_id = d.id",
            "SELECT * FROM employees CROSS JOIN departments",
            "SELECT e.name AS employee, m.name AS manager FROM employees e LEFT JOIN employees m ON e.manager_id = m.id",

            // 5. Subqueries
            "SELECT name FROM employees WHERE salary = (SELECT MAX(salary) FROM employees)",
            "SELECT name FROM employees e WHERE salary > (SELECT AVG(salary) FROM employees WHERE dept_id = e.dept_id)",
            "SELECT * FROM employees WHERE dept_id IN (SELECT id FROM departments WHERE location = 'NYC')",
            "SELECT * FROM employees e WHERE EXISTS (SELECT 1 FROM projects p WHERE p.emp_id = e.id)",

            // 6. Set Operations
            "SELECT name FROM employees UNION SELECT name FROM contractors",
            "SELECT name FROM employees UNION ALL SELECT name FROM contractors",
            "SELECT name FROM employees INTERSECT SELECT name FROM contractors",
            "SELECT name FROM employees EXCEPT SELECT name FROM contractors",

            // 7. CTE & Window Functions
            "WITH high_earners AS ( SELECT * FROM employees WHERE salary > 100000 ) SELECT dept, COUNT(*) FROM high_earners GROUP BY dept",
            "WITH RECURSIVE org_chart AS ( SELECT id, name, manager_id, 1 AS level FROM employees WHERE manager_id IS NULL UNION ALL SELECT e.id, e.name, e.manager_id, oc.level + 1 FROM employees e JOIN org_chart oc ON e.manager_id = oc.id ) SELECT * FROM org_chart",
            "SELECT name, dept, salary, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn, RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS rnk, DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS drnk, SUM(salary) OVER (PARTITION BY dept) AS dept_total, LAG(salary) OVER (ORDER BY hire_date) AS prev_salary, LEAD(salary) OVER (ORDER BY hire_date) AS next_salary FROM employees",

            // 8. Advanced SELECT
            "SELECT name, CASE WHEN salary < 50000 THEN 'Low' WHEN salary < 100000 THEN 'Medium' ELSE 'High' END AS band FROM employees",
            "SELECT * FROM sales PIVOT (SUM(amount) FOR quarter IN ([Q1],[Q2],[Q3],[Q4])) AS p",
            "SELECT dept, region, SUM(sales) FROM orders GROUP BY ROLLUP(dept, region)",
            "SELECT dept, region, SUM(sales) FROM orders GROUP BY CUBE(dept, region)",
            "SELECT dept, region, SUM(sales) FROM orders GROUP BY GROUPING SETS ((dept), (region), ())",

            // 9. DML
            "INSERT INTO employees (name, dept, salary) VALUES ('Alice New', 'IT', 75000)",
            "INSERT INTO employees (name, dept) SELECT name, dept FROM new_hires",
            "INSERT INTO employees (id, name) VALUES (101,'A'), (102,'B'), (103,'C')",
            "UPDATE employees SET salary = salary * 1.10 WHERE dept = 'IT'",
            "DELETE FROM employees WHERE hire_date < '2010-01-01'",
            "DELETE FROM employees WHERE dept_id IN (SELECT id FROM departments WHERE closed = 1)",
            "MERGE INTO accounts t USING (SELECT 1 AS id, 999.0 AS balance) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET t.balance = s.balance WHEN NOT MATCHED THEN INSERT (id, balance) VALUES (s.id, s.balance)",
            "INSERT INTO accounts (id, balance) VALUES (1, 888.0) ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance",

            // 10. DDL
            "CREATE TABLE orders2 ( id INT PRIMARY KEY, amount DECIMAL(10,2) CHECK (amount > 0), status VARCHAR(20) DEFAULT 'pending' )",
            "CREATE INDEX idx_emp_dept ON employees(dept_id)",
            "CREATE VIEW high_earners_view AS SELECT id, name, salary FROM employees WHERE salary > 100000",
            "ALTER TABLE employees ADD COLUMN phone VARCHAR(20)",
            "ALTER TABLE employees DROP COLUMN phone",
            "TRUNCATE TABLE orders2",

            // 11. TCL & Meta
            "SAVEPOINT sp1",
            "EXPLAIN SELECT * FROM employees WHERE dept = 'IT'",
            "SHOW TABLES"
        };

        int passed = 0;
        try (Statement stmt = dm.connection.createStatement()) {
            for (int i = 0; i < queries.length; i++) {
                String q = queries[i];
                String translated = com.dbtool.panels.SqlEditorPanel.translateCompatibilitySql(q, dm);
                try {
                    boolean hasRs = stmt.execute(translated);
                    if (hasRs) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            int count = 0;
                            while (rs.next()) count++;
                            System.out.println("  [PASS #" + (i + 1) + "] " + q.substring(0, Math.min(45, q.length())) + "... -> " + count + " rows returned");
                        }
                    } else {
                        int affected = stmt.getUpdateCount();
                        System.out.println("  [PASS #" + (i + 1) + "] " + q.substring(0, Math.min(45, q.length())) + "... -> affected: " + affected);
                    }
                    passed++;
                } catch (Exception ex) {
                    System.err.println("  [FAIL #" + (i + 1) + "] " + q + " -> " + ex.getMessage());
                }
            }
        }

        System.out.println("\nRESULTS: " + passed + " / " + queries.length + " queries PASSED!");
        if (passed == queries.length) {
            System.out.println("ALL QUERIES FROM USER GUIDE VERIFIED WORKING!");
        } else {
            System.exit(1);
        }
    }
}
