package com.codeconnect.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class FxToast {

    public static void show(Stage owner, String message, boolean success) {
        // Find a fallback owner if none was passed
        if (owner == null) {
            for (javafx.stage.Window w : javafx.stage.Stage.getWindows()) {
                if (w instanceof Stage && w.isShowing()) { owner = (Stage) w; break; }
            }
            if (owner == null) return;
        }

        Label lbl = new Label(message);
        StackPane pane = new StackPane(lbl);
        pane.getStyleClass().addAll("toast", success ? "toast-success" : "toast-error");
        pane.setAlignment(Pos.CENTER);
        pane.setMinWidth(200);
        try {
            pane.getStylesheets().add(FxToast.class.getResource("/com/codeconnect/theme.css").toExternalForm());
        } catch (Exception ignored) {}

        Popup popup = new Popup();
        popup.getContent().add(pane);
        popup.setAutoFix(true);

        pane.applyCss();
        pane.layout();

        double x = owner.getX() + owner.getWidth() / 2 - 150;
        double y = owner.getY() + owner.getHeight() - 80;
        popup.show(owner, x, y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), pane);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);

        PauseTransition hold = new PauseTransition(Duration.millis(2500));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), pane);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> popup.hide());

        new SequentialTransition(fadeIn, hold, fadeOut).play();
    }
}
