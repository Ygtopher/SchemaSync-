package com.dbtool.core.connection;

import com.dbtool.core.model.SslMode;
import java.util.Properties;

public class SslConfiguration {
    private SslMode mode = SslMode.PREFER;

    public SslConfiguration() {}
    public SslConfiguration(SslMode mode) { this.mode = mode; }

    public void applyTo(Properties props) {
        if (props != null && mode != null) {
            props.setProperty("ssl", String.valueOf(mode != SslMode.DISABLE));
            props.setProperty("sslmode", mode.getValue());
        }
    }
}
