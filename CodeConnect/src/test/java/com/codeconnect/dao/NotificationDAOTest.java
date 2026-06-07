package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDAOTest {

    private static final NotificationDAO notifDAO = new NotificationDAO();
    private static final UserDAO userDAO = new UserDAO();

    @BeforeAll
    static void init() {
        DatabaseHelper.initializeDatabase();
    }

    @Test
    void addListAndMarkRead() {
        String uname = "notif_user_" + System.currentTimeMillis();
        userDAO.registerUser(new User(0, uname, "pw1234", "Developer", uname + "@t.test", false));
        Integer uid = userDAO.findIdByUsername(uname);
        assertNotNull(uid);

        int before = notifDAO.countUnread(uid);
        assertTrue(notifDAO.addNotification(uid, "MENTION", "Test mention message", null, null));
        assertEquals(before + 1, notifDAO.countUnread(uid));

        List<NotificationDAO.Notification> list = notifDAO.listForUser(uid, 10);
        assertFalse(list.isEmpty());
        NotificationDAO.Notification first = list.get(0);
        assertEquals("MENTION", first.type);
        assertFalse(first.read);

        assertTrue(notifDAO.markRead(first.id));
        assertEquals(before, notifDAO.countUnread(uid));
    }
}
