package com.codeconnect.ui;

import com.codeconnect.dao.CodeSnippetDAO;
import com.codeconnect.dao.DiscussionRoomDAO;
import com.codeconnect.model.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MyRoomsView {

    private VBox root;

    private final java.util.function.IntConsumer onOpen;

    public MyRoomsView(Stage stage, DiscussionRoomDAO roomDAO, CodeSnippetDAO snippetDAO,
                       java.util.function.IntConsumer onOpen) {
        this.onOpen = onOpen;
        init(stage, roomDAO);
    }

    /** @deprecated kept for legacy callers; rooms cannot be re-entered through this constructor */
    @Deprecated
    public MyRoomsView(Stage stage, DiscussionRoomDAO roomDAO, CodeSnippetDAO snippetDAO) {
        this(stage, roomDAO, snippetDAO, sid -> {});
    }

    private void init(Stage stage, DiscussionRoomDAO roomDAO) {
        root = new VBox(16);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(24, 28, 24, 28));

        Label heading = new Label("My Discussion Rooms");
        heading.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        var user = Session.getCurrentUser();
        if (user == null) { root.getChildren().add(heading); return; }

        var rooms = roomDAO.listRoomsForUser(user.getId());

        Label countLbl = new Label(rooms.size() + " ROOM" + (rooms.size() == 1 ? "" : "S"));
        countLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#374150; -fx-font-weight:bold;");

        FlowPane grid = new FlowPane(16, 16);
        grid.setPadding(new Insets(6, 0, 0, 0));

        if (rooms.isEmpty()) {
            Label empty = new Label("You haven't joined any discussion rooms yet.");
            empty.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:13px; -fx-font-style:italic;");
            grid.getChildren().add(empty);
        } else {
            for (DiscussionRoomDAO.RoomSummary r : rooms) {
                grid.getChildren().add(buildRoomCard(stage, roomDAO, r));
            }
        }

        ScrollPane sp = new ScrollPane(new VBox(12, countLbl, grid));
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#080C10;");
        sp.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(sp, Priority.ALWAYS);

        root.getChildren().addAll(heading, sp);
    }

    private VBox buildRoomCard(Stage stage, DiscussionRoomDAO roomDAO,
                               DiscussionRoomDAO.RoomSummary r) {
        Label name = new Label(r.roomName);
        name.setStyle("-fx-font-weight:bold; -fx-text-fill:#D4DCE8; -fx-font-size:14px;");
        name.setWrapText(true);

        Label snippet = new Label(r.snippetTitle);
        snippet.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:11px;");
        snippet.setWrapText(true);

        Label msgs = new Label(r.messageCount + " messages");
        msgs.setStyle("-fx-background-color:rgba(0,200,150,0.15); -fx-text-fill:#00C896; -fx-background-radius:3; -fx-padding:2 8 2 8; -fx-font-size:10px; -fx-font-weight:bold;");

        Button renameBtn = new Button("Rename");
        renameBtn.getStyleClass().add("btn-ghost");
        renameBtn.setStyle(renameBtn.getStyle() + " -fx-font-size:10px; -fx-padding:4 10 4 10;");
        renameBtn.setOnAction(e -> {
            TextInputDialog dlg = new TextInputDialog(r.roomName);
            dlg.setTitle("Rename Room");
            dlg.setHeaderText("Enter new room name:");
            dlg.showAndWait().ifPresent(newName -> {
                if (!newName.trim().isEmpty()) {
                    roomDAO.updateRoomName(r.roomId, newName.trim());
                    name.setText(newName.trim());
                    FxToast.show(stage, "Room renamed!", true);
                }
            });
        });

        Button openBtn = new Button("Open \u2192");
        openBtn.getStyleClass().add("btn-primary");
        openBtn.setStyle(openBtn.getStyle() + " -fx-font-size:10px; -fx-padding:4 10 4 10;");
        openBtn.setOnAction(e -> onOpen.accept(r.snippetId));

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(6, msgs, new javafx.scene.layout.Region(), renameBtn, openBtn);
        HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, name, snippet, spacer, footer);
        card.setPrefSize(220, 130);
        card.setMinSize(220, 130);
        card.setMaxSize(220, 130);
        card.setPadding(new Insets(14, 14, 14, 14));
        card.setStyle("-fx-background-color:#141B22; -fx-background-radius:4; -fx-border-color:#1E2832 #1E2832 #1E2832 #00C896; -fx-border-radius:4; -fx-border-width:1 1 1 4;");
        return card;
    }

    public VBox getView() { return root; }
}
