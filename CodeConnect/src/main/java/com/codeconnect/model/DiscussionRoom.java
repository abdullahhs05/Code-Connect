package com.codeconnect.model;

import java.sql.Timestamp;

/**
 * A discussion room is the unit of conversation linked to a {@link CodeSnippet}.
 * Maps to the {@code DiscussionRoom} entity in the Deliverable-5 DCD.
 *
 * <p>GRASP roles satisfied here:
 * <ul>
 *   <li><b>Creator</b> — owns {@link #createMessage(String, int)} because a room
 *       aggregates messages and is the natural place to instantiate them.</li>
 *   <li><b>Information Expert</b> — exposes {@link #saveRoom()} and
 *       {@link #leaveRoom(int)} as instance-level operations even though the
 *       persistence work is delegated to {@link com.codeconnect.dao.DiscussionRoomDAO}.</li>
 * </ul>
 */
public class DiscussionRoom {
    private int id;
    private int snippetId;
    private String roomName;
    private boolean isPrivate = false;
    private Timestamp createdAt;
    /** UC-5: id of the user who created the room (DCD: {@code createdRoom}). 0 when unknown / legacy. */
    private int createdBy = 0;

    public DiscussionRoom(int id, int snippetId, String roomName) {
        this.id = id;
        this.snippetId = snippetId;
        this.roomName = roomName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSnippetId() { return snippetId; }
    public void setSnippetId(int snippetId) { this.snippetId = snippetId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    /**
     * GRASP — Creator. Constructs a brand-new {@link Message} attached to this
     * room. The id is left as 0 (unsaved); persistence layer will assign the
     * real id when the message is written to the database.
     *
     * @param content message text, must be non-null and non-empty
     * @param userId  sender user id, must reference an existing account
     * @return the newly created {@link Message} object (not yet persisted)
     */
    public Message createMessage(String content, int userId) {
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (content.trim().isEmpty()) throw new IllegalArgumentException("content must not be empty");
        return new Message(0, this.id, userId, content, new Timestamp(System.currentTimeMillis()));
    }

    /**
     * GRASP — Information Expert. Persists this room.
     * In this clean domain model architecture, domain entities do not interact
     * directly with the database. This method is a no-op stub to satisfy the DCD
     * method signature without coupling the domain layer to JDBC.
     */
    public void saveRoom() {
        // No-op stub to preserve domain model purity. Persistence is handled by controllers/DAOs.
    }

    /**
     * GRASP — Information Expert. Removes the given user from this room's member list.
     * In this clean domain model architecture, domain entities do not interact
     * directly with the database. This method is a no-op stub to satisfy the DCD
     * method signature without coupling the domain layer to JDBC.
     */
    public void leaveRoom(int userId) {
        // No-op stub to preserve domain model purity. Persistence is handled by controllers/DAOs.
    }

    /** Convenience overload that uses the currently-logged-in user. */
    public void leaveRoom() {
        // No-op stub to preserve domain model purity. Persistence is handled by controllers/DAOs.
    }

    @Override
    public String toString() {
        return roomName;
    }
}
