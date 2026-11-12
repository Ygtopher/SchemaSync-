package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionConfig;
import com.dbtool.core.model.SslMode;

public class JdbcUrlBuilder {
    public static String buildUrl(ConnectionConfig config) {
        if (config == null || config.getType() == null) {
            throw new IllegalArgumentException("Config and type must not be null");
        }
        SslMode ssl = config.getSslMode();
        switch (config.getType()) {
            case POSTGRESQL: {
                String base = "jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName()
                        + "?tcpKeepAlive=true&gssEncMode=disable";
                if (ssl != null) {
                    base += "&sslmode=" + ssl.getValue();
                }
                return base;
            }
            case MYSQL: {
                SslMode effective = ssl != null ? ssl : SslMode.PREFER;
                return "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName()
                        + "?sslMode=" + toMySqlSslMode(effective)
                        + "&connectionTimeZone=SERVER&connectTimeout=" + Math.max(1000, config.getTimeoutSeconds() * 1000);
            }
            case SQLITE: {
                String path = (config.getFilePath() != null && !config.getFilePath().isEmpty())
                        ? config.getFilePath()
                        : config.getDatabaseName();
                return "jdbc:sqlite:" + path;
            }
            case H2:
                if (config.getFilePath() != null && !config.getFilePath().isEmpty()) {
                    return "jdbc:h2:" + config.getFilePath() + ";AUTO_SERVER=TRUE";
                }
                return "jdbc:h2:mem:" + config.getDatabaseName() + ";DB_CLOSE_DELAY=-1";
            case MS_ACCESS:
                return "jdbc:ucanaccess://" + config.getFilePath();
            default:
                return "";
        }
    }

    public static String toMySqlSslMode(SslMode mode) {
        if (mode == null) return "PREFERRED";
        switch (mode) {
            case DISABLE:
                return "DISABLED";
            case REQUIRE:
                return "REQUIRED";
            case VERIFY_CA:
                return "VERIFY_CA";
            case VERIFY_FULL:
                return "VERIFY_IDENTITY";
            case ALLOW:
            case PREFER:
            default:
                return "PREFERRED";
        }
    }
}
