package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    public static final class AuthResult {
        public final User user;
        public final String reason; // null on success, otherwise: "INVALID" | "DISABLED"
        public AuthResult(User user, String reason) { this.user = user; this.reason = reason; }
    }

    /**
     * Reconstructs the correct {@link com.codeconnect.model.Account} subclass
     * ({@link com.codeconnect.model.Developer} or
     * {@link com.codeconnect.model.Admin}) based on the {@code role} column.
     * Falls back to {@link User} for legacy/unknown roles to keep loading safe.
     */
    private static User mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String username = rs.getString("username");
        String password = rs.getString("password");
        String role = rs.getString("role");
        String email = safeGetString(rs, "email");
        boolean disabled = safeGetInt(rs, "disabled") == 1;
        if ("Admin".equalsIgnoreCase(role)) {
            return new com.codeconnect.model.Admin(id, username, password, email, disabled);
        }
        if ("Developer".equalsIgnoreCase(role)) {
            return new com.codeconnect.model.Developer(id, username, password, email, disabled);
        }
        return new User(id, username, password, role, email, disabled);
    }

    private static String safeGetString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException e) { return ""; }
    }

    private static int safeGetInt(ResultSet rs, String col) {
        try { return rs.getInt(col); } catch (SQLException e) { return 0; }
    }

    /** Returns user if creds match and account active. Falls back to plaintext for legacy seeds. */
    public AuthResult authenticateDetailed(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return new AuthResult(null, "INVALID");
                User u = mapRow(rs);
                boolean ok = u.verifyPassword(password);
                if (!ok) return new AuthResult(null, "INVALID");
                if (u.isDisabled()) return new AuthResult(null, "DISABLED");
                return new AuthResult(u, null);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "authenticateDetailed failed", e);
        }
        return new AuthResult(null, "INVALID");
    }

    /** Backward-compatible authenticate: returns User or null. */
    public User authenticate(String username, String password) {
        return authenticateDetailed(username, password).user;
    }

    /** Lookup by username; returns null if not found. Used by AuthController. */
    public User findByUsername(String username) {
        String q = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByUsername failed", e);
        }
        return null;
    }

    /** Inserts a new account (password should be already hashed) and returns the persisted User, or null on failure. */
    public User register(String username, String hashedPassword, String role, String email) {
        String q = "INSERT INTO users (username, password, role, email, disabled) VALUES (?, ?, ?, ?, 0)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hashedPassword);
            ps.setString(3, role);
            ps.setString(4, email == null ? "" : email);
            if (ps.executeUpdate() > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int newId = keys.getInt(1);
                        String safeEmail = email == null ? "" : email;
                        // UC-1 step 4: instantiate the concrete subclass per DCD.
                        if ("Admin".equalsIgnoreCase(role)) {
                            return new com.codeconnect.model.Admin(newId, username, hashedPassword, safeEmail, false);
                        }
                        return new com.codeconnect.model.Developer(newId, username, hashedPassword, safeEmail, false);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "register failed", e);
        }
        return null;
    }

    /** Persists a freshly-computed password hash. Used to upgrade legacy plaintext seeds on first login. */
    public boolean updatePasswordHash(int userId, String newHash) {
        String q = "UPDATE users SET password = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, newHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updatePasswordHash failed", e);
        }
        return false;
    }

    /** Consolidates registration by hashing if needed and delegating to register() */
    public boolean registerUser(User user) {
        String hashed = (user.getPassword() != null && user.getPassword().startsWith("$2"))
                ? user.getPassword()
                : BCrypt.hashpw(user.getPassword() == null ? "" : user.getPassword(), BCrypt.gensalt());
        User registered = register(user.getUsername(), hashed, user.getRole(), user.getEmail());
        if (registered != null) {
            user.setId(registered.getId());
            return true;
        }
        return false;
    }

    public User getUserById(int id) {
        String query = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getUserById failed", e);
        }
        return null;
    }

    public Integer findIdByUsername(String username) {
        String query = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM users ORDER BY id";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) users.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getAllUsers failed", e);
        }
        return users;
    }

    public boolean updateUser(User user) {
        // If password looks already-hashed (starts with $2), keep it. Otherwise hash.
        String pw = user.getPassword();
        if (pw != null && !pw.isEmpty() && !pw.startsWith("$2")) {
            pw = BCrypt.hashpw(pw, BCrypt.gensalt());
        }
        String query = "UPDATE users SET username = ?, password = ?, email = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, pw);
            pstmt.setString(3, user.getEmail() == null ? "" : user.getEmail());
            pstmt.setInt(4, user.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateUser failed", e);
        }
        return false;
    }

    public boolean setRole(int userId, String role) {
        if (!"Admin".equals(role) && !"Developer".equals(role)) return false;
        String q = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, role);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "setRole failed", e);
        }
        return false;
    }

    public boolean setDisabled(int userId, boolean disabled) {
        String q = "UPDATE users SET disabled = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, disabled ? 1 : 0);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "setDisabled failed", e);
        }
        return false;
    }
}
