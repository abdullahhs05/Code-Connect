package com.codeconnect.controller;

import com.codeconnect.dao.CodeSnippetDAO;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * GRASP — Pure Fabrication. Centralises all snippet-related system operations
 * (upload, search, view, moderation) so that UI views never talk directly to
 * the persistence layer for these flows.
 *
 * <p>Maps to the {@code SnippetController} class in the Deliverable-5 DCD and
 * implements:
 * <ul>
 *   <li>UC-3 Upload Code Snippet ({@link #handleUpload})</li>
 *   <li>UC-4 View Code Snippet ({@link #requestSnippetData})</li>
 *   <li>UC-9 Search Code Discussions ({@link #executeSearch})</li>
 *   <li>UC-12 Moderate Snippets ({@link #applyModeration})</li>
 * </ul>
 */
public class SnippetController {

    /** Hard upper bound on accepted snippet size (UC-3 extension 4a). */
    public static final int MAX_CODE_BYTES = 256 * 1024;

    private final CodeSnippetDAO snippetDAO;

    public SnippetController() {
        this(new CodeSnippetDAO());
    }

    /** Test seam — inject a mock DAO. */
    public SnippetController(CodeSnippetDAO snippetDAO) {
        this.snippetDAO = snippetDAO;
    }

    /** Result envelope for upload/moderation operations. */
    public static final class OpResult {
        public final boolean success;
        public final String message;
        public final CodeSnippet snippet; // null on failure or for moderation ops

        public OpResult(boolean success, String message, CodeSnippet snippet) {
            this.success = success;
            this.message = message;
            this.snippet = snippet;
        }
    }

    /**
     * UC-3 main flow steps 2-5. Validates required fields and size, then
     * persists a new snippet for the currently logged-in user.
     */
    public OpResult handleUpload(String title, String language, String code, String description, String tags) {
        return handleUpload(title, language, code, description, tags, "");
    }

    /** Overload that records the original filename when the snippet was imported via a file picker (UC-3). */
    public OpResult handleUpload(String title, String language, String code, String description, String tags, String uploadedFile) {
        // UC-3 step 3: validateData()
        OpResult validation = validateData(title, language, code);
        if (!validation.success) return validation;

        User current = Session.getCurrentUser();
        if (current == null) {
            return new OpResult(false, "You must be logged in to upload.", null);
        }
        // UC-3 step 4: instantiate CodeSnippet (Information Expert)
        CodeSnippet snippet = new CodeSnippet(0, title.trim(), language.trim(), code, description, current.getId());
        snippet.setTags(tags == null ? "" : tags);
        snippet.setUploaderUsername(current.getUsername());
        snippet.setUploadedFile(uploadedFile == null ? "" : uploadedFile);

        // UC-3 step 5: DatabaseManager.saveSnippet
        if (!snippetDAO.addSnippet(snippet)) {
            return new OpResult(false, "Database error — snippet not saved.", null);
        }
        return new OpResult(true, "Snippet uploaded.", snippet);
    }

    /**
     * UC-3 step 3 surfaced as a public helper so the UI can pre-validate before
     * even building the snippet object. Returns success=true when input passes.
     */
    public OpResult validateData(String title, String language, String code) {
        if (title == null || title.trim().isEmpty()) {
            return new OpResult(false, "Title is required.", null);
        }
        if (language == null || language.trim().isEmpty()) {
            return new OpResult(false, "Language is required.", null);
        }
        if (code == null || code.isEmpty()) {
            return new OpResult(false, "Code body is empty.", null);
        }
        if (code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CODE_BYTES) {
            return new OpResult(false, "Code is too large (>256 KB).", null);
        }
        return new OpResult(true, "ok", null);
    }

    /**
     * UC-4 step 2-3. Returns the requested snippet with its metadata + bookmark
     * state for the viewer. Returns null when the snippet was deleted/hidden
     * (UC-4 extension 3a).
     */
    public CodeSnippet requestSnippetData(int snippetId) {
        User current = Session.getCurrentUser();
        return snippetDAO.findById(snippetId, current == null ? 0 : current.getId());
    }

    /**
     * UC-9 main flow. Filters the visible snippets by case-insensitive
     * keyword match on title/tags/description and optional language equality.
     *
     * @param keywords free-text search; null/empty matches all
     * @param language exact language match; null/empty matches all
     */
    public List<CodeSnippet> executeSearch(String keywords, String language) {
        User current = Session.getCurrentUser();
        boolean includeHidden = current != null && current.isAdmin();
        List<CodeSnippet> all = snippetDAO.findAllWithDetails(current == null ? 0 : current.getId(), includeHidden);
        String kw = keywords == null ? "" : keywords.trim().toLowerCase(Locale.ROOT);
        String lang = language == null ? "" : language.trim();
        return all.stream()
                .filter(s -> lang.isEmpty() || lang.equalsIgnoreCase(s.getLanguage()))
                .filter(s -> kw.isEmpty()
                        || s.getTitle().toLowerCase(Locale.ROOT).contains(kw)
                        || (s.getTags() != null && s.getTags().toLowerCase(Locale.ROOT).contains(kw))
                        || (s.getDescription() != null && s.getDescription().toLowerCase(Locale.ROOT).contains(kw)))
                .collect(Collectors.toList());
    }

    /**
     * UC-12 step 5-7. Applies an admin moderation action. Allowed actions:
     * {@code "HIDE"}, {@code "UNHIDE"}, {@code "DELETE"}.
     *
     * <p>Permission check (UC-12 precondition): caller must be admin.
     */
    public OpResult applyModeration(int snippetId, String action) {
        User current = Session.getCurrentUser();
        if (current == null || !current.isAdmin()) {
            return new OpResult(false, "Only administrators can moderate content.", null);
        }
        if (action == null) return new OpResult(false, "No action specified.", null);
        switch (action.toUpperCase(Locale.ROOT)) {
            case "HIDE":
                return snippetDAO.setHidden(snippetId, true)
                        ? new OpResult(true, "Snippet hidden.", null)
                        : new OpResult(false, "Failed to hide snippet.", null);
            case "UNHIDE":
                return snippetDAO.setHidden(snippetId, false)
                        ? new OpResult(true, "Snippet restored.", null)
                        : new OpResult(false, "Failed to restore snippet.", null);
            case "DELETE":
                return snippetDAO.deleteSnippet(snippetId)
                        ? new OpResult(true, "Snippet deleted.", null)
                        : new OpResult(false, "Failed to delete snippet.", null);
            default:
                return new OpResult(false, "Unknown moderation action: " + action, null);
        }
    }
}
