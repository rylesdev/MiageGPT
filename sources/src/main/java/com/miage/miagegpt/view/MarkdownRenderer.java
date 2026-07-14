package com.miage.miagegpt.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextFlow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownRenderer {

    private static final Pattern INLINE_MD = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|\\*([^*\\n]+?)\\*|`([^`\\n]+)`|(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)");

    public static VBox renderContainer(String text, boolean isDarkMode) {
        VBox container = new VBox(4);
        container.setMaxWidth(670);
        String codeBg = isDarkMode ? "#2D2D3A" : "#F3F4F6";
        String codeHeaderBg = isDarkMode ? "#3A3A4A" : "#E5E7EB";
        javafx.scene.paint.Color codeFill = javafx.scene.paint.Color.web(isDarkMode ? "#93C5FD" : "#1D4ED8");
        javafx.scene.paint.Color headerColor = javafx.scene.paint.Color.web(isDarkMode ? "#9CA3AF" : "#6B7280");

        String[] lines = text.split("\n", -1);
        boolean inCodeBlock = false;
        StringBuilder normalText = new StringBuilder();
        StringBuilder codeText = new StringBuilder();
        String codeLang = "code";

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    if (normalText.length() > 0) {
                        container.getChildren().add(renderFlow(normalText.toString(), isDarkMode));
                        normalText = new StringBuilder();
                    }
                    codeLang = line.substring(3).trim();
                    if (codeLang.isEmpty()) codeLang = "code";
                    inCodeBlock = true;
                } else {
                    container.getChildren().add(buildCodeBlock(codeText.toString(), codeLang, isDarkMode, codeBg, codeHeaderBg, codeFill, headerColor));
                    codeText = new StringBuilder();
                    inCodeBlock = false;
                }
            } else if (inCodeBlock) {
                if (codeText.length() > 0) codeText.append("\n");
                codeText.append(line);
            } else {
                if (normalText.length() > 0) normalText.append("\n");
                normalText.append(line);
            }
        }

        if (normalText.length() > 0)
            container.getChildren().add(renderFlow(normalText.toString(), isDarkMode));
        if (inCodeBlock && codeText.length() > 0)
            container.getChildren().add(renderFlow(codeText.toString(), isDarkMode));

        return container;
    }

    private static VBox buildCodeBlock(String code, String lang, boolean isDarkMode,
            String codeBg, String codeHeaderBg, javafx.scene.paint.Color codeFill,
            javafx.scene.paint.Color headerColor) {
        VBox block = new VBox(0);
        block.setStyle("-fx-background-color: " + codeBg + "; -fx-background-radius: 6;");
        block.setMaxWidth(650);

        Label langLabel = new Label(lang);
        langLabel.setStyle("-fx-text-fill: " + toHex(headerColor) + "; -fx-font-size: 11px; -fx-font-family: 'Monospace';");

        Button copyBtn = new Button("📋 Copier");
        copyBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6; -fx-background-color: transparent; -fx-text-fill: "
                + toHex(headerColor) + "; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("✓ Copié");
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            pause.setOnFinished(ev -> copyBtn.setText("📋 Copier"));
            pause.play();
        });

        HBox header = new HBox();
        header.setStyle("-fx-background-color: " + codeHeaderBg + "; -fx-background-radius: 6 6 0 0; -fx-padding: 4 8;");
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(langLabel, spacer, copyBtn);

        TextFlow codeFlow = new TextFlow();
        codeFlow.setStyle("-fx-padding: 8 12;");
        codeFlow.setLineSpacing(3);
        String[] codeLines = code.split("\n", -1);
        for (int i = 0; i < codeLines.length; i++) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(codeLines[i]);
            t.setFill(codeFill);
            t.setFont(Font.font("Monospace", 13));
            codeFlow.getChildren().add(t);
            if (i < codeLines.length - 1)
                codeFlow.getChildren().add(new javafx.scene.text.Text("\n"));
        }

        block.getChildren().addAll(header, codeFlow);
        return block;
    }

    private static String toHex(javafx.scene.paint.Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    public static TextFlow renderFlow(String text, boolean isDarkMode) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(4);
        flow.setMaxWidth(670);
        javafx.scene.paint.Color textFill = javafx.scene.paint.Color.web(isDarkMode ? "#ECECF1" : "#1F2937");
        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            if (line.startsWith("#### ")) {
                addInlineNodes(flow, line.substring(5), textFill, true, 14, isDarkMode);
            } else if (line.startsWith("### ")) {
                addInlineNodes(flow, line.substring(4), textFill, true, 15, isDarkMode);
            } else if (line.startsWith("## ")) {
                addInlineNodes(flow, line.substring(3), textFill, true, 16, isDarkMode);
            } else if (line.startsWith("# ")) {
                addInlineNodes(flow, line.substring(2), textFill, true, 18, isDarkMode);
            } else if (line.startsWith("> ")) {
                javafx.scene.paint.Color quoteColor = javafx.scene.paint.Color.web(isDarkMode ? "#9CA3AF" : "#6B7280");
                javafx.scene.text.Text quotePrefix = new javafx.scene.text.Text("  | ");
                quotePrefix.setFill(quoteColor);
                quotePrefix.setFont(Font.font("System", FontWeight.BOLD, 14));
                flow.getChildren().add(quotePrefix);
                addInlineNodes(flow, line.substring(2), quoteColor, false, 14, isDarkMode);
            } else if (line.length() > 2 && (line.startsWith("- ") || line.startsWith("* "))) {
                javafx.scene.text.Text bullet = new javafx.scene.text.Text("  • ");
                bullet.setFill(textFill);
                bullet.setFont(Font.font("System", 14));
                flow.getChildren().add(bullet);
                addInlineNodes(flow, line.substring(2), textFill, false, 14, isDarkMode);
            } else {
                addInlineNodes(flow, line, textFill, false, 14, isDarkMode);
            }
            if (li < lines.length - 1) {
                flow.getChildren().add(new javafx.scene.text.Text("\n"));
            }
        }
        return flow;
    }

    private static void addInlineNodes(TextFlow flow, String text,
            javafx.scene.paint.Color textFill, boolean bold, double fontSize, boolean isDarkMode) {
        Matcher matcher = INLINE_MD.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                javafx.scene.text.Text plain = new javafx.scene.text.Text(text.substring(lastEnd, matcher.start()));
                plain.setFill(textFill);
                plain.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));
                flow.getChildren().add(plain);
            }
            if (matcher.group(1) != null) {
                javafx.scene.text.Text t = new javafx.scene.text.Text(matcher.group(1));
                t.setFill(textFill);
                t.setFont(Font.font("System", FontWeight.BOLD, fontSize));
                flow.getChildren().add(t);
            } else if (matcher.group(2) != null) {
                javafx.scene.text.Text t = new javafx.scene.text.Text(matcher.group(2));
                t.setFill(textFill);
                t.setFont(Font.font("System", FontWeight.NORMAL, FontPosture.ITALIC, fontSize));
                flow.getChildren().add(t);
            } else if (matcher.group(3) != null) {
                javafx.scene.text.Text t = new javafx.scene.text.Text(" " + matcher.group(3) + " ");
                t.setFill(isDarkMode
                        ? javafx.scene.paint.Color.web("#93C5FD")
                        : javafx.scene.paint.Color.web("#1D4ED8"));
                t.setFont(Font.font("Monospace", fontSize));
                t.getProperties().put("isCode", Boolean.TRUE);
                flow.getChildren().add(t);
            } else if (matcher.group(4) != null) {
                String url = matcher.group(4);
                Hyperlink link = new Hyperlink(url);
                link.setStyle("-fx-text-fill: #58A6FF; -fx-font-size: " + (int) fontSize
                        + "px; -fx-border-width: 0; -fx-padding: 0;");
                link.setOnAction(ev -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } catch (Exception ignored) {
                    }
                });
                flow.getChildren().add(link);
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            javafx.scene.text.Text remaining = new javafx.scene.text.Text(text.substring(lastEnd));
            remaining.setFill(textFill);
            remaining.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));
            flow.getChildren().add(remaining);
        }
    }
}
