package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BookmarkDAO {

    private static final Logger LOGGER = Logger.getLogger(BookmarkDAO.class.getName());

    public boolean isBookmarked(int userId, int snippetId) {
        String q = "SELECT 1 FROM bookmarks WHERE user_id = ? AND snippet_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            ps.setInt(2, snippetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "isBookmarked failed", e);
        }
        return false;
    }

    public boolean setBookmarked(int userId, int snippetId, boolean bookmarked) {
        if (bookmarked) {
            String q = DatabaseHelper.insertIgnorePrefix() + "bookmarks (user_id, snippet_id) VALUES (?, ?)";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, userId);
                ps.setInt(2, snippetId);
                return ps.executeUpdate() > 0 || isBookmarked(userId, snippetId);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "setBookmarked (add) failed", e);
            }
        } else {
            String q = "DELETE FROM bookmarks WHERE user_id = ? AND snippet_id = ?";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, userId);
                ps.setInt(2, snippetId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "setBookmarked (remove) failed", e);
            }
        }
        return false;
    }

    public Set<Integer> getBookmarkedSnippetIds(int userId) {
        Set<Integer> ids = new HashSet<>();
        String q = "SELECT snippet_id FROM bookmarks WHERE user_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getBookmarkedSnippetIds failed", e);
        }
        return ids;
    }
}
