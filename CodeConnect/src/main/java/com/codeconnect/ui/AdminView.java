package com.codeconnect.ui;

import com.codeconnect.dao.CodeSnippetDAO;
import com.codeconnect.dao.UserDAO;
import com.codeconnect.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;

public class AdminView {

    private final VBox root;

    public AdminView(Stage stage, UserDAO userDAO, CodeSnippetDAO snippetDAO) {
        root = new VBox(16);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(24, 28, 24, 28));

        Label heading = new Label("Admin Panel");
        heading.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        List<User> allUsers = userDAO.getAllUsers();
        long admins = allUsers.stream().filter(u -> "Admin".equals(u.getRole())).count();
        long devs   = allUsers.stream().filter(u -> "Developer".equals(u.getRole())).count();
        int totalSnip = snippetDAO.findAllWithDetails(0).size();

        HBox statsRow = new HBox(12,
            statCard("Total Users",    String.valueOf(allUsers.size()), "#00C896"),
            statCard("Admins",         String.valueOf(admins),           "#F5A524"),
            statCard("Developers",     String.valueOf(devs),             "#3fb950"),
            statCard("Total Snippets", String.valueOf(totalSnip),        "#EF4444")
        );

        Canvas pie = buildPie(admins, devs);

        VBox usersCard = buildUsersCard(stage, userDAO, allUsers);
        VBox snippetsCard = buildSnippetsCard(stage, snippetDAO);

        ScrollPane sp = new ScrollPane(new VBox(16, statsRow, pie, usersCard, snippetsCard));
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#080C10;");
        sp.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(sp, Priority.ALWAYS);

