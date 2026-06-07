package com.codeconnect.model;

/**
 * Concrete account type for an end-user developer.
 * Maps to {@code Developer extends Account} in the Deliverable-5 DCD.
 *
 * <p>Adds developer-only attributes: {@code bio}.
 * The {@code role} label is fixed to {@code "Developer"} so that legacy
 * persistence (which stores {@code users.role} as a string column)
 * continues to round-trip correctly.
 */
public class Developer extends User {

    private String bio = "";

    public Developer(int id, String username, String password) {
        super(id, username, password, "Developer");
    }

    public Developer(int id, String username, String password, String email, boolean disabled) {
        super(id, username, password, "Developer", email, disabled);
    }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio != null ? bio : ""; }

    @Override
    public String getRole() { return "Developer"; }
}
