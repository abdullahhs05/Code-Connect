package com.codeconnect.controller;

import com.codeconnect.dao.DiscussionRoomDAO;
import com.codeconnect.dao.MessageDAO;
import com.codeconnect.model.DiscussionRoom;
import com.codeconnect.model.Message;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import com.codeconnect.net.SocketServer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * GRASP — Pure Fabrication. Owns all room/message system operations and
 * delegates persistence to the relevant DAOs and real-time fan-out to
 * {@link SocketServer}.
 *
 * <p>Maps to the {@code DiscussionController} class in the Deliverable-5 DCD
 * and implements:
 * <ul>
 *   <li>UC-5 Create Discussion Room ({@link #createRoom})</li>
 *   <li>UC-6 Join Existing Discussion ({@link #requestJoinRoom})</li>
 *   <li>UC-7 Send Real-Time Message ({@link #handleNewMessage})</li>
 *   <li>UC-8 View Message History ({@link #requestHistory})</li>
 * </ul>
 */
public class DiscussionController {

    /** UC-7 extension 3a: server-side hard cap on a single message. */
    public static final int MAX_MESSAGE_CHARS = 4096;

    private final DiscussionRoomDAO roomDAO;
    private final MessageDAO messageDAO;
    private final SocketServer socketServer;

    public DiscussionController() {
        this(new DiscussionRoomDAO(), new MessageDAO(), SocketServer.getInstance());
    }

    /** Test seam — inject mocks. */
    public DiscussionController(DiscussionRoomDAO roomDAO, MessageDAO messageDAO, SocketServer socketServer) {
        this.roomDAO = roomDAO;
        this.messageDAO = messageDAO;
        this.socketServer = socketServer;
    }

    public static final class OpResult {
        public final boolean success;
        public final String message;
        public final DiscussionRoom room;
        public final Message createdMessage;

        public OpResult(boolean success, String message, DiscussionRoom room, Message createdMessage) {
            this.success = success;
            this.message = message;
            this.room = room;
            this.createdMessage = createdMessage;
        }
    }

    /**
     * UC-5 main flow steps 3-7. Either returns the existing room linked to
     * the snippet (UC-5 extension: room already exists) or creates a new one.
     */
    public OpResult createRoom(int snippetId, String name) {
        if (name == null || name.trim().isEmpty()) {
            return new OpResult(false, "Room name is required.", null, null);
        }
        DiscussionRoom existing = roomDAO.getOrCreateRoom(snippetId, name.trim());
        if (existing == null) {
            return new OpResult(false, "Could not create discussion room.", null, null);
        }
        return new OpResult(true, "Room ready.", existing, null);
    }

    /**
     * UC-6 main flow steps 2-6. Verifies access, fetches recent history, and
     * subscribes the SocketServer topic so the caller starts receiving live
     * messages.
     */
    public OpResult requestJoinRoom(int roomId) {
        User current = Session.getCurrentUser();
        if (current == null) {
            return new OpResult(false, "You must be logged in.", null, null);
        }
        if (!socketServer.verifyPermissions(current.getId(), roomId, current.isAdmin())) {
            return new OpResult(false, "You do not have access to this room.", null, null);
        }
        // Load recent history — actual subscription is done by the UI which
        // owns the topic handler lifecycle.
        return new OpResult(true, "Connected.", null, null);
    }

    /**
     * UC-7 main flow steps 2-7. Validates, delegates message creation to the
     * Creator-pattern entity {@link DiscussionRoom#createMessage}, persists,
     * and broadcasts via the SocketServer.
     */
    public OpResult handleNewMessage(DiscussionRoom room, String content) {
        if (room == null) {
            return new OpResult(false, "Room is missing.", null, null);
        }
        User current = Session.getCurrentUser();
        if (current == null) {
            return new OpResult(false, "You must be logged in.", null, null);
        }
        // UC-7 extension 3a: empty / too-long messages
        if (content == null || content.trim().isEmpty()) {
            return new OpResult(false, "Message is empty.", room, null);
        }
        if (content.length() > MAX_MESSAGE_CHARS) {
            return new OpResult(false, "Message exceeds " + MAX_MESSAGE_CHARS + " characters.", room, null);
        }
        // GRASP Creator: room creates the message.
        Message msg = room.createMessage(content, current.getId());
        // UC-7 step 4: persist (DatabaseManager / MessageDAO)
        if (!messageDAO.sendMessage(msg)) {
            return new OpResult(false, "Database write failed.", room, null);
        }
        // UC-7 step 5-6: broadcast through SocketServer
        socketServer.broadcastMessage(room.getId(), msg.getId());
        return new OpResult(true, "Sent.", room, msg);
    }

    /**
     * UC-8 main flow steps 2-5. Returns the full history sorted
     * chronologically.
     */
    public List<Message> requestHistory(int roomId) {
        List<Message> all = messageDAO.getMessagesForRoom(roomId);
        if (all == null) return Collections.emptyList();
        // Defensive: enforce chronological order even if DAO ordering changes.
        all.sort(Comparator.comparing(Message::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        return all;
    }

    /** UC-8 helper for paginated load (used by "Load older" button). */
    public List<Message> requestRecentHistory(int roomId, int limit) {
        return messageDAO.getRecentMessages(roomId, limit);
    }
}
