package com.dbtool;

import com.dbtool.util.SqlSnippetsLibrary;
import com.dbtool.util.SqlSnippetsLibrary.Snippet;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SqlSnippetsLibraryTest {

    @Test
    public void testAllSnippetsLoaded() {
        List<Snippet> all = SqlSnippetsLibrary.getAllSnippets();
        assertNotNull(all);
        assertFalse(all.isEmpty(), "Snippets library should not be empty");
        assertTrue(all.size() >= 35, "Expected at least 35 comprehensive templates, found: " + all.size());

        for (Snippet s : all) {
            assertNotNull(s.getCategory(), "Category must not be null");
            assertNotNull(s.getTitle(), "Title must not be null");
            assertNotNull(s.getSql(), "SQL content must not be null for " + s.getTitle());
            assertFalse(s.getSql().trim().isEmpty(), "SQL content must not be empty for " + s.getTitle());
            assertNotNull(s.getDescription(), "Description must not be null for " + s.getTitle());
            assertNotNull(s.getDialect(), "Dialect must not be null for " + s.getTitle());
        }
    }

    @Test
    public void testTheSelectMachinery() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> selectMachinery = categories.get("🔥 1. The SELECT Machinery");
        assertNotNull(selectMachinery, "The SELECT Machinery category must exist");
        
        boolean hasLateral = selectMachinery.stream().anyMatch(s -> s.getSql().contains("LATERAL"));
        boolean hasTableSample = selectMachinery.stream().anyMatch(s -> s.getSql().contains("TABLESAMPLE"));
        boolean hasSkipLocked = selectMachinery.stream().anyMatch(s -> s.getSql().contains("SKIP LOCKED"));
        boolean hasNamedWindow = selectMachinery.stream().anyMatch(s -> s.getSql().contains("WINDOW w AS"));
        boolean hasFilter = selectMachinery.stream().anyMatch(s -> s.getSql().contains("FILTER (WHERE"));
        boolean hasDistinctOn = selectMachinery.stream().anyMatch(s -> s.getSql().contains("DISTINCT ON"));

        assertTrue(hasLateral, "Must have LATERAL join");
        assertTrue(hasTableSample, "Must have TABLESAMPLE");
        assertTrue(hasSkipLocked, "Must have SKIP LOCKED");
        assertTrue(hasNamedWindow, "Must have Named WINDOW clause");
        assertTrue(hasFilter, "Must have FILTER clause");
        assertTrue(hasDistinctOn, "Must have DISTINCT ON");
    }

    @Test
    public void testFrameAwareWindowFunctions() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> windowSnippets = categories.get("🔥 2. Frame-Aware Window Functions");
        assertNotNull(windowSnippets, "Frame-aware window functions category must exist");

        boolean hasExplicitRows = windowSnippets.stream().anyMatch(s -> s.getSql().contains("ROWS BETWEEN UNBOUNDED PRECEDING"));
        boolean hasMovingAvg = windowSnippets.stream().anyMatch(s -> s.getSql().contains("ROWS BETWEEN 6 PRECEDING"));
        boolean hasCenteredWindow = windowSnippets.stream().anyMatch(s -> s.getSql().contains("3 PRECEDING AND 3 FOLLOWING"));
        boolean hasExcludeCurrent = windowSnippets.stream().anyMatch(s -> s.getSql().contains("EXCLUDE CURRENT ROW"));
        boolean hasNavigational = windowSnippets.stream().anyMatch(s -> s.getSql().contains("NTILE") && s.getSql().contains("FIRST_VALUE"));

        assertTrue(hasExplicitRows, "Must have explicit ROWS running total");
        assertTrue(hasMovingAvg, "Must have moving average");
        assertTrue(hasCenteredWindow, "Must have centered window");
        assertTrue(hasExcludeCurrent, "Must have EXCLUDE CURRENT ROW");
        assertTrue(hasNavigational, "Must have NTILE and FIRST_VALUE");
    }

    @Test
    public void testRecursiveCtes() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> cteSnippets = categories.get("🔥 3. Recursive CTEs in Production");
        assertNotNull(cteSnippets, "Recursive CTE category must exist");

        boolean hasCycleDetection = cteSnippets.stream().anyMatch(s -> s.getSql().contains("ANY(path)"));
        boolean hasDateSeries = cteSnippets.stream().anyMatch(s -> s.getSql().contains("WITH RECURSIVE dates"));
        boolean hasHierarchy = cteSnippets.stream().anyMatch(s -> s.getSql().contains("org_chart"));

        assertTrue(hasCycleDetection, "Must have cycle detection with array path guard");
        assertTrue(hasDateSeries, "Must have date series generation");
        assertTrue(hasHierarchy, "Must have hierarchical org chart");
    }

    @Test
    public void testProductionConcurrencyAndLocks() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> concurrencySnippets = categories.get("🔥 4. Production Concurrency & Control");
        assertNotNull(concurrencySnippets, "Production Concurrency category must exist");

        boolean hasIsolation = concurrencySnippets.stream().anyMatch(s -> s.getSql().contains("SERIALIZABLE"));
        boolean hasAdvisory = concurrencySnippets.stream().anyMatch(s -> s.getSql().contains("pg_advisory_lock"));
        boolean hasListenNotify = concurrencySnippets.stream().anyMatch(s -> s.getSql().contains("LISTEN") && s.getSql().contains("NOTIFY"));
        boolean hasConcurrentRefresh = concurrencySnippets.stream().anyMatch(s -> s.getSql().contains("CONCURRENTLY"));

        assertTrue(hasIsolation, "Must have isolation level settings");
        assertTrue(hasAdvisory, "Must have PostgreSQL advisory locks");
        assertTrue(hasListenNotify, "Must have LISTEN/NOTIFY pub-sub");
        assertTrue(hasConcurrentRefresh, "Must have concurrent materialized view refresh");
    }

    @Test
    public void testEngineSpecificFeatures() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> engineSnippets = categories.get("🔥 5. Engine-Specific Features");
        assertNotNull(engineSnippets, "Engine-Specific Features category must exist");

        boolean hasJsonb = engineSnippets.stream().anyMatch(s -> s.getSql().contains("jsonb_path_query") || s.getSql().contains("@>"));
        boolean hasMySQL = engineSnippets.stream().anyMatch(s -> s.getSql().contains("JSON_TABLE") || s.getSql().contains("MATCH"));
        boolean hasOracle = engineSnippets.stream().anyMatch(s -> s.getSql().contains("CONNECT BY") || s.getSql().contains("MODEL"));
        boolean hasSqlServer = engineSnippets.stream().anyMatch(s -> s.getSql().contains("CROSS APPLY") || s.getSql().contains("OPENJSON"));
        boolean hasSnowflakeBQ = engineSnippets.stream().anyMatch(s -> s.getSql().contains("QUALIFY") || s.getSql().contains("UNNEST"));

        assertTrue(hasJsonb, "Must have PostgreSQL JSONB & path queries");
        assertTrue(hasMySQL, "Must have MySQL JSON_TABLE / MATCH");
        assertTrue(hasOracle, "Must have Oracle CONNECT BY / MODEL");
        assertTrue(hasSqlServer, "Must have SQL Server CROSS APPLY / OPENJSON");
        assertTrue(hasSnowflakeBQ, "Must have Snowflake / BigQuery QUALIFY / UNNEST");
    }

    @Test
    public void testIndexArchitectures() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> indexSnippets = categories.get("🔥 6. Index Architectures");
        assertNotNull(indexSnippets, "Index Architectures category must exist");

        boolean hasGinBrin = indexSnippets.stream().anyMatch(s -> s.getSql().contains("USING GIN") && s.getSql().contains("USING BRIN"));
        boolean hasPartial = indexSnippets.stream().anyMatch(s -> s.getSql().contains("WHERE status = 'open'"));
        boolean hasCovering = indexSnippets.stream().anyMatch(s -> s.getSql().contains("INCLUDE"));
        boolean hasIndexOnlyScan = indexSnippets.stream().anyMatch(s -> s.getSql().contains("Index Only Scan") || s.getSql().contains("EXPLAIN (ANALYZE, BUFFERS)"));

        assertTrue(hasGinBrin, "Must have GIN and BRIN index definitions");
        assertTrue(hasPartial, "Must have partial index");
        assertTrue(hasCovering, "Must have covering index with INCLUDE");
        assertTrue(hasIndexOnlyScan, "Must have index-only scan proof query");
    }

    @Test
    public void testPlannerAndOptimization() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> plannerSnippets = categories.get("🔥 7. Planner & Optimization Reality");
        assertNotNull(plannerSnippets, "Planner & Optimization category must exist");

        boolean hasExplainBuffers = plannerSnippets.stream().anyMatch(s -> s.getSql().contains("EXPLAIN (ANALYZE, BUFFERS"));
        boolean hasKnobs = plannerSnippets.stream().anyMatch(s -> s.getSql().contains("work_mem"));
        boolean hasHints = plannerSnippets.stream().anyMatch(s -> s.getSql().contains("INDEX") && s.getSql().contains("USE INDEX"));

        assertTrue(hasExplainBuffers, "Must have EXPLAIN ANALYZE BUFFERS");
        assertTrue(hasKnobs, "Must have session knobs");
        assertTrue(hasHints, "Must have optimizer hints");
    }

    @Test
    public void testSetTheoreticAndAnalyticMonsters() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> analyticSnippets = categories.get("🔥 8. Set-Theoretic & Analytic Monsters");
        assertNotNull(analyticSnippets, "Set-Theoretic category must exist");

        boolean hasGapsIslands = analyticSnippets.stream().anyMatch(s -> s.getSql().contains("ROW_NUMBER") && s.getSql().contains("INTERVAL '1 day'"));
        boolean hasDeduplication = analyticSnippets.stream().anyMatch(s -> s.getSql().contains("DELETE FROM") && s.getSql().contains("USING"));
        boolean hasSessionization = analyticSnippets.stream().anyMatch(s -> s.getSql().contains("session_id") && s.getSql().contains("LAG(ts)"));
        boolean hasMedian = analyticSnippets.stream().anyMatch(s -> s.getSql().contains("PERCENTILE_CONT(0.5)"));
        boolean hasTopN = analyticSnippets.stream().anyMatch(s -> s.getSql().contains("Approach 1") && s.getSql().contains("Approach 2") && s.getSql().contains("Approach 3"));

        assertTrue(hasGapsIslands, "Must have Gaps & Islands pattern");
        assertTrue(hasDeduplication, "Must have Deduplication keeping latest pattern");
        assertTrue(hasSessionization, "Must have 30-min sessionization pattern");
        assertTrue(hasMedian, "Must have Exact Median pattern");
        assertTrue(hasTopN, "Must have Top-N (3 distinct approaches)");
    }

    @Test
    public void testSecurityAndCorrectness() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> secSnippets = categories.get("🔥 9. Security & Correctness");
        assertNotNull(secSnippets, "Security category must exist");

        boolean hasRls = secSnippets.stream().anyMatch(s -> s.getSql().contains("ENABLE ROW LEVEL SECURITY"));
        boolean hasColumnGrants = secSnippets.stream().anyMatch(s -> s.getSql().contains("GRANT SELECT (id, name"));
        boolean hasPrepare = secSnippets.stream().anyMatch(s -> s.getSql().contains("PREPARE") && s.getSql().contains("EXECUTE"));
        boolean hasSafeDynamic = secSnippets.stream().anyMatch(s -> s.getSql().contains("EXECUTE format") && s.getSql().contains("%I"));

        assertTrue(hasRls, "Must have Row-Level Security RLS");
        assertTrue(hasColumnGrants, "Must have Column-Level Grants");
        assertTrue(hasPrepare, "Must have PREPARE & EXECUTE parameterized statements");
        assertTrue(hasSafeDynamic, "Must have Safe Dynamic SQL with %I");
    }

    @Test
    public void testBeyondStandardSql() {
        Map<String, List<Snippet>> categories = SqlSnippetsLibrary.getCategoryMap();
        List<Snippet> beyondSnippets = categories.get("🔥 10. Beyond Standard SQL");
        assertNotNull(beyondSnippets, "Beyond Standard SQL category must exist");

        boolean hasPlpgsql = beyondSnippets.stream().anyMatch(s -> s.getSql().contains("CREATE OR REPLACE FUNCTION") && s.getSql().contains("plpgsql"));
        boolean hasJsonPath = beyondSnippets.stream().anyMatch(s -> s.getSql().contains("jsonb_path_query") && s.getSql().contains("$.store.book"));
        boolean hasFdw = beyondSnippets.stream().anyMatch(s -> s.getSql().contains("FOREIGN DATA WRAPPER") && s.getSql().contains("CREATE FOREIGN TABLE"));
        boolean hasMatchRecognize = beyondSnippets.stream().anyMatch(s -> s.getSql().contains("MATCH_RECOGNIZE"));
        boolean hasSqlPgq = beyondSnippets.stream().anyMatch(s -> s.getSql().contains("GRAPH_TABLE") && s.getSql().contains("MATCH (src:Person"));

        assertTrue(hasPlpgsql, "Must have PL/pgSQL procedural dialect");
        assertTrue(hasJsonPath, "Must have SQL/JSON Path standard");
        assertTrue(hasFdw, "Must have SQL/MED Foreign Data Wrappers");
        assertTrue(hasMatchRecognize, "Must have SQL:2016 MATCH_RECOGNIZE");
        assertTrue(hasSqlPgq, "Must have SQL:2023 SQL/PGQ Property Graph Queries");
    }

    @Test
    public void testSearchFunctionality() {
        List<Snippet> lateralResults = SqlSnippetsLibrary.search("LATERAL");
        assertFalse(lateralResults.isEmpty(), "Searching 'LATERAL' should find snippets");

        List<Snippet> windowResults = SqlSnippetsLibrary.search("EXCLUDE");
        assertFalse(windowResults.isEmpty(), "Searching 'EXCLUDE' should find snippets");

        List<Snippet> jsonResults = SqlSnippetsLibrary.search("JSONB");
        assertFalse(jsonResults.isEmpty(), "Searching 'JSONB' should find snippets");

        List<Snippet> emptyQuery = SqlSnippetsLibrary.search("");
        assertEquals(SqlSnippetsLibrary.getAllSnippets().size(), emptyQuery.size());
    }

    @Test
    public void testPopupMenuPopulation() {
        JPopupMenu menu = new JPopupMenu();
        SqlSnippetsLibrary.populatePopupMenu(menu, sql -> {}, () -> {});
        assertTrue(menu.getComponentCount() > 0, "Popup menu should contain items");
    }

    @Test
    public void testSubMenuPopulation() {
        JMenu menu = new JMenu("Templates");
        SqlSnippetsLibrary.populateSubMenu(menu, sql -> {});
        assertTrue(menu.getItemCount() > 0, "Submenu should contain items");
    }
}
