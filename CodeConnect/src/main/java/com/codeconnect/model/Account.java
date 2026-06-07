package com.codeconnect.model;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Abstract base class for any authenticatable account in CodeConnect.
 * Maps directly to the abstract {@code Account} class shown in the
 * Design Class Diagram (Deliverable 5).
 *
 * <p>Concrete subclasses: {@link Developer}, {@link Admin}.
 * <p>GRASP: Information Expert — owns {@link #verifyPassword(String)} because
 * the password hash lives on this object.
 */
public abstract class Account {

    protected int id;
    protected String username;
    /** BCrypt hash, never plaintext. */
    protected String password;

    protected Account() { }

    protected Account(int id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    /**
     * Subclass-supplied role label (e.g. "Developer" or "Admin"). Used by
     * controllers and persistence layer to round-trip the right concrete type.
     */
    public abstract String getRole();

    /**
     * Hook called by {@link com.codeconnect.controller.AuthController} on a
     * successful credential check. Default implementation activates the
     * application-wide {@link Session}; subclasses may override.
     */
    public void login() {
        Session.setCurrentUser((User) this);
    }

    /**
     * Hook called by {@link com.codeconnect.controller.AuthController} on
     * sign-out. Clears the session for the current account.
     */
    public void logout() {
        if (Session.getCurrentUser() != null && Session.getCurrentUser().getId() == this.id) {
            Session.setCurrentUser(null);
        }
    }

    /**
     * Verifies a plaintext attempt against the stored hash. Accepts a legacy
     * plaintext-equality fallback for accounts seeded before BCrypt rollout.
     * GRASP: Information Expert.
     */
    public boolean verifyPassword(String attempt) {
        if (attempt == null || password == null) return false;
        try {
            if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
                return BCrypt.checkpw(attempt, password);
            }
        } catch (IllegalArgumentException ignored) {
            // not a valid bcrypt hash; fall through to plaintext fallback
        }
        return attempt.equals(password);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
