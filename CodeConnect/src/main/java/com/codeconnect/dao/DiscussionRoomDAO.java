package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.DiscussionRoom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscussionRoomDAO {

    private static final Logger LOGGER = Logger.getLogger(DiscussionRoomDAO.class.getName());

    public static final class RoomSummary {
        public int roomId;
        public int snippetId;
        public String roomName;
        public String snippetTitle;
        public int messageCount;
    }
    
    public DiscussionRoom getOrCreateRoom(int snippetId, String roomName) {
        String selectQuery = "SELECT * FROM discussion_rooms WHERE snippet_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmtSelect = conn.prepareStatement(selectQuery)) {

            pstmtSelect.setInt(1, snippetId);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                if (rs.next()) {
                    DiscussionRoom r = new DiscussionRoom(rs.getInt("id"), rs.getInt("snippet_id"), rs.getString("room_name"));
                    try { r.setPrivate(rs.getInt("is_private") == 1); } catch (SQLException ignored) {}
                    return r;
                }
            }

            // If not found, create
            String insertQuery = "INSERT INTO discussion_rooms (snippet_id, room_name, is_private) VALUES (?, ?, 0)";
            try (PreparedStatement pstmtInsert = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                pstmtInsert.setInt(1, snippetId);
                pstmtInsert.setString(2, roomName);
                if (pstmtInsert.executeUpdate() > 0) {
                    try (ResultSet generatedKeys = pstmtInsert.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            return new DiscussionRoom(generatedKeys.getInt(1), snippetId, roomName);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getOrCreateRoom failed", e);
        }
        return null;
    }

    public boolean setPrivate(int roomId, boolean isPrivate) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE discussion_rooms SET is_private = ? WHERE id = ?")) {
            ps.setInt(1, isPrivate ? 1 : 0);
            ps.setInt(2, roomId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "setPrivate failed", e);
        }
        return false;
    }

    /**
     * Inserts a brand-new discussion room and returns its generated id, or -1
     * on failure. Used by {@link com.codeconnect.model.DiscussionRoom#saveRoom()}
     * and by {@link com.codeconnect.controller.DiscussionController}.
     */
    public int createRoom(int snippetId, String roomName) {
        String q = "INSERT INTO discussion_rooms (snippet_id, room_name) VALUES (?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, snippetId);
            ps.setString(2, roomName);
            if (ps.executeUpdate() > 0) {
                try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "createRoom failed", e);
        }
        return -1;
    }

    /** Removes a single user from a private room's member list. */
    public boolean removeMember(int roomId, int userId) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM room_members WHERE room_id = ? AND user_id = ?")) {
            ps.setInt(1, roomId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "removeMember failed", e);
        }
        return false;
    }

    public boolean addMember(int roomId, int userId) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(DatabaseHelper.insertIgnorePrefix() + "room_members (room_id, user_id) VALUES (?, ?)")) {
            ps.setInt(1, roomId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "addMember failed", e);
        }
        return false;
    }

    /** Returns true if user can access room: room is public, user is admin, owns the snippet, or is a member. */
    public boolean canAccess(int roomId, int userId, boolean isAdmin) {
        if (isAdmin) return true;
        String q = "SELECT dr.is_private, cs.uploader_id, " +
                "(SELECT 1 FROM room_members rm WHERE rm.room_id = dr.id AND rm.user_id = ?) AS is_member " +
                "FROM discussion_rooms dr JOIN code_snippets cs ON cs.id = dr.snippet_id WHERE dr.id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            ps.setInt(2, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                int priv = rs.getInt(1);
                int uploader = rs.getInt(2);
                int isMember = rs.getInt(3);
                if (priv == 0) return true;
                if (uploader == userId) return true;
                return isMember == 1;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "canAccess failed", e);
        }
        return false;
    }

    public boolean updateRoomName(int roomId, String newName) {
        String query = "UPDATE discussion_rooms SET room_name = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, newName);
            pstmt.setInt(2, roomId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateRoomName failed", e);
        }
        return false;
    }

    public List<RoomSummary> listRoomsForUser(int userId) {
        List<RoomSummary> list = new ArrayList<>();
        // A room "belongs" to a user if they are a member, the snippet owner, or have messaged in it
        String sql = "SELECT dr.id, dr.snippet_id, dr.room_name, cs.title, " +
                "       (SELECT COUNT(*) FROM messages mm WHERE mm.room_id = dr.id) AS msg_count " +
                "FROM discussion_rooms dr " +
                "JOIN code_snippets cs ON cs.id = dr.snippet_id " +
                "WHERE dr.id IN (" +
                "      SELECT room_id FROM room_members WHERE user_id = ? " +
                "      UNION SELECT d2.id FROM discussion_rooms d2 " +
                "             JOIN code_snippets c2 ON c2.id = d2.snippet_id WHERE c2.uploader_id = ? " +
                "      UNION SELECT room_id FROM messages WHERE sender_id = ?" +
                ") " +
                "ORDER BY dr.id DESC";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RoomSummary r = new RoomSummary();
                    r.roomId = rs.getInt(1);
                    r.snippetId = rs.getInt(2);
                    r.roomName = rs.getString(3);
                    r.snippetTitle = rs.getString(4);
                    r.messageCount = rs.getInt(5);
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "listRoomsForUser failed", e);
        }
        return list;
    }
}
