package com.miage.miagegpt.service;

import com.miage.miagegpt.model.DatabaseManager;

public class QuestionAnalyzer {

    private final DatabaseManager db;

    public QuestionAnalyzer(DatabaseManager db) {
        this.db = db;
    }

    public String analyzeAndSearch(String question) {
        return db != null ? db.getAllData() : null;
    }

    public String buildSystemPrompt(String dbContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Tu es MiageGPT, le chatbot officiel de l'association AMS (Association MIAGE Sorbonne) et de la filière MIAGE.\n");
        prompt.append("Réponds simplement, clairement et avec un ton naturel.\n");
        prompt.append("N'invente pas d'information.\n\n");

        if (dbContext != null && !dbContext.isEmpty()) {
            prompt.append("=== BASE DE DONNÉES ===\n");
            prompt.append(dbContext);
            prompt.append("\n=== FIN DE LA BASE DE DONNÉES ===\n\n");
            prompt.append("Réponds en t'appuyant sur ces données et sur la question de l'utilisateur.\n");
        }

        return prompt.toString();
    }
}