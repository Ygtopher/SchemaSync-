package com.dbtool.core.driver;

import com.dbtool.core.dialect.DialectType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * Serializable metadata describing a JDBC driver (built-in fallback, downloaded Maven Central
 * artifact, or user-provided custom JAR stored under {@code ~/.schemasync/drivers/}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverDescriptor implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String displayName;
    private DialectType dialectType = DialectType.GENERIC;
    private String driverClassName;
    private String jdbcUrlPrefix;
    private int defaultPort = -1;
    private String mavenGroupId;
    private String mavenArtifactId;
    private String mavenVersion;
    private String jarFileName;
    private boolean builtIn;

    public DriverDescriptor() {}

    public DriverDescriptor(
            String id,
            String displayName,
            DialectType dialectType,
            String driverClassName,
            String jdbcUrlPrefix,
            int defaultPort,
            String mavenGroupId,
            String mavenArtifactId,
            String mavenVersion,
            String jarFileName,
            boolean builtIn
    ) {
        this.id = id;
        this.displayName = displayName;
        this.dialectType = dialectType != null ? dialectType : DialectType.GENERIC;
        this.driverClassName = driverClassName;
        this.jdbcUrlPrefix = jdbcUrlPrefix;
        this.defaultPort = defaultPort;
        this.mavenGroupId = mavenGroupId;
        this.mavenArtifactId = mavenArtifactId;
        this.mavenVersion = mavenVersion;
        this.jarFileName = jarFileName;
        this.builtIn = builtIn;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public DialectType getDialectType() { return dialectType; }
    public void setDialectType(DialectType dialectType) { this.dialectType = dialectType; }

    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

    public String getJdbcUrlPrefix() { return jdbcUrlPrefix; }
    public void setJdbcUrlPrefix(String jdbcUrlPrefix) { this.jdbcUrlPrefix = jdbcUrlPrefix; }

    public int getDefaultPort() { return defaultPort; }
    public void setDefaultPort(int defaultPort) { this.defaultPort = defaultPort; }

    public String getMavenGroupId() { return mavenGroupId; }
    public void setMavenGroupId(String mavenGroupId) { this.mavenGroupId = mavenGroupId; }

    public String getMavenArtifactId() { return mavenArtifactId; }
    public void setMavenArtifactId(String mavenArtifactId) { this.mavenArtifactId = mavenArtifactId; }

    public String getMavenVersion() { return mavenVersion; }
    public void setMavenVersion(String mavenVersion) { this.mavenVersion = mavenVersion; }

    public String getJarFileName() { return jarFileName; }
    public void setJarFileName(String jarFileName) { this.jarFileName = jarFileName; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    public String buildMavenCentralJarUrl() {
        if (mavenGroupId == null || mavenArtifactId == null || mavenVersion == null) {
            return null;
        }
        return "https://repo1.maven.org/maven2/"
                + mavenGroupId.replace('.', '/')
                + "/" + mavenArtifactId
                + "/" + mavenVersion
                + "/" + mavenArtifactId + "-" + mavenVersion + ".jar";
    }
}
