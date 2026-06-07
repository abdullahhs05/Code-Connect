package com.codeconnect.model;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link Account} abstract hierarchy from the Deliverable-5 DCD.
 * Ensures Information-Expert behaviour for {@code verifyPassword}.
 */
class AccountTest {

    @Test
    void developerExtendsAccount() {
        Developer d = new Developer(1, "alice", BCrypt.hashpw("pw", BCrypt.gensalt(4)));
        assertTrue(d instanceof User);
        assertTrue(d instanceof Account);
        assertEquals("Developer", d.getRole());
    }

    @Test
    void adminExtendsAccount() {
        Admin a = new Admin(2, "bob", "plaintext-legacy");
        assertTrue(a instanceof User);
        assertTrue(a instanceof Account);
        assertEquals("Admin", a.getRole());
        assertEquals(1, a.getAdminLevel());
    }

    @Test
    void verifyPasswordHandlesBcryptHash() {
        String hash = BCrypt.hashpw("hunter2", BCrypt.gensalt(4));
        Developer d = new Developer(1, "u", hash);
        assertTrue(d.verifyPassword("hunter2"));
        assertFalse(d.verifyPassword("wrong"));
    }

    @Test
    void verifyPasswordHandlesPlaintextLegacy() {
        // Seed accounts (admin/admin, dev/dev) are stored as plaintext in older
        // DBs. The Information-Expert verifyPassword falls back to equality.
        Admin a = new Admin(1, "admin", "admin");
        assertTrue(a.verifyPassword("admin"));
        assertFalse(a.verifyPassword("Admin"));
    }

    @Test
    void verifyPasswordRejectsNulls() {
        Developer d = new Developer(1, "u", "x");
        assertFalse(d.verifyPassword(null));
    }
}
