package com.codeconnect.ui;

import com.codeconnect.controller.AuthController;
import com.codeconnect.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class AuthView {

    private final StackPane root;
    private final AuthController authController = new AuthController();
    private final Consumer<User> onLogin;

    public AuthView(Consumer<User> onLogin) {
        this.onLogin = onLogin;

        root = new StackPane();
        root.setStyle("-fx-background-color:#080C10;");

        // ---- Split layout: left hero (40%) + right form (60%) ----
        javafx.scene.layout.HBox split = new javafx.scene.layout.HBox();
        split.setFillHeight(true);

        // Hero panel
        javafx.scene.layout.VBox hero = new javafx.scene.layout.VBox(14);
        hero.getStyleClass().add("auth-hero");
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(48, 44, 48, 44));
        hero.setPrefWidth(440);
        hero.setMinWidth(300);

        Label logoMono = new Label("</>");
        logoMono.setStyle("-fx-font-size:38px; -fx-font-weight:bold; -fx-text-fill:#FFFFFF; -fx-font-family:'JetBrains Mono','Consolas',monospace;");

        Label wordmark = new Label("CodeConnect");
        wordmark.setStyle("-fx-font-size:30px; -fx-font-weight:bold; -fx-text-fill:#FFFFFF;");

        Label tagline = new Label("Review, discuss, and iterate on code\nwith your team. Asynchronously or live.");
        tagline.setStyle("-fx-font-size:13px; -fx-text-fill:rgba(255,255,255,0.75); -fx-line-spacing:4;");
        tagline.setWrapText(true);

        javafx.scene.layout.Region spacer1 = new javafx.scene.layout.Region();
        javafx.scene.layout.VBox.setVgrow(spacer1, javafx.scene.layout.Priority.ALWAYS);

        Label footer = new Label("\u2022  Secure by design\n\u2022  Real-time over sockets\n\u2022  Private rooms & mentions");
        footer.setStyle("-fx-font-size:11px; -fx-text-fill:rgba(255,255,255,0.55); -fx-line-spacing:3;");

        hero.getChildren().addAll(logoMono, wordmark, tagline, spacer1, footer);

        // Form panel
        javafx.scene.layout.StackPane formWrap = new javafx.scene.layout.StackPane();
        formWrap.setStyle("-fx-background-color:#0E1318;");
        javafx.scene.layout.HBox.setHgrow(formWrap, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.layout.VBox formCol = new javafx.scene.layout.VBox();
        formCol.setMaxWidth(400);
        formCol.setAlignment(Pos.CENTER_LEFT);

        StackPane inner = new StackPane();
        VBox loginPane = buildLoginPane(inner);
        buildRegisterPane(inner);
        inner.getChildren().setAll(loginPane);

        formCol.getChildren().add(inner);
        formWrap.getChildren().add(formCol);
        StackPane.setAlignment(formCol, Pos.CENTER);
        formWrap.setPadding(new Insets(24, 48, 24, 48));

        split.getChildren().addAll(hero, formWrap);
        root.getChildren().add(split);
    }

    private VBox buildLoginPane(StackPane parent) {
        Label title = new Label("Welcome back to CodeConnect");
        title.getStyleClass().addAll("title-large");
        title.setStyle("-fx-font-size:20px;");

        TextField userField = styledField("Username");
        PasswordField passField = styledPass("Password");
        Label errLabel = errLabel();

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);

        Hyperlink createLink = new Hyperlink("Don't have an account? Register");
        createLink.getStyleClass().add("label-accent");

        VBox box = new VBox(14, title, userField, passField, errLabel, loginBtn, createLink);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0));

        loginBtn.setOnAction(e -> {
            String u = userField.getText().trim();
            String p = passField.getText().trim();
            if (u.isEmpty() || p.isEmpty()) { errLabel.setText("Fields cannot be empty."); return; }
            // UC-2: route through AuthController (Pure Fabrication)
            AuthController.AuthResult res = authController.authenticateUser(u, p);
            if (res.success) {
                onLogin.accept(res.user);
            } else {
                errLabel.setText(res.message);
                passField.clear();
            }
        });
        passField.setOnAction(e -> loginBtn.fire());

        createLink.setOnAction(e -> {
            parent.getChildren().setAll(buildRegisterPane(parent));
        });
        return box;
    }

    private VBox buildRegisterPane(StackPane parent) {
        Label title = new Label("Create an Account");
        title.getStyleClass().add("title-large");
        title.setStyle("-fx-font-size:20px;");

        TextField userField   = styledField("Choose a Username");
        TextField emailField  = styledField("Email Address");
        PasswordField passField = styledPass("Create a Password");
        // SECURITY (gap #2): self-registration is always Developer; admins are seeded or promoted.
        Label roleNote = new Label("You'll be registered as a Developer. Admins promote accounts in the Admin panel.");
        roleNote.setStyle("-fx-font-size:11px; -fx-text-fill:#6A7A8E;");
        roleNote.setWrapText(true);

        Label errLabel = errLabel();

        Button regBtn = new Button("Register");
        regBtn.getStyleClass().add("btn-success");
        regBtn.setMaxWidth(Double.MAX_VALUE);

        Hyperlink loginLink = new Hyperlink("Already have an account? Login");
        loginLink.getStyleClass().add("label-accent");

        VBox box = new VBox(14, title, userField, emailField, passField, roleNote, errLabel, regBtn, loginLink);
        box.setAlignment(Pos.CENTER_LEFT);

        regBtn.setOnAction(e -> {
            String u = userField.getText().trim();
            String em = emailField.getText().trim();
            String p = passField.getText().trim();
            // SECURITY: ignore the requested role; AuthController forces "Developer" for self-registration.
            if (u.isEmpty() || em.isEmpty() || p.isEmpty()) { errLabel.setText("All fields are required."); return; }
            if (u.length() < 3) { errLabel.setText("Username must be at least 3 characters."); return; }
            if (!em.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { errLabel.setText("Please enter a valid email address."); return; }
            if (p.length() < 4) { errLabel.setText("Password must be at least 4 characters."); return; }
            // UC-1: route through AuthController (Pure Fabrication)
            AuthController.AuthResult res = authController.handleRegister(u, em, p, "Developer");
            if (res.success) {
                parent.getChildren().setAll(buildLoginPane(parent));
            } else {
                errLabel.setText(res.message);
            }
        });

        loginLink.setOnAction(e -> parent.getChildren().setAll(buildLoginPane(parent)));
        return box;
    }

    private TextField styledField(String ph) {
        TextField f = new TextField();
        f.setPromptText(ph);
        f.getStyleClass().add("text-field-dark");
        return f;
    }

    private PasswordField styledPass(String ph) {
        PasswordField f = new PasswordField();
        f.setPromptText(ph);
        f.getStyleClass().add("password-field-dark");
        return f;
    }

    private Label errLabel() {
        Label l = new Label("");
        l.getStyleClass().add("label-danger");
        l.setStyle("-fx-font-size:12px;");
        l.setWrapText(true);
        return l;
    }

    public StackPane getView() { return root; }
}
