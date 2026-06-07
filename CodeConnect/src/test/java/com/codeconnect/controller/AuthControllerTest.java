package com.codeconnect.controller;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for {@link AuthController}. Exercises UC-1, UC-2, UC-10 on the
 * default SQLite backend.
 */
class AuthControllerTest {

    @BeforeAll
    static void setup() { DatabaseHelper.initializeDatabase(); }

    @Test
    void registerThenLoginRoundTrip() {
        AuthController auth = new AuthController();
        String unique = "test_" + System.nanoTime();

        AuthController.AuthResult reg = auth.handleRegister(unique, unique + "@x.com", "secret123", "Developer");
        assertTrue(reg.success, reg.message);
        assertNotNull(reg.user);
        assertEquals("Developer", reg.user.getRole());

        AuthController.AuthResult login = auth.authenticateUser(unique, "secret123");
        assertTrue(login.success, login.message);
        assertNotNull(Session.getCurrentUser());
        assertEquals(unique, Session.getCurrentUser().getUsername());
    }

    @Test
    void directCallerCanSeedAdminAccount() {
        // This tests the seeding path, not the UI path. The UI (AuthView) hardcodes "Developer"
        // for self-registration. However, calling the controller directly allows creating
        // an Admin user for initial system seeding.
        AuthController auth = new AuthController();
        String unique = "seed_" + System.nanoTime();
        AuthController.AuthResult reg = auth.handleRegister(unique, unique + "@x.com", "secret123", "Admin");
        assertTrue(reg.success);
        assertEquals("Admin", reg.user.getRole());
    }

    @Test
    void invalidCredentialsRejected() {
        AuthController auth = new AuthController();
        AuthController.AuthResult login = auth.authenticateUser("definitely-no-such-user", "x");
        assertFalse(login.success);
        assertNull(login.user);
        assertEquals("Invalid username or password.", login.message);
    }

    @Test
    void logoutClearsSession() {
        AuthController auth = new AuthController();
        String unique = "lo_" + System.nanoTime();
        auth.handleRegister(unique, unique + "@x.com", "pw1234", "Developer");
        auth.authenticateUser(unique, "pw1234");
        assertNotNull(Session.getCurrentUser());
        assertTrue(auth.requestLogout());
        assertNull(Session.getCurrentUser());
    }

    @Test
    void registeredAccountIsConcreteSubclass() {
        AuthController auth = new AuthController();
        String unique = "poly_" + System.nanoTime();
        AuthController.AuthResult reg = auth.handleRegister(unique, unique + "@x.com", "secret123", "Developer");
        assertTrue(reg.success, reg.message);
        // DCD compliance: registered user must be a Developer instance, not just a User.
        assertTrue(reg.user instanceof com.codeconnect.model.Developer,
                "Expected Developer instance, got " + reg.user.getClass().getSimpleName());

        // And re-loading via the DAO should also produce a Developer instance.
        com.codeconnect.dao.UserDAO dao = new com.codeconnect.dao.UserDAO();
        User reloaded = dao.findByUsername(unique);
        assertTrue(reloaded instanceof com.codeconnect.model.Developer,
                "Reloaded user should be Developer, got " + reloaded.getClass().getSimpleName());
    }

    @Test
    void disabledAccountCannotLogin() {
        AuthController auth = new AuthController();
        String unique = "dis_" + System.nanoTime();
        auth.handleRegister(unique, unique + "@x.com", "pw1234", "Developer");
        // Disable directly via DAO
        com.codeconnect.dao.UserDAO dao = new com.codeconnect.dao.UserDAO();
        User u = dao.findByUsername(unique);
        assertNotNull(u);
        dao.setDisabled(u.getId(), true);

        AuthController.AuthResult login = auth.authenticateUser(unique, "pw1234");
        assertFalse(login.success);
        assertTrue(login.message.toLowerCase().contains("disabled"));
    }
}
