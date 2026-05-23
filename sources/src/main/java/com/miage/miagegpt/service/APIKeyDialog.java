package com.miage.miagegpt.service;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.net.URISyntaxException;

public class APIKeyDialog {

    private String resultApiKey = null;
    private final Stage stage;

    public APIKeyDialog(Stage owner) {
        this.stage = new Stage();
        this.stage.initOwner(owner);
        this.stage.getIcons().add(new Image(getClass().getResourceAsStream("/icon_texte.png")));
        this.stage.setTitle("Configuration de la clé API GROQ");
        this.stage.setWidth(500);
        this.stage.setHeight(300);
        this.stage.setResizable(false);
    }

    public String showFirstTimeSetup() {
        return showDialog(true);
    }

    public String showUpdateDialog(String currentKey) {
        return showDialog(false, currentKey);
    }

    private String showDialog(boolean isFirstTime, String... currentKey) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-font-size: 12;");

        Label titleLabel = new Label(isFirstTime ? "Configuration de la clé API GROQ" : "Modifier la clé API GROQ");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        Label instructionLabel = new Label(
                isFirstTime
                        ? "Vous devez configurer une clé API GROQ pour utiliser cette application."
                        : "Voulez-vous utiliser la même clé API ou en utiliser une nouvelle ?\n\n");
        instructionLabel.setWrapText(false);

        Hyperlink groqKeysLink = new Hyperlink("https://console.groq.com/keys");
        groqKeysLink.setOnAction(e -> openUrl("https://console.groq.com/keys"));
        groqKeysLink.setVisible(true);
        groqKeysLink.setManaged(true);
        groqKeysLink.setFocusTraversable(false);

        VBox instructionBox = new VBox(4);
        instructionBox.getChildren().addAll(
                instructionLabel,
                new Label("Créez une clé sur :"),
                groqKeysLink);

        if (!isFirstTime && currentKey.length > 0 && currentKey[0] != null) {
            Label currentKeyLabel = new Label("Clé actuelle: " + maskApiKey(currentKey[0]));
            currentKeyLabel.setStyle("-fx-text-fill: #666;");
            root.getChildren().add(currentKeyLabel);
        }

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Entrez votre clé API (commence par 'gsk_')");
        passwordField.setPrefHeight(40);

        CheckBox showPasswordCheckBox = new CheckBox("Afficher la clé");
        TextField textField = new TextField();
        textField.setPromptText("Entrez votre clé API (commence par 'gsk_')");
        textField.setPrefHeight(40);
        textField.setVisible(false);
        textField.setManaged(false);

        showPasswordCheckBox.setOnAction(e -> {
            if (showPasswordCheckBox.isSelected()) {
                textField.setText(passwordField.getText());
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                textField.setVisible(true);
                textField.setManaged(true);
            } else {
                passwordField.setText(textField.getText());
                textField.setVisible(false);
                textField.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
            }
        });

        Button confirmButton = new Button(isFirstTime ? "Confirmer" : "Utiliser cette clé");
        Button keepButton = null;
        Button cancelButton = new Button("Annuler");

        confirmButton.setPrefWidth(120);
        confirmButton.setStyle("-fx-font-size: 12; -fx-padding: 8;");
        cancelButton.setPrefWidth(120);
        cancelButton.setStyle("-fx-font-size: 12; -fx-padding: 8;");

        confirmButton.setOnAction(e -> {
            String input = showPasswordCheckBox.isSelected() ? textField.getText() : passwordField.getText();
            if (validateApiKey(input)) {
                try {
                    GroqAPIService service = new GroqAPIService(input);
                    int status = service.testApiKeyStatus();
                    if (status == 200) {
                        resultApiKey = input;
                        stage.close();
                    } else if (status == 401) {
                        showError("Clé refusée par l'API (401). Vérifiez votre clé.");
                    }
                } catch (Exception ex) {
                    showError("Erreur lors du test de la clé API.");
                }
            } else {
                showError("Clé API invalide. Elle doit commencer par 'gsk_'.");
            }
        });

        cancelButton.setOnAction(e -> {
            resultApiKey = null;
            stage.close();
        });

        HBox passwordBox = new HBox(10);
        passwordBox.getChildren().addAll(
                passwordField,
                textField,
                showPasswordCheckBox);
        HBox.setHgrow(passwordField, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(textField, javafx.scene.layout.Priority.ALWAYS);

        root.getChildren().addAll(
                titleLabel,
                instructionBox,
                passwordBox);

        HBox buttonBox = new HBox(10);
        if (isFirstTime) {
            buttonBox.setPadding(new Insets(15, 0, 0, 0));
        } else {
            buttonBox.setPadding(new Insets(15, 0, 10, 0));
        }
        buttonBox.setStyle("-fx-alignment: center-right;");

        if (!isFirstTime && currentKey.length > 0 && currentKey[0] != null) {
            keepButton = new Button("Garder la clé actuelle");
            keepButton.setPrefWidth(150);
            keepButton.setStyle("-fx-font-size: 12; -fx-padding: 8;");
            keepButton.setOnAction(e -> {
                try {
                    GroqAPIService service = new GroqAPIService(currentKey[0]);
                    int status = service.testApiKeyStatus();
                    if (status == 200) {
                        resultApiKey = currentKey[0];
                        stage.close();
                    } else if (status == 401) {
                        showError("Clé actuelle refusée par l'API (401). Veuillez en fournir une autre.");
                    } else if (status <= 0) {
                        showError("Impossible de tester la clé actuelle: erreur réseau.");
                    } else {
                        showError("Erreur lors du test de la clé actuelle (code " + status + ").");
                    }
                } catch (Exception ex) {
                    showError("Erreur réseau lors du test de la clé: " + ex.getMessage());
                }
            });
            buttonBox.getChildren().addAll(keepButton, confirmButton, cancelButton);
        } else {
            buttonBox.getChildren().addAll(confirmButton, cancelButton);
        }

        root.getChildren().add(buttonBox);

        if (isFirstTime) {
            stage.setHeight(300);
        } else {
            stage.setHeight(340);
        }

        Scene scene = new Scene(root);
        
        final Button defaultButton = (keepButton != null) ? keepButton : confirmButton;
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                defaultButton.fire();
                event.consume();
            }
        });
        
        stage.setScene(scene);
        
        stage.setOnShown(event -> defaultButton.requestFocus());
        stage.showAndWait();

        return resultApiKey;
    }

    private boolean validateApiKey(String apiKey) {
        return apiKey != null && !apiKey.isEmpty() && apiKey.trim().startsWith("gsk_");
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur de validation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (URISyntaxException | java.io.IOException ex) {
            showError("Impossible d'ouvrir le lien : " + url);
        }
    }
}
