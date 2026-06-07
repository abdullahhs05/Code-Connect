package com.codeconnect.ui;

import com.codeconnect.dao.CodeSnippetDAO;
import com.codeconnect.dao.UserDAO;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.util.List;
import java.util.function.Consumer;

public class ProfileView {

    private final VBox root;

    public ProfileView(Stage stage, UserDAO userDAO, CodeSnippetDAO snippetDAO, Consumer<User> onUpdated) {
        root = new VBox(16);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(24, 28, 24, 28));

        Label heading = new Label("My Profile");
        heading.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        VBox editCard = buildEditCard(stage, userDAO, onUpdated);
        VBox snippetsCard = buildSnippetsCard(stage, snippetDAO);

        ScrollPane sp = new ScrollPane(new VBox(16, editCard, snippetsCard));
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#080C10;");
        sp.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(sp, Priority.ALWAYS);

        root.getChildren().addAll(heading, sp);
    }

    private VBox buildEditCard(Stage stage, UserDAO userDAO, Consumer<User> onUpdated) {
        User u = Session.getCurrentUser();

        TextField usernameField = new TextField(u != null ? u.getUsername() : "");
        usernameField.getStyleClass().add("text-field-dark");
        PasswordField passField = new PasswordField();
        passField.setPromptText("New password (leave blank to keep)");
        passField.getStyleClass().add("password-field-dark");

        Label errLabel = new Label("");
        errLabel.setStyle("-fx-text-fill:#EF4444; -fx-font-size:12px;");

        Button updateBtn = new Button("Update Profile");
        updateBtn.getStyleClass().add("btn-primary");

        updateBtn.setOnAction(e -> {
            String uname = usernameField.getText().trim();
            String pass  = passField.getText().trim();
            User cur = Session.getCurrentUser();
            if (cur == null) return;
            if (uname.isEmpty()) { errLabel.setText("Username cannot be empty."); return; }
            if (uname.length() < 3) { errLabel.setText("Username must be at least 3 characters."); return; }
            cur.setUsername(uname);
            if (!pass.isEmpty()) {
                if (pass.length() < 4) { errLabel.setText("Password must be at least 4 characters."); return; }
                cur.setPassword(pass);
            }
            if (userDAO.updateUser(cur)) {
                onUpdated.accept(cur);
                FxToast.show(stage, "Profile updated!", true);
                errLabel.setText("");
            } else {
                errLabel.setText("Update failed. Username may already be taken.");
            }
        });

        VBox card = new VBox(10,
            lbl("Username"), usernameField,
            lbl("New Password"), passField,
            errLabel, updateBtn);
        card.setStyle("-fx-background-color:#0E1318; -fx-background-radius:8; -fx-border-color:#1E2832; -fx-border-radius:8; -fx-border-width:1;");
        card.setPadding(new Insets(18));
        return card;
    }

    private VBox buildSnippetsCard(Stage stage, CodeSnippetDAO snippetDAO) {
        Label title = new Label("My Uploaded Snippets");
        title.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        ListView<CodeSnippet> list = new ListView<>();
        list.getStyleClass().add("list-view-dark");
        list.setPrefHeight(200);

        Runnable reload = () -> {
            list.getItems().clear();
            User u = Session.getCurrentUser();
            if (u != null) {
                for (CodeSnippet s : snippetDAO.findAllWithDetails(u.getId())) {
                    if (s.getUploaderId() == u.getId()) list.getItems().add(s);
                }
            }
        };
        reload.run();

        Button editBtn = new Button("Edit Selected Snippet");
        editBtn.getStyleClass().add("btn-ghost");
        editBtn.setOnAction(e -> {
            CodeSnippet sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { FxToast.show(stage, "Select a snippet first.", false); return; }
            showEditDialog(stage, snippetDAO, sel, reload);
        });

        VBox card = new VBox(10, title, list, editBtn);
        card.setStyle("-fx-background-color:#0E1318; -fx-background-radius:8; -fx-border-color:#1E2832; -fx-border-radius:8; -fx-border-width:1;");
        card.setPadding(new Insets(18));
        return card;
    }

    private void showEditDialog(Stage owner, CodeSnippetDAO dao, CodeSnippet s, Runnable onDone) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Snippet: " + s.getTitle());
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(620, 580);
        dlg.getDialogPane().setStyle("-fx-background-color:#0E1318;");

        TextField titleF = new TextField(s.getTitle()); titleF.getStyleClass().add("text-field-dark");
        TextField langF  = new TextField(s.getLanguage()); langF.getStyleClass().add("text-field-dark");
        TextField tagsF  = new TextField(s.getTags() != null ? s.getTags() : ""); tagsF.getStyleClass().add("text-field-dark");
        TextArea descA   = new TextArea(s.getDescription()); descA.getStyleClass().add("text-area-dark"); descA.setPrefRowCount(3); descA.setWrapText(true);
        CodeArea codeA   = SyntaxHighlighter.buildCodeArea(s.getCode(), true);
        VBox codeWrap = new VBox(codeA); codeWrap.setPrefHeight(200);
        codeWrap.setStyle("-fx-border-color:#1E2832; -fx-border-width:1; -fx-border-radius:6;");

        VBox form = new VBox(8,
            lbl("Title"), titleF, lbl("Language"), langF,
            lbl("Tags"), tagsF, lbl("Description"), descA,
            lbl("Code"), codeWrap);
        form.setPadding(new Insets(12));

        ScrollPane sp = new ScrollPane(form); sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#0E1318;");
        dlg.getDialogPane().setContent(sp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                s.setTitle(titleF.getText()); s.setLanguage(langF.getText());
                s.setTags(tagsF.getText()); s.setDescription(descA.getText());
                s.setCode(codeA.getText());
                if (dao.updateSnippet(s)) { onDone.run(); FxToast.show(owner, "Snippet updated!", true); }
            }
        });
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:12px;");
        return l;
    }

    public VBox getView() { return root; }
}
