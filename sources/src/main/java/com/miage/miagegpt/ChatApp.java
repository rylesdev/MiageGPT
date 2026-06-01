package com.miage.miagegpt;

import com.miage.miagegpt.controller.ChatController;
import com.miage.miagegpt.model.DatabaseManager;
import com.miage.miagegpt.service.APIKeyDialog;
import com.miage.miagegpt.service.Config;
import com.miage.miagegpt.view.ChatView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class ChatApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        if (!initializeAPIKey(primaryStage)) {
            Platform.exit();
            return;
        }

        DatabaseManager.getInstance().initializeAfterApiKeyValidation();

        ChatController controller = new ChatController();
        ChatView view = new ChatView(controller);
        view.initialize(primaryStage);
    }

    private boolean initializeAPIKey(Stage owner) {
        APIKeyDialog dialog = new APIKeyDialog(owner);
        String apiKey;

        if (Config.isApiKeyConfigured()) {
            String currentKey = Config.getGROQ_API_KEY();
            apiKey = dialog.showUpdateDialog(currentKey);
        } else {
            apiKey = dialog.showFirstTimeSetup();
        }

        if (apiKey != null) {
            Config.setGROQ_API_KEY(apiKey);
            return true;
        }

        return false;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
