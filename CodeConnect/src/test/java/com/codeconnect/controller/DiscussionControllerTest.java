package com.codeconnect.controller;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.DiscussionRoom;
import com.codeconnect.model.Message;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.dao.UserDAO;
import com.codeconnect.dao.CodeSnippetDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscussionControllerTest {

    private static User testUser;
    private static User adminUser;

    @BeforeAll
    static void setup() {
        DatabaseHelper.initializeDatabase();
        UserDAO userDAO = new UserDAO();
        testUser = userDAO.findByUsername("dev_test_user");
        if (testUser == null) {
            userDAO.register("dev_test_user", "hashed_pw", "Developer", "dev@test.local");
            testUser = userDAO.findByUsername("dev_test_user");
        }
        adminUser = userDAO.findByUsername("admin");
        if (adminUser == null) {
            userDAO.register("admin", "admin_pw", "Admin", "admin@test.local");
            adminUser = userDAO.findByUsername("admin");
        }
    }

    private int createTestSnippet(String title) {
        CodeSnippetDAO snippetDAO = new CodeSnippetDAO();
        CodeSnippet snippet = new CodeSnippet(0, title, "Java", "public class A {}", "description", testUser.getId());
        boolean success = snippetDAO.addSnippet(snippet);
        assertTrue(success, "Failed to create test snippet for database FK constraints");
        return snippet.getId();
    }

    @BeforeEach
    void clearSession() {
        Session.setCurrentUser(null);
    }

    @Test
    void testCreateRoom() {
        int snippetId = createTestSnippet("Create Room Snippet");
        DiscussionController controller = new DiscussionController();

        DiscussionController.OpResult res = controller.createRoom(snippetId, "");
        assertFalse(res.success);
        assertEquals("Room name is required.", res.message);

        res = controller.createRoom(snippetId, "Room for Snippet 1");
        assertTrue(res.success);
        assertNotNull(res.room);
        assertEquals("Room for Snippet 1", res.room.getRoomName());
        assertEquals(snippetId, res.room.getSnippetId());
    }

    @Test
    void testRequestJoinRoom() {
        int snippetId = createTestSnippet("Join Room Snippet");
        DiscussionController controller = new DiscussionController();
        DiscussionController.OpResult roomRes = controller.createRoom(snippetId, "Room 2");
        assertTrue(roomRes.success);
        int roomId = roomRes.room.getId();

        DiscussionController.OpResult joinRes = controller.requestJoinRoom(roomId);
        assertFalse(joinRes.success);
        assertEquals("You must be logged in.", joinRes.message);

        Session.setCurrentUser(testUser);
        joinRes = controller.requestJoinRoom(roomId);
        assertTrue(joinRes.success);
    }

    @Test
    void testHandleNewMessageAndHistory() {
        int snippetId = createTestSnippet("Message History Snippet");
        DiscussionController controller = new DiscussionController();
        DiscussionController.OpResult roomRes = controller.createRoom(snippetId, "Room 3");
        assertTrue(roomRes.success);
        DiscussionRoom room = roomRes.room;

        DiscussionController.OpResult msgRes = controller.handleNewMessage(room, "Hello!");
        assertFalse(msgRes.success);
        assertEquals("You must be logged in.", msgRes.message);

        Session.setCurrentUser(testUser);

        msgRes = controller.handleNewMessage(room, "  ");
        assertFalse(msgRes.success);
        assertEquals("Message is empty.", msgRes.message);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DiscussionController.MAX_MESSAGE_CHARS + 10; i++) {
            sb.append("a");
        }
        msgRes = controller.handleNewMessage(room, sb.toString());
        assertFalse(msgRes.success);
        assertTrue(msgRes.message.contains("Message exceeds"));

        DiscussionController.OpResult validMsgRes = controller.handleNewMessage(room, "Valid test message");
        assertTrue(validMsgRes.success);
        assertNotNull(validMsgRes.createdMessage);
        assertEquals("Valid test message", validMsgRes.createdMessage.getContent());

        List<Message> history = controller.requestHistory(room.getId());
        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(m -> m.getId() == validMsgRes.createdMessage.getId()));

        List<Message> recentHistory = controller.requestRecentHistory(room.getId(), 5);
        assertFalse(recentHistory.isEmpty());
        assertTrue(recentHistory.stream().anyMatch(m -> m.getId() == validMsgRes.createdMessage.getId()));
    }
}
