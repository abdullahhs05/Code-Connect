package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageDAO {

    private static final Logger LOGGER = Logger.getLogger(MessageDAO.class.getName());

    public boolean sendMessage(Message message) {
        String query = "INSERT INTO messages (room_id, sender_id, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, message.getRoomId());
            if (message.getSenderId() == 0) {
                pstmt.setNull(2, Types.INTEGER);
            } else {
                pstmt.setInt(2, message.getSenderId());
            }
            pstmt.setString(3, message.getContent());
            
            if (pstmt.executeUpdate() > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        message.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "sendMessage failed", e);
        }
        return false;
    }

    public List<Message> getMessagesForRoom(int roomId) {
        List<Message> messages = new ArrayList<>();
        String query = "SELECT * FROM messages WHERE room_id = ? ORDER BY timestamp ASC";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
              
            pstmt.setInt(1, roomId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int senderId = rs.getInt("sender_id");
                    if (rs.wasNull()) {
                        senderId = 0;
                    }
                    messages.add(new Message(
                            rs.getInt("id"),
                            rs.getInt("room_id"),
                            senderId,
                            rs.getString("content"),
                            rs.getTimestamp("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getMessagesForRoom failed", e);
        }
        return messages;
    }

    /** Returns the most recent {@code limit} messages in chronological order. */
    public List<Message> getRecentMessages(int roomId, int limit) {
        List<Message> out = new ArrayList<>();
        String query = "SELECT * FROM (SELECT * FROM messages WHERE room_id = ? ORDER BY id DESC LIMIT ?) sub ORDER BY id ASC";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, roomId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int senderId = rs.getInt("sender_id");
                    if (rs.wasNull()) senderId = 0;
                    out.add(new Message(rs.getInt("id"), rs.getInt("room_id"), senderId,
                            rs.getString("content"), rs.getTimestamp("timestamp")));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getRecentMessages failed", e);
        }
        return out;
    }

    /** Returns messages older than the given id, up to {@code limit}, in chronological order. */
    public List<Message> getOlderThan(int roomId, int oldestId, int limit) {
        List<Message> out = new ArrayList<>();
        String query = "SELECT * FROM (SELECT * FROM messages WHERE room_id = ? AND id < ? ORDER BY id DESC LIMIT ?) sub ORDER BY id ASC";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, roomId);
            pstmt.setInt(2, oldestId);
            pstmt.setInt(3, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int senderId = rs.getInt("sender_id");
                    if (rs.wasNull()) senderId = 0;
                    out.add(new Message(rs.getInt("id"), rs.getInt("room_id"), senderId,
                            rs.getString("content"), rs.getTimestamp("timestamp")));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getOlderThan failed", e);
        }
        return out;
    }

    public int countForRoom(int roomId) {
        String q = "SELECT COUNT(*) FROM messages WHERE room_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "countForRoom failed", e);
        }
        return 0;
    }

    public boolean deleteMessage(int messageId) {
        String query = "DELETE FROM messages WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
              
            pstmt.setInt(1, messageId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteMessage failed", e);
        }
        return false;
    }

    public boolean updateMessage(int messageId, String newContent) {
        String query = "UPDATE messages SET content = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
              
            pstmt.setString(1, newContent);
            pstmt.setInt(2, messageId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateMessage failed", e);
        }
        return false;
    }
}
