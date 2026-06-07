package com.codeconnect.ui;

import com.codeconnect.dao.CodeSnippetDAO;
import com.codeconnect.dao.DashboardDAO;
import com.codeconnect.model.CodeSnippet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.function.Consumer;

public class RightInsightView {

    private final VBox roomsBody    = new VBox(8);
    private final VBox onlineBody   = new VBox(6);
    private final VBox activityBody = new VBox(6);
    private final VBox snippetsBody = new VBox(8);

    private final DashboardDAO dao = new DashboardDAO();
    private final CodeSnippetDAO snippetDAO = new CodeSnippetDAO();

    private Consumer<Integer> onJoinRoom;
    private final VBox scrollContent;

    public RightInsightView() {
        scrollContent = new VBox(0);
        scrollContent.setStyle("-fx-background-color:#0E1318;");
        scrollContent.setPadding(new Insets(12));

        scrollContent.getChildren().addAll(
            section("Active Rooms",       roomsBody),
            separator(),
            section("Online Now",         onlineBody),
            separator(),
            section("Recent Activity",    activityBody),
            separator(),
            section("Hot Snippets",       snippetsBody)
        );

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        scrollContent.getChildren().add(spacer);
    }

    public void setOnJoinRoom(Consumer<Integer> onJoinRoom) {
        this.onJoinRoom = onJoinRoom;
    }

    private VBox section(String title, VBox body) {
        Label h = new Label(title.toUpperCase());
        h.setStyle("-fx-font-weight:bold; -fx-font-size:10px; -fx-text-fill:#374150;");
        VBox wrap = new VBox(8, h, body);
        wrap.setPadding(new Insets(10, 0, 10, 0));
        return wrap;
    }