        root.getChildren().addAll(heading, sp);
    }

    private HBox statCard(String label, String val, String accent) {
        Label v = new Label(val);
        v.setStyle("-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");
        Label l = new Label(label);
        l.setStyle("-fx-font-size:11px; -fx-text-fill:#6A7A8E;");
        VBox inner = new VBox(2, v, l);
        inner.setPadding(new Insets(14, 14, 14, 18));
        HBox card = new HBox(inner);
        card.getStyleClass().add("stat-card");
        card.setStyle("-fx-border-color: transparent transparent transparent " + accent + "; -fx-border-width: 0 0 0 4;");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Canvas buildPie(long admins, long devs) {
        Canvas c = new Canvas(200, 200);
        GraphicsContext gc = c.getGraphicsContext2D();
        long total = admins + devs;
        if (total == 0) return c;
        double adminArc = 360.0 * admins / total;
        gc.setFill(Color.web("#F5A524"));
        gc.fillArc(20, 20, 160, 160, 90, -adminArc, javafx.scene.shape.ArcType.ROUND);
        gc.setFill(Color.web("#3fb950"));
        gc.fillArc(20, 20, 160, 160, 90 - adminArc, -(360 - adminArc), javafx.scene.shape.ArcType.ROUND);
        gc.setFill(Color.web("#D4DCE8"));
        gc.setFont(javafx.scene.text.Font.font("SansSerif", 11));
        gc.fillText("Admins: " + admins, 30, 185);
        gc.fillText("Devs: " + devs, 120, 185);
        return c;
    }

    private VBox buildUsersCard(Stage stage, UserDAO userDAO, List<User> allUsers) {
        Label title = new Label("All Users");
        title.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        ListView<User> list = new ListView<>();
        list.getStyleClass().add("list-view-dark");
        list.setPrefHeight(280);
        list.getItems().addAll(allUsers);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setText(null); setGraphic(null); return; }
                String tag = u.isDisabled() ? "  [DISABLED]" : "";
                String email = (u.getEmail() != null && !u.getEmail().isEmpty()) ? "  <" + u.getEmail() + ">" : "";
                setText(u.getUsername() + " — " + u.getRole() + email + tag);
                setStyle(u.isDisabled() ? "-fx-text-fill:#EF4444;" : "");
            }
        });

        Button toggleRoleBtn = new Button("Promote / Demote");
        toggleRoleBtn.getStyleClass().add("btn-ghost");
        toggleRoleBtn.setOnAction(e -> {
            User sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { FxToast.show(stage, "Select a user first.", false); return; }
            User me = com.codeconnect.model.Session.getCurrentUser();
            if (me != null && sel.getId() == me.getId()) {
                FxToast.show(stage, "Cannot change your own role.", false); return;
            }
            String newRole = "Admin".equals(sel.getRole()) ? "Developer" : "Admin";
            if (userDAO.setRole(sel.getId(), newRole)) {
                sel.setRole(newRole);
                list.refresh();
                FxToast.show(stage, "Role changed to " + newRole + ".", true);
            }
        });

        Button toggleDisableBtn = new Button("Disable / Enable Selected");
        toggleDisableBtn.getStyleClass().add("btn-ghost");
        toggleDisableBtn.setOnAction(e -> {
            User sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { FxToast.show(stage, "Select a user first.", false); return; }
            User me = com.codeconnect.model.Session.getCurrentUser();
            if (me != null && sel.getId() == me.getId()) {
                FxToast.show(stage, "Cannot disable your own account.", false); return;
            }
            boolean newState = !sel.isDisabled();
            if (userDAO.setDisabled(sel.getId(), newState)) {
                sel.setDisabled(newState);
                list.refresh();
                FxToast.show(stage, "User " + (newState ? "disabled" : "enabled") + ".", true);
            }
        });

        Button deleteBtn = new Button("Delete Selected User");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            User sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { FxToast.show(stage, "Select a user first.", false); return; }
            User me = com.codeconnect.model.Session.getCurrentUser();
            if (me != null && sel.getId() == me.getId()) {
                FxToast.show(stage, "Cannot delete your own account!", false); return;
            }
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete user '" + sel.getUsername() + "'?");
            a.setHeaderText("Confirm Delete");
            a.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    try (var conn = com.codeconnect.db.DatabaseHelper.getConnection();
                         var ps = conn.prepareStatement("DELETE FROM users WHERE id=?")) {
                        ps.setInt(1, sel.getId());
                        ps.executeUpdate();
                        list.getItems().remove(sel);
                        FxToast.show(stage, "User deleted.", true);
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
            });
        });

        HBox actionRow = new HBox(8, toggleRoleBtn, toggleDisableBtn, deleteBtn);
        VBox card = new VBox(10, title, list, actionRow);
        card.setStyle("-fx-background-color:#0E1318; -fx-background-radius:8; -fx-border-color:#1E2832; -fx-border-radius:8; -fx-border-width:1;");
        card.setPadding(new Insets(18));
        return card;
    }

    private VBox buildSnippetsCard(Stage stage, CodeSnippetDAO snippetDAO) {
        Label title = new Label("Snippet Moderation");
        title.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");
        Label sub = new Label("Hide or remove problematic snippets. Hidden snippets are invisible to all users.");
        sub.setStyle("-fx-font-size:11px; -fx-text-fill:#6A7A8E;");

        ListView<com.codeconnect.model.CodeSnippet> list = new ListView<>();
        list.getStyleClass().add("list-view-dark");
        list.setPrefHeight(220);
        list.getItems().addAll(snippetDAO.findAllWithDetails(0, true));

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.codeconnect.model.CodeSnippet s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); return; }
                String tag = s.isHidden() ? "  [HIDDEN]" : "";
                setText("#" + s.getId() + "  " + s.getTitle() + " [" + s.getLanguage() + "] by "
                        + s.getUploaderUsername() + tag);
                setStyle(s.isHidden() ? "-fx-text-fill:#F5A524;" : "");
            }
        });

        Button hideBtn = new Button("Hide / Unhide Selected");
        hideBtn.getStyleClass().add("btn-ghost");
        hideBtn.setOnAction(e -> {
            var sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { FxToast.show(stage, "Select a snippet first.", false); return; }
            boolean newState = !sel.isHidden();
            if (snippetDAO.setHidden(sel.getId(), newState)) {
                sel.setHidden(newState);
                list.refresh();
                FxToast.show(stage, "Snippet " + (newState ? "hidden" : "unhidden") + ".", true);
            }
        });

        Button delSnipBtn = new Button("Delete Selected Snippet");
        delSnipBtn.getStyleClass().add("btn-danger");
        delSnipBtn.setOnAction(e -> {
            var sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { FxToast.show(stage, "Select a snippet first.", false); return; }
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Permanently delete '" + sel.getTitle() + "'?");
            a.setHeaderText("Confirm Delete");
            a.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK && snippetDAO.deleteSnippet(sel.getId())) {
                    list.getItems().remove(sel);
                    FxToast.show(stage, "Snippet deleted.", true);
                }
            });
        });

        HBox actions = new HBox(8, hideBtn, delSnipBtn);
        VBox card = new VBox(10, title, sub, list, actions);
        card.setStyle("-fx-background-color:#0E1318; -fx-background-radius:8; -fx-border-color:#1E2832; -fx-border-radius:8; -fx-border-width:1;");
        card.setPadding(new Insets(18));
        return card;
    }

    public VBox getView() { return root; }
}
