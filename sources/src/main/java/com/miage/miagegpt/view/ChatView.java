package com.miage.miagegpt.view;

import com.miage.miagegpt.controller.ChatController;
import com.miage.miagegpt.model.ConversationManager;
import com.miage.miagegpt.service.APIResponse;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.miage.miagegpt.ChatApp;
import com.miage.miagegpt.service.Config;

public class ChatView {
    private VBox chatBox;
    private VBox welcomeBox;
    private ScrollPane scrollPane;
    private Map<String, VBox> conversations = new HashMap<>();
    private Map<String, LocalDateTime> conversationDates = new HashMap<>();
    private Map<String, Integer> conversationMessageCounts = new HashMap<>();
    private Map<String, Long> conversationStartTimes = new HashMap<>();
    private String currentConversation = null;
    private Label dateTimeLabel;
    private Label statsLabel;
    private BorderPane centerPane;
    private VBox inputArea;
    private BorderPane root;
    private javafx.scene.layout.StackPane summarizeBtn;
    private TextArea inputField;
    private Button sendBtn;
    private Button changeApiKeyBtn;

    private Map<String, String> conversationHistory = new HashMap<>();

    private javafx.animation.Timeline typingTimeline;
    private HBox typingMessageRow;

    private boolean isDarkMode = false;
    private Button themeToggleBtn;
    private ChatController controller;
    private String lastPingValue = "Ping: --ms";
    private ConversationSidebar sidebar;
    private javafx.stage.Stage primaryStage;

    private Thread currentMessageThread = null;
    private volatile boolean stopMessageDisplay = false;

    public ChatView(ChatController controller) {
        this.controller = controller;
    }

