package com.miage.miagegpt;

import com.miage.miagegpt.controller.ChatController;
import com.miage.miagegpt.model.DatabaseManager;
import com.miage.miagegpt.service.APIKeyDialog;
import com.miage.miagegpt.service.Config;
import com.miage.miagegpt.service.GroqAPIService;
import com.miage.miagegpt.view.ChatView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class ChatApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        if (Config.isApiKeyConfigured()) {
            // Clé déjà enregistrée : tester en arrière-plan
            String savedKey = Config.getGROQ_API_KEY();
            Thread bgThread = new Thread(() -> {
                boolean valid = false;
                try {
                    GroqAPIService service = new GroqAPIService(savedKey);
                    int status = service.testApiKeyStatus();
                    valid = (status == 200);
                } catch (Exception e) {
                    valid = false;
                }

                final boolean keyValid = valid;
                Platform.runLater(() -> {
                    if (keyValid) {
                        // Clé valide → on charge la BD et on ouvre MiageGPT directement
                        DatabaseManager.getInstance().initializeAfterApiKeyValidation();
                        openChatView(primaryStage, null);
                    } else {
                        // Clé invalide → fenêtre de config
                        String newKey = showApiKeyDialog(primaryStage);
                        if (newKey != null) {
                            Config.setGROQ_API_KEY(newKey);
                            DatabaseManager.getInstance().initializeAfterApiKeyValidation();
                            openChatView(primaryStage, null);
                        } else {
                            Platform.exit();
                        }
                    }
                });
            });
            bgThread.setDaemon(true);
            bgThread.start();
        } else {
            // Pas de clé → fenêtre de config
            String apiKey = showApiKeyDialog(primaryStage);
            if (apiKey != null) {
                Config.setGROQ_API_KEY(apiKey);
                DatabaseManager.getInstance().initializeAfterApiKeyValidation();
                openChatView(primaryStage, null);
            } else {
                Platform.exit();
            }
        }
    }

    /**
     * Ouvre la fenêtre de configuration de la clé API.
     * Retourne la nouvelle clé si validée, null si annulé.
     */
    public static String showApiKeyDialog(Stage owner) {
        APIKeyDialog dialog = new APIKeyDialog(owner);
        if (Config.isApiKeyConfigured()) {
            return dialog.showUpdateDialog(Config.getGROQ_API_KEY());
        } else {
            return dialog.showFirstTimeSetup();
        }
    }

    /**
     * Construit et affiche la ChatView.
     * Si chatViewHolder n'est pas null, on y stocke la référence (pour permettre
     * à l'écran d'accueil de rouvrir la fenêtre de config).
     */
    public static void openChatView(Stage primaryStage, ChatView[] chatViewHolder) {
        ChatController controller = new ChatController();
        ChatView view = new ChatView(controller);
        if (chatViewHolder != null) {
            chatViewHolder[0] = view;
        }
        view.initialize(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}