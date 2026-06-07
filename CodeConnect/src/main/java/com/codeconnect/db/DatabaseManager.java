package com.codeconnect.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * GRASP — Pure Fabrication. Application-facing facade for all database
 * operations. Maps to the {@code DatabaseManager} class in the Deliverable-5
 * DCD; concrete connection / DDL work is delegated to {@link DatabaseHelper}.
 *
 * <p>Public DAO classes (in the {@code com.codeconnect.dao} package) sit on
 * top of this facade; UI code never talks to JDBC directly.
 */
public final class DatabaseManager {

    private static final DatabaseManager INSTANCE = new DatabaseManager();

    private DatabaseManager() { }

    public static DatabaseManager getInstance() { return INSTANCE; }

    /**
     * Initializes the schema (creates tables, indexes, seed users) using the
     * configured backend. Idempotent — safe to call on every startup.
     */
    public void initialize() {
        DatabaseHelper.initializeDatabase();
    }

    /** Borrows a JDBC connection. The caller is responsible for closing it. */
    public Connection getConnection() throws SQLException {
        return DatabaseHelper.getConnection();
    }

    /** Returns the active backend (SQLITE or MYSQL). */
    public DatabaseHelper.Backend getBackend() {
        return DatabaseHelper.getKind();
    }

    /** Vendor-portable timestamp expression usable inside {@code INSERT}/{@code UPDATE}. */
    public String nowSql() { return DatabaseHelper.nowSql(); }

    /** Vendor-portable {@code INSERT IGNORE INTO} prefix. */
    public String insertIgnorePrefix() { return DatabaseHelper.insertIgnorePrefix(); }
}
