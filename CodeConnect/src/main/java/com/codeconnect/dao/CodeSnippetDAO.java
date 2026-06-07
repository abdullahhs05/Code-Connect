package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.CodeSnippet;

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

public class CodeSnippetDAO {

    private static final Logger LOGGER = Logger.getLogger(CodeSnippetDAO.class.getName());

    public boolean addSnippet(CodeSnippet snippet) {
        String query = "INSERT INTO code_snippets (title, language, code, description, uploader_id, tags, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, " + DatabaseHelper.nowSql() + ")";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, snippet.getTitle());
            pstmt.setString(2, snippet.getLanguage());
            pstmt.setString(3, snippet.getCode());
            pstmt.setString(4, snippet.getDescription());
            pstmt.setInt(5, snippet.getUploaderId());
            pstmt.setString(6, snippet.getTags() != null ? snippet.getTags() : "");

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        snippet.setId(rs.getInt(1));
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "addSnippet failed", e);
        }
        return false;
    }

    public List<CodeSnippet> getAllSnippets() {
        return findAllWithDetails(0);
    }

    /**
     * @param viewerUserId pass 0 to skip bookmark flag
     */
    public List<CodeSnippet> findAllWithDetails(int viewerUserId) {
        return findAllWithDetails(viewerUserId, false);
    }

    public List<CodeSnippet> findAllWithDetails(int viewerUserId, boolean includeHidden) {
        List<CodeSnippet> snippets = new ArrayList<>();
        String query = "SELECT cs.*, u.username AS uploader_username, " +
                "(SELECT COUNT(*) FROM messages m JOIN discussion_rooms dr ON dr.id = m.room_id " +
                "WHERE dr.snippet_id = cs.id AND IFNULL(m.sender_id, 0) <> 0) AS comment_count, " +
                "EXISTS(SELECT 1 FROM bookmarks b WHERE b.user_id = ? AND b.snippet_id = cs.id) AS bookmarked " +
                "FROM code_snippets cs JOIN users u ON u.id = cs.uploader_id " +
                (includeHidden ? "" : "WHERE IFNULL(cs.hidden, 0) = 0 ") +
                "ORDER BY COALESCE(cs.created_at, CURRENT_TIMESTAMP) DESC, cs.id DESC";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, viewerUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    snippets.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAllWithDetails failed", e);
        }
        return snippets;
    }

    public boolean setHidden(int snippetId, boolean hidden) {
        String q = "UPDATE code_snippets SET hidden = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setInt(2, snippetId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "setHidden failed", e);
        }
        return false;
    }

    public CodeSnippet findById(int snippetId, int viewerUserId) {
        String q = "SELECT cs.*, u.username AS uploader_username, " +
                "(SELECT COUNT(*) FROM messages m JOIN discussion_rooms dr ON dr.id = m.room_id " +
                "  WHERE dr.snippet_id = cs.id) AS comment_count, " +
                "(SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END " +
                "  FROM bookmarks b WHERE b.user_id = ? AND b.snippet_id = cs.id) AS bookmarked " +
                "FROM code_snippets cs LEFT JOIN users u ON cs.uploader_id = u.id " +
                "WHERE cs.id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, viewerUserId);
            ps.setInt(2, snippetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findById failed", e);
        }
        return null;
    }

    public boolean deleteSnippet(int snippetId) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM code_snippets WHERE id = ?")) {
            ps.setInt(1, snippetId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteSnippet failed", e);
        }
        return false;
    }

    private CodeSnippet mapRow(ResultSet rs) throws SQLException {
        CodeSnippet snippet = new CodeSnippet(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("language"),
                rs.getString("code"),
                rs.getString("description"),
                rs.getInt("uploader_id"));
        snippet.setUploaderUsername(rs.getString("uploader_username"));
        try {
            snippet.setTags(rs.getString("tags"));
        } catch (SQLException ignored) {
            snippet.setTags("");
        }
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts == null) {
            snippet.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        } else {
            snippet.setCreatedAt(ts);
        }
        snippet.setCommentCount(rs.getInt("comment_count"));
        snippet.setBookmarked(rs.getInt("bookmarked") == 1);
        try { snippet.setHidden(rs.getInt("hidden") == 1); } catch (SQLException ignored) {}
        return snippet;
    }

    public boolean updateSnippet(CodeSnippet snippet) {
        String query = "UPDATE code_snippets SET title = ?, language = ?, code = ?, description = ?, tags = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, snippet.getTitle());
            pstmt.setString(2, snippet.getLanguage());
            pstmt.setString(3, snippet.getCode());
            pstmt.setString(4, snippet.getDescription());
            pstmt.setString(5, snippet.getTags() != null ? snippet.getTags() : "");
            pstmt.setInt(6, snippet.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateSnippet failed", e);
        }
        return false;
    }
}