    private Separator separator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#21262D;");
        return sep;
    }

    public void refresh(int userId) {
        refreshRooms();
        refreshOnline(userId);
        refreshActivity();
        refreshTopSnippets(userId);
    }

    private void refreshRooms() {
        roomsBody.getChildren().clear();
        List<DashboardDAO.ActiveRoomRow> rooms = dao.loadActiveRoomsPreview();
        if (rooms.isEmpty()) {
            roomsBody.getChildren().add(emptyState("No active rooms right now"));
        } else {
            for (DashboardDAO.ActiveRoomRow r : rooms) {
                roomsBody.getChildren().add(buildRoomCard(r));
            }
        }
    }

    private void refreshOnline(int userId) {
        onlineBody.getChildren().clear();
        List<DashboardDAO.OnlineUserRow> users = dao.loadOnlineUsers(userId);
        if (users.isEmpty()) {
            onlineBody.getChildren().add(emptyState("No one else online right now"));
        } else {
            for (DashboardDAO.OnlineUserRow u : users) {
                onlineBody.getChildren().add(buildOnlineRow(u));
            }
        }
    }

    private void refreshActivity() {
        activityBody.getChildren().clear();
        List<DashboardDAO.ActivityRow> rows = dao.loadRecentActivity();
        if (rows.isEmpty()) {
            activityBody.getChildren().add(emptyState("No recent activity"));
        } else {
            for (DashboardDAO.ActivityRow a : rows) {
                String text = a.htmlLine.replaceAll("<[^>]+>", "");
                String time = DashboardDAO.relativeTime(a.timestamp);

                Label textLbl = new Label(text);
                textLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#6A7A8E;");
                textLbl.setWrapText(true);

                Label timeLbl = new Label(time);
                timeLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#374150;");

                VBox row = new VBox(2, textLbl, timeLbl);
                activityBody.getChildren().add(row);
            }
        }
    }

    private void refreshTopSnippets(int userId) {
        snippetsBody.getChildren().clear();
        List<CodeSnippet> all = snippetDAO.findAllWithDetails(userId);
        all.sort((a, b) -> Integer.compare(b.getCommentCount(), a.getCommentCount()));
        List<CodeSnippet> top = all.size() > 4 ? all.subList(0, 4) : all;

        if (top.isEmpty()) {
            snippetsBody.getChildren().add(emptyState("No snippets shared yet"));
        } else {
            for (CodeSnippet s : top) {
                snippetsBody.getChildren().add(buildSnippetMiniCard(s));
            }
        }
    }

    private VBox buildRoomCard(DashboardDAO.ActiveRoomRow r) {
        Label name = new Label(r.roomName);
        name.setStyle("-fx-font-weight:bold; -fx-font-size:11px; -fx-text-fill:#D4DCE8;");
        name.setWrapText(true);

        Label status = r.idle
            ? styledLabel("idle", "#374150", 10)
            : styledLabel("● " + r.onlineCount + " online", "#3fb950", 10);

        Label lang = styledLabel(r.language != null && !r.language.isEmpty() ? r.language : "", "#6A7A8E", 10);

        Button joinBtn = new Button("→");
        joinBtn.setStyle("-fx-background-color:rgba(0,200,150,0.15); -fx-text-fill:#00C896; -fx-font-size:11px; -fx-cursor:hand; -fx-padding:2 7 2 7; -fx-background-radius:4;");
        Tooltip.install(joinBtn, new Tooltip("Join " + r.roomName));
        joinBtn.setOnAction(e -> { if (onJoinRoom != null) onJoinRoom.accept(r.snippetId); });

        HBox footer = new HBox(4, status, lang);
        HBox.setHgrow(status, Priority.ALWAYS);

        HBox topRow = new HBox(4, name, joinBtn);
        HBox.setHgrow(name, Priority.ALWAYS);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(4, topRow, footer);
        card.getStyleClass().add("room-mini-card");
        card.setPadding(new Insets(8));
        return card;
    }

    private HBox buildOnlineRow(DashboardDAO.OnlineUserRow u) {
        Color fxColor = u.avatarColor;

        Circle av = new Circle(12);
        av.setFill(fxColor);
        Label ini = new Label(u.initials);
        ini.setStyle("-fx-text-fill:white; -fx-font-size:9px; -fx-font-weight:bold;");
        StackPane avStack = new StackPane(av, ini);
        avStack.setPrefSize(24, 24);

        Label nm = new Label(u.username);
        nm.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:11px;");

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill:" + (u.online ? "#3fb950" : "#374150") + "; -fx-font-size:9px;");

        HBox row = new HBox(7, avStack, nm, dot);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nm, Priority.ALWAYS);
        return row;
    }

    private VBox buildSnippetMiniCard(CodeSnippet s) {
        Label title = new Label(s.getTitle());
        title.setStyle("-fx-text-fill:#00C896; -fx-font-size:11px; -fx-font-weight:bold;");
        title.setWrapText(true);

        Label lang = styledLabel(s.getLanguage(), "#6A7A8E", 10);
        Label comments = styledLabel(s.getCommentCount() + " comments", "#374150", 10);

        HBox meta = new HBox(6, lang, comments);
        meta.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(3, title, meta);
        card.setStyle("-fx-background-color:#080C10; -fx-background-radius:6; -fx-padding:7 8 7 8;");
        return card;
    }

    private Label emptyState(String msg) {
        Label l = new Label(msg);
        l.setStyle("-fx-text-fill:#374150; -fx-font-size:11px; -fx-font-style:italic;");
        l.setWrapText(true);
        return l;
    }

    private Label styledLabel(String text, String color, int size) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + "; -fx-font-size:" + size + "px;");
        return l;
    }

    public ScrollPane getView() {
        ScrollPane sp = new ScrollPane(scrollContent);
        sp.setFitToWidth(true);
        sp.setPrefWidth(210);
        sp.setStyle("-fx-background-color:#0E1318; -fx-border-color:#21262D; -fx-border-width:0 0 0 1;");
        sp.getStyleClass().add("scroll-pane-dark");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }
}
