package com.codeconnect.ui;

import com.codeconnect.dao.BookmarkDAO;
import com.codeconnect.dao.CodeSnippetDAO;
import com.codeconnect.dao.DashboardDAO;
import com.codeconnect.dao.DiscussionRoomDAO;
import com.codeconnect.dao.UserDAO;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.DiscussionRoom;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

public class MainWindow {

    private Stage stage;
    private Scene scene;
    private StackPane root;
    private BorderPane appShell;

    private SidebarView sidebar;
    private RightInsightView rightPanel;
    private DashboardView dashboardView;
    private StackPane centerHost;

    private final CodeSnippetDAO snippetDAO = new CodeSnippetDAO();
    private final UserDAO userDAO = new UserDAO();
    private final DiscussionRoomDAO roomDAO = new DiscussionRoomDAO();
    private final BookmarkDAO bookmarkDAO = new BookmarkDAO();
    private final DashboardDAO dashDAO = new DashboardDAO();
    private final com.codeconnect.dao.NotificationDAO notifDAO = new com.codeconnect.dao.NotificationDAO();

    private Timeline refreshTimer;
    private Timeline sessionTimer;
    private long lastActivity = System.currentTimeMillis();

    public void show(Stage primaryStage) {
        this.stage = primaryStage;

        root = new StackPane();
        root.setStyle("-fx-background-color:#080C10;");

        appShell = buildAppShell();
        AuthView authView = new AuthView(this::onLoginSuccess);

        root.getChildren().add(appShell);
        root.getChildren().add(authView.getView());
        appShell.setVisible(false);

        scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/com/codeconnect/theme.css").toExternalForm());

        scene.setOnKeyPressed(e -> { lastActivity = System.currentTimeMillis(); sidebar.resetSessionTimer(); });
        scene.setOnMouseMoved(e -> { lastActivity = System.currentTimeMillis(); sidebar.resetSessionTimer(); });
        scene.setOnMouseClicked(e -> { lastActivity = System.currentTimeMillis(); sidebar.resetSessionTimer(); });

        stage.setTitle("CodeConnect — Collaborative Code Discussion");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.show();

        startSessionTimer();
    }

    private BorderPane buildAppShell() {
        sidebar = new SidebarView(this::handleNav, this::handleLogout);
        rightPanel = new RightInsightView();

        centerHost = new StackPane();
        centerHost.setStyle("-fx-background-color:#080C10;");

        dashboardView = new DashboardView(
            s -> openDiscussion(s),
            s -> openViewDialog(s),
            s -> deleteSnippet(s),
            s -> toggleBookmark(s),
            this::showNotifPopup
        );
        dashboardView.setTabListener(idx -> {
            if (idx == 2) sidebar.setActive("BOOKMARKS");
            else sidebar.setActive("DASHBOARD");
        });
        centerHost.getChildren().add(dashboardView.getView());

        BorderPane shell = new BorderPane();
        shell.setStyle("-fx-background-color:#080C10;");
        shell.setLeft(sidebar.getView());
        shell.setCenter(centerHost);
        rightPanel.setOnJoinRoom(snippetId -> {
            try {
                var snippets = snippetDAO.findAllWithDetails(
                    com.codeconnect.model.Session.getCurrentUser() != null
                        ? com.codeconnect.model.Session.getCurrentUser().getId() : 0);
                snippets.stream().filter(s -> s.getId() == snippetId).findFirst().ifPresent(s -> openDiscussion(s));
            } catch (Exception ex) { ex.printStackTrace(); }
        });
        shell.setRight(rightPanel.getView());
        return shell;
    }

    private void onLoginSuccess(User user) {
        Session.setCurrentUser(user);
        sidebar.bindUser(user);
        sidebar.setActive("DASHBOARD");
        reloadSnippets();
        refreshInsights();
        startRefreshTimer();
        appShell.setVisible(true);
        root.getChildren().stream()
            .filter(n -> n instanceof javafx.scene.layout.VBox || n.getStyleClass().contains("auth-bg"))
            .forEach(n -> n.setVisible(false));
        root.getChildren().removeIf(n -> n != appShell);
        FxToast.show(stage, "Welcome back, " + user.getUsername() + "!", true);
        int myCount = (int) snippetDAO.findAllWithDetails(user.getId()).stream()
            .filter(s -> s.getUploaderId() == user.getId()).count();
        sidebar.updateSnippetCount(myCount);
    }

    private void handleLogout() {
        Session.setCurrentUser(null);
        stopRefreshTimer();
        sidebar.stopSessionCountdown();
        DiscussionWindow.closeAll();
        AuthView authView = new AuthView(this::onLoginSuccess);
        root.getChildren().clear();
        root.getChildren().add(authView.getView());
        appShell.setVisible(false);
        root.getChildren().add(appShell);
    }

