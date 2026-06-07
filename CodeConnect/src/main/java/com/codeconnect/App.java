package com.codeconnect;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.ui.MainWindow;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        Stage splash = buildSplash();
        splash.show();

        Thread dbThread = new Thread(() -> {
            DatabaseHelper.initializeDatabase();
            try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                splash.close();
                try {
                    MainWindow main = new MainWindow();
                    main.show(primaryStage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        dbThread.setDaemon(true);
        dbThread.start();
    }

    private Stage buildSplash() {
        Label mono = new Label("</>");
        mono.setStyle("-fx-font-size:42px; -fx-font-weight:bold; -fx-text-fill:#00C896; -fx-font-family:'JetBrains Mono','Consolas',monospace;");

        Label title = new Label("CodeConnect");
        title.setStyle("-fx-font-size:28px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        Label sub = new Label("TERMINAL \u2022 LUXE \u2022 COLLABORATIVE");
        sub.setStyle("-fx-font-size:9px; -fx-text-fill:#374150; -fx-font-weight:bold;");

        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(220);
        bar.setStyle("-fx-accent:#00C896;");
        bar.setProgress(-1);

        VBox box = new VBox(10, mono, title, sub, bar);
        box.setAlignment(Pos.CENTER);
        box.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #0E1318, #080C10);"
            + "-fx-background-radius:12;"
            + "-fx-border-color: #1E2832;"
            + "-fx-border-radius:12;"
            + "-fx-border-width:1;"
            + "-fx-padding:44 60 44 60;");

        Scene scene = new Scene(box, 420, 200);
        scene.setFill(Color.TRANSPARENT);

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(false);

        FadeTransition ft = new FadeTransition(Duration.millis(600), box);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        return stage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
