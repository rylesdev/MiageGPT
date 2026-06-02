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

    private final GroqAPIService groqService;
    private final ConversationManager conversationManager;

    public ChatController() {

        DatabaseManager.getInstance();

        this.groqService = new GroqAPIService(Config.getGROQ_API_KEY());
        this.conversationManager = new ConversationManager();
    }

    public APIResponse sendMessageWithPing(String text, String history) {
        APIResponse directAnswer = handleConversationHistoryQuestion(text, history);
        if (directAnswer != null) {
            return directAnswer;
        }
        return groqService.getResponseWithHistoryAndPing(text, history);
    }

    public String sendMessage(String text, String history) {
        APIResponse directAnswer = handleConversationHistoryQuestion(text, history);
        if (directAnswer != null) {
            return directAnswer.content;
        }
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

    public void exportConversation(String conversationName, File file) throws Exception {
        conversationManager.exportConversationFile(conversationName, file);
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
        if (history == null || history.isEmpty()) return messages;

        String[] lines = history.split("\\n");
        String currentRole = null;
        StringBuilder currentMessage = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("User: ")) {
                if (currentRole != null) {
                    messages.add(new String[]{currentRole, currentMessage.toString()});
                }
                currentRole = "user";
                currentMessage = new StringBuilder(line.substring(6));
            } else if (line.startsWith("MiageGPT: ")) {
                if (currentRole != null) {
                    messages.add(new String[]{currentRole, currentMessage.toString()});
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
            messages.add(new String[]{currentRole, currentMessage.toString()});
        }
        return messages;
    }

    private APIResponse handleConversationHistoryQuestion(String question, String history) {
        if (!isFirstMessageQuestion(question)) {
            return null;
        }

        List<String[]> messages = parseHistory(history);
        if (messages.size() > GroqAPIService.MAX_CONTEXT_MESSAGES) {
            return new APIResponse(
                    "Je ne peux pas retrouver ce message parce qu'il est hors de la fenêtre de contexte actuelle.",
                    0);
        }

        for (String[] message : messages) {
            if (message.length >= 2 && "user".equals(message[0]) && message[1] != null && !message[1].isBlank()) {
                String answer = "Ton premier message utilisateur était :\n\n\"" + message[1] + "\"";
                return new APIResponse(answer, 0);
            }
        }

        return new APIResponse("Je n'ai pas assez d'historique pour retrouver le premier message utilisateur.", 0);
    }

    private boolean isFirstMessageQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }

        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("premier message")
                || normalized.contains("première message")
                || normalized.contains("first message")
                || normalized.contains("what was my first message")
                || normalized.contains("quel est le premier message")
                || normalized.contains("quel était mon premier message");
    }
}
