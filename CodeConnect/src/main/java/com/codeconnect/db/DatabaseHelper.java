package com.codeconnect.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Two-backend database manager: SQLite (default, single-machine) or MySQL (LAN, multi-desktop).
 *
 * <p>Configuration is sourced in this priority order:
 * <ol>
 *   <li>System property {@code -Dcc.db.kind=...}, {@code -Dcc.db.url=...}, etc.</li>
 *   <li>Environment variable {@code DB_KIND}, {@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD}.</li>
 *   <li>Defaults: {@code kind=sqlite}, {@code url=jdbc:sqlite:codeconnect.db}.</li>
 * </ol>
 *
 * <p>Example to run against MySQL on another machine:
 * <pre>
 *   set DB_KIND=mysql
 *   set DB_URL=jdbc:mysql://192.168.1.10:3306/codeconnect?useSSL=false&serverTimezone=UTC
 *   set DB_USER=cc_user
 *   set DB_PASSWORD=cc_pw
 * </pre>
 */
public class DatabaseHelper {

    public enum Backend { SQLITE, MYSQL }

    private static final Backend KIND;
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        String k = prop("cc.db.kind", "DB_KIND", "sqlite").trim().toLowerCase();
        KIND = "mysql".equals(k) ? Backend.MYSQL : Backend.SQLITE;

        if (KIND == Backend.MYSQL) {
            URL = prop("cc.db.url", "DB_URL", "jdbc:mysql://localhost:3306/codeconnect?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
            USER = prop("cc.db.user", "DB_USER", "root");
            PASSWORD = prop("cc.db.password", "DB_PASSWORD", "");
        } else {
            URL = prop("cc.db.url", "DB_URL", "jdbc:sqlite:codeconnect.db");
            USER = null;
            PASSWORD = null;
        }
    }

    private static String prop(String sysKey, String envKey, String def) {
        String v = System.getProperty(sysKey);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(envKey);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }

    public static Backend getKind() { return KIND; }

