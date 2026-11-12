package com.dbtool.core.driver;

import com.dbtool.core.dialect.DialectRegistry;
import com.dbtool.core.dialect.DialectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class DriverClassLoaderManagerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        DriverClassLoaderManager.setDriversDirOverrideForTesting(null);
    }

    @Test
    void testIsolatedUrlClassLoaderDriverShimAndServiceLoaderDialect() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK JavaCompiler required for isolated JAR test");

        Path buildRoot = tempDir.resolve("plugin-build");
        Path srcDir = buildRoot.resolve("src");
        Path classesDir = buildRoot.resolve("classes");
        Files.createDirectories(srcDir.resolve("com/example/extdriver"));
        Files.createDirectories(classesDir);

        // Compile an external custom java.sql.Driver + custom DatabaseDialect NOT present on app classpath
        Path driverSrc = srcDir.resolve("com/example/extdriver/FakeIsolatedDriver.java");
        Files.writeString(driverSrc,
                "package com.example.extdriver;\n" +
                "import java.sql.*; import java.util.Properties; import java.util.logging.Logger;\n" +
                "public class FakeIsolatedDriver implements Driver {\n" +
                "    @Override public Connection connect(String url, Properties info) throws SQLException {\n" +
                "        if (!acceptsURL(url)) return null;\n" +
                "        return DriverManager.getConnection(\"jdbc:h2:mem:ext_backing;DB_CLOSE_DELAY=-1\", \"sa\", \"\");\n" +
                "    }\n" +
                "    @Override public boolean acceptsURL(String url) { return url != null && url.startsWith(\"jdbc:fakedb:\"); }\n" +
                "    @Override public DriverPropertyInfo[] getPropertyInfo(String u, Properties i) { return new DriverPropertyInfo[0]; }\n" +
                "    @Override public int getMajorVersion() { return 1; }\n" +
                "    @Override public int getMinorVersion() { return 0; }\n" +
                "    @Override public boolean jdbcCompliant() { return false; }\n" +
                "    @Override public Logger getParentLogger() { return Logger.getGlobal(); }\n" +
                "}\n",
                StandardCharsets.UTF_8);

        Path dialectSrc = srcDir.resolve("com/example/extdriver/FakeExtDialect.java");
        Files.writeString(dialectSrc,
                "package com.example.extdriver;\n" +
                "import com.dbtool.core.dialect.*;\n" +
                "import java.util.*;\n" +
                "public class FakeExtDialect extends GenericSqlDialect {\n" +
                "    @Override public List<String> getJdbcUrlPrefixes() { return Collections.singletonList(\"jdbc:fakedb:\"); }\n" +
                "    @Override public String quoteIdentifier(String id) { return \"~\" + id + \"~\"; }\n" +
                "}\n",
                StandardCharsets.UTF_8);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int rc = compiler.run(null, null, errBuf,
                "-d", classesDir.toString(),
                driverSrc.toString(),
                dialectSrc.toString());
        assertEquals(0, rc, "Plugin compile failed: " + errBuf.toString(StandardCharsets.UTF_8));

        // Package into external JAR with META-INF/services/com.dbtool.core.dialect.DatabaseDialect
        Path jarPath = buildRoot.resolve("fake-ext-driver.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String cls : new String[]{"FakeIsolatedDriver.class", "FakeExtDialect.class"}) {
                jos.putNextEntry(new JarEntry("com/example/extdriver/" + cls));
                jos.write(Files.readAllBytes(classesDir.resolve("com/example/extdriver/" + cls)));
                jos.closeEntry();
            }
            jos.putNextEntry(new JarEntry("META-INF/services/com.dbtool.core.dialect.DatabaseDialect"));
            jos.write("com.example.extdriver.FakeExtDialect\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }

        Path driversStore = tempDir.resolve("drivers-store");
        DriverClassLoaderManager.setDriversDirOverrideForTesting(driversStore);
        DriverClassLoaderManager mgr = DriverClassLoaderManager.getInstance();

        DriverDescriptor desc = new DriverDescriptor(
                "fake-ext", "Fake Isolated DB", DialectType.GENERIC,
                "com.example.extdriver.FakeIsolatedDriver", "jdbc:fakedb:", 9999,
                null, null, null, "fake-ext-driver.jar", false
        );

        DriverShim shim = mgr.installCustomDriverJar(desc, jarPath);
        assertNotNull(shim);
        assertTrue(mgr.isDriverAvailable(desc));
        assertTrue(Files.exists(driversStore.resolve("fake-ext-driver.jar")));
        assertTrue(Files.exists(driversStore.resolve("drivers.json")));

        // Verify DriverManager routes jdbc:fakedb: cleanly via DriverShim
        try (Connection conn = DriverManager.getConnection("jdbc:fakedb://localhost/demo")) {
            assertFalse(conn.isClosed());
        }

        // Verify ServiceLoader inside URLClassLoader registered the custom dialect for jdbc:fakedb:
        assertEquals("~col~", DialectRegistry.getInstance().resolveFromJdbcUrl("jdbc:fakedb://localhost/demo").quoteIdentifier("col"));

        // Cleanup removes non-built-in driver cleanly
        assertTrue(mgr.removeCustomDriver("fake-ext"));
        assertFalse(Files.exists(driversStore.resolve("fake-ext-driver.jar")));
    }
}