    private void handleNav(String key) {
        sidebar.setActive(key);
        centerHost.getChildren().clear();
        switch (key) {
            case "DASHBOARD" -> {
                centerHost.getChildren().add(dashboardView.getView());
                dashboardView.setActiveTab(0);
                reloadSnippets();
            }
            case "UPLOAD" -> {
                UploadView uv = new UploadView(snippet -> {
                    reloadSnippets();
                    handleNav("DASHBOARD");
                    FxToast.show(stage, "Snippet uploaded!", true);
                });
                centerHost.getChildren().add(uv.getView());
            }
            case "PROFILE" -> {
                ProfileView pv = new ProfileView(stage, userDAO, snippetDAO, u -> sidebar.bindUser(u));
                centerHost.getChildren().add(pv.getView());
            }
            case "MY_ROOMS" -> {
                MyRoomsView mrv = new MyRoomsView(stage, roomDAO, snippetDAO, snippetId -> {
                    User cu = Session.getCurrentUser();
                    CodeSnippet sn = snippetDAO.findById(snippetId, cu != null ? cu.getId() : 0);
                    if (sn != null) openDiscussion(sn);
                });
                centerHost.getChildren().add(mrv.getView());
            }
            case "BOOKMARKS" -> {
                centerHost.getChildren().add(dashboardView.getView());
                dashboardView.setActiveTab(2);
                reloadSnippets();
                sidebar.setActive("BOOKMARKS");
            }
            case "NOTIFICATIONS" -> {
                NotificationsView nv = new NotificationsView(dashDAO, snippetId -> {
                    User cu = Session.getCurrentUser();
                    CodeSnippet sn = snippetDAO.findById(snippetId, cu != null ? cu.getId() : 0);
                    if (sn != null) openDiscussion(sn);
                });
                centerHost.getChildren().add(nv.getView());
            }
            case "ADMIN" -> {
                AdminView av = new AdminView(stage, userDAO, snippetDAO);
                centerHost.getChildren().add(av.getView());
            }
        }
    }

    private void openDiscussion(CodeSnippet s) {
        DiscussionRoom room = roomDAO.getOrCreateRoom(s.getId(), "Room for " + s.getTitle());
        if (room == null) return;
        User u = Session.getCurrentUser();
        if (u != null) {
            boolean isAdmin = "Admin".equals(u.getRole());
            if (!roomDAO.canAccess(room.getId(), u.getId(), isAdmin)) {
                FxToast.show(stage, "This is a private room. Access denied.", false);
                return;
            }
            // Track membership so user appears in MyRooms even before sending a message
            roomDAO.addMember(room.getId(), u.getId());
        }
        new DiscussionWindow(s, room, stage).show();
    }

    private void openViewDialog(CodeSnippet s) {
        new ViewCodeDialog(stage, s).show();
    }

    private void deleteSnippet(CodeSnippet s) {
        User u = Session.getCurrentUser();
        if (u == null) return;
        boolean canDelete = "Admin".equals(u.getRole()) || s.getUploaderId() == u.getId();
        if (!canDelete) { FxToast.show(stage, "No permission to delete this.", false); return; }
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.CONFIRMATION,
            "Permanently delete this snippet?");
        alert.setHeaderText("Confirm Delete");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                if (snippetDAO.deleteSnippet(s.getId())) {
                    reloadSnippets();
                    FxToast.show(stage, "Snippet deleted.", true);
                } else {
                    FxToast.show(stage, "Delete failed.", false);
                }
            }
        });
    }

    private void toggleBookmark(CodeSnippet s) {
        User u = Session.getCurrentUser();
        if (u == null) return;
        bookmarkDAO.setBookmarked(u.getId(), s.getId(), !s.isBookmarked());
        reloadSnippets();
    }

    private void showNotifPopup() {
        sidebar.setActive("NOTIFICATIONS");
        handleNav("NOTIFICATIONS");
    }

    private void reloadSnippets() {
        User u = Session.getCurrentUser();
        if (u == null) return;
        List<CodeSnippet> snippets = snippetDAO.findAllWithDetails(u.getId());
        DashboardDAO.StatCounts stats = dashDAO.loadStatsForUser(u.getId());
        int myCount = (int) snippets.stream().filter(s -> s.getUploaderId() == u.getId()).count();
        Platform.runLater(() -> {
            dashboardView.setSnippets(snippets);
            dashboardView.updateStats(stats);
            dashboardView.setBellUnread(dashDAO.hasUnreadBell(u.getId()));
            sidebar.updateSnippetCount(myCount);
        });
    }

    private void refreshInsights() {
        User u = Session.getCurrentUser();
        if (u == null) return;
        rightPanel.refresh(u.getId());
        int profile = dashDAO.countProfileAlerts(u.getId());
        int rooms   = dashDAO.countMyRoomsForUser(u.getId());
        int notifs  = notifDAO.countUnread(u.getId());
        sidebar.refreshBadges(profile, rooms, notifs);
    }

    private void startRefreshTimer() {
        stopRefreshTimer();
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            reloadSnippets();
            refreshInsights();
        }));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void stopRefreshTimer() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    private void startSessionTimer() {
        sessionTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            if (Session.getCurrentUser() != null &&
                System.currentTimeMillis() - lastActivity > 300_000L) {
                handleLogout();
                FxToast.show(stage, "Session expired due to inactivity.", false);
            }
        }));
        sessionTimer.setCycleCount(Timeline.INDEFINITE);
        sessionTimer.play();
    }
}
