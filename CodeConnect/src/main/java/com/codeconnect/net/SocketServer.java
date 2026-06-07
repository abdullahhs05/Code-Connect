package com.codeconnect.net;

import com.codeconnect.dao.DiscussionRoomDAO;

import java.util.function.Consumer;

/**
 * GRASP — Pure Fabrication. Real-time messaging facade. Maps to the
 * {@code SocketServer} class in the Deliverable-5 DCD; the actual TCP
 * listener / client logic is implemented by {@link LocalEventBus}, which is
 * LAN-aware and can either bind a {@link java.net.ServerSocket} or join a
 * remote hub.
 *
 * <p>Topic convention: {@code "ROOM:<roomId>"} for room-scoped events,
 * {@code "USER:<userId>"} for direct user notifications. Payload is a small
 * descriptor (e.g. {@code "newmsg:<messageId>"}).
 */
public final class SocketServer {

    private static final SocketServer INSTANCE = new SocketServer();
    private final DiscussionRoomDAO roomDAO = new DiscussionRoomDAO();

    public static SocketServer getInstance() { return INSTANCE; }

    private final LocalEventBus bus = LocalEventBus.getInstance();

    /** True if this process is currently the bus server (bound the listening socket). */
    public boolean isRunning() { return bus.isServer(); }

    /**
     * UC-6 step 3. Verifies the user can access the requested room.
     * Public + member-of + snippet-owner all pass; admins always pass.
     */
    public boolean verifyPermissions(int userId, int roomId, boolean isAdmin) {
        return roomDAO.canAccess(roomId, userId, isAdmin);
    }

    /** UC-6 step 4. Subscribes a UI handler to a room topic. */
    public void connectUser(int userId, int roomId, Consumer<String> handler) {
        bus.subscribe("ROOM:" + roomId, handler);
    }

    /** Companion to {@link #connectUser} — invoked when the chat window closes. */
    public void disconnectUser(int roomId, Consumer<String> handler) {
        bus.unsubscribe("ROOM:" + roomId, handler);
    }

    /** UC-7 step 5-6. Pushes a "newmsg:<id>" event to all subscribers of the room. */
    public void broadcastMessage(int roomId, int messageId) {
        bus.publish("ROOM:" + roomId, "newmsg:" + messageId);
    }

    /** Generic publish; lets controllers raise custom payloads (e.g. presence). */
    public void publish(String topic, String payload) {
        bus.publish(topic, payload);
    }

    /** Generic subscribe. */
    public void subscribe(String topic, Consumer<String> handler) {
        bus.subscribe(topic, handler);
    }

    public void unsubscribe(String topic, Consumer<String> handler) {
        bus.unsubscribe(topic, handler);
    }
}
