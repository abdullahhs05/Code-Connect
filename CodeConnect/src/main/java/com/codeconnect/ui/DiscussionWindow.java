package com.codeconnect.ui;

import com.codeconnect.controller.DiscussionController;
import com.codeconnect.dao.MessageDAO;
import com.codeconnect.dao.NotificationDAO;
import com.codeconnect.dao.UserDAO;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.DiscussionRoom;
import com.codeconnect.model.Message;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import com.codeconnect.net.SocketServer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscussionWindow {

    private static final List<DiscussionWindow> openWindows = new ArrayList<>();
    private static final int PAGE_SIZE = 50;

    private final Stage stage;
    private final CodeSnippet snippet;
    private final DiscussionRoom room;
    private final MessageDAO messageDAO = new MessageDAO();
    private final NotificationDAO notifDAO = new NotificationDAO();
    private final UserDAO userDAO = new UserDAO();
    private final DiscussionController discussionController = new DiscussionController();
    private final SocketServer socketServer = SocketServer.getInstance();
    private final VBox chatBody = new VBox(8);
    private final ListView<String> participantList = new ListView<>();
    private final TextField messageField = new TextField();
    private Timeline refreshTimer;
    private int lastMessageCount = 0;
    private int oldestLoadedId = Integer.MAX_VALUE;
    private Button loadOlderBtn;
    private final java.util.regex.Pattern MENTION_PATTERN =
        java.util.regex.Pattern.compile("@([A-Za-z0-9_]{3,32})");
    private String busTopic;
    private java.util.function.Consumer<String> busHandler;

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public DiscussionWindow(CodeSnippet snippet, DiscussionRoom room, Stage owner) {
        this.snippet = snippet;
        this.room = room;
        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Discussion: " + snippet.getTitle());
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        openWindows.add(this);
        stage.setOnCloseRequest(e -> {
            e.consume();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Leave this discussion room?");
            alert.setHeaderText("Confirm Leave");
            alert.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) closeWindow();
            });
        });

        // Send join message
        var u = Session.getCurrentUser();
        if (u != null) {
            messageDAO.sendMessage(new Message(0, room.getId(), 0,
                u.getUsername() + " joined the room.", new Timestamp(System.currentTimeMillis())));
        }
    }

    public void show() {
        SplitPane split = new SplitPane(buildCodePanel(), buildChatPanel());
        split.setDividerPositions(0.48);
        split.setStyle("-fx-background-color:#080C10;");

        Scene scene = new Scene(split, 1000, 660);
        scene.getStylesheets().add(getClass().getResource("/com/codeconnect/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();

        loadInitialMessages();
        subscribeToEventBus();

        // Polling fallback (5s) — covers cases where bus is unreachable
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> loadMessages()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void subscribeToEventBus() {
        // UC-6 step 4: connect this client to the room topic via SocketServer (Pure Fabrication facade).
        busTopic = "ROOM:" + room.getId();
        busHandler = payload -> Platform.runLater(this::loadMessages);
        socketServer.subscribe(busTopic, busHandler);
    }

    private void unsubscribeFromEventBus() {
        if (busTopic != null && busHandler != null) {
            socketServer.unsubscribe(busTopic, busHandler);
        }
    }

    private void loadInitialMessages() {
        List<Message> msgs = messageDAO.getRecentMessages(room.getId(), PAGE_SIZE);
        lastMessageCount = msgs.size();
        if (!msgs.isEmpty()) oldestLoadedId = msgs.get(0).getId();
        int total = messageDAO.countForRoom(room.getId());
        Platform.runLater(() -> {
            renderMessages(msgs);
            if (loadOlderBtn != null) loadOlderBtn.setVisible(total > msgs.size());
        });
    }

    private void loadOlder() {
        if (oldestLoadedId == Integer.MAX_VALUE) return;
        List<Message> older = messageDAO.getOlderThan(room.getId(), oldestLoadedId, PAGE_SIZE);
        if (older.isEmpty()) {
            if (loadOlderBtn != null) loadOlderBtn.setVisible(false);
            return;
        }
        oldestLoadedId = older.get(0).getId();
        Platform.runLater(() -> {
            int insertAt = (loadOlderBtn != null && chatBody.getChildren().contains(loadOlderBtn)) ? 1 : 0;
            for (int i = older.size() - 1; i >= 0; i--) {
                chatBody.getChildren().add(insertAt, buildBubble(older.get(i)));
            }
            int total = messageDAO.countForRoom(room.getId());
            int loaded = chatBody.getChildren().size() - (loadOlderBtn != null ? 1 : 0);
            if (loaded >= total) loadOlderBtn.setVisible(false);
        });
    }

    private VBox buildCodePanel() {
        Label titleLbl = new Label(snippet.getTitle());
        titleLbl.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");
        Label langBadge = new Label(snippet.getLanguage());
        langBadge.setStyle("-fx-background-color:rgba(0,200,150,0.15); -fx-text-fill:#00C896; -fx-padding:3 8 3 8; -fx-background-radius:4; -fx-font-size:11px;");
        HBox header = new HBox(8, titleLbl, langBadge);
        header.setAlignment(Pos.CENTER_LEFT);

        CodeArea codeArea = SyntaxHighlighter.buildCodeArea(snippet.getCode(), false);
        VBox codeWrap = new VBox(codeArea);
        codeWrap.setStyle("-fx-border-color:#1E2832; -fx-border-width:1; -fx-border-radius:6;");
        VBox.setVgrow(codeWrap, Priority.ALWAYS);

        Button copyBtn = new Button("Copy");
        copyBtn.getStyleClass().add("btn-ghost");
        copyBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(snippet.getCode());
            Clipboard.getSystemClipboard().setContent(cc);
            FxToast.show(stage, "Copied!", true);
        });

        Button exportBtn = new Button("Export .txt");
        exportBtn.getStyleClass().add("btn-ghost");
        exportBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(snippet.getTitle().replaceAll("[^a-zA-Z0-9_]", "_") + ".txt");
            File f = fc.showSaveDialog(stage);
            if (f != null) {
                try { Files.writeString(f.toPath(), snippet.getCode()); FxToast.show(stage, "Exported!", true); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        HBox actions = new HBox(8, copyBtn, exportBtn);
        actions.setPadding(new Insets(8, 0, 0, 0));

        VBox panel = new VBox(10, header, codeWrap, actions);
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color:#080C10;");
        VBox.setVgrow(codeWrap, Priority.ALWAYS);
        return panel;
    }

    private VBox buildChatPanel() {
        Label roomTitle = new Label(room.getRoomName());
        roomTitle.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        Button renameBtn = new Button("Rename");
        renameBtn.getStyleClass().add("btn-ghost");
        renameBtn.setOnAction(e -> {
            TextInputDialog dlg = new TextInputDialog(room.getRoomName());
            dlg.setTitle("Rename Room");
            dlg.setHeaderText("New room name:");
            dlg.getDialogPane().setStyle("-fx-background-color:#0E1318;");
            dlg.showAndWait().ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    new com.codeconnect.dao.DiscussionRoomDAO().updateRoomName(room.getId(), name.trim());
                    room.setRoomName(name.trim());
                    roomTitle.setText(name.trim());
                    FxToast.show(stage, "Room renamed!", true);
                }
            });
        });

        Button exportChatBtn = new Button("Export Chat");
        exportChatBtn.getStyleClass().add("btn-ghost");
        exportChatBtn.setOnAction(e -> exportChat());

        HBox chatHeader = new HBox(8, roomTitle, new Region(), renameBtn, exportChatBtn);
        HBox.setHgrow(chatHeader.getChildren().get(1), Priority.ALWAYS);
        chatHeader.setAlignment(Pos.CENTER_LEFT);
        chatHeader.setPadding(new Insets(0, 0, 8, 0));

        loadOlderBtn = new Button("\u2191 Load older messages");
        loadOlderBtn.getStyleClass().add("btn-ghost");
        loadOlderBtn.setMaxWidth(Double.MAX_VALUE);
        loadOlderBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
        loadOlderBtn.setVisible(false);
        loadOlderBtn.setOnAction(e -> loadOlder());

        chatBody.setPadding(new Insets(8));
        chatBody.setStyle("-fx-background-color:#080C10;");
        ScrollPane chatScroll = new ScrollPane(chatBody);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background-color:#080C10;");
        chatScroll.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        chatBody.heightProperty().addListener((o, ov, nv) ->
            chatScroll.setVvalue(chatScroll.getVmax()));

        participantList.getStyleClass().add("list-view-dark");
        participantList.setPrefWidth(140);
        participantList.setStyle("-fx-font-size:11px;");

        SplitPane innerSplit = new SplitPane(chatScroll, participantList);
        innerSplit.setDividerPositions(0.82);
        innerSplit.setStyle("-fx-background-color:#080C10;");
        VBox.setVgrow(innerSplit, Priority.ALWAYS);

        messageField.setPromptText("type a message... (@username to mention)");
        messageField.getStyleClass().add("text-field-dark");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Label prefix = new Label(">");
        prefix.getStyleClass().add("terminal-prefix");

        Button sendBtn = new Button("\u2192");
        sendBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#00C896; -fx-font-size:22px; -fx-font-weight:bold; -fx-cursor:hand; -fx-padding:2 12 2 12;");
        sendBtn.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        HBox inputRow = new HBox(10, prefix, messageField, sendBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(10, 4, 6, 4));
        inputRow.setStyle("-fx-border-color:#1E2832 transparent transparent transparent; -fx-border-width:1 0 0 0;");

        VBox panel = new VBox(0, chatHeader, innerSplit, inputRow);
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color:#080C10;");
        VBox.setVgrow(innerSplit, Priority.ALWAYS);
        return panel;
    }

    private void sendMessage() {
        String text = sanitize(messageField.getText().trim());
        if (text.isEmpty()) return;
        var u = Session.getCurrentUser();
        if (u == null) return;
        // UC-7: route through DiscussionController (Pure Fabrication).
        DiscussionController.OpResult res = discussionController.handleNewMessage(room, text);
        if (!res.success) {
            // Lightweight error feedback inside the chat itself
            Label err = new Label(res.message);
            err.setStyle("-fx-text-fill:#EF4444; -fx-font-size:11px;");
            chatBody.getChildren().add(err);
            return;
        }

        // Parse @mentions and create notifications for matched users
        java.util.regex.Matcher m = MENTION_PATTERN.matcher(text);
        java.util.Set<String> mentioned = new java.util.HashSet<>();
        while (m.find()) mentioned.add(m.group(1));
        for (String name : mentioned) {
            if (name.equalsIgnoreCase(u.getUsername())) continue;
            Integer targetId = userDAO.findIdByUsername(name);
            if (targetId != null) {
                String preview = text.length() > 80 ? text.substring(0, 77) + "..." : text;
                notifDAO.addNotification(targetId, "MENTION",
                    u.getUsername() + " mentioned you in '" + room.getRoomName() + "': " + preview,
                    room.getId(), snippet.getId());
            }
        }

        messageField.clear();
        loadMessages();
    }

    private void loadMessages() {
        // Load just the most recent page; older messages stay in DOM via Load Older
        List<Message> msgs = messageDAO.getRecentMessages(room.getId(), PAGE_SIZE);
        if (msgs.size() == lastMessageCount && !msgs.isEmpty()
                && oldestLoadedId <= msgs.get(0).getId()) {
            // No new messages
            return;
        }
        lastMessageCount = msgs.size();
        if (!msgs.isEmpty()) oldestLoadedId = Math.min(oldestLoadedId, msgs.get(0).getId());
        Platform.runLater(() -> renderMessages(msgs));
    }

    private void renderMessages(List<Message> msgs) {
        chatBody.getChildren().clear();
        int total = messageDAO.countForRoom(room.getId());
        if (loadOlderBtn != null && total > msgs.size()) {
            chatBody.getChildren().add(loadOlderBtn);
            loadOlderBtn.setVisible(true);
        }
        if (msgs.isEmpty()) {
            renderEmptyState();
            return;
        }
        Set<String> participants = new HashSet<>();
        for (Message m : msgs) {
            chatBody.getChildren().add(buildBubble(m));
            if (m.getSenderId() != 0) participants.add("User " + m.getSenderId());
        }
        participantList.getItems().setAll(participants);
    }

    private void renderEmptyState() {
        VBox empty = new VBox(6);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(40, 8, 40, 8));
        Label icon = new Label("\uD83D\uDCAC");
        icon.setStyle("-fx-font-size:32px;");
        Label title = new Label("Start the discussion!");
        title.setStyle("-fx-text-fill:#D4DCE8; -fx-font-size:14px; -fx-font-weight:bold;");
        Label sub = new Label("No messages yet. Be the first to leave a comment.");
        sub.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:12px;");
        empty.getChildren().addAll(icon, title, sub);
        chatBody.getChildren().add(empty);
        participantList.getItems().clear();
    }

    private HBox buildBubble(Message m) {
        var me = Session.getCurrentUser();
        boolean isSystem = m.getSenderId() == 0;
        boolean isMe = me != null && m.getSenderId() == me.getId();

        if (isSystem) {
            return buildSystemBubbleRow(m);
        }
        return buildUserBubbleRow(m, isMe, me);
    }

    private HBox buildSystemBubbleRow(Message m) {
        Label sysLbl = new Label(m.getContent());
        sysLbl.setStyle("-fx-text-fill:#374150; -fx-font-style:italic; -fx-font-size:11px;");
        HBox row = new HBox(sysLbl);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private HBox buildUserBubbleRow(Message m, boolean isMe, User me) {
        String content = m.getContent();
        if (me != null && content.contains("@" + me.getUsername())) {
            content = content.replace("@" + me.getUsername(),
                "[@" + me.getUsername() + "]");
        }

        Label text = new Label(content);
        text.setStyle("-fx-text-fill:#D4DCE8; -fx-font-size:13px;");
        text.setWrapText(true);
        text.setMaxWidth(340);

        String time = m.getTimestamp() != null ?
            m.getTimestamp().toString().substring(11, 16) : "";
        Label timeLbl = new Label(time);
        timeLbl.setStyle("-fx-text-fill:" + (isMe ? "#8899cc" : "#374150") + "; -fx-font-size:10px;");

        VBox bubble = new VBox(4, text, timeLbl);
        bubble.setPadding(new Insets(10, 14, 10, 14));
        bubble.setStyle(isMe
            ? "-fx-background-color:#1a4a8a; -fx-background-radius:16 16 4 16;"
            : "-fx-background-color:#2d333b; -fx-background-radius:16 16 16 4;");

        ContextMenu ctx = buildContextMenu(m);
        bubble.setOnContextMenuRequested(e -> ctx.show(bubble, e.getScreenX(), e.getScreenY()));

        if (!isMe) {
            StackPane avPane = buildAvatarPane(m.getSenderId());
            HBox row = new HBox(8, avPane, bubble);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2, 8, 2, 8));
            return row;
        } else {
            HBox row = new HBox(bubble);
            row.setAlignment(Pos.CENTER_RIGHT);
            row.setPadding(new Insets(2, 8, 2, 8));
            return row;
        }
    }

    private StackPane buildAvatarPane(int senderId) {
        String initials = "?";
        if (senderId > 0) initials = String.valueOf(senderId).substring(0, 1);
        double hue = (senderId * 47.0) % 360;
        Color av = Color.hsb(hue, 0.45, 0.75);
        Circle circ = new Circle(14, av);
        Label ini = new Label(initials);
        ini.setStyle("-fx-text-fill:white; -fx-font-size:10px; -fx-font-weight:bold;");
        StackPane avPane = new StackPane(circ, ini);
        avPane.setPrefSize(28, 28);
        return avPane;
    }

    private ContextMenu buildContextMenu(Message m) {
        var me = Session.getCurrentUser();
        ContextMenu ctx = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit Message");
        editItem.setOnAction(e -> {
            if (me == null || m.getSenderId() != me.getId()) {
                FxToast.show(stage, "Can only edit your own messages.", false); return;
            }
            TextInputDialog dlg = new TextInputDialog(m.getContent());
            dlg.setTitle("Edit Message");
            dlg.setHeaderText("Edit your message:");
            dlg.getDialogPane().setStyle("-fx-background-color:#0E1318;");
            dlg.showAndWait().ifPresent(newText -> {
                if (!newText.trim().isEmpty()) {
                    messageDAO.updateMessage(m.getId(), newText.trim());
                    loadMessages();
                    FxToast.show(stage, "Message updated.", true);
                }
            });
        });

        MenuItem deleteItem = new MenuItem("Delete Message");
        deleteItem.setOnAction(e -> {
            if (me == null || m.getSenderId() != me.getId()) {
                FxToast.show(stage, "Can only delete your own messages.", false); return;
            }
            messageDAO.deleteMessage(m.getId());
            loadMessages();
            FxToast.show(stage, "Message deleted.", true);
        });

        MenuItem copyItem = new MenuItem("Copy Text");
        copyItem.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(m.getContent());
            Clipboard.getSystemClipboard().setContent(cc);
        });

        ctx.getItems().addAll(editItem, deleteItem, new SeparatorMenuItem(), copyItem);
        return ctx;
    }

    private void exportChat() {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("chat_" + room.getRoomName().replaceAll("[^a-zA-Z0-9_]", "_") + ".txt");
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            List<Message> msgs = messageDAO.getMessagesForRoom(room.getId());
            StringBuilder sb = new StringBuilder("--- Chat Log: " + room.getRoomName() + " ---\n\n");
            for (Message m : msgs) {
                String sender = m.getSenderId() == 0 ? "SYSTEM" : "User " + m.getSenderId();
                var me = Session.getCurrentUser();
                if (me != null && m.getSenderId() == me.getId()) sender = me.getUsername() + " (You)";
                sb.append("[").append(m.getTimestamp().toString(), 0, 16).append("] ");
                sb.append(sender).append(": ").append(m.getContent()).append("\n");
            }
            Files.writeString(f.toPath(), sb.toString());
            FxToast.show(stage, "Chat log exported!", true);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void closeWindow() {
        if (refreshTimer != null) refreshTimer.stop();
        unsubscribeFromEventBus();
        openWindows.remove(this);
        stage.close();
    }

    public static void closeAll() {
        new ArrayList<>(openWindows).forEach(w -> {
            if (w.refreshTimer != null) w.refreshTimer.stop();
            w.stage.close();
        });
        openWindows.clear();
    }
}
