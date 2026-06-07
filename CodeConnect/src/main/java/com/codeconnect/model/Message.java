package com.codeconnect.model;

import java.sql.Timestamp;

public class Message {
    private int id;
    private int roomId;
    private int senderId;
    private String content;
    private Timestamp timestamp;

    public Message(int id, int roomId, int senderId, String content, Timestamp timestamp) {
        this.id = id;
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }
    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    /**
     * DCD attribute alias. The Deliverable-5 diagram lists both {@code datetime}
     * and {@code timestamp} on Message; we store a single SQL timestamp column
     * and expose this read-only view as a {@link java.time.LocalDateTime} for
     * client code that prefers the modern API.
     */
    public java.time.LocalDateTime getDatetime() {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
