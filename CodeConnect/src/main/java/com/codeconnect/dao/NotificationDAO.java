package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NotificationDAO {

    private static final Logger LOGGER = Logger.getLogger(NotificationDAO.class.getName());

    public static final class Notification {
        public int id;
        public int userId;
        public String type;        // MENTION | REPLY | SYSTEM
        public String message;
        public Integer roomId;
        public Integer snippetId;
        public boolean read;
        public Timestamp createdAt;
    }

    public boolean addNotification(int userId, String type, String message,
                                   Integer roomId, Integer snippetId) {
        String q = "INSERT INTO notifications (user_id, type, message, room_id, snippet_id, is_read, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 0, " + DatabaseHelper.nowSql() + ")";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setString(3, message);
            if (roomId != null) ps.setInt(4, roomId); else ps.setNull(4, Types.INTEGER);
            if (snippetId != null) ps.setInt(5, snippetId); else ps.setNull(5, Types.INTEGER);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "addNotification failed", e);
        }
        return false;
    }

    public List<Notification> listForUser(int userId, int limit) {
        List<Notification> out = new ArrayList<>();
        String q = "SELECT * FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification();
                    n.id = rs.getInt("id");
                    n.userId = rs.getInt("user_id");
                    n.type = rs.getString("type");
                    n.message = rs.getString("message");
                    int rid = rs.getInt("room_id");      n.roomId    = rs.wasNull() ? null : rid;
                    int sid = rs.getInt("snippet_id");   n.snippetId = rs.wasNull() ? null : sid;
                    n.read = rs.getInt("is_read") == 1;
                    n.createdAt = rs.getTimestamp("created_at");
                    out.add(n);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "listForUser failed", e);
        }
        return out;
    }

    public int countUnread(int userId) {
        String q = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "countUnread failed", e);
        }
        return 0;
    }

    public boolean markRead(int notificationId) {
        String q = "UPDATE notifications SET is_read = 1 WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, notificationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "markRead failed", e);
        }
        return false;
    }

    public boolean markAllRead(int userId) {
        String q = "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "markAllRead failed", e);
        }
        return false;
    }
}
