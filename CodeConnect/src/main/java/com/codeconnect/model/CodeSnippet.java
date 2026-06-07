package com.codeconnect.model;

import java.sql.Timestamp;

public class CodeSnippet {
    private int id;
    private String title;
    private String language;
    private String code;
    private String description;
    private int uploaderId;
    private String uploaderUsername = "";
    private Timestamp createdAt;
    private String tags = "";
    private int commentCount;
    private boolean bookmarked;
    private boolean hidden = false;
    /** Original filename when uploaded via the file picker (UC-3). Empty if pasted directly. */
    private String uploadedFile = "";
    /** Cached colour-coded rendering produced by {@link #formatSyntax()}. Lazily computed. */
    private String colorCodedFile = "";

    public CodeSnippet(int id, String title, String language, String code, String description, int uploaderId) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.code = code;
        this.description = description;
        this.uploaderId = uploaderId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getUploaderId() { return uploaderId; }
    public void setUploaderId(int uploaderId) { this.uploaderId = uploaderId; }

    public String getUploaderUsername() { return uploaderUsername; }
    public void setUploaderUsername(String uploaderUsername) { this.uploaderUsername = uploaderUsername != null ? uploaderUsername : ""; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags != null ? tags : ""; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public boolean isBookmarked() { return bookmarked; }
    public void setBookmarked(boolean bookmarked) { this.bookmarked = bookmarked; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getUploadedFile() { return uploadedFile; }
    public void setUploadedFile(String uploadedFile) { this.uploadedFile = uploadedFile != null ? uploadedFile : ""; }

    /** Returns the cached colour-coded rendering, populating it on first call via {@link #formatSyntax()}. */
    public String getColorCodedFile() {
        if (colorCodedFile == null || colorCodedFile.isEmpty()) {
            colorCodedFile = formatSyntax();
        }
        return colorCodedFile;
    }
    public void setColorCodedFile(String colorCodedFile) { this.colorCodedFile = colorCodedFile != null ? colorCodedFile : ""; }

    /**
     * GRASP — Information Expert: this entity owns its source text and language,
     * so it is best placed to produce a syntax-decorated form of itself. The
     * returned string has tokens wrapped in lightweight markers so a UI layer
     * (e.g. {@code SyntaxHighlighter}) can re-style them, while still being
     * human-readable for non-UI consumers (logs, exports).
     *
     * <p>For a richer JavaFX-styled rendering, the UI uses
     * {@link com.codeconnect.ui.SyntaxHighlighter} which honours the same
     * language identifiers.
     */
    public String formatSyntax() {
        if (code == null || code.isEmpty()) return "";
        String lang = language == null ? "" : language.toLowerCase();
        String header = "// language=" + (lang.isEmpty() ? "plain" : lang) + "\n";
        // Lightweight, language-aware decoration: highlight common keywords inline.
        String[] keywords;
        switch (lang) {
            case "java":   keywords = new String[]{"public","private","protected","class","static","final","void","int","String","new","return","if","else","for","while","try","catch","throw","throws","extends","implements","package","import"}; break;
            case "python": keywords = new String[]{"def","class","return","if","elif","else","for","while","import","from","as","try","except","finally","with","lambda","yield","pass"}; break;
            case "javascript":
            case "js":     keywords = new String[]{"function","const","let","var","return","if","else","for","while","class","extends","new","this","async","await","try","catch","throw","import","export"}; break;
            default:       keywords = new String[0];
        }
        String formatted = code;
        for (String kw : keywords) {
            // word-boundary replace, wrap with ** markers (UI ignores; logs preserve)
            formatted = formatted.replaceAll("\\b" + java.util.regex.Pattern.quote(kw) + "\\b", "**" + kw + "**");
        }
        return header + formatted;
    }

    @Override
    public String toString() {
        return title + " [" + language + "]";
    }
}
