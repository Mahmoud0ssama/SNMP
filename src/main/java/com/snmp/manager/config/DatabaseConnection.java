package com.snmp.manager.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Manages PostgreSQL JDBC connection configuration and lifecycle.

public class DatabaseConnection {

    private static final String CONFIG_RESOURCE = "db.properties";

    private final String url;
    private final String user;
    private final String password;

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
        this.password = password;
    }

    // Loads the connection configuration from the  db.properties resource on the classpath.
     
    public static DatabaseConnection fromResource() throws IOException {
        return fromResource(CONFIG_RESOURCE);
    }

    //Loads the connection configuration from the named properties resource.
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

    // Opens a new JDBC connection to the configured PostgreSQL database.
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }
}
