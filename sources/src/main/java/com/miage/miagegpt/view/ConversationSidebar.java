package com.miage.miagegpt.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConversationSidebar {

    private ListView<String> historyList;
    private javafx.collections.ObservableList<String> allItems;
    private javafx.collections.transformation.FilteredList<String> filteredItems;
    private Label connectionIndicator;
    private Label pingLabel;
    private ImageView logoView;

    private final Runnable onNewChat;
    private final Consumer<String> onSwitchConversation;
    private final Consumer<String> onRenameConversation;
    private final Consumer<String> onDeleteConversation;
    private final Runnable onHome;
    private final Supplier<Boolean> isDarkMode;
    private final Supplier<String> activeConversation;

    public ConversationSidebar(
            Runnable onNewChat,
            Consumer<String> onSwitchConversation,
            Consumer<String> onRenameConversation,
            Consumer<String> onDeleteConversation,
            Runnable onHome,
            Supplier<Boolean> isDarkMode,
            Supplier<String> activeConversation) {
        this.onNewChat = onNewChat;
        this.onSwitchConversation = onSwitchConversation;
        this.onRenameConversation = onRenameConversation;
        this.onDeleteConversation = onDeleteConversation;
        this.onHome = onHome;
        this.isDarkMode = isDarkMode;
        this.activeConversation = activeConversation;
    }

    public VBox build() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setMinWidth(320);
        sidebar.setMaxWidth(320);
        sidebar.getStyleClass().add("sidebar");

        String logoPath = isDarkMode.get() ? "/icon_logo_sombre.png" : "/icon_logo_clair.png";
        logoView = new ImageView(new Image(ConversationSidebar.class.getResourceAsStream(logoPath)));
        logoView.setPreserveRatio(true);
        logoView.setFitWidth(240);
        logoView.setSmooth(true);

        StackPane logoContainer = new StackPane(logoView);
        logoContainer.setAlignment(Pos.CENTER);
        logoContainer.setMaxWidth(Double.MAX_VALUE);
        logoContainer.setPadding(new Insets(0, 0, 6, 0));

        Button newChatBtn = new Button("+ Nouvelle conversation");
        newChatBtn.setMaxWidth(Double.MAX_VALUE);
        newChatBtn.getStyleClass().add("new-chat-btn");
        newChatBtn.setOnAction(e -> onNewChat.run());
        addHoverEffect(newChatBtn);

        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.getStyleClass().add("sidebar-bar");
        topBar.getChildren().add(newChatBtn);
        HBox.setHgrow(newChatBtn, Priority.ALWAYS);

        Label connLabel = new Label("Connecté");
        connLabel.getStyleClass().add("connection-ok");
        connectionIndicator = connLabel;

        HBox connBox = new HBox(8);
        connBox.setAlignment(Pos.CENTER);
        connBox.getChildren().add(connLabel);

        pingLabel = new Label("Ping: --ms");
        pingLabel.getStyleClass().add("ping-label");

        HBox bottomBar = new HBox(15);
        bottomBar.setPadding(new Insets(10));
        bottomBar.getStyleClass().add("sidebar-bar");
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.getChildren().addAll(connBox, pingLabel);

        Button homeBtn = new Button("🏠 Page d'accueil");
        homeBtn.getStyleClass().add("home-btn");
        homeBtn.setOnAction(e -> onHome.run());
        addHoverEffect(homeBtn);

        Label historyLabel = new Label("Historique");
        historyLabel.getStyleClass().add("secondary-label");

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Rechercher...");
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.getStyleClass().add("input-field");
        searchField.setStyle("-fx-font-size: 12px; -fx-padding: 5 8;");

        allItems = javafx.collections.FXCollections.observableArrayList();
        filteredItems = new javafx.collections.transformation.FilteredList<>(allItems, s -> true);
        searchField.textProperty().addListener((obs, old, val) -> {
            String lower = val == null ? "" : val.toLowerCase().trim();
            filteredItems.setPredicate(lower.isEmpty() ? s -> true : s -> s.toLowerCase().contains(lower));
        });

        historyList = new ListView<>();
        historyList.setItems(filteredItems);
        historyList.getStyleClass().add("history-list");
        historyList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    setOnMouseEntered(null);
                    setOnMouseExited(null);
                    setOnMouseClicked(null);
                    setOnMousePressed(null);
                } else {
                    boolean dark = isDarkMode.get();
                    String textColor = dark ? "#ECECF1" : "#0F172A";
                    String hoverBg = dark ? "#2A2B32" : "#E1ECF4";
                    String activeBg = dark ? "#2A2B32" : "#D7E8F5";
                    String indicatorColor = dark ? "#19C37D" : "#0F766E";

                    HBox cellBox = new HBox(8);
                    cellBox.setAlignment(Pos.CENTER_LEFT);

                    HBox leftBox = new HBox(8);
                    leftBox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(leftBox, Priority.ALWAYS);

                    Label textLabel = new Label(item);
                    textLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 13px;");
                    textLabel.setMaxWidth(190);
                    textLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    HBox.setHgrow(textLabel, Priority.ALWAYS);

                    if (item.equals(activeConversation.get())) {
                        Label indicator = new Label("●");
                        indicator.setStyle("-fx-text-fill: " + indicatorColor + "; -fx-font-size: 10px;");
                        leftBox.getChildren().addAll(indicator, textLabel);
                        setStyle("-fx-background-color: " + activeBg + "; -fx-padding: 8 10; -fx-cursor: hand;");
                    } else {
                        leftBox.getChildren().add(textLabel);
                        setStyle("-fx-background-color: transparent; -fx-padding: 8 10; -fx-cursor: hand;");
                    }

                    cellBox.getChildren().add(leftBox);

                    HBox actionBox = new HBox(5);
                    actionBox.setAlignment(Pos.CENTER_RIGHT);
                    actionBox.setVisible(false);
                    actionBox.setManaged(true);

                    String baseStyle = "-fx-background-color: transparent; -fx-text-fill: #8E8EA0; -fx-font-size: 14px; -fx-padding: 2 6; -fx-cursor: hand;";
                    String renameHover = "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6; -fx-cursor: hand;";
                    String deleteHover = "-fx-background-color: transparent; -fx-text-fill: #FF6B6B; -fx-font-size: 14px; -fx-padding: 2 6; -fx-cursor: hand;";

                    Button renameBtn = new Button("✎");
                    renameBtn.setStyle(baseStyle);
                    renameBtn.setOnMouseEntered(e -> renameBtn.setStyle(renameHover));
                    renameBtn.setOnMouseExited(e -> renameBtn.setStyle(baseStyle));
                    renameBtn.setOnAction(e -> { e.consume(); onRenameConversation.accept(item); });
                    addHoverEffect(renameBtn);

                    Button deleteBtn = new Button("🗑");
                    deleteBtn.setStyle(baseStyle);
                    deleteBtn.setOnMouseEntered(e -> deleteBtn.setStyle(deleteHover));
                    deleteBtn.setOnMouseExited(e -> deleteBtn.setStyle(baseStyle));
                    deleteBtn.setOnAction(e -> { e.consume(); onDeleteConversation.accept(item); });
                    addHoverEffect(deleteBtn);

                    actionBox.getChildren().addAll(renameBtn, deleteBtn);
                    cellBox.getChildren().add(actionBox);

                    setGraphic(cellBox);
                    setText(null);

                    setOnMouseEntered(e -> {
                        actionBox.setVisible(true);
                        if (!item.equals(activeConversation.get())) {
                            setStyle("-fx-background-color: " + hoverBg + "; -fx-padding: 8 10; -fx-cursor: hand;");
                        }
                    });
                    setOnMouseExited(e -> {
                        actionBox.setVisible(false);
                        if (!item.equals(activeConversation.get())) {
                            setStyle("-fx-background-color: transparent; -fx-padding: 8 10; -fx-cursor: hand;");
                        }
                    });
                }
            }
        });

        historyList.setOnMouseClicked(e -> {
            String selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isEmpty() && e.getClickCount() == 1) {
                onSwitchConversation.accept(selected);
            }
        });

        HBox homeBtnContainer = new HBox();
        homeBtnContainer.setAlignment(Pos.CENTER);
        homeBtnContainer.getChildren().add(homeBtn);

        VBox.setVgrow(historyList, Priority.ALWAYS);
        sidebar.getChildren().addAll(logoContainer, homeBtnContainer, topBar, historyLabel, searchField, historyList, bottomBar);

        return sidebar;
    }

    public void setConnectionStatus(boolean connected) {
        if (connectionIndicator == null) return;
        if (connected) {
            connectionIndicator.setText("Connecté");
            connectionIndicator.getStyleClass().setAll("connection-ok");
        } else {
            connectionIndicator.setText("Déconnecté");
            connectionIndicator.getStyleClass().setAll("connection-ko");
        }
    }

    public void setPingText(String text) {
        if (pingLabel != null) pingLabel.setText(text);
    }

    public void refresh() {
        if (historyList != null) {
            historyList.refresh();
            String active = activeConversation.get();
            if (active != null) {
                historyList.getSelectionModel().select(active);
            }
        }
    }

    public void updateLogo(boolean darkMode) {
        if (logoView == null) return;
        String logoPath = darkMode ? "/icon_logo_sombre.png" : "/icon_logo_clair.png";
        try {
            logoView.setImage(new Image(ConversationSidebar.class.getResourceAsStream(logoPath)));
        } catch (Exception ignored) {
        }
    }

    public ListView<String> getHistoryList() {
        return historyList;
    }

    public javafx.collections.ObservableList<String> getConversationItems() {
        return allItems;
    }

    private static void addHoverEffect(Button button) {
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
}