    public static Connection getConnection() throws SQLException {
        Connection conn;
        if (KIND == Backend.MYSQL) {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
        } else {
            conn = DriverManager.getConnection(URL);
            try (Statement pragma = conn.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
        }
        return conn;
    }

    /** SQL fragment that yields the current timestamp; usable wherever a value is allowed. */
    public static String nowSql() {
        return "CURRENT_TIMESTAMP"; // both SQLite and MySQL accept this
    }

    /** Vendor-correct prefix for "insert if not present" semantics. */
    public static String insertIgnorePrefix() {
        return KIND == Backend.MYSQL ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ";
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        // MySQL is case-insensitive on identifiers, but JDBC may have stored exact case
        try (ResultSet rs = md.getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
            return rs.next();
        }
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String sqlType) throws SQLException {
        if (!columnExists(conn, table, column)) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
            }
        }
    }

    public static void initializeDatabase() {
        try (Connection conn = getRawConnection();
             Statement stmt = conn.createStatement()) {

            if (KIND == Backend.SQLITE) {
                stmt.execute("PRAGMA foreign_keys = OFF");
            }

            // ---- DDL is shared between both backends with vendor-specific id syntax ----
            String pkAuto = KIND == Backend.MYSQL
                    ? "id INT AUTO_INCREMENT PRIMARY KEY,"
                    : "id INTEGER PRIMARY KEY AUTOINCREMENT,";
            String textType = KIND == Backend.MYSQL ? "VARCHAR(512)" : "TEXT";
            String longText = KIND == Backend.MYSQL ? "MEDIUMTEXT" : "TEXT";
            String tsCol    = KIND == Backend.MYSQL ? "DATETIME" : "DATETIME";

            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    pkAuto +
                    "username " + textType + " NOT NULL UNIQUE," +
                    "password " + textType + " NOT NULL," +
                    "role " + textType + " NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS code_snippets (" +
                    pkAuto +
                    "title " + textType + " NOT NULL," +
                    "language " + textType + " NOT NULL," +
                    "code " + longText + " NOT NULL," +
                    "description " + longText + "," +
                    "uploader_id INT," +
                    "created_at " + tsCol + "," +
                    "tags " + textType + "," +
                    "FOREIGN KEY(uploader_id) REFERENCES users(id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS discussion_rooms (" +
                    pkAuto +
                    "snippet_id INT," +
                    "room_name " + textType + " NOT NULL," +
                    "FOREIGN KEY(snippet_id) REFERENCES code_snippets(id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    pkAuto +
                    "room_id INT," +
                    "sender_id INT," +
                    "content " + longText + " NOT NULL," +
                    "timestamp " + tsCol + " DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(room_id) REFERENCES discussion_rooms(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(sender_id) REFERENCES users(id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS bookmarks (" +
                    "user_id INT NOT NULL," +
                    "snippet_id INT NOT NULL," +
                    "created_at " + tsCol + "," +
                    "PRIMARY KEY (user_id, snippet_id)," +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(snippet_id) REFERENCES code_snippets(id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS notifications (" +
                    pkAuto +
                    "user_id INT NOT NULL," +
                    "type " + textType + " NOT NULL," +
                    "message " + longText + " NOT NULL," +
                    "room_id INT," +
                    "snippet_id INT," +
                    "is_read INT NOT NULL DEFAULT 0," +
                    "created_at " + tsCol + " NOT NULL," +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS room_members (" +
                    "room_id INT NOT NULL," +
                    "user_id INT NOT NULL," +
                    "PRIMARY KEY (room_id, user_id)," +
                    "FOREIGN KEY(room_id) REFERENCES discussion_rooms(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");

            // ---- Backwards-compat columns for users upgrading from older DBs ----
            addColumnIfMissing(conn, "code_snippets", "created_at", tsCol);
            addColumnIfMissing(conn, "code_snippets", "tags", textType);
            addColumnIfMissing(conn, "code_snippets", "hidden", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "bookmarks", "created_at", tsCol);
            addColumnIfMissing(conn, "users", "email", textType);
            addColumnIfMissing(conn, "users", "disabled", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "discussion_rooms", "is_private", "INT NOT NULL DEFAULT 0");

            // Backfill nulls (CURRENT_TIMESTAMP works for both backends as expression value)
            stmt.execute("UPDATE code_snippets SET created_at = CURRENT_TIMESTAMP " +
                    "WHERE created_at IS NULL");
            stmt.execute("UPDATE code_snippets SET tags = '' WHERE tags IS NULL");
            stmt.execute("UPDATE code_snippets SET hidden = 0 WHERE hidden IS NULL");
            stmt.execute("UPDATE bookmarks SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL");
            stmt.execute("UPDATE users SET disabled = 0 WHERE disabled IS NULL");
            stmt.execute("UPDATE users SET email = '' WHERE email IS NULL");

            // Indexes: both backends accept "CREATE INDEX IF NOT EXISTS"
            // (MySQL 8.0.0+ supports it; if older MySQL, wrap in try)
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_messages_room_time ON messages(room_id, timestamp)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications(user_id, is_read)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_notifications_user_time ON notifications(user_id, created_at)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_snippets_uploader ON code_snippets(uploader_id)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_snippets_created ON code_snippets(created_at)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_bookmarks_user ON bookmarks(user_id)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_rooms_snippet ON discussion_rooms(snippet_id)");
            execIgnore(stmt, "CREATE INDEX IF NOT EXISTS idx_room_members_user ON room_members(user_id)");

            // Seed
            String ins = insertIgnorePrefix() +
                    "users (username, password, role, email, disabled) VALUES (?, ?, ?, ?, 0)";
            try (var ps = conn.prepareStatement(ins)) {
                ps.setString(1, "admin"); ps.setString(2, "admin");
                ps.setString(3, "Admin"); ps.setString(4, "admin@codeconnect.local");
                ps.executeUpdate();
                ps.setString(1, "dev"); ps.setString(2, "dev");
                ps.setString(3, "Developer"); ps.setString(4, "dev@codeconnect.local");
                ps.executeUpdate();
            }

            // Repair orphan FK data (best-effort)
            execIgnore(stmt, "DELETE FROM bookmarks WHERE user_id NOT IN (SELECT id FROM users) " +
                    "OR snippet_id NOT IN (SELECT id FROM code_snippets)");
            execIgnore(stmt, "UPDATE messages SET sender_id = NULL WHERE sender_id = 0 " +
                    "OR sender_id NOT IN (SELECT id FROM users)");
            execIgnore(stmt, "DELETE FROM discussion_rooms WHERE snippet_id IS NULL " +
                    "OR snippet_id NOT IN (SELECT id FROM code_snippets)");

            if (KIND == Backend.SQLITE) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            System.out.println("[DB] Initialized backend=" + KIND + " url=" + URL);
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Connection without PRAGMA setup, used during init when FKs may need to stay off. */
    private static Connection getRawConnection() throws SQLException {
        if (KIND == Backend.MYSQL) {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return DriverManager.getConnection(URL);
    }

    private static void execIgnore(Statement stmt, String sql) {
        try { stmt.execute(sql); }
        catch (SQLException e) {
            // Older MySQL may not support "IF NOT EXISTS" on CREATE INDEX or "DELETE … self-reference";
            // log and continue.
            System.err.println("[DB] non-fatal: " + e.getMessage());
        }
    }
}
