package com.codeconnect.controller;

import com.codeconnect.dao.UserDAO;
import com.codeconnect.model.Account;
import com.codeconnect.model.Admin;
import com.codeconnect.model.Developer;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import org.mindrot.jbcrypt.BCrypt;

/**
 * GRASP — Pure Fabrication. The {@code AuthController} keeps authentication
 * concerns out of UI code and out of domain entities. It is the single
 * authority for login, registration, logout and session handling.
 *
 * <p>Maps to the {@code AuthController} class in the Deliverable-5 DCD and
 * implements the system operations called out in:
 * <ul>
 *   <li>UC-1 Register Account ({@link #handleRegister})</li>
 *   <li>UC-2 Login ({@link #authenticateUser})</li>
 *   <li>UC-10 Logout ({@link #requestLogout})</li>
 * </ul>
 */
public class AuthController {

    private final UserDAO userDAO;

    public AuthController() {
        this(new UserDAO());
    }

    /** Test seam — inject a mock DAO. */
    public AuthController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** Result envelope for register/login operations. */
    public static final class AuthResult {
        public final boolean success;
        public final String message;
        public final User user; // null on failure

        public AuthResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
    }

    /**
     * UC-1 main flow steps 2-6. Validates input, checks for duplicate
     * username/email, hashes the password with BCrypt, creates the
     * appropriate concrete subclass ({@link Developer} by default), and
     * persists it via {@link UserDAO}.
     *
     * @return {@link AuthResult} where {@code success=false} carries a
     *         user-friendly error message for any extension flow
     *         (4a invalid, 5a duplicate, 6a DB error).
     */
    public AuthResult handleRegister(String username, String email, String password, String role) {
        if (username == null || username.trim().isEmpty()) {
            return new AuthResult(false, "Username is required.", null);
        }
        if (password == null || password.length() < 3) {
            return new AuthResult(false, "Password must be at least 3 characters.", null);
        }
        if (email == null || email.trim().isEmpty()) {
            return new AuthResult(false, "Email is required.", null);
        }
        if (!email.contains("@")) {
            return new AuthResult(false, "Invalid email format.", null);
        }
        if (userDAO.findByUsername(username) != null) {
            return new AuthResult(false, "Username is already taken.", null);
        }
        // Force role to Developer unless caller is explicitly seeding admins.
        String safeRole = "Admin".equalsIgnoreCase(role) ? "Admin" : "Developer";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        User created = userDAO.register(username, hash, safeRole, email == null ? "" : email);
        if (created == null) {
            return new AuthResult(false, "Database error — could not create account.", null);
        }
        return new AuthResult(true, "Account created.", created);
    }

    /**
     * UC-2 main flow steps 2-6. Looks up the account, runs
     * {@link Account#verifyPassword(String)} (Information Expert), opens the
     * application session, and returns the polymorphic {@link User}.
     *
     * <p>Disabled accounts (extension 4b) and missing credentials (extension
     * 4a) yield distinct error messages so the UI can surface them precisely.
     */
    public AuthResult authenticateUser(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return new AuthResult(false, "Username and password are required.", null);
        }
        User stored = userDAO.findByUsername(username);
        if (stored == null) {
            return new AuthResult(false, "Invalid username or password.", null);
        }
        if (stored.isDisabled()) {
            return new AuthResult(false, "Account is disabled. Please contact an admin.", null);
        }
        if (!stored.verifyPassword(password)) {
            return new AuthResult(false, "Invalid username or password.", null);
        }
        // Opportunistically upgrade legacy plaintext passwords to BCrypt on a successful login.
        if (!isBcryptHash(stored.getPassword())) {
            try {
                String upgraded = BCrypt.hashpw(password, BCrypt.gensalt(10));
                userDAO.updatePasswordHash(stored.getId(), upgraded);
                stored.setPassword(upgraded);
            } catch (Exception ignored) { /* best-effort */ }
        }
        createSession(stored);
        return new AuthResult(true, "Login successful.", stored);
    }

    /** UC-2 step 5: opens an application-wide session for the authenticated user. */
    public void createSession(User user) {
        Session.setCurrentUser(user);
    }

    /**
     * UC-10 main flow. Calls {@link Account#logout()} on the current account
     * (which clears the session), then returns true. Returns false if no user
     * was logged in (idempotent).
     */
    public boolean requestLogout() {
        User current = Session.getCurrentUser();
        if (current == null) return false;
        current.logout();
        return true;
    }

    /** Convenience: for views that just need the current user. */
    public User getCurrentUser() {
        return Session.getCurrentUser();
    }

    private static boolean isBcryptHash(String pw) {
        return pw != null && (pw.startsWith("$2a$") || pw.startsWith("$2b$") || pw.startsWith("$2y$"));
    }
}
