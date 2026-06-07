package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserDAOTest {

    private static final UserDAO dao = new UserDAO();

    @BeforeAll
    static void init() {
        DatabaseHelper.initializeDatabase();
    }

    @Test
    @DisplayName("Seeded admin account authenticates with plaintext fallback")
    void adminCanLogin() {
        UserDAO.AuthResult res = dao.authenticateDetailed("admin", "admin");
        assertNotNull(res.user, "admin login should succeed");
        assertEquals("Admin", res.user.getRole());
        assertNull(res.reason);
    }

    @Test
    @DisplayName("Wrong password returns INVALID")
    void wrongPasswordFails() {
        UserDAO.AuthResult res = dao.authenticateDetailed("admin", "wrong-pass");
        assertNull(res.user);
        assertEquals("INVALID", res.reason);
    }

    @Test
    @DisplayName("Register + authenticate with BCrypt hash works end-to-end")
    void registerAndLogin() {
        String user = "junit_user_" + System.currentTimeMillis();
        User u = new User(0, user, "s3cret!", "Developer", user + "@test.local", false);
        assertTrue(dao.registerUser(u));

        UserDAO.AuthResult res = dao.authenticateDetailed(user, "s3cret!");
        assertNotNull(res.user);
        assertEquals(user, res.user.getUsername());
        assertFalse(res.user.isDisabled());
    }

    @Test
    @DisplayName("Disabled account is rejected on login")
    void disabledAccountBlocked() {
        String user = "junit_disabled_" + System.currentTimeMillis();
        User u = new User(0, user, "abcd", "Developer", user + "@test.local", false);
        assertTrue(dao.registerUser(u));

        Integer id = dao.findIdByUsername(user);
        assertNotNull(id);
        assertTrue(dao.setDisabled(id, true));

        UserDAO.AuthResult res = dao.authenticateDetailed(user, "abcd");
        assertNull(res.user);
        assertEquals("DISABLED", res.reason);
    }
}
