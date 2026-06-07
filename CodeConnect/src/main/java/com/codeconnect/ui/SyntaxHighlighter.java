package com.codeconnect.ui;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    private static final String[] KEYWORDS = {
        "abstract","assert","boolean","break","byte","case","catch","char","class",
        "const","continue","default","do","double","else","enum","extends","final",
        "finally","float","for","goto","if","implements","import","instanceof","int",
        "interface","long","native","new","package","private","protected","public",
        "return","short","static","strictfp","super","switch","synchronized","this",
        "throw","throws","transient","try","var","void","volatile","while","true","false","null",
        "def","lambda","yield","record","sealed","permits","non-sealed"
    };

    private static final String KEYWORD_PATTERN   = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN     = "[()\\[\\]{}]";
    private static final String SEMICOLON_PATTERN = ";";
    private static final String STRING_PATTERN    = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN   = "//[^\n]*|/\\*(.|\\R)*?\\*/";
    private static final String NUMBER_PATTERN    = "\\b\\d+(\\.\\d+)?[fFlLdD]?\\b";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>"   + KEYWORD_PATTERN   + ")"
        + "|(?<PAREN>"  + PAREN_PATTERN     + ")"
        + "|(?<SEMI>"   + SEMICOLON_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN    + ")"
        + "|(?<COMMENT>"+ COMMENT_PATTERN   + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN    + ")"
    );

    public static CodeArea buildCodeArea(String code, boolean editable) {
        CodeArea area = new CodeArea();
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        area.getStyleClass().add("code-area");
        area.setEditable(editable);

        area.textProperty().addListener((obs, oldText, newText) ->
            area.setStyleSpans(0, computeHighlighting(newText))
        );

        area.replaceText(code != null ? code : "");
        return area;
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        while (matcher.find()) {
            String styleClass =
                matcher.group("KEYWORD")  != null ? "keyword"   :
                matcher.group("PAREN")    != null ? "paren"     :
                matcher.group("SEMI")     != null ? "semicolon" :
                matcher.group("STRING")   != null ? "string"    :
                matcher.group("COMMENT")  != null ? "comment"   :
                matcher.group("NUMBER")   != null ? "number"    : "default";
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }
}
