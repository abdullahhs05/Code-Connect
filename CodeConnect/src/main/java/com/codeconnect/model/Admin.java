package com.codeconnect.model;

/**
 * Concrete account type for an administrator.
 * Maps to {@code Admin extends Account} in the Deliverable-5 DCD.
 *
 * <p>Adds {@code adminLevel} (1 = standard moderator, 2 = super admin)
 * for fine-grained admin permissions.
 */
public class Admin extends User {

    private int adminLevel = 1;

    public Admin(int id, String username, String password) {
        super(id, username, password, "Admin");
    }

    public Admin(int id, String username, String password, String email, boolean disabled) {
        super(id, username, password, "Admin", email, disabled);
    }

    public int getAdminLevel() { return adminLevel; }
    public void setAdminLevel(int adminLevel) { this.adminLevel = adminLevel; }

    @Override
    public String getRole() { return "Admin"; }
}
