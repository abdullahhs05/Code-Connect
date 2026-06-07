package com.codeconnect.ui;

import com.codeconnect.dao.DashboardDAO;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class DashboardView {

    private final Consumer<CodeSnippet> onJoin, onView, onDelete, onBookmark;
    private final Runnable onBell;

    private final VBox root;
    private final VBox cardsHost = new VBox(10);
    private final TextField searchField = new TextField();
    private final TextField authorField = new TextField();
    private final ComboBox<String> langFilter = new ComboBox<>();
    private final ComboBox<String> dateFilter = new ComboBox<>();
    private final Label[] statValues = new Label[4];
    private final Button[] tabBtns  = new Button[4];

    private List<CodeSnippet> source = new ArrayList<>();
    private int activeTab = 0;
    private IntConsumer tabListener;
    private Integer selectedId = null;

    private final Circle bellDot = new Circle(4, Color.web("#EF4444"));

    public DashboardView(Consumer<CodeSnippet> onJoin, Consumer<CodeSnippet> onView,
                         Consumer<CodeSnippet> onDelete, Consumer<CodeSnippet> onBookmark,
                         Runnable onBell) {
        this.onJoin = onJoin; this.onView = onView;
        this.onDelete = onDelete; this.onBookmark = onBookmark;
        this.onBell = onBell;

        root = new VBox(0);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(12, 16, 16, 16));

        root.getChildren().addAll(buildTopBar(), buildCenter());
    }

    private VBox buildTopBar() {
        searchField.setPromptText("Search snippets, rooms, users...");
        searchField.getStyleClass().add("text-field-dark");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        langFilter.getItems().addAll("All Languages","Java","Python","C++","JavaScript","HTML/CSS","Other");
        langFilter.setValue("All Languages");
        langFilter.getStyleClass().add("combo-dark");

        dateFilter.getItems().addAll("Any Date","Today","Last 7 days","Last 30 days");
        dateFilter.setValue("Any Date");
        dateFilter.getStyleClass().add("combo-dark");

        authorField.setPromptText("Author username");
        authorField.getStyleClass().add("text-field-dark");
        authorField.setPrefWidth(150);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-primary");
        searchBtn.setOnAction(e -> rebuildCards());
        searchField.setOnAction(e -> rebuildCards());
        authorField.setOnAction(e -> rebuildCards());
        langFilter.setOnAction(e -> rebuildCards());
        dateFilter.setOnAction(e -> rebuildCards());

        Button bellBtn = new Button("🔔");
        bellBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#6A7A8E; -fx-font-size:16px; -fx-cursor:hand; -fx-padding:4 8 4 8;");
        bellBtn.setOnAction(e -> onBell.run());
        bellDot.setVisible(false);
        StackPane bellStack = new StackPane(bellBtn, bellDot);
        StackPane.setAlignment(bellDot, Pos.TOP_RIGHT);
        StackPane.setMargin(bellDot, new Insets(4, 4, 0, 0));

        HBox topRow = new HBox(8, searchField, authorField, langFilter, dateFilter, searchBtn, bellStack);
        topRow.setAlignment(Pos.CENTER_LEFT);

        HBox statsRow = new HBox(12);
        statsRow.setPadding(new Insets(14, 0, 14, 0));
        String[] labels  = {"My Snippets","Active Rooms","Messages Today","Pending Reviews"};
        String[] accents = {"#00C896","#3fb950","#F5A524","#EF4444"};
        for (int i = 0; i < 4; i++) {
            statsRow.getChildren().add(statCard(labels[i], accents[i], i));
        }
        HBox.setHgrow(statsRow, Priority.ALWAYS);

        VBox top = new VBox(10, topRow, statsRow);
        return top;
    }

    private HBox statCard(String label, String accent, int idx) {
        Label val = new Label("0");
        val.getStyleClass().add("kpi-number");
        String cls;
        switch (accent) {
            case "#00C896": cls = "kpi-number-teal"; break;
            case "#F5A524": cls = "kpi-number-amber"; break;
            case "#3fb950": cls = "kpi-number-green"; break;
            case "#EF4444": cls = "kpi-number-red"; break;
            default: cls = "kpi-number-teal";
        }
        val.getStyleClass().add(cls);
        statValues[idx] = val;

        Label sub = new Label(label.toUpperCase());
        sub.getStyleClass().add("kpi-label");

        VBox content = new VBox(-2, val, sub);
        content.getStyleClass().add("kpi-block");
        content.setStyle("-fx-border-color: transparent transparent " + accent + " transparent; -fx-border-width: 0 0 1 0;");

        HBox card = new HBox(content);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildCenter() {
        HBox tabRow = new HBox(0);
        tabRow.setPadding(new Insets(4, 0, 10, 0));
        String[] tabs = {"All Snippets","My Uploads","Bookmarked","Recent"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = new Button(tabs[i]);
            b.getStyleClass().add("tab-btn");
            b.setOnAction(e -> selectTab(idx));
            tabBtns[i] = b;
            tabRow.getChildren().add(b);
            if (i < tabs.length - 1) tabRow.getChildren().add(new Region() {{ setMinWidth(18); }});
        }
        selectTab(0);

        cardsHost.setPadding(new Insets(8));
        cardsHost.setStyle("-fx-background-color:#080C10;");

        ScrollPane sp = new ScrollPane(cardsHost);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#080C10; -fx-border-color:#1E2832;");
        sp.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox center = new VBox(0, tabRow, sp);
        VBox.setVgrow(center, Priority.ALWAYS);
        return center;
    }

    public void setTabListener(IntConsumer l) { this.tabListener = l; }

    public void setActiveTab(int idx) {
        if (idx >= 0 && idx < 4) selectTab(idx);
    }

    private void selectTab(int idx) {
        activeTab = idx;
        for (int i = 0; i < tabBtns.length; i++) {
            tabBtns[i].getStyleClass().removeAll("tab-btn-active");
            if (i == idx) tabBtns[i].getStyleClass().add("tab-btn-active");
        }
        rebuildCards();
        if (tabListener != null) tabListener.accept(idx);
    }

    public void setSnippets(List<CodeSnippet> snippets) {
        this.source = snippets != null ? new ArrayList<>(snippets) : new ArrayList<>();
        rebuildCards();
    }

    public void updateStats(DashboardDAO.StatCounts c) {
        statValues[0].setText(String.valueOf(c.mySnippets));
        statValues[1].setText(String.valueOf(c.activeRooms));
        statValues[2].setText(String.valueOf(c.messagesToday));
        statValues[3].setText(String.valueOf(c.pendingReviews));
    }

    public void setBellUnread(boolean unread) { bellDot.setVisible(unread); }

    private List<CodeSnippet> filtered() {
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String authorQ = authorField.getText() != null ? authorField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String selLang = langFilter.getValue();
        String selDate = dateFilter.getValue();
        var cur = Session.getCurrentUser();

        long now = System.currentTimeMillis();
        long cutoff = -1L;
        if ("Today".equals(selDate))         cutoff = now - 24L * 60 * 60 * 1000;
        else if ("Last 7 days".equals(selDate))  cutoff = now - 7L * 24 * 60 * 60 * 1000;
        else if ("Last 30 days".equals(selDate)) cutoff = now - 30L * 24 * 60 * 60 * 1000;

        List<CodeSnippet> base = new ArrayList<>();
        for (CodeSnippet s : source) {
            if (activeTab == 1 && (cur == null || s.getUploaderId() != cur.getId())) continue;
            if (activeTab == 2 && !s.isBookmarked()) continue;
            base.add(s);
        }
        if (activeTab == 3) {
            base.sort((a, b) -> {
                long ta = a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0;
                long tb = b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0;
                return Long.compare(tb, ta);
            });
            if (base.size() > 20) base = new ArrayList<>(base.subList(0, 20));
        }

        List<CodeSnippet> out = new ArrayList<>();
        for (CodeSnippet s : base) {
            boolean ms = q.isEmpty()
                || s.getTitle().toLowerCase(Locale.ROOT).contains(q)
                || s.getLanguage().toLowerCase(Locale.ROOT).contains(q)
                || (s.getUploaderUsername() != null && s.getUploaderUsername().toLowerCase(Locale.ROOT).contains(q))
                || (s.getTags() != null && s.getTags().toLowerCase(Locale.ROOT).contains(q));
            boolean ml = "All Languages".equals(selLang) || s.getLanguage().equalsIgnoreCase(selLang);
            boolean ma = authorQ.isEmpty()
                || (s.getUploaderUsername() != null && s.getUploaderUsername().toLowerCase(Locale.ROOT).contains(authorQ));
            boolean md = cutoff < 0
                || (s.getCreatedAt() != null && s.getCreatedAt().getTime() >= cutoff);
            if (ms && ml && ma && md) out.add(s);
        }
        return out;
    }

    private void rebuildCards() {
        cardsHost.getChildren().clear();
        List<CodeSnippet> list = filtered();
        if (list.isEmpty()) {
            Label empty = new Label("No snippets found. Try a different search or filter!");
            empty.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:13px; -fx-padding:40 10 40 10;");
            cardsHost.getChildren().add(empty);
        } else {
            for (CodeSnippet s : list) {
                cardsHost.getChildren().add(buildSnippetCard(s));
            }
        }
    }

    private VBox buildSnippetCard(CodeSnippet s) {
        VBox card = new VBox(8);
        card.getStyleClass().add("snippet-card");
        if (selectedId != null && selectedId == s.getId()) card.getStyleClass().add("snippet-card-selected");
        card.setOnMouseClicked(e -> { selectedId = s.getId(); rebuildCards(); });

        Label title = new Label(s.getTitle());
        title.setStyle("-fx-text-fill:#00C896; -fx-font-weight:bold; -fx-font-size:15px;");
        Label langBadge = langBadge(s.getLanguage());
        HBox titleRow = new HBox(8, title, langBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        Label desc = new Label(s.getDescription() != null && !s.getDescription().isEmpty()
            ? s.getDescription() : "No description provided.");
        desc.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:12px;");
        desc.setWrapText(true);

        HBox tagRow = buildTagRow(s.getTags());

        Label uploader = new Label(s.getUploaderUsername() != null ? s.getUploaderUsername() : "User " + s.getUploaderId());
        uploader.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:11px;");
        Label ago = new Label(DashboardDAO.relativeTime(s.getCreatedAt()));
        ago.setStyle("-fx-text-fill:#374150; -fx-font-size:11px;");
        Label comments = new Label(s.getCommentCount() + " comments");
        comments.setStyle("-fx-text-fill:#374150; -fx-font-size:11px;");
        HBox meta = new HBox(8, uploader, dot(), ago, comments);
        meta.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();
        sep.getStyleClass().add("separator-dark");

        Button joinBtn = new Button("Join Discussion");
        joinBtn.getStyleClass().add("btn-primary");
        joinBtn.setOnAction(e -> { e.consume(); onJoin.accept(s); });

        Button viewBtn = new Button("View Code");
        viewBtn.getStyleClass().add("btn-ghost");
        viewBtn.setOnAction(e -> { e.consume(); onView.accept(s); });

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().add("btn-danger");
        delBtn.setOnAction(e -> { e.consume(); onDelete.accept(s); });

        Button bmBtn = new Button(s.isBookmarked() ? "★" : "☆");
        bmBtn.setStyle("-fx-background-color:transparent; -fx-font-size:18px; -fx-cursor:hand; -fx-padding:4 8; -fx-text-fill:"
            + (s.isBookmarked() ? "#F5A524" : "#374150") + ";");
        bmBtn.setOnAction(e -> { e.consume(); onBookmark.accept(s); });

        HBox actions = new HBox(8, joinBtn, viewBtn, delBtn, bmBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(8, 0, 0, 0));

        card.getChildren().addAll(titleRow, desc, tagRow, meta, sep, actions);
        return card;
    }

    private HBox buildTagRow(String tags) {
        HBox row = new HBox(6);
        if (tags == null || tags.trim().isEmpty()) return row;
        Arrays.stream(tags.split(","))
            .map(String::trim).filter(t -> !t.isEmpty())
            .forEach(t -> row.getChildren().add(tagPill(t)));
        return row;
    }

    private Label tagPill(String text) {
        Label l = new Label(text);
        String low = text.toLowerCase(Locale.ROOT);
        if (low.contains("review")) l.getStyleClass().add("tag-review");
        else if (low.contains("bug")) l.getStyleClass().add("tag-bug");
        else l.getStyleClass().add("tag-default");
        return l;
    }

    private Label langBadge(String lang) {
        Label l = new Label(lang != null ? lang : "");
        String low = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        if (low.contains("java") && !low.contains("script")) l.getStyleClass().add("lang-java");
        else if (low.contains("python")) l.getStyleClass().add("lang-python");
        else l.getStyleClass().add("lang-default");
        return l;
    }

    private Label dot() {
        Label d = new Label("●");
        d.setStyle("-fx-text-fill:#00C896; -fx-font-size:8px;");
        return d;
    }

    public VBox getView() { return root; }
}
