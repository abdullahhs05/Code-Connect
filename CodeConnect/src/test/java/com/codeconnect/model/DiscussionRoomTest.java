package com.codeconnect.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the GRASP-Creator behaviour on {@link DiscussionRoom} —
 * specifically that {@code createMessage} is the only place a freshly-formed
 * {@link Message} is constructed and that input validation is enforced
 * before persistence is even attempted.
 */
class DiscussionRoomTest {

    @Test
    void createMessageBindsRoomAndSender() {
        DiscussionRoom room = new DiscussionRoom(42, 7, "test-room");
        Message m = room.createMessage("hello", 5);
        assertEquals(42, m.getRoomId());
        assertEquals(5, m.getSenderId());
        assertEquals("hello", m.getContent());
        assertEquals(0, m.getId(), "fresh message must be unsaved");
        assertNotNull(m.getTimestamp());
    }

    @Test
    void createMessageRejectsBlankContent() {
        DiscussionRoom room = new DiscussionRoom(1, 1, "x");
        assertThrows(IllegalArgumentException.class, () -> room.createMessage("   ", 1));
        assertThrows(IllegalArgumentException.class, () -> room.createMessage(null, 1));
    }
}
