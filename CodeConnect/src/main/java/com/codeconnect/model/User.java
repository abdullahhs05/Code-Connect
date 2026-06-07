package com.codeconnect.model;

/**
 * Concrete authenticated user. Sits between {@link Account} (abstract base
 * from the DCD) and the role-specific subclasses {@link Developer} and
 * {@link Admin}, while preserving the existing public accessors used widely
 * across the UI/DAO layers.
 *
 * <p>Persistence treats role as a string column (because SQLite has no enum
 * type), so the polymorphic identity is reconstructed in
 * {@link com.codeconnect.dao.UserDAO} by inspecting that column.
 */
public class User extends Account {
    private String role; // "Developer" or "Admin"
    private String email = "";
    private boolean disabled = false;

    public User(int id, String username, String password, String role) {
        super(id, username, password);
        this.role = role;
    }

    public User(int id, String username, String password, String role, String email, boolean disabled) {
        this(id, username, password, role);
        this.email = email != null ? email : "";
        this.disabled = disabled;
    }

    @Override
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email : ""; }
    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    /** True when this user has administrator privileges. */
    public boolean isAdmin() { return "Admin".equalsIgnoreCase(role); }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
