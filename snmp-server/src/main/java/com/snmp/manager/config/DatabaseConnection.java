package com.snmp.manager.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

// Manages PostgreSQL JDBC connection configuration and lifecycle using HikariCP.

public class DatabaseConnection {

    private static final String CONFIG_RESOURCE = "db.properties";

    private final HikariDataSource dataSource;
    private final String url;
    private final String user;

    // Constructs a connection manager from the given parameters.
    public DatabaseConnection(String url, String user, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be null or blank");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        this.url = url;
        this.user = user;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        
        // Recommended HikariCP settings for PostgreSQL
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000); // 30 minutes

        this.dataSource = new HikariDataSource(config);
    }

    // Loads the connection configuration from the db.properties resource on the classpath.
    public static DatabaseConnection fromResource() throws IOException {
        return fromResource(CONFIG_RESOURCE);
    }

    // Loads the connection configuration from the named properties resource.
    public static DatabaseConnection fromResource(String resource) throws IOException {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + resource);
            }
            props.load(in);
        }
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password", "");
        return new DatabaseConnection(url, user, password);
    }

    // Gets a connection from the HikariCP connection pool.
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }
    
    // Close the connection pool gracefully
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
