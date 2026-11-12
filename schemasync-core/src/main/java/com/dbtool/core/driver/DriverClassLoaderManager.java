package com.dbtool.core.driver;

import com.dbtool.core.dialect.DialectRegistry;
import com.dbtool.core.dialect.DialectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages externalized JDBC driver descriptors, isolated {@link URLClassLoader} classloading,
 * {@link DriverShim} registration with {@link DriverManager}, and Maven Central downloads under
 * {@code ~/.schemasync/drivers/}.
 */
public class DriverClassLoaderManager {

    private static final DriverClassLoaderManager INSTANCE = new DriverClassLoaderManager();
    private static volatile Path driversDirOverride;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, DriverDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, URLClassLoader> activeLoaders = new ConcurrentHashMap<>();
    private final Map<String, DriverShim> registeredShims = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    private DriverClassLoaderManager() {}

    public static DriverClassLoaderManager getInstance() {
        INSTANCE.ensureInitialized();
        return INSTANCE;
    }

    /**
     * Test hook for redirecting driver descriptor storage & isolated JAR directory.
     */
    public static synchronized void setDriversDirOverrideForTesting(Path dir) {
        INSTANCE.closeAllLoaders();
        driversDirOverride = dir;
        INSTANCE.descriptors.clear();
        INSTANCE.initialized = false;
    }

    public static Path getDriversDirectory() {
        if (driversDirOverride != null) {
            return driversDirOverride;
        }
        return Paths.get(System.getProperty("user.home"), ".schemasync", "drivers");
    }

    private synchronized void ensureInitialized() {
        if (initialized) return;
        try {
            Files.createDirectories(getDriversDirectory());
            seedDefaultDescriptors();
            loadPersistedDescriptors();
            initialized = true;
        } catch (IOException e) {
            seedDefaultDescriptors();
            initialized = true;
        }
    }

    private void seedDefaultDescriptors() {
        List<DriverDescriptor> defaults = Arrays.asList(
                new DriverDescriptor("postgresql", "PostgreSQL", DialectType.POSTGRESQL,
                        "org.postgresql.Driver", "jdbc:postgresql:", 5432,
                        "org.postgresql", "postgresql", "42.7.2", "postgresql-42.7.2.jar", true),
                new DriverDescriptor("mysql", "MySQL", DialectType.MYSQL,
                        "com.mysql.cj.jdbc.Driver", "jdbc:mysql:", 3306,
                        "com.mysql", "mysql-connector-j", "8.3.0", "mysql-connector-j-8.3.0.jar", true),
                new DriverDescriptor("mariadb", "MariaDB", DialectType.MYSQL,
                        "org.mariadb.jdbc.Driver", "jdbc:mariadb:", 3306,
                        "org.mariadb.jdbc", "mariadb-java-client", "3.3.3", "mariadb-java-client-3.3.3.jar", false),
                new DriverDescriptor("sqlite", "SQLite", DialectType.SQLITE,
                        "org.sqlite.JDBC", "jdbc:sqlite:", -1,
                        "org.xerial", "sqlite-jdbc", "3.45.2.0", "sqlite-jdbc-3.45.2.0.jar", true),
                new DriverDescriptor("h2", "H2 Database", DialectType.H2,
                        "org.h2.Driver", "jdbc:h2:", 9092,
                        "com.h2database", "h2", "2.2.224", "h2-2.2.224.jar", true),
                new DriverDescriptor("ms-access", "MS Access (UCanAccess)", DialectType.ACCESS,
                        "net.ucanaccess.jdbc.UcanaccessDriver", "jdbc:ucanaccess:", -1,
                        "net.sf.ucanaccess", "ucanaccess", "5.0.1", "ucanaccess-5.0.1.jar", true),
                new DriverDescriptor("duckdb", "DuckDB", DialectType.POSTGRESQL,
                        "org.duckdb.DuckDBDriver", "jdbc:duckdb:", -1,
                        "org.duckdb", "duckdb_jdbc", "1.1.3", "duckdb_jdbc-1.1.3.jar", false),
                new DriverDescriptor("sqlserver", "Microsoft SQL Server", DialectType.GENERIC,
                        "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver:", 1433,
                        "com.microsoft.sqlserver", "mssql-jdbc", "12.6.1.jre11", "mssql-jdbc-12.6.1.jre11.jar", false),
                new DriverDescriptor("oracle", "Oracle Database", DialectType.GENERIC,
                        "oracle.jdbc.OracleDriver", "jdbc:oracle:", 1521,
                        "com.oracle.database.jdbc", "ojdbc11", "23.4.0.24.05", "ojdbc11-23.4.0.24.05.jar", false)
        );
        for (DriverDescriptor d : defaults) {
            descriptors.put(d.getId(), d);
        }
    }

