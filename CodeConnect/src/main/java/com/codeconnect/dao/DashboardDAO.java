package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;

import javafx.scene.paint.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DashboardDAO {

    private static final Logger LOGGER = Logger.getLogger(DashboardDAO.class.getName());

    public static final class StatCounts {
        public int mySnippets;
        public int activeRooms;
        public int messagesToday;
        public int pendingReviews;
    }

    public static final class ActiveRoomRow {
        public int roomId;
        public int snippetId;
        public String roomName;
        public String language;
        public int onlineCount;
        public boolean idle;
    }

    public static final class OnlineUserRow {
        public String username;
        public String initials;
        public Color avatarColor;
        public boolean online;
    }

    public static final class ActivityRow {
        public String htmlLine;
        public Timestamp timestamp;
    }

    public StatCounts loadStatsForUser(int userId) {
        StatCounts s = new StatCounts();
        try (Connection conn = DatabaseHelper.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM code_snippets WHERE uploader_id = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) s.mySnippets = rs.getInt(1);
                }
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(DISTINCT dr.id) FROM discussion_rooms dr " +
                                 "JOIN messages m ON m.room_id = dr.id " +
                                 "WHERE datetime(m.timestamp) >= datetime('now', '-7 days')")) {
                if (rs.next()) s.activeRooms = rs.getInt(1);
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM messages WHERE date(timestamp) = date('now')")) {
                if (rs.next()) s.messagesToday = rs.getInt(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM code_snippets cs WHERE cs.uploader_id = ? AND (" +
                            "NOT EXISTS (SELECT 1 FROM discussion_rooms dr WHERE dr.snippet_id = cs.id) " +
                            "OR NOT EXISTS (SELECT 1 FROM messages m JOIN discussion_rooms dr ON dr.id = m.room_id " +
                            "WHERE dr.snippet_id = cs.id AND IFNULL(m.sender_id, 0) <> 0))")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) s.pendingReviews = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "loadStatsForUser failed", e);
        }
        return s;
    }

    public List<ActiveRoomRow> loadActiveRoomsPreview() {
        List<ActiveRoomRow> rows = new ArrayList<>();
        String sql = "SELECT dr.id, dr.snippet_id, dr.room_name, cs.language, " +
                "(SELECT COUNT(DISTINCT m.sender_id) FROM messages m WHERE m.room_id = dr.id " +
                "AND IFNULL(m.sender_id, 0) <> 0 AND datetime(m.timestamp) >= datetime('now', '-30 minutes')) AS online_cnt " +
                "FROM discussion_rooms dr JOIN code_snippets cs ON cs.id = dr.snippet_id " +
                "ORDER BY COALESCE((SELECT MAX(timestamp) FROM messages m WHERE m.room_id = dr.id), datetime('1970-01-01')) DESC LIMIT 5";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ActiveRoomRow r = new ActiveRoomRow();
                r.roomId = rs.getInt(1);
                r.snippetId = rs.getInt(2);
                r.roomName = rs.getString(3);
                r.language = rs.getString(4);
                r.onlineCount = rs.getInt(5);
                r.idle = r.onlineCount <= 0;
                rows.add(r);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "loadActiveRoomsPreview failed", e);
        }
        if (rows.isEmpty()) {
            ActiveRoomRow placeholder = new ActiveRoomRow();
            placeholder.roomId = 0;
            placeholder.roomName = "No rooms yet";
            placeholder.language = "";
            placeholder.onlineCount = 0;
            placeholder.idle = true;
            rows.add(placeholder);
        }
        return rows;
    }

    public List<OnlineUserRow> loadOnlineUsers(int currentUserId) {
        List<OnlineUserRow> rows = new ArrayList<>();
        String sql = "SELECT DISTINCT u.id, u.username FROM users u " +
                "JOIN messages m ON m.sender_id = u.id " +
                "WHERE IFNULL(m.sender_id, 0) <> 0 AND datetime(m.timestamp) >= datetime('now', '-30 minutes') " +
                "ORDER BY u.username LIMIT 8";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                OnlineUserRow r = new OnlineUserRow();
                r.username = rs.getString(2);
                r.initials = initialsFor(r.username);
                r.avatarColor = colorForUser(rs.getInt(1));
                r.online = true;
                rows.add(r);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "loadOnlineUsers failed", e);
        }
        mergeCurrentUser(rows, currentUserId);
        return rows;
    }

    private void mergeCurrentUser(List<OnlineUserRow> rows, int currentUserId) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username FROM users WHERE id = ?")) {
            ps.setInt(1, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                String uname = rs.getString(2);
                for (OnlineUserRow r : rows) {
                    if (uname.equalsIgnoreCase(r.username)) return;
                }
                OnlineUserRow self = new OnlineUserRow();
                self.username = uname;
                self.initials = initialsFor(uname);
                self.avatarColor = colorForUser(rs.getInt(1));
                self.online = true;
                rows.add(0, self);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "mergeCurrentUser failed", e);
        }
    }

    public List<ActivityRow> loadRecentActivity() {
        List<ActivityRow> out = new ArrayList<>();
        String sql = "SELECT u, t, k, ts FROM (" +
                "SELECT u.username AS u, cs.title AS t, 'uploaded' AS k, COALESCE(cs.created_at, CURRENT_TIMESTAMP) AS ts " +
                "FROM code_snippets cs JOIN users u ON u.id = cs.uploader_id " +
                "UNION ALL " +
                "SELECT u.username, cs.title, 'commented', m.timestamp FROM messages m " +
                "JOIN users u ON u.id = m.sender_id " +
                "JOIN discussion_rooms dr ON dr.id = m.room_id " +
                "JOIN code_snippets cs ON cs.id = dr.snippet_id " +
                "WHERE IFNULL(m.sender_id, 0) <> 0" +
                ") ORDER BY ts DESC LIMIT 12";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int n = 0;
            while (rs.next() && n < 5) {
                String user = rs.getString(1);
                String title = rs.getString(2);
                String kind = rs.getString(3);
                ActivityRow row = new ActivityRow();
                if ("uploaded".equals(kind)) {
                    row.htmlLine = "<html><span style='color:#8892a4'>" + esc(user) + " uploaded </span>" +
                            "<span style='color:#5c7cfa'>" + esc(title) + "</span></html>";
                } else {
                    row.htmlLine = "<html><span style='color:#5c7cfa'>" + esc(user) + "</span>" +
                            "<span style='color:#8892a4'> commented on </span>" +
                            "<span style='color:#5c7cfa'>" + esc(title) + "</span></html>";
                }
                try { row.timestamp = rs.getTimestamp(4); } catch (Exception ignored) {}
                out.add(row);
                n++;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "loadRecentActivity failed", e);
        }
        if (out.isEmpty()) {
            ActivityRow r = new ActivityRow();
            r.htmlLine = "<html><span style='color:#8892a4'>No recent activity yet.</span></html>";
            out.add(r);
        }
        return out;
    }

    public int countMyRoomsForUser(int userId) {
        String sql = "SELECT COUNT(DISTINCT dr.id) FROM discussion_rooms dr " +
                "JOIN messages m ON m.room_id = dr.id WHERE m.sender_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "countMyRoomsForUser failed", e);
        }
        return 0;
    }

    public int countNotificationsForUser(int userId) {
        String sql = "SELECT COUNT(*) FROM messages m " +
                "JOIN discussion_rooms dr ON dr.id = m.room_id " +
                "JOIN code_snippets cs ON cs.id = dr.snippet_id " +
                "WHERE m.sender_id <> ? AND IFNULL(m.sender_id, 0) <> 0 " +
                "AND datetime(m.timestamp) >= datetime('now', '-1 days') " +
                "AND (cs.uploader_id = ? OR EXISTS (SELECT 1 FROM messages m2 WHERE m2.room_id = dr.id AND m2.sender_id = ?))";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "countNotificationsForUser failed", e);
        }
        return 0;
    }

    public int countProfileAlerts(int userId) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM code_snippets WHERE uploader_id = ? AND (description IS NULL OR trim(description) = '')")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.min(9, rs.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "countProfileAlerts failed", e);
        }
        return 0;
    }

    public boolean hasUnreadBell(int userId) {
        return countNotificationsForUser(userId) > 0;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String initialsFor(String username) {
        if (username == null || username.isEmpty()) return "?";
        String[] p = username.trim().split("\\s+");
        if (p.length >= 2) {
            return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
        }
        String u = p[0];
        if (u.length() >= 2) return u.substring(0, 2).toUpperCase();
        return u.toUpperCase();
    }

    public static Color colorForUser(int userId) {
        double hue = (Math.abs(userId * 47) % 360);
        return Color.hsb(hue, 0.45, 0.85);
    }

    public static String relativeTime(Timestamp ts) {
        if (ts == null) return "";
        long diffMs = System.currentTimeMillis() - ts.getTime();
        if (diffMs < 60_000) return "just now";
        long m = diffMs / 60_000;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 48) return h + "h ago";
        long d = h / 24;
        return d + "d ago";
    }
}
