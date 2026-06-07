package com.codeconnect.ui;

import com.codeconnect.dao.DashboardDAO;
import com.codeconnect.dao.NotificationDAO;
import com.codeconnect.model.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NotificationsView {

    private VBox root;
    private final NotificationDAO notifDAO = new NotificationDAO();
    private final java.util.function.IntConsumer onOpenSnippet;

    public NotificationsView(DashboardDAO dashDAO, java.util.function.IntConsumer onOpenSnippet) {
        this.onOpenSnippet = onOpenSnippet != null ? onOpenSnippet : id -> {};
        init(dashDAO);
    }

    /** Legacy ctor — no click-through. */
    public NotificationsView(DashboardDAO dashDAO) {
        this(dashDAO, null);
    }

    private void init(DashboardDAO dashDAO) {
        root = new VBox(14);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(24, 28, 24, 28));

        var u = Session.getCurrentUser();
        int unread = u != null ? notifDAO.countUnread(u.getId()) : 0;

        Label heading = new Label("Notifications");
        heading.setStyle("-fx-font-size:22px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        Label unreadLbl = new Label(unread + " unread");
        unreadLbl.setStyle("-fx-font-size:11px; -fx-text-fill:" + (unread > 0 ? "#00C896" : "#374150")
                + "; -fx-font-weight:bold; -fx-padding:3 9 3 9; -fx-background-color:"
                + (unread > 0 ? "rgba(0,200,150,0.15)" : "#1a1f24") + "; -fx-background-radius:10;");

        Button markAllBtn = new Button("Mark all read");
        markAllBtn.getStyleClass().add("btn-ghost");
        markAllBtn.setStyle(markAllBtn.getStyle() + " -fx-font-size:11px;");

        HBox headerRow = new HBox(10, heading, unreadLbl, new Region(), markAllBtn);
        HBox.setHgrow(headerRow.getChildren().get(2), Priority.ALWAYS);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8);
        card.setStyle("-fx-background-color:#0E1318; -fx-background-radius:8; -fx-border-color:#1E2832; -fx-border-radius:8; -fx-border-width:1;");
        card.setPadding(new Insets(18));

        Runnable rebuild = () -> {
            card.getChildren().clear();
            if (u == null) return;
            var notifs = notifDAO.listForUser(u.getId(), 100);
            if (notifs.isEmpty()) {
                Label empty = new Label("You're all caught up. No notifications yet.");
                empty.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:13px; -fx-font-style:italic;");
                card.getChildren().add(empty);
                return;
            }
            // Group by date bucket
            Map<String, List<NotificationDAO.Notification>> grouped = new TreeMap<>((a, b) -> b.compareTo(a));
            for (var n : notifs) {
                String bucket = bucketFor(n.createdAt);
                grouped.computeIfAbsent(bucket, k -> new ArrayList<>()).add(n);
            }
            for (var entry : grouped.entrySet()) {
                Label section = new Label(entry.getKey().toUpperCase());
                section.setStyle("-fx-font-size:10px; -fx-text-fill:#374150; -fx-font-weight:bold; -fx-padding:8 0 4 0;");
                card.getChildren().add(section);
                for (var n : entry.getValue()) {
                    Runnable navigate = () -> {
                        if (n.snippetId != null) onOpenSnippet.accept(n.snippetId);
                    };
                    card.getChildren().add(buildRow(n, navigate));
                }
            }
        };
        rebuild.run();

        markAllBtn.setOnAction(e -> {
            if (u == null) return;
            notifDAO.markAllRead(u.getId());
            unreadLbl.setText("0 unread");
            unreadLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#374150; -fx-font-weight:bold; -fx-padding:3 9 3 9; -fx-background-color:#1a1f24; -fx-background-radius:10;");
            rebuild.run();
            FxToast.show(null, "All notifications marked read.", true);
        });

        ScrollPane sp = new ScrollPane(new VBox(0, card));
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#080C10;");
        sp.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(sp, Priority.ALWAYS);

        root.getChildren().addAll(headerRow, new Separator(), sp);
    }

    private HBox buildRow(NotificationDAO.Notification n, Runnable onClick) {
        Circle dot = new Circle(4, n.read ? Color.web("#1E2832") : Color.web("#00C896"));

        String typeLbl;
        String typeColor;
        switch (n.type == null ? "" : n.type) {
            case "MENTION": typeLbl = "@"; typeColor = "#F5A524"; break;
            case "REPLY":   typeLbl = "↩"; typeColor = "#3fb950"; break;
            default:        typeLbl = "•"; typeColor = "#00C896";
        }
        Label typeIcon = new Label(typeLbl);
        typeIcon.setStyle("-fx-text-fill:" + typeColor + "; -fx-font-size:14px; -fx-font-weight:bold; -fx-min-width:22px;");

        Label msg = new Label(n.message != null ? n.message : "");
        msg.setStyle("-fx-text-fill:" + (n.read ? "#6A7A8E" : "#D4DCE8") + "; -fx-font-size:12px;");
        msg.setWrapText(true);
        HBox.setHgrow(msg, Priority.ALWAYS);

        Label time = new Label(DashboardDAO.relativeTime(n.createdAt));
        time.setStyle("-fx-text-fill:#374150; -fx-font-size:10px;");

        HBox row = new HBox(10, dot, typeIcon, msg, time);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 6, 8, 6));
        row.setStyle("-fx-cursor:hand;");
        row.setOnMouseClicked(e -> {
            if (!n.read) {
                notifDAO.markRead(n.id);
                n.read = true;
                dot.setFill(Color.web("#1E2832"));
                msg.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:12px;");
            }
            onClick.run();
        });
        return row;
    }

    private String bucketFor(Timestamp ts) {
        if (ts == null) return "Earlier";
        LocalDate d = ts.toLocalDateTime().toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "Today";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        if (d.isAfter(today.minusDays(7))) return "This Week";
        if (d.isAfter(today.minusDays(30))) return "This Month";
        return "Earlier";
    }

    public VBox getView() { return root; }
}