    private void loadPersistedDescriptors() {
        Path jsonPath = getDriversDirectory().resolve("drivers.json");
        if (!Files.exists(jsonPath)) return;
        try {
            DriverDescriptor[] loaded = mapper.readValue(jsonPath.toFile(), DriverDescriptor[].class);
            if (loaded != null) {
                for (DriverDescriptor d : loaded) {
                    if (d != null && d.getId() != null) {
                        descriptors.put(d.getId(), d);
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private synchronized void persistDescriptors() throws IOException {
        Files.createDirectories(getDriversDirectory());
        Path jsonPath = getDriversDirectory().resolve("drivers.json");
        mapper.writeValue(jsonPath.toFile(), descriptors.values());
    }

    public synchronized List<DriverDescriptor> getDrivers() {
        ensureInitialized();
        return Collections.unmodifiableList(new ArrayList<>(descriptors.values()));
    }

    public synchronized DriverDescriptor getDriver(String id) {
        ensureInitialized();
        return id != null ? descriptors.get(id) : null;
    }

    public synchronized boolean isDriverAvailable(DriverDescriptor desc) {
        if (desc == null) return false;
        if (registeredShims.containsKey(desc.getId())) {
            return true;
        }
        if (desc.getJarFileName() != null) {
            Path jarPath = getDriversDirectory().resolve(desc.getJarFileName());
            if (Files.exists(jarPath)) {
                return true;
            }
        }
        if (desc.getDriverClassName() != null) {
            try {
                Class.forName(desc.getDriverClassName(), false, DriverClassLoaderManager.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException ignored) {}
        }
        return false;
    }

    /**
     * Installs an external JAR into {@code ~/.schemasync/drivers/}, loads the JDBC driver inside
     * an isolated {@link URLClassLoader}, wraps it in {@link DriverShim}, registers it with
     * {@link DriverManager}, and discovers any {@code META-INF/services} dialects shipped in the JAR.
     */
    public synchronized DriverShim installCustomDriverJar(DriverDescriptor descriptor, Path sourceJar)
            throws IOException, ReflectiveOperationException, SQLException {
        ensureInitialized();
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(sourceJar, "sourceJar must not be null");
        if (!Files.isRegularFile(sourceJar)) {
            throw new IOException("Driver JAR not found: " + sourceJar);
        }

        String fileName = (descriptor.getJarFileName() != null && !descriptor.getJarFileName().trim().isEmpty())
                ? Paths.get(descriptor.getJarFileName()).getFileName().toString()
                : sourceJar.getFileName().toString();
        descriptor.setJarFileName(fileName);

        Files.createDirectories(getDriversDirectory());
        Path targetJar = getDriversDirectory().resolve(fileName);
        if (!sourceJar.toAbsolutePath().normalize().equals(targetJar.toAbsolutePath().normalize())) {
            Files.copy(sourceJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
        }

        DriverShim shim = loadAndRegisterJar(descriptor, targetJar);
        descriptors.put(descriptor.getId(), descriptor);
        persistDescriptors();
        return shim;
    }

    public synchronized DriverShim loadDriver(String driverId)
            throws IOException, ReflectiveOperationException, SQLException {
        ensureInitialized();
        DriverDescriptor desc = descriptors.get(driverId);
        if (desc == null) {
            throw new IllegalArgumentException("Unknown driver id: " + driverId);
        }
        if (registeredShims.containsKey(driverId)) {
            return registeredShims.get(driverId);
        }
        if (desc.getJarFileName() != null) {
            Path jarPath = getDriversDirectory().resolve(desc.getJarFileName());
            if (Files.isRegularFile(jarPath)) {
                return loadAndRegisterJar(desc, jarPath);
            }
        }
        throw new IOException("No external JAR present for driver '" + driverId + "' under " + getDriversDirectory());
    }

    private DriverShim loadAndRegisterJar(DriverDescriptor desc, Path jarPath)
            throws IOException, ReflectiveOperationException, SQLException {
        if (desc.getDriverClassName() == null || desc.getDriverClassName().trim().isEmpty()) {
            throw new IllegalArgumentException("driverClassName must not be empty");
        }
        closeLoader(desc.getId());

        URL[] urls = new URL[]{jarPath.toUri().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, DriverClassLoaderManager.class.getClassLoader());
        Class<?> clazz = Class.forName(desc.getDriverClassName().trim(), true, loader);
        if (!Driver.class.isAssignableFrom(clazz)) {
            loader.close();
            throw new IllegalArgumentException("Class does not implement java.sql.Driver: " + desc.getDriverClassName());
        }
        Driver rawDriver = (Driver) clazz.getDeclaredConstructor().newInstance();
        DriverShim shim = new DriverShim(rawDriver);
        DriverManager.registerDriver(shim);
        activeLoaders.put(desc.getId(), loader);
        registeredShims.put(desc.getId(), shim);

        // Allow external driver plugins to register custom DatabaseDialect implementations
        DialectRegistry.getInstance().reloadFromClassLoader(loader);
        return shim;
    }

    /**
     * Ensures any configured external driver matching {@code jdbcUrl} is loaded into {@link DriverManager}
     * before a connection attempt. Never throws on missing optional JARs so built-in drivers remain unaffected.
     */
    public void ensureDriverForUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) return;
        ensureInitialized();
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        for (DriverDescriptor desc : descriptors.values()) {
            String prefix = desc.getJdbcUrlPrefix();
            if (prefix != null && !prefix.isEmpty() && lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                if (!registeredShims.containsKey(desc.getId()) && desc.getJarFileName() != null) {
                    Path jarPath = getDriversDirectory().resolve(desc.getJarFileName());
                    if (Files.isRegularFile(jarPath)) {
                        try {
                            loadAndRegisterJar(desc, jarPath);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    /**
     * Downloads a driver JAR from Maven Central, stores it under {@code ~/.schemasync/drivers/},
     * and activates it immediately via {@link URLClassLoader} + {@link DriverShim}.
     */
    public synchronized DriverShim downloadFromMavenCentral(DriverDescriptor desc, Consumer<String> statusConsumer)
            throws IOException, ReflectiveOperationException, SQLException {
        ensureInitialized();
        String urlStr = desc.buildMavenCentralJarUrl();
        if (urlStr == null) {
            throw new IllegalArgumentException("Maven coordinates are incomplete for driver: " + desc.getId());
        }
        if (statusConsumer != null) {
            statusConsumer.accept("Downloading " + urlStr + "...");
        }
        Files.createDirectories(getDriversDirectory());
        String fileName = (desc.getJarFileName() != null && !desc.getJarFileName().isEmpty())
                ? Paths.get(desc.getJarFileName()).getFileName().toString()
                : desc.getMavenArtifactId() + "-" + desc.getMavenVersion() + ".jar";
        Path dest = getDriversDirectory().resolve(fileName);
        Path tmp = Files.createTempFile(getDriversDirectory(), "dl-", ".jar.part");

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "SchemaSync-DriverManager/2.0");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("Maven Central returned HTTP " + code + " for " + urlStr);
            }
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmp) < 1024) {
                throw new IOException("Downloaded file too small to be a valid JDBC driver JAR");
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            desc.setJarFileName(fileName);
            return installCustomDriverJar(desc, dest);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public synchronized boolean removeCustomDriver(String driverId) throws IOException {
        ensureInitialized();
        DriverDescriptor desc = descriptors.get(driverId);
        if (desc == null || desc.isBuiltIn()) {
            return false;
        }
        closeLoader(driverId);
        if (desc.getJarFileName() != null) {
            Files.deleteIfExists(getDriversDirectory().resolve(desc.getJarFileName()));
        }
        descriptors.remove(driverId);
        persistDescriptors();
        return true;
    }

    private void closeLoader(String driverId) {
        DriverShim shim = registeredShims.remove(driverId);
        if (shim != null) {
            try {
                DriverManager.deregisterDriver(shim);
            } catch (SQLException ignored) {}
        }
        URLClassLoader old = activeLoaders.remove(driverId);
        if (old != null) {
            try {
                old.close();
            } catch (IOException ignored) {}
        }
    }

    public synchronized void closeAllLoaders() {
        for (String id : new ArrayList<>(activeLoaders.keySet())) {
            closeLoader(id);
        }
    }
}
