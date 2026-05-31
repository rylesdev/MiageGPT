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
        prompt.append("Réponds en français, de façon claire, précise, concise et utile.\n");
        prompt.append("Évite les répétitions, les détours inutiles et les réponses embrouillées.\n");
        prompt.append("Si la réponse est courte, donne une réponse courte. Si une liste aide, utilise une liste simple.\n");
        prompt.append("N'affiche jamais d'identifiant technique, notamment les colonnes 'id' de la base de données.\n");
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