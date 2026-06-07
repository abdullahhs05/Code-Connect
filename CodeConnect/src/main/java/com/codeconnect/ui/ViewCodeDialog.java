package com.codeconnect.ui;

import com.codeconnect.model.CodeSnippet;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.nio.file.Files;

public class ViewCodeDialog {

    private final Stage owner;
    private final CodeSnippet snippet;

    public ViewCodeDialog(Stage owner, CodeSnippet snippet) {
        this.owner = owner;
        this.snippet = snippet;
    }

    public void show() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initOwner(owner);
        dlg.setTitle("View Code: " + snippet.getTitle());
        dlg.setMinWidth(700);
        dlg.setMinHeight(520);

        Label titleLbl = new Label(snippet.getTitle());
        titleLbl.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        Label lang = new Label(snippet.getLanguage());
        lang.setStyle("-fx-text-fill:#00C896; -fx-font-size:12px; -fx-background-color:rgba(0,200,150,0.15); -fx-padding:3 8 3 8; -fx-background-radius:4;");

        HBox header = new HBox(10, titleLbl, lang);
        header.setPadding(new Insets(0, 0, 10, 0));

        CodeArea codeArea = SyntaxHighlighter.buildCodeArea(snippet.getCode(), false);
        VBox codeWrap = new VBox(codeArea);
        VBox.setVgrow(codeWrap, Priority.ALWAYS);
        codeWrap.setStyle("-fx-border-color:#1E2832; -fx-border-width:1; -fx-border-radius:6;");

        Button copyBtn = new Button("Copy Code");
        copyBtn.getStyleClass().add("btn-ghost");
        copyBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(snippet.getCode());
            Clipboard.getSystemClipboard().setContent(cc);
            FxToast.show(dlg, "Copied to clipboard!", true);
        });

        Button exportBtn = new Button("Export .txt");
        exportBtn.getStyleClass().add("btn-ghost");
        exportBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(snippet.getTitle().replaceAll("[^a-zA-Z0-9_]", "_") + ".txt");
            File f = fc.showSaveDialog(dlg);
            if (f != null) {
                try { Files.writeString(f.toPath(), snippet.getCode()); FxToast.show(dlg, "Exported!", true); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-primary");
        closeBtn.setOnAction(e -> dlg.close());

        HBox actions = new HBox(8, copyBtn, exportBtn, new Region(), closeBtn);
        HBox.setHgrow(actions.getChildren().get(2), Priority.ALWAYS);
        actions.setPadding(new Insets(10, 0, 0, 0));

        VBox root = new VBox(10, header, codeWrap, actions);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(20));
        VBox.setVgrow(codeWrap, Priority.ALWAYS);

        Scene scene = new Scene(root, 720, 540);
        scene.getStylesheets().add(getClass().getResource("/com/codeconnect/theme.css").toExternalForm());
        dlg.setScene(scene);
        dlg.show();
    }
}
