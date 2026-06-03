package com.miage.miagegpt.controller;

import com.miage.miagegpt.model.ConversationManager;
import com.miage.miagegpt.model.DatabaseManager;
import com.miage.miagegpt.service.APIResponse;
import com.miage.miagegpt.service.Config;
import com.miage.miagegpt.service.GroqAPIService;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

public class ChatController {

    private GroqAPIService groqService;
    private final ConversationManager conversationManager;

    public ChatController() {

        DatabaseManager.getInstance();

        this.groqService = new GroqAPIService(Config.getGROQ_API_KEY());
        this.conversationManager = new ConversationManager();
    }

    public APIResponse sendMessageWithPing(String text, String history) {
        return groqService.getResponseWithHistoryAndPing(text, history);
    }

    public String sendMessage(String text, String history) {
        return groqService.getResponseWithHistory(text, history);
    }

    public String summarizeConversation(String history, String prompt) {
        return groqService.getSummaryResponse(history, prompt);
    }

    public String generateConversationName(String firstMessage) {
        return groqService.generateTitle(firstMessage);
    }

    public boolean testConnection() {
        return groqService.testConnection();
    }

    public List<ConversationManager.ConversationData> loadConversations() {
        return conversationManager.loadAll();
    }

    public void saveConversation(String name, LocalDateTime date, String history,
            String language, int messageCount) {
        conversationManager.saveConversation(name, date, history, language, messageCount);
    }

    public void deleteConversation(String name) {
        conversationManager.deleteConversation(name);
    }

    public void renameConversation(String oldName, String newName, LocalDateTime date,
            String history, String language, int messageCount) {
        conversationManager.renameConversation(oldName, newName, date, history, language, messageCount);
    }

    public String buildTurn(String historyBefore, String userPrompt, String assistantResponse) {
        StringBuilder sb = new StringBuilder();
        if (historyBefore != null && !historyBefore.isEmpty()) {
            sb.append(historyBefore).append("\n");
        }
        sb.append("User: ").append(userPrompt).append("\nMiageGPT: ").append(assistantResponse);
        return sb.toString();
    }

    public List<String[]> parseHistory(String history) {
        List<String[]> messages = new ArrayList<>();
        if (history == null || history.isEmpty())
            return messages;

        String[] lines = history.split("\\n");
        String currentRole = null;
        StringBuilder currentMessage = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("User: ")) {
                if (currentRole != null) {
                    messages.add(new String[] { currentRole, currentMessage.toString() });
                }
                currentRole = "user";
                currentMessage = new StringBuilder(line.substring(6));
            } else if (line.startsWith("MiageGPT: ")) {
                if (currentRole != null) {
                    messages.add(new String[] { currentRole, currentMessage.toString() });
                }
                currentRole = "assistant";
                currentMessage = new StringBuilder(line.substring(10));
            } else {
                if (currentRole != null) {
                    currentMessage.append("\n").append(line);
                }
            }
        }
        if (currentRole != null) {
            messages.add(new String[] { currentRole, currentMessage.toString() });
        }
        return messages;
    }

    /**
     * Réinitialise le service Groq avec la nouvelle clé API enregistrée dans Config.
     */
    public void reinitializeGroqService() {
        groqService.updateApiKey(Config.getGROQ_API_KEY());
    }
}