    public void initialize(Stage primaryStage) {
        this.primaryStage = primaryStage;
        sidebar = new ConversationSidebar(
                this::onNewChat,
                this::onSwitchConversation,
                this::renameConversation,
                this::deleteConversation,
                this::onHome,
                () -> isDarkMode,
                () -> currentConversation);
        VBox sidebarNode = sidebar.build();

        chatBox = new VBox(20);
        chatBox.setPadding(new Insets(20));
        chatBox.getStyleClass().add("chat-box");
        attachScrollListener(chatBox);

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFocusTraversable(false);
        scrollPane.getStyleClass().add("chat-scroll-pane");

        dateTimeLabel = new Label();
        dateTimeLabel.getStyleClass().add("header-secondary-label");
        // AJOUT : Libérer l'espace quand c'est caché
        dateTimeLabel.managedProperty().bind(dateTimeLabel.visibleProperty());
        updateDateTimeLabel();

        statsLabel = new Label();
        statsLabel.getStyleClass().add("header-secondary-label");
        // AJOUT : Libérer l'espace quand c'est caché
        statsLabel.managedProperty().bind(statsLabel.visibleProperty());

        summarizeBtn = createGradientButton("✨ Résumer la conversation", this::summarizeConversation);
        // AJOUT : Libérer l'espace quand c'est caché
        summarizeBtn.managedProperty().bind(summarizeBtn.visibleProperty());
        HBox.setMargin(summarizeBtn, new Insets(0, 0, 0, 20));

        HBox.setMargin(statsLabel, new Insets(0, 0, 0, 24));

        themeToggleBtn = new Button();
        themeToggleBtn.getStyleClass().add("theme-toggle");
        updateThemeButtonLabel();
        addHoverScaleEffect(themeToggleBtn);
        themeToggleBtn.setOnAction(e -> toggleTheme());

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15, 15, 15, 15));
        header.getStyleClass().add("header-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button changeApiKeyBtn = new Button("🔑 Clé API");
        changeApiKeyBtn.getStyleClass().addAll("theme-toggle");
        changeApiKeyBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white;");
        changeApiKeyBtn.setOnAction(e -> openApiKeyConfigWindow());
        changeApiKeyBtn.setVisible(false);
        // AJOUT : Libérer l'espace quand c'est caché
        changeApiKeyBtn.managedProperty().bind(changeApiKeyBtn.visibleProperty());
        addHoverScaleEffect(changeApiKeyBtn);
        this.changeApiKeyBtn = changeApiKeyBtn;
        HBox.setMargin(changeApiKeyBtn, new Insets(0, 8, 0, 0));

        // MODIFICATION : Placer le spacer EN PREMIER, pour que tout le reste soit
        // poussé à droite
        // à côté du bouton de thème.
        header.getChildren().addAll(dateTimeLabel, summarizeBtn, statsLabel, spacer, changeApiKeyBtn, themeToggleBtn);
        centerPane = new BorderPane();
        centerPane.setTop(header);
        centerPane.setCenter(scrollPane);
        centerPane.getStyleClass().add("center-pane");

        showWelcomeScreen();
        inputArea = createInputArea();
        root = new BorderPane();
        root.setLeft(sidebarNode);
        root.setCenter(centerPane);
        applyTheme();

        loadSavedConversations();

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        this.primaryStage.setTitle("MiageGPT");
        this.primaryStage.setScene(scene);
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icon_texte.png")));
        } catch (Exception ignored) {
        }

        loadWindowPreferences(primaryStage);
        primaryStage.setOnCloseRequest(event -> {
            saveWindowPreferences(primaryStage);
            saveAllConversations();
        });

        primaryStage.show();
    }

    private void onNewChat() {
        int conversationNumber = conversations.size() + 1;
        String baseName = "Conversation ";
        String conversationName = baseName + conversationNumber;
        while (conversations.containsKey(conversationName)) {
            conversationNumber++;
            conversationName = baseName + conversationNumber;
        }

        VBox newChatBox = new VBox(20);
        newChatBox.setPadding(new Insets(20));
        newChatBox.getStyleClass().add("chat-box");
        conversations.put(conversationName, newChatBox);
        conversationDates.put(conversationName, LocalDateTime.now());
        conversationStartTimes.put(conversationName, System.currentTimeMillis());
        conversationMessageCounts.put(conversationName, 0);

        sidebar.getConversationItems().add(0, conversationName);

        switchConversation(conversationName);
        sidebar.refresh();
        saveCurrentConversation();

        root.setBottom(inputArea);
    }

    private void onSwitchConversation(String conversationName) {
        if (conversations.containsKey(conversationName)) {
            switchConversation(conversationName);
            sidebar.refresh();
            if (root.getBottom() == null) {
                root.setBottom(inputArea);
            }
        }
    }

    private void onHome() {
        currentConversation = null;
        showWelcomeScreen();
        applyTheme();
        root.setBottom(null);
    }

    private VBox createInputArea() {
        VBox inputArea = new VBox(10);
        inputArea.setPadding(new Insets(20, 20, 20, 20));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.getStyleClass().add("input-area");

        inputField = new TextArea();
        inputField.setPromptText("Envoyez un message...");
        inputField.setPrefRowCount(3);
        inputField.setWrapText(true);
        inputField.setMaxHeight(150);
        inputField.setMaxWidth(800);
        inputField.getStyleClass().add("input-field");

        sendBtn = new Button("➤");
        sendBtn.getStyleClass().add("send-btn");

        HBox inputBox = new HBox(10, inputField, sendBtn);
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setMaxWidth(800);

        applySendHandler(this::handleSendMessage);

        inputArea.getChildren().add(inputBox);
        return inputArea;
    }

    private void addUserMessage(String text) {
        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        MessageBubble bubble = MessageBubble.forUser(text, timeStamp);
        chatBox.getChildren().add(bubble.row);

        int count = conversationMessageCounts.getOrDefault(currentConversation, 0);
        conversationMessageCounts.put(currentConversation, count + 1);
        updateStatsLabel();
        scrollToBottom();
        javafx.application.Platform.runLater(this::scrollToBottom);
    }

    private void addAIMessage(String text) {
        addAIMessage(text, null, null);
    }

    private void addAIMessage(String text, String sourcePrompt, String historyBefore) {
        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        final String[] currentText = { text };
        MessageBubble bubble = MessageBubble.forAI(timeStamp, () -> currentText[0]);

        final Button[] reloadBtn = { null };
        if (sourcePrompt != null && historyBefore != null) {
            reloadBtn[0] = new Button("↻");
            reloadBtn[0].getStyleClass().add("reload-btn");
            reloadBtn[0].setTooltip(new Tooltip("Générer une nouvelle version"));
            reloadBtn[0].setDisable(true);

            reloadBtn[0].setOnAction(e -> {
                reloadBtn[0].setDisable(true);
                reloadBtn[0].setText("…");
                String previousResponse = currentText[0];

                new Thread(() -> {
                    try {
                        APIResponse apiResponse = controller.sendMessageWithPing(sourcePrompt, historyBefore);
                        String newResponse = apiResponse.content;
                        long pingMs = apiResponse.pingMs;

                        javafx.application.Platform.runLater(() -> {
                            lastPingValue = "Ping: " + pingMs + "ms";
                            sidebar.setPingText(lastPingValue);

                            currentText[0] = newResponse;

                            swapToLabel(bubble.box, bubble.messageLabel);
                            Thread displayThread = new Thread(() -> {
                                stopMessageDisplay = false;
                                for (int i = 0; i <= newResponse.length(); i++) {
                                    if (stopMessageDisplay)
                                        break;
                                    final int index = i;
                                    javafx.application.Platform.runLater(() -> {
                                        bubble.messageLabel.setText(newResponse.substring(0, index));
                                        scrollToBottom();
                                    });
                                    try {
                                        Thread.sleep(1);
                                    } catch (InterruptedException ignored) {
                                    }
                                }

                                javafx.application.Platform.runLater(() -> {
                                    String fullHistory = conversationHistory.getOrDefault(currentConversation,
                                            historyBefore);
                                    String oldTurn = controller.buildTurn(historyBefore, sourcePrompt,
                                            previousResponse);
                                    String newTurn = controller.buildTurn(historyBefore, sourcePrompt, newResponse);

                                    String updatedHistory;
                                    if (fullHistory.contains(oldTurn)) {
                                        updatedHistory = fullHistory.replaceFirst(
                                                java.util.regex.Pattern.quote(oldTurn),
                                                java.util.regex.Matcher.quoteReplacement(newTurn));
                                    } else {
                                        String oldLine = (historyBefore != null && !historyBefore.isEmpty())
                                                ? historyBefore + "\nMiageGPT: " + previousResponse
                                                : "MiageGPT: " + previousResponse;
                                        String newLine = (historyBefore != null && !historyBefore.isEmpty())
                                                ? historyBefore + "\nMiageGPT: " + newResponse
                                                : "MiageGPT: " + newResponse;
                                        if (fullHistory.contains(oldLine)) {
                                            updatedHistory = fullHistory.replaceFirst(
                                                    java.util.regex.Pattern.quote(oldLine),
                                                    java.util.regex.Matcher.quoteReplacement(newLine));
                                        } else if (fullHistory.equals(historyBefore) || fullHistory.isEmpty()) {
                                            updatedHistory = newTurn;
                                        } else {
                                            updatedHistory = fullHistory;
                                        }
                                    }
                                    conversationHistory.put(currentConversation, updatedHistory);
                                    saveCurrentConversation();

                                    reloadBtn[0].setText("↻");
                                    reloadBtn[0].setDisable(false);
                                    bubble.copyBtn.setText("📋");

                                    renderMarkdownInBox(bubble.box, bubble.messageLabel, currentText[0]);
                                });
                            });
                            displayThread.start();
                        });
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> {
                            reloadBtn[0].setText("↻");
                            reloadBtn[0].setDisable(false);
                            bubble.copyBtn.setText("📋");
                            addAIMessage("Erreur: " + ex.getMessage());
                        });
                    }
                }).start();
            });

            Region gap = new Region();
            gap.setMinWidth(6);
            int insertAt = bubble.footerBox.getChildren().size() - 1;
            bubble.footerBox.getChildren().add(insertAt, gap);
            bubble.footerBox.getChildren().add(insertAt, reloadBtn[0]);
        }

        chatBox.getChildren().add(bubble.row);

        conversationMessageCounts.put(currentConversation,
                conversationMessageCounts.getOrDefault(currentConversation, 0) + 1);
        updateStatsLabel();

        scrollToBottom();
        javafx.application.Platform.runLater(this::scrollToBottom);

        currentMessageThread = new Thread(() -> {
            stopMessageDisplay = false;
            for (int i = 0; i <= currentText[0].length(); i++) {
                if (stopMessageDisplay)
                    break;
                final int index = i;
                javafx.application.Platform.runLater(() -> {
                    bubble.messageLabel.setText(currentText[0].substring(0, index));
                    scrollToBottom();
                });
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }

            javafx.application.Platform.runLater(() -> {
                stopSendButtonLoader();
                if (reloadBtn[0] != null) {
                    reloadBtn[0].setDisable(false);
                }
                renderMarkdownInBox(bubble.box, bubble.messageLabel, currentText[0]);
            });
        });

        currentMessageThread.start();
    }

    private void renderMarkdownInBox(VBox messageBox, Label messageLabel, String text) {
        VBox container = MarkdownRenderer.renderContainer(text, isDarkMode);
        int idx = messageBox.getChildren().indexOf(messageLabel);
        if (idx >= 0) {
            messageBox.getChildren().set(idx, container);
        }
        messageBox.getProperties().put("textFlow", container);
        messageBox.getProperties().put("markdownSource", text);
    }

    private void swapToLabel(VBox messageBox, Label messageLabel) {
        Object flowObj = messageBox.getProperties().get("textFlow");
        if (flowObj instanceof javafx.scene.Node) {
            int idx = messageBox.getChildren().indexOf(flowObj);
            if (idx >= 0) {
                messageBox.getChildren().set(idx, messageLabel);
            }
            messageBox.getProperties().remove("textFlow");
            messageBox.getProperties().remove("markdownSource");
        }
    }

    private void applySendHandler(Runnable handler) {
        if (sendBtn != null) {
            sendBtn.setOnAction(e -> handler.run());
        }
        if (inputField != null) {
            inputField.setOnKeyPressed(e -> {
                if (e.getCode().toString().equals("ENTER")) {
                    if (e.isShiftDown()) {
                        int pos = inputField.getCaretPosition();
                        inputField.insertText(pos, "\n");
                    } else {
                        handler.run();
                    }
                    e.consume();
                }
            });
        }
    }

    private void switchConversation(String conversationName) {

        if (currentConversation != null && conversations.containsKey(currentConversation)) {
            conversations.put(currentConversation, chatBox);
        }

        currentConversation = conversationName;
        chatBox = conversations.get(conversationName);

        if (chatBox == null) {
            chatBox = new VBox(20);
            chatBox.setPadding(new Insets(20));
            chatBox.setStyle("-fx-background-color: rgba(8, 107, 174);");
            attachScrollListener(chatBox);
            conversations.put(conversationName, chatBox);
            conversationDates.put(conversationName, LocalDateTime.now());
            conversationStartTimes.put(conversationName, System.currentTimeMillis());
            conversationMessageCounts.put(conversationName, 0);

            conversationHistory.put(conversationName, "");
        }

        scrollPane.setContent(chatBox);
        updateDateTimeLabel();
        updateStatsLabel();

        // MODIFICATION : On affiche les infos de la conversation
        dateTimeLabel.setVisible(true);
        statsLabel.setVisible(true);
        summarizeBtn.setVisible(true);

        // On cache le bouton Clé API
        changeApiKeyBtn.setVisible(false);

        applyTheme();
        scrollToBottom();
    }

    private void applyRename(String oldName, String newName) {
        if (newName == null || newName.isBlank() || newName.equals(oldName))
            return;
        if (conversations.containsKey(newName))
            return;

        int index = sidebar.getConversationItems().indexOf(oldName);
        if (index >= 0)
            sidebar.getConversationItems().set(index, newName);

        VBox conv = conversations.remove(oldName);
        LocalDateTime date = conversationDates.remove(oldName);
        String history = conversationHistory.remove(oldName);
        Long startTime = conversationStartTimes.remove(oldName);
        Integer msgCount = conversationMessageCounts.remove(oldName);

        if (conv != null)
            conversations.put(newName, conv);
        if (date != null)
            conversationDates.put(newName, date);
        if (history != null)
            conversationHistory.put(newName, history);
        if (startTime != null)
            conversationStartTimes.put(newName, startTime);
        if (msgCount != null)
            conversationMessageCounts.put(newName, msgCount);

        controller.renameConversation(oldName, newName, date, history, null, msgCount != null ? msgCount : 0);

        if (currentConversation.equals(oldName))
            currentConversation = newName;
    }

    private void renameConversation(String oldName) {
        final int MAX_NAME_LEN = 40;

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Renommer la conversation");
        dialog.setHeaderText("Entrez un nouveau nom pour cette conversation :");

        ButtonType okButtonType = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField(oldName);
        nameField.setPromptText("Nom");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 11px;");
        errorLabel.setWrapText(true);

        VBox content = new VBox(8);
        content.getChildren().addAll(new Label("Nom :"), nameField, errorLabel);
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okButtonType);
        Runnable refreshValidation = () -> {
            String trimmed = nameField.getText() == null ? "" : nameField.getText().trim();
            boolean tooLong = trimmed.length() > MAX_NAME_LEN;
            boolean empty = trimmed.isEmpty();
            boolean unchanged = trimmed.equals(oldName);
            boolean duplicate = !empty && !unchanged && conversations.containsKey(trimmed);

            if (tooLong) {
                errorLabel.setText("Le nom est trop long. Maximum : " + MAX_NAME_LEN + " caractères.");
            } else if (duplicate) {
                errorLabel.setText("Une conversation porte déjà ce nom.");
            } else if (empty) {
                errorLabel.setText("Le nom ne peut pas être vide.");
            } else if (unchanged) {
                errorLabel.setText("Modifiez le nom avant de valider.");
            } else {
                errorLabel.setText("");
            }

            okButton.setDisable(tooLong || empty || unchanged || duplicate);
        };

        nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidation.run());
        refreshValidation.run();

        dialog.setResultConverter(button -> {
            if (button == okButtonType) {
                String trimmed = nameField.getText() == null ? "" : nameField.getText().trim();
                return trimmed;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.isEmpty() && !newName.equals(oldName)) {
                int index = sidebar.getConversationItems().indexOf(oldName);
                if (index >= 0) {
                    sidebar.getConversationItems().set(index, newName);
                }

                VBox conv = conversations.remove(oldName);
                LocalDateTime date = conversationDates.remove(oldName);
                String history = conversationHistory.remove(oldName);
                Long startTime = conversationStartTimes.remove(oldName);
                Integer msgCount = conversationMessageCounts.remove(oldName);

                if (conv != null) {
                    conversations.put(newName, conv);
                }
                if (date != null) {
                    conversationDates.put(newName, date);
                }
                if (history != null) {
                    conversationHistory.put(newName, history);
                }
                if (startTime != null) {
                    conversationStartTimes.put(newName, startTime);
                }
                if (msgCount != null) {
                    conversationMessageCounts.put(newName, msgCount);
                }

                controller.renameConversation(oldName, newName, date, history,
                        null, msgCount != null ? msgCount : 0);

                if (currentConversation.equals(oldName)) {
                    currentConversation = newName;
                }

                sidebar.refresh();
            }
        });
    }

    private void deleteConversation(String conversationName) {

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Supprimer la conversation");
        confirmation.setHeaderText("Êtes-vous sûr de vouloir supprimer cette conversation ?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {

                sidebar.getConversationItems().remove(conversationName);

                conversations.remove(conversationName);
                conversationDates.remove(conversationName);
                conversationHistory.remove(conversationName);

                controller.deleteConversation(conversationName);

                if (currentConversation.equals(conversationName)) {
                    if (!sidebar.getConversationItems().isEmpty()) {
                        String newConv = sidebar.getConversationItems().get(0);
                        switchConversation(newConv);
                    } else {

                        currentConversation = null;
                        showWelcomeScreenEmptyHistory();
                        root.setBottom(null);
                    }
                }

                sidebar.refresh();
            }
        });
    }

    private void updateDateTimeLabel() {
        LocalDateTime date = conversationDates.get(currentConversation);
        if (date != null && currentConversation != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            dateTimeLabel.setText("Créée le : " + date.format(formatter));
        } else {
            dateTimeLabel.setText("");
        }
    }

    private static final String[] QUICK_QUESTIONS = {
            "Quelles sont les matières de la L3 MIAGE ?",
            "Quelles sont les matières du M1 IBI ?",
            "Quelles sont les matières du M1 SBI ?",
            "Quelles sont les matières du M2 IBI ?",
            "Quelles sont les matières du M2 SBI ?",
            "Quelle est la différence entre IBI et SBI ?",
            "Comment intégrer la MIAGE Paris 1 ?",
            "Quels sont les critères d'admission en MIAGE ?",
            "Peut-on entrer directement en M2 MIAGE ?",
            "Quand ouvrent les candidatures ?",
            "Comment fonctionne l'alternance en MIAGE ?",
            "Quel est le salaire d'un alternant MIAGE ?",
            "Qui paie les frais de scolarité en alternance ?",
            "Peut-on faire la MIAGE sans alternance ?",
            "Quel salaire après la MIAGE ?",
            "Quels métiers après la MIAGE ?",
            "La MIAGE est-elle reconnue par l'État ?",
            "La MIAGE prépare-t-elle à des certifications ?",
            "Peut-on partir en Erasmus en MIAGE ?",
            "Quelle est l'adresse du campus ?",
            "Y a-t-il une cantine au campus PMF ?",
            "Que signifie l'acronyme MIAGE ?",
            "Qu'est-ce que l'AMS ?",
            "Qui sont les membres du bureau AMS ?",
            "Y a-t-il un système de parrainage ?",
            "Qu'est-ce que MIAGE Connection ?",
            "Y a-t-il de l'IA en M1 MIAGE ?",
            "Y a-t-il de la blockchain en MIAGE ?",
            "Quels langages de programmation sont enseignés ?",
            "Y a-t-il beaucoup de projets de groupe ?"
    };

    private void sendQuickQuestion(String question) {
        onNewChat();
        inputField.setText(question);
        handleSendMessage();
    }

    private void showWelcomeScreen() {
        String bgColor = isDarkMode ? "rgba(8, 107, 174)" : "#FFFFFF";
        String textColor = isDarkMode ? "white" : "#1F2937";
        String secondaryColor = isDarkMode ? "#8E8EA0" : "#4B5563";

        welcomeBox = new VBox(25);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setPadding(new Insets(60, 100, 60, 100));
        welcomeBox.setStyle("-fx-background-color: " + bgColor + ";");

        Label title = new Label("Bienvenue sur MiageGPT");
        title.setFont(Font.font("System", FontWeight.BOLD, 48));
        title.setStyle("-fx-text-fill: " + textColor + ";");

        Label subtitle = new Label("Ton assistant dédié à la MIAGE et à l'AMS");
        subtitle.setFont(Font.font("System", 26));
        subtitle.setStyle("-fx-text-fill: " + secondaryColor + ";");
        subtitle.setUserData("secondary");

        javafx.scene.text.Text icon = new javafx.scene.text.Text("💬");
        icon.setFont(Font.font("System", 100));
        icon.setFill(isDarkMode ? javafx.scene.paint.Color.WHITE : javafx.scene.paint.Color.web("#111827"));

        Label questionsLabel = new Label("Commence par une question :");
        questionsLabel.setFont(Font.font("System", 15));
        questionsLabel.setStyle("-fx-text-fill: " + secondaryColor + ";");
        questionsLabel.setUserData("secondary");

        javafx.scene.layout.FlowPane quickPane = new javafx.scene.layout.FlowPane(10, 10);
        quickPane.setAlignment(Pos.CENTER);
        quickPane.setMaxWidth(720);

        java.util.List<String> pool = new java.util.ArrayList<>(java.util.Arrays.asList(QUICK_QUESTIONS));
        java.util.Collections.shuffle(pool);
        for (String q : pool.subList(0, 6)) {
            Button btn = new Button(q);
            btn.getStyleClass().add("quick-question-btn");
            btn.setOnAction(e -> sendQuickQuestion(q));
            addHoverScaleEffect(btn);
            quickPane.getChildren().add(btn);
        }

        welcomeBox.getChildren().addAll(icon, title, subtitle, questionsLabel, quickPane);
        scrollPane.setContent(welcomeBox);

        // MODIFICATION : On cache complètement ces éléments
        dateTimeLabel.setVisible(false);
        statsLabel.setVisible(false);
        summarizeBtn.setVisible(false);

        // On affiche le bouton Clé API
        changeApiKeyBtn.setVisible(true);
    }

    private void openApiKeyConfigWindow() {
        primaryStage.hide();

        String newKey = ChatApp.showApiKeyDialog(primaryStage);

        if (newKey != null) {
            // Nouvelle clé valide → on met à jour et on réaffiche
            Config.setGROQ_API_KEY(newKey);
            controller.reinitializeGroqService();
        }
        // Dans tous les cas (annulé ou nouvelle clé), on réaffiche MiageGPT
        primaryStage.show();
    }

    private void showWelcomeScreenEmptyHistory() {

        showWelcomeScreen();
        sidebar.getConversationItems().clear();
        sidebar.refresh();
    }

    private void loadWindowPreferences(javafx.stage.Stage stage) {
        try {
            java.io.File f = new java.io.File(com.miage.miagegpt.service.PathResolver.getDataDir(),
                    "config.properties");
            if (!f.exists())
                return;

            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                props.load(fis);
            }
            if (Boolean.parseBoolean(props.getProperty("window_maximized", "false"))) {
                stage.setMaximized(true);
            }
            isDarkMode = Boolean.parseBoolean(props.getProperty("dark_mode", "false"));
            applyTheme();
            updateThemeButtonLabel();
        } catch (Exception ignored) {
        }
    }

    private void saveWindowPreferences(javafx.stage.Stage stage) {
        try {
            java.io.File f = new java.io.File(com.miage.miagegpt.service.PathResolver.getDataDir(),
                    "config.properties");
            java.util.Properties props = new java.util.Properties();
            if (f.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    props.load(fis);
                }
            }
            props.setProperty("window_maximized", String.valueOf(stage.isMaximized()));
            props.setProperty("dark_mode", String.valueOf(isDarkMode));
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                props.store(fos, "MiageGPT local configuration");
            }
        } catch (Exception ignored) {
        }
    }

    private void attachScrollListener(VBox box) {
        box.heightProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue(1.0));
    }

    private void scrollToBottom() {
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

    private void showTypingIndicator() {
        javafx.application.Platform.runLater(() -> {

            if (sendBtn != null) {
                double baseRadius = sendBtn.getWidth() / 2.0 + 2;
                javafx.scene.shape.Circle borderCircle = new javafx.scene.shape.Circle(baseRadius);
                borderCircle.setStroke(javafx.scene.paint.Color.web("#2563EB"));
                borderCircle.setStrokeWidth(2);
                borderCircle.setFill(null);
                borderCircle.setManaged(false);
                borderCircle.setMouseTransparent(true);
                borderCircle.setLayoutX(sendBtn.getLayoutX() + sendBtn.getWidth() / 2.0);
                borderCircle.setLayoutY(sendBtn.getLayoutY() + sendBtn.getHeight() / 2.0);
                if (sendBtn.getParent() instanceof HBox) {
                    HBox parentBox = (HBox) sendBtn.getParent();
                    if (!parentBox.getChildren().contains(borderCircle)) {
                        parentBox.getChildren().add(borderCircle);
                    }
                    borderCircle.toFront();
                    sendBtn.toFront();
                }

                javafx.animation.RotateTransition rotate = new javafx.animation.RotateTransition(
                        javafx.util.Duration.seconds(0.6), borderCircle);
                rotate.setByAngle(360);
                rotate.setCycleCount(javafx.animation.Animation.INDEFINITE);
                rotate.setInterpolator(javafx.animation.Interpolator.LINEAR);
                rotate.play();

                javafx.animation.ScaleTransition pulse = new javafx.animation.ScaleTransition(
                        javafx.util.Duration.seconds(0.8), borderCircle);
                pulse.setFromY(0.9);
                pulse.setToY(1.2);
                pulse.setAutoReverse(true);
                pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
                pulse.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);

                javafx.animation.ParallelTransition parallelTransition = new javafx.animation.ParallelTransition(rotate,
                        pulse);
                parallelTransition.play();

                sendBtn.setUserData(parallelTransition);
                sendBtn.getProperties().put("borderCircle", borderCircle);
            }

            HBox messageRow = new HBox();
            messageRow.setAlignment(Pos.CENTER_LEFT);
            messageRow.setPadding(new Insets(10, 0, 10, 0));

            String dotColor = "#10A37F";

            VBox messageBox = new VBox(5);
            messageBox.setMaxWidth(700);
            messageBox.getStyleClass().add("message-box");

            Label authorLabel = new Label("MiageGPT");
            authorLabel.getStyleClass().add("ai-author");

            HBox typingBox = new HBox(5);
            typingBox.setAlignment(Pos.CENTER_LEFT);

            Label dot1 = new Label("â—");
            dot1.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 10px;");
            Label dot2 = new Label("â—");
            dot2.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.6;");
            Label dot3 = new Label("â—");
            dot3.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.3;");

            typingBox.getChildren().addAll(dot1, dot2, dot3);

            messageBox.getChildren().addAll(authorLabel, typingBox);
            messageRow.getChildren().add(messageBox);

            typingMessageRow = messageRow;
            chatBox.getChildren().add(messageRow);
            scrollToBottom();

            typingTimeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(
                            javafx.util.Duration.millis(300),
                            e -> {
                                dot1.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.3;");
                                dot2.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 1.0;");
                                dot3.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.6;");
                            }),
                    new javafx.animation.KeyFrame(
                            javafx.util.Duration.millis(600),
                            e -> {
                                dot1.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 1.0;");
                                dot2.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.3;");
                                dot3.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.6;");
                            }),
                    new javafx.animation.KeyFrame(
                            javafx.util.Duration.millis(900),
                            e -> {
                                dot1.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.3;");
                                dot2.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 0.6;");
                                dot3.setStyle(
                                        "-fx-text-fill: " + dotColor + "; -fx-font-size: 10px; -fx-opacity: 1.0;");
                            }));
            typingTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
            typingTimeline.play();
        });
    }

    private void updateStatsLabel() {
        if (currentConversation == null || statsLabel == null) {
            return;
        }

        int messageCount = conversationMessageCounts.getOrDefault(currentConversation, 0);

        String statsText = "📊 " + messageCount +
                " message" + (messageCount > 1 ? "s" : "");
        statsLabel.setText(statsText);
    }

    private void summarizeConversation() {
        if (currentConversation == null) {
            return;
        }

        String history = conversationHistory.getOrDefault(currentConversation, "");
        if (history.isEmpty()) {
            addAIMessage("Il n'y a pas assez de contenu pour résumer.");
            return;
        }

        String summaryPrompt = "Résume brièvement la conversation suivante en 2-3 phrases:\n\n" + history;

        showTypingIndicator();
        new Thread(() -> {
            try {
                setConnectionStatus(true);
                String summary = controller.summarizeConversation(history, summaryPrompt);

                javafx.application.Platform.runLater(() -> {
                    hideTypingIndicator();
                    setConnectionStatus(true);

                    String newHistory = history;
                    if (!newHistory.isEmpty()) {
                        newHistory += "\n";
                    }
                    newHistory += "MiageGPT: " + summary;
                    conversationHistory.put(currentConversation, newHistory);
                    saveCurrentConversation();

                    addAIMessage("**Résumé:** " + summary);
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    hideTypingIndicator();
                    setConnectionStatus(false);
                    addAIMessage("Erreur lors du résumé: " + e.getMessage());
                });
            }
        }).start();
    }

    private StackPane createGradientButton(String text, Runnable action) {
        double width = 180;
        double height = 44;
        double cornerRadius = 14;
        double gradientW = width * 12;
        double gradientH = height * 12;

        StackPane buttonContainer = new StackPane();
        buttonContainer.setPrefSize(width, height);
        buttonContainer.setMaxSize(width, height);

        javafx.scene.shape.Rectangle gradientRect = new javafx.scene.shape.Rectangle(gradientW, gradientH);
        gradientRect.setFill(javafx.scene.paint.LinearGradient.valueOf(
                "from 0% 0% to 100% 100%, #7CE8FF 0%, #4CA4FF 20%, #7A6BFF 40%, #FF7EDB 60%, #FFD95E 80%, #7CE8FF 100%"));
        gradientRect.setMouseTransparent(true);
        gradientRect.setManaged(false);

        javafx.animation.RotateTransition rotate = new javafx.animation.RotateTransition(
                javafx.util.Duration.seconds(5), gradientRect);
        rotate.setByAngle(360);
        rotate.setCycleCount(javafx.animation.RotateTransition.INDEFINITE);
        rotate.setInterpolator(javafx.animation.Interpolator.LINEAR);
        rotate.play();

        javafx.scene.shape.Rectangle clipRect = new javafx.scene.shape.Rectangle(width, height);
        clipRect.setArcWidth(cornerRadius * 2);
        clipRect.setArcHeight(cornerRadius * 2);
        buttonContainer.setClip(clipRect);

        javafx.scene.shape.Rectangle innerBackground = new javafx.scene.shape.Rectangle(width - 4, height - 4);
        innerBackground.setArcWidth((cornerRadius - 2) * 2);
        innerBackground.setArcHeight((cornerRadius - 2) * 2);
        innerBackground.setId("innerBackground");
        innerBackground.setFill(javafx.scene.paint.Color.rgb(32, 33, 35, 1.0));

        java.util.function.BiConsumer<Double, Double> alignGradient = (currentWidth, currentHeight) -> {
            double alignedWidth = currentWidth != null && currentWidth > 0 ? currentWidth : width;
            double alignedHeight = currentHeight != null && currentHeight > 0 ? currentHeight : height;
            gradientRect.setTranslateX((alignedWidth - gradientW) / 2.0);
            gradientRect.setTranslateY((alignedHeight - gradientH) / 2.0);
            clipRect.setWidth(alignedWidth);
            clipRect.setHeight(alignedHeight);
            innerBackground.setWidth(Math.max(0, alignedWidth - 4));
            innerBackground.setHeight(Math.max(0, alignedHeight - 4));
        };

        buttonContainer.widthProperty().addListener(
                (obs, oldValue, newValue) -> alignGradient.accept(newValue.doubleValue(), buttonContainer.getHeight()));
        buttonContainer.heightProperty().addListener(
                (obs, oldValue, newValue) -> alignGradient.accept(buttonContainer.getWidth(), newValue.doubleValue()));
        alignGradient.accept(width, height);

        javafx.scene.control.Label label = new javafx.scene.control.Label(text);
        label.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 12));
        label.setId("btnLabel");
        label.setTextFill(javafx.scene.paint.Color.WHITE);

        buttonContainer.setAlignment(javafx.geometry.Pos.CENTER);
        buttonContainer.getChildren().addAll(gradientRect, innerBackground, label);
        buttonContainer.setOnMouseClicked(e -> action.run());

        javafx.animation.ScaleTransition scaleUp = new javafx.animation.ScaleTransition(
                javafx.util.Duration.millis(200), buttonContainer);
        scaleUp.setToX(1.05);
        scaleUp.setToY(1.05);

        javafx.animation.ScaleTransition scaleDown = new javafx.animation.ScaleTransition(
                javafx.util.Duration.millis(200), buttonContainer);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        buttonContainer.setOnMouseEntered(e -> {
            scaleUp.play();
            buttonContainer.setCursor(javafx.scene.Cursor.HAND);
        });
        buttonContainer.setOnMouseExited(e -> scaleDown.play());

        return buttonContainer;
    }

    private void addHoverScaleEffect(Button button) {
        javafx.animation.ScaleTransition scaleUp = new javafx.animation.ScaleTransition(
                javafx.util.Duration.millis(200), button);
        scaleUp.setToX(1.05);
        scaleUp.setToY(1.05);

        javafx.animation.ScaleTransition scaleDown = new javafx.animation.ScaleTransition(
                javafx.util.Duration.millis(200), button);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        button.setOnMouseEntered(e -> scaleUp.play());
        button.setOnMouseExited(e -> scaleDown.play());
    }

    private javafx.animation.Timeline loaderTimeline;

    private void startSendButtonLoader() {
        if (loaderTimeline != null) {
            loaderTimeline.stop();
        }

        String[] loaderStates = { "•", "◦", "▪", "▫", "◐", "◓", "◑", "◒", "➤", "➜" };
        final int[] index = { 0 };

        loaderTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                        javafx.util.Duration.millis(80),
                        e -> {
                            if (sendBtn != null) {
                                sendBtn.setText(loaderStates[index[0]]);
                                index[0] = (index[0] + 1) % loaderStates.length;
                            }
                        }));
        loaderTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        loaderTimeline.play();
    }

    private void stopSendButtonLoader() {
        if (loaderTimeline != null) {
            loaderTimeline.stop();
            loaderTimeline = null;
        }
        if (sendBtn != null) {
            sendBtn.setText("➤");
        }
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme();
        updateThemeButtonLabel();
    }

    private void updateThemeButtonLabel() {

        themeToggleBtn.setText(isDarkMode ? "🌙" : "☀");
    }

    private void applyTheme() {
        root.getStyleClass().setAll(isDarkMode ? "dark-mode" : "light-mode");

        String bg = isDarkMode ? "rgba(8, 107, 174)" : "#FFFFFF";
        scrollPane.setStyle("-fx-background: " + bg + "; -fx-background-color: " + bg + ";");
        chatBox.setStyle("-fx-background-color: " + bg + ";");

        sidebar.updateLogo(isDarkMode);
        sidebar.refresh();

        if (welcomeBox != null && scrollPane.getContent() == welcomeBox) {
            welcomeBox.setStyle("-fx-background-color: " + bg + ";");
            String textColor = isDarkMode ? "#ECECF1" : "#1F2937";
            String secondaryColor = isDarkMode ? "#c8c8c8" : "#4B5563";
            updateWelcomeBoxColors(welcomeBox, textColor, secondaryColor, isDarkMode);
        }

        if (summarizeBtn != null) {
            javafx.scene.shape.Rectangle innerBg = (javafx.scene.shape.Rectangle) summarizeBtn
                    .lookup("#innerBackground");
            javafx.scene.control.Label btnLabel = (javafx.scene.control.Label) summarizeBtn.lookup("#btnLabel");
            if (innerBg != null && btnLabel != null) {
                String btnBg = isDarkMode ? "#2A2B32" : "#E5E7EB";
                String btnText = isDarkMode ? "white" : "#111827";
                innerBg.setFill(javafx.scene.paint.Color.web(btnBg));
                btnLabel.setTextFill(javafx.scene.paint.Color.web(btnText));
            }
        }

        updateAllMarkdownFlows();
    }

    private void updateAllMarkdownFlows() {
        for (javafx.scene.Node messageNode : chatBox.getChildren()) {
            if (messageNode instanceof HBox) {
                HBox messageRow = (HBox) messageNode;
                for (javafx.scene.Node child : messageRow.getChildren()) {
                    if (child instanceof VBox) {
                        VBox messageBox = (VBox) child;
                        Object markdownSource = messageBox.getProperties().get("markdownSource");
                        Object textFlowObj = messageBox.getProperties().get("textFlow");
                        if (markdownSource instanceof String && textFlowObj instanceof javafx.scene.Node) {
                            VBox newContainer = MarkdownRenderer.renderContainer((String) markdownSource, isDarkMode);
                            int idx = messageBox.getChildren().indexOf(textFlowObj);
                            if (idx >= 0) {
                                messageBox.getChildren().set(idx, newContainer);
                            }
                            messageBox.getProperties().put("textFlow", newContainer);
                        }
                    }
                }
            }
        }
    }

    private void updateWelcomeBoxColors(VBox box, String textColor, String secondaryColor, boolean darkMode) {
        for (javafx.scene.Node child : box.getChildren()) {
            if (child instanceof Label) {
                Label label = (Label) child;
                String color = "secondary".equals(label.getUserData()) ? secondaryColor : textColor;
                label.setStyle("-fx-text-fill: " + color + ";");
            } else if (child instanceof javafx.scene.text.Text) {
                javafx.scene.text.Text text = (javafx.scene.text.Text) child;
                text.setFill(darkMode ? javafx.scene.paint.Color.WHITE : javafx.scene.paint.Color.web("#111827"));
            }
        }
    }

    private void handleSendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty())
            return;

        addUserMessage(text);
        inputField.clear();
        showTypingIndicator();

        // Nommage automatique sur le premier message
        int msgCount = conversationMessageCounts.getOrDefault(currentConversation, 0);
        if (msgCount == 1) {
            final String convToName = currentConversation;
            final String firstMsg = text;
            new Thread(() -> {
                String title = controller.generateConversationName(firstMsg);
                if (title != null && !title.isBlank()) {
                    javafx.application.Platform.runLater(() -> applyRename(convToName, title));
                }
            }).start();
        }

        javafx.application.Platform.runLater(() -> {
            startSendButtonLoader();

            currentMessageThread = new Thread(() -> {
                try {
                    String history = conversationHistory.getOrDefault(currentConversation, "");
                    APIResponse apiResponse = controller.sendMessageWithPing(text, history);
                    String botResponse = apiResponse.content;
                    long pingMs = apiResponse.pingMs;

                    javafx.application.Platform.runLater(() -> {
                        hideTypingIndicator();

                        lastPingValue = "Ping: " + pingMs + "ms";
                        sidebar.setPingText(lastPingValue);

                        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                                javafx.util.Duration.millis(500));
                        pause.setOnFinished(ev -> {
                            addAIMessage(botResponse, text, history);
                            conversationHistory.put(currentConversation,
                                    controller.buildTurn(history, text, botResponse));
                            saveCurrentConversation();
                        });
                        pause.play();
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        hideTypingIndicator();
                        stopSendButtonLoader();
                        addAIMessage("Erreur: " + e.getMessage());
                    });
                }
            });
            currentMessageThread.start();
        });
    }

    private void hideTypingIndicator() {

        if (sendBtn != null) {
            Object animationObj = sendBtn.getUserData();
            if (animationObj instanceof javafx.animation.ParallelTransition) {
                ((javafx.animation.ParallelTransition) animationObj).stop();
                sendBtn.setUserData(null);
            } else if (animationObj instanceof javafx.animation.RotateTransition) {

                ((javafx.animation.RotateTransition) animationObj).stop();
                sendBtn.setUserData(null);
            }
            Object circleObj = sendBtn.getProperties().get("borderCircle");
            if (circleObj instanceof javafx.scene.shape.Circle) {
                javafx.scene.shape.Circle borderCircle = (javafx.scene.shape.Circle) circleObj;
                if (sendBtn.getParent() instanceof HBox) {
                    HBox parentBox = (HBox) sendBtn.getParent();
                    parentBox.getChildren().remove(borderCircle);
                }
                sendBtn.getProperties().remove("borderCircle");
            }
            sendBtn.setStyle("");

            applySendHandler(this::handleSendMessage);
        }

        if (typingTimeline != null) {
            typingTimeline.stop();
            typingTimeline = null;
        }
        if (typingMessageRow != null) {
            chatBox.getChildren().remove(typingMessageRow);
            typingMessageRow = null;
        }

        stopMessageDisplay = true;
        if (currentMessageThread != null) {
            currentMessageThread.interrupt();
            currentMessageThread = null;
        }
    }

    private void setConnectionStatus(boolean connected) {
        sidebar.setConnectionStatus(connected);
    }

    private void loadSavedConversations() {
        List<ConversationManager.ConversationData> savedConversations = controller.loadConversations();

        for (ConversationManager.ConversationData data : savedConversations) {

            VBox convBox = rebuildConversationUI(data.history);

            conversations.put(data.name, convBox);
            conversationDates.put(data.name, data.date != null ? data.date : LocalDateTime.now());
            conversationHistory.put(data.name, data.history != null ? data.history : "");
            conversationMessageCounts.put(data.name, data.messageCount);

            long startTime = data.date != null
                    ? data.date.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            conversationStartTimes.put(data.name, startTime);

            sidebar.getConversationItems().add(data.name);
        }

    }

    private VBox rebuildConversationUI(String history) {
        VBox convBox = new VBox(20);
        convBox.setPadding(new Insets(20));
        convBox.getStyleClass().add("chat-box");

        if (history == null || history.trim().isEmpty()) {
            return convBox;
        }

        List<String[]> messages = controller.parseHistory(history);
        for (String[] msg : messages) {
            if ("user".equals(msg[0])) {
                convBox.getChildren().add(MessageBubble.staticUser(msg[1]));
            } else if ("assistant".equals(msg[0])) {
                convBox.getChildren().add(MessageBubble.staticAI(msg[1], isDarkMode));
            }
        }

        return convBox;
    }

    private void saveCurrentConversation() {
        if (currentConversation == null)
            return;

        controller.saveConversation(
                currentConversation,
                conversationDates.get(currentConversation),
                conversationHistory.getOrDefault(currentConversation, ""),
                null,
                conversationMessageCounts.getOrDefault(currentConversation, 0));
    }

    private void saveAllConversations() {
        for (String name : conversations.keySet()) {
            controller.saveConversation(
                    name,
                    conversationDates.get(name),
                    conversationHistory.getOrDefault(name, ""),
                    null,
                    conversationMessageCounts.getOrDefault(name, 0));
        }
    }
}
