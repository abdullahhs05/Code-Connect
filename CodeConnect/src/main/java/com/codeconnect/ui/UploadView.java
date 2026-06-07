package com.codeconnect.ui;

import com.codeconnect.controller.SnippetController;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class UploadView {

    private final VBox root;
    private final SnippetController snippetController = new SnippetController();
    private String importedFilename = "";

    public UploadView(Consumer<CodeSnippet> onSuccess) {
        root = new VBox(14);
        root.setStyle("-fx-background-color:#080C10;");
        root.setPadding(new Insets(24, 28, 24, 28));

        Label heading = new Label("Share a Code Snippet");
        heading.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#D4DCE8;");

        TextField titleField = field("Snippet Title");
        TextField langField  = field("Language (e.g. Java, Python, C++)");
        TextField tagsField  = field("Tags (comma-separated): review needed, bug, ...");

        TextArea descArea = new TextArea();
        descArea.setPromptText("Description (optional)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);
        descArea.getStyleClass().add("text-area-dark");

        CodeArea codeArea = SyntaxHighlighter.buildCodeArea("", true);
        codeArea.setStyle("-fx-font-size:13px;");
        VBox codeWrap = new VBox(codeArea);
        codeWrap.setPrefHeight(280);
        codeWrap.setStyle("-fx-border-color:#1E2832; -fx-border-radius:6; -fx-background-radius:6; -fx-border-width:1;");

        Label errLabel = new Label("");
        errLabel.setStyle("-fx-text-fill:#EF4444; -fx-font-size:12px;");

        Button importBtn = new Button("Import from File...");
        importBtn.getStyleClass().add("btn-ghost");

        Button uploadBtn = new Button("Share Code Snippet");
        uploadBtn.getStyleClass().add("btn-success");
        uploadBtn.setPrefWidth(220);

        importBtn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select a code file");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Code Files",
                    "*.java", "*.py", "*.cpp", "*.c", "*.h", "*.hpp", "*.cs", "*.js", "*.ts",
                    "*.html", "*.css", "*.go", "*.rs", "*.rb", "*.php", "*.kt", "*.swift",
                    "*.sql", "*.sh", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
            File f = fc.showOpenDialog(null);
            if (f == null) return;
            // Size limit: 256 KB to keep DB lean
            if (f.length() > 256 * 1024) {
                errLabel.setText("File too large (max 256 KB).");
                return;
            }
            try {
                String content = Files.readString(f.toPath());
                codeArea.replaceText(content);
                String detected = detectLanguage(f.getName());
                if (detected != null && langField.getText().trim().isEmpty()) langField.setText(detected);
                if (titleField.getText().trim().isEmpty()) titleField.setText(f.getName());
                importedFilename = f.getName();
                errLabel.setText("");
            } catch (Exception ex) {
                errLabel.setText("Could not read file: " + ex.getMessage());
            }
        });

        uploadBtn.setOnAction(e -> {
            if (Session.getCurrentUser() == null) return;
            // UC-3: route through SnippetController (Pure Fabrication)
            SnippetController.OpResult res = snippetController.handleUpload(
                    titleField.getText().trim(),
                    langField.getText().trim(),
                    codeArea.getText(),
                    descArea.getText().trim(),
                    tagsField.getText().trim(),
                    importedFilename);
            if (res.success) {
                onSuccess.accept(res.snippet);
            } else {
                errLabel.setText(res.message);
            }
        });

        HBox codeHeader = new HBox(8, lbl("Code"), new Region(), importBtn);
        HBox.setHgrow(codeHeader.getChildren().get(1), Priority.ALWAYS);
        codeHeader.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(10,
            lbl("Title"), titleField,
            lbl("Language"), langField,
            lbl("Tags"), tagsField,
            lbl("Description"), descArea,
            codeHeader, codeWrap,
            errLabel, uploadBtn
        );
        form.setStyle("-fx-background-color:#0E1318; -fx-background-radius:10; -fx-border-color:#1E2832; -fx-border-radius:10; -fx-border-width:1;");
        form.setPadding(new Insets(20));

        ScrollPane sp = new ScrollPane(form);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#080C10;");
        sp.getStyleClass().add("scroll-pane-dark");
        VBox.setVgrow(sp, Priority.ALWAYS);

        root.getChildren().addAll(heading, sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill:#6A7A8E; -fx-font-size:12px;");
        return l;
    }

    private TextField field(String ph) {
        TextField f = new TextField();
        f.setPromptText(ph);
        f.getStyleClass().add("text-field-dark");
        return f;
    }

    private static final Map<String, String> EXT_TO_LANG = new HashMap<>();
    static {
        EXT_TO_LANG.put("java", "Java");      EXT_TO_LANG.put("py", "Python");
        EXT_TO_LANG.put("cpp", "C++");        EXT_TO_LANG.put("c", "C");
        EXT_TO_LANG.put("h", "C");            EXT_TO_LANG.put("hpp", "C++");
        EXT_TO_LANG.put("cs", "C#");          EXT_TO_LANG.put("js", "JavaScript");
        EXT_TO_LANG.put("ts", "TypeScript");  EXT_TO_LANG.put("html", "HTML");
        EXT_TO_LANG.put("css", "CSS");        EXT_TO_LANG.put("go", "Go");
        EXT_TO_LANG.put("rs", "Rust");        EXT_TO_LANG.put("rb", "Ruby");
        EXT_TO_LANG.put("php", "PHP");        EXT_TO_LANG.put("kt", "Kotlin");
        EXT_TO_LANG.put("swift", "Swift");    EXT_TO_LANG.put("sql", "SQL");
        EXT_TO_LANG.put("sh", "Bash");
    }

    private static String detectLanguage(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        return EXT_TO_LANG.get(filename.substring(dot + 1).toLowerCase());
    }

    public VBox getView() { return root; }
}
