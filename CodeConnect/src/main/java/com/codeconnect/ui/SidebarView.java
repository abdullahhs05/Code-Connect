package com.codeconnect.ui;

import com.codeconnect.dao.DashboardDAO;
import com.codeconnect.model.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SidebarView {

    private static final double EXPANDED_WIDTH  = 240;
    private static final double COLLAPSED_WIDTH = 62;

    private final VBox root;
    private final Consumer<String> onNav;
    private final Runnable onLogout;
    private String activeKey = "DASHBOARD";
    private final Map<String, NavEntry> entries = new LinkedHashMap<>();

    private Canvas avatarCanvas;
    private Label avatarLabel;
    private Label userLabel;
    private Label roleLabel;
    private Label rolePill;
    private Label snippetCountLabel;
    private Label sessionLabel;
    private VBox adminSection;
    private VBox navLabelsBox;
    private Button collapseBtn;
    private boolean collapsed = false;

    private Timeline sessionCountdown;
    private long loginTime = System.currentTimeMillis();

    private final Runnable onQuickUpload;

    public SidebarView(Consumer<String> onNav, Runnable onLogout) {
        this(onNav, onLogout, null);
    }

    public SidebarView(Consumer<String> onNav, Runnable onLogout, Runnable onQuickUpload) {
        this.onNav = onNav;
        this.onLogout = onLogout;
        this.onQuickUpload = onQuickUpload;

        root = new VBox();
        root.getStyleClass().add("sidebar");
        root.setPrefWidth(EXPANDED_WIDTH);
        root.setSpacing(0);

        root.getChildren().addAll(buildCollapseBar(), buildTop(), buildQuickAction(), buildNav(), buildBottom());
    }

    private HBox buildCollapseBar() {
        collapseBtn = new Button("«");
        collapseBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#374150; -fx-font-size:13px; -fx-cursor:hand; -fx-padding:4 8 4 8;");
        collapseBtn.setOnAction(e -> toggleCollapse());
        Tooltip.install(collapseBtn, new Tooltip("Collapse / Expand sidebar"));

        HBox bar = new HBox(collapseBtn);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(6, 4, 0, 4));
        return bar;
    }

    private VBox buildTop() {
        Label brand = new Label("CodeConnect");
        brand.getStyleClass().add("brand-label");

        Label sub = new Label("Collaborative Code Platform");
        sub.getStyleClass().add("brand-sub");
        VBox.setMargin(sub, new Insets(2, 0, 10, 0));

        avatarCanvas = new Canvas(44, 44);
        avatarLabel = new Label("?");
        avatarLabel.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        StackPane avatarStack = new StackPane(avatarCanvas, avatarLabel);
        avatarStack.setPrefSize(44, 44);
        avatarStack.setMaxSize(44, 44);

        userLabel = new Label(" ");
        userLabel.setStyle("-fx-text-fill:#D4DCE8; -fx-font-weight:bold; -fx-font-size:13px;");

        rolePill = new Label(" ");
        rolePill.setStyle("-fx-background-radius:4; -fx-padding:1 7 1 7; -fx-font-size:10px; -fx-font-weight:bold;");

        snippetCountLabel = new Label("");
        snippetCountLabel.setStyle("-fx-text-fill:#374150; -fx-font-size:10px;");

        sessionLabel = new Label("");
        sessionLabel.setStyle("-fx-text-fill:#F5A524; -fx-font-size:10px;");

        VBox textCol = new VBox(2, userLabel, rolePill, snippetCountLabel, sessionLabel);

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill:#3fb950; -fx-font-size:10px;");
        dot.setTranslateY(-2);

        HBox userCard = new HBox(10, avatarStack, textCol);
        userCard.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textCol, Priority.ALWAYS);
        userCard.getChildren().add(dot);
        userCard.getStyleClass().add("user-card");
        userCard.setPadding(new Insets(10));

        navLabelsBox = new VBox(2, brand, sub, userCard);
        VBox.setMargin(navLabelsBox, new Insets(0, 0, 6, 0));

        return navLabelsBox;
    }

    private void drawAvatar() {
        GraphicsContext gc = avatarCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, avatarCanvas.getWidth(), avatarCanvas.getHeight());
        String colorHex = avatarLabel.getUserData() != null ? (String) avatarLabel.getUserData() : "#00C896";
        gc.setFill(Color.web(colorHex));
        gc.fillOval(0, 0, avatarCanvas.getWidth(), avatarCanvas.getHeight());
    }

    private HBox buildQuickAction() {
        Button quickBtn = new Button("+ New Snippet");
        quickBtn.getStyleClass().add("btn-success");
        quickBtn.setMaxWidth(Double.MAX_VALUE);
        quickBtn.setStyle(quickBtn.getStyle() + " -fx-font-size:12px; -fx-padding:7 12 7 12;");
        quickBtn.setOnAction(e -> {
            if (onQuickUpload != null) onQuickUpload.run();
            else { setActive("UPLOAD"); onNav.accept("UPLOAD"); }
        });
        Tooltip.install(quickBtn, new Tooltip("Share a new code snippet"));

        HBox wrap = new HBox(quickBtn);
        wrap.setPadding(new Insets(0, 8, 8, 8));
        HBox.setHgrow(quickBtn, Priority.ALWAYS);
        return wrap;
    }

    private VBox buildNav() {
        VBox nav = new VBox(2);
        VBox.setVgrow(nav, Priority.ALWAYS);
        nav.setPadding(new Insets(4, 0, 0, 0));

        nav.getChildren().add(sectionLabel("MAIN"));
        nav.getChildren().add(navRow("DASHBOARD",     "🏠",  "Dashboard",       null));
        nav.getChildren().add(navRow("UPLOAD",        "📤",  "Upload Snippet",  null));
        nav.getChildren().add(navRow("PROFILE",       "👤",  "My Profile",      "profileBadge"));

        nav.getChildren().add(sectionLabel("COLLABORATION"));
        nav.getChildren().add(navRow("MY_ROOMS",      "💬",  "My Rooms",        "roomsBadge"));
        nav.getChildren().add(navRow("BOOKMARKS",     "★",   "Bookmarks",       null));
        nav.getChildren().add(navRow("NOTIFICATIONS", "🔔",  "Notifications",   "notifBadge"));

        adminSection = new VBox(2);
        adminSection.setVisible(false);
        adminSection.setManaged(false);
        adminSection.getChildren().add(sectionLabel("ADMIN"));
        adminSection.getChildren().add(navRow("ADMIN", "⚙", "Admin Panel", null));
        nav.getChildren().add(adminSection);

        return nav;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section-label");
        VBox.setMargin(l, new Insets(8, 0, 2, 0));
        return l;
    }

    private Button navRow(String key, String icon, String label, String badgeKey) {
        Label badge = new Label("");
        badge.getStyleClass().add("badge");
        badge.setVisible(false);
        badge.setManaged(false);

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size:14px; -fx-min-width:20px;");
        Label textLbl = new Label(label);
        textLbl.setStyle("-fx-font-size:13px;");

        HBox graphic = new HBox(8, iconLbl, textLbl, badge);
        graphic.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textLbl, Priority.ALWAYS);

        Button btn = new Button();
        btn.setGraphic(graphic);
        btn.getStyleClass().add("nav-item");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);

        Tooltip.install(btn, new Tooltip(label));

        btn.setOnAction(e -> {
            setActive(key);
            onNav.accept(key);
        });

        NavEntry entry = new NavEntry(key, btn, badge, badgeKey, iconLbl, textLbl);
        entries.put(key, entry);
        return btn;
    }

    private VBox buildBottom() {
        Button logout = new Button("Logout");
        logout.getStyleClass().add("logout-btn");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> onLogout.run());
        Tooltip.install(logout, new Tooltip("Sign out of CodeConnect"));

        VBox bottom = new VBox(logout);
        VBox.setMargin(bottom, new Insets(12, 0, 0, 0));
        return bottom;
    }

    private void toggleCollapse() {
        collapsed = !collapsed;
        double targetWidth = collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;
        root.setPrefWidth(targetWidth);
        collapseBtn.setText(collapsed ? "»" : "«");

        navLabelsBox.setVisible(!collapsed);
        navLabelsBox.setManaged(!collapsed);

        for (NavEntry e : entries.values()) {
            e.textLbl.setVisible(!collapsed);
            e.textLbl.setManaged(!collapsed);
            if (!collapsed) {
                e.badge.setManaged(true);
            }
        }
    }

    public void bindUser(User u) {
        if (u == null) return;
        userLabel.setText(u.getUsername());

        boolean admin = "Admin".equals(u.getRole());
        rolePill.setText(u.getRole());
        rolePill.setStyle(rolePill.getStyle() +
            (admin ? "-fx-background-color:#3a2e0a; -fx-text-fill:#F5A524;"
                   : "-fx-background-color:#0a2a1a; -fx-text-fill:#3fb950;"));

        String ini = DashboardDAO.initialsFor(u.getUsername());
        avatarLabel.setText(ini);
        String hex = colorHexForUser(u.getId());
        avatarLabel.setUserData(hex);
        avatarLabel.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;");
        drawAvatar();

        adminSection.setVisible(admin);
        adminSection.setManaged(admin);

        loginTime = System.currentTimeMillis();
        startSessionCountdown();
    }

    public void updateSnippetCount(int count) {
        snippetCountLabel.setText(count + " snippet" + (count == 1 ? "" : "s") + " shared");
    }

    private void startSessionCountdown() {
        if (sessionCountdown != null) sessionCountdown.stop();
        sessionCountdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long elapsed = System.currentTimeMillis() - loginTime;
            long remaining = 300_000L - elapsed;
            if (remaining <= 0) {
                sessionLabel.setText("Session expiring...");
                sessionLabel.setStyle("-fx-text-fill:#EF4444; -fx-font-size:10px;");
            } else if (remaining < 60_000) {
                long secs = remaining / 1000;
                sessionLabel.setText("Session: " + secs + "s left");
                sessionLabel.setStyle("-fx-text-fill:#EF4444; -fx-font-size:10px;");
            } else {
                long mins = remaining / 60_000;
                sessionLabel.setText("Session: " + mins + "m left");
                sessionLabel.setStyle("-fx-text-fill:#F5A524; -fx-font-size:10px;");
            }
        }));
        sessionCountdown.setCycleCount(Timeline.INDEFINITE);
        sessionCountdown.play();
    }

    public void resetSessionTimer() {
        loginTime = System.currentTimeMillis();
    }

    public void stopSessionCountdown() {
        if (sessionCountdown != null) sessionCountdown.stop();
        sessionLabel.setText("");
    }

    private String colorHexForUser(int id) {
        float hue = (Math.abs(id * 47) % 360);
        Color c = Color.hsb(hue, 0.45, 0.85);
        return String.format("#%02x%02x%02x",
            (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    public void setActive(String key) {
        this.activeKey = key;
        for (NavEntry e : entries.values()) {
            e.btn.getStyleClass().removeAll("nav-item-active");
            if (e.key.equals(key)) e.btn.getStyleClass().add("nav-item-active");
        }
    }

    public void refreshBadges(int profile, int rooms, int notifs) {
        setBadge("profileBadge", profile);
        setBadge("roomsBadge", rooms);
        setBadge("notifBadge", notifs);
    }

    private void setBadge(String badgeKey, int value) {
        for (NavEntry e : entries.values()) {
            if (badgeKey.equals(e.badgeKey)) {
                if (value > 0) {
                    e.badge.setText(String.valueOf(Math.min(99, value)));
                    e.badge.setVisible(true);
                    e.badge.setManaged(!collapsed);
                } else {
                    e.badge.setVisible(false);
                    e.badge.setManaged(false);
                }
            }
        }
    }

    public VBox getView() { return root; }

    private static final class NavEntry {
        final String key;
        final Button btn;
        final Label badge;
        final String badgeKey;
        final Label iconLbl;
        final Label textLbl;

        NavEntry(String key, Button btn, Label badge, String badgeKey, Label iconLbl, Label textLbl) {
            this.key = key; this.btn = btn; this.badge = badge;
            this.badgeKey = badgeKey; this.iconLbl = iconLbl; this.textLbl = textLbl;
        }
    }
}
