package com.miage.miagegpt.service;

import com.miage.miagegpt.model.DatabaseManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class GroqAPIService {
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private String apiKey;
    private static final int TIMEOUT = 30000;
    private QuestionAnalyzer questionAnalyzer;

    static {
        try {
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
        } catch (Exception ignored) {}
    }

    public GroqAPIService(String apiKey) {
        this.apiKey = apiKey;
        this.questionAnalyzer = new QuestionAnalyzer(DatabaseManager.getInstance());
    }

    public String getResponseWithHistory(String question, String conversationHistory) {
        try {
            String schemaSummary = DatabaseManager.getInstance().getSchemaSummary();
            java.util.List<String> tables = selectTablesForQuestion(schemaSummary, question);

            String dbContext = buildSelectedTablesContext(tables);
            if (dbContext == null || dbContext.isBlank()) {
                dbContext = questionAnalyzer.analyzeAndSearch(question);
            }

            String systemPrompt = questionAnalyzer.buildSystemPrompt(dbContext);
            String jsonBody = createJsonRequest(systemPrompt, conversationHistory, question);
            APIResponse response = sendRequest(jsonBody);
            return response.content;
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }

    public APIResponse getResponseWithHistoryAndPing(String question, String conversationHistory) {
        try {
            String schemaSummary = DatabaseManager.getInstance().getSchemaSummary();
            java.util.List<String> tables = selectTablesForQuestion(schemaSummary, question);

            String dbContext = buildSelectedTablesContext(tables);
            if (dbContext == null || dbContext.isBlank()) {
                dbContext = questionAnalyzer.analyzeAndSearch(question);
            }

            String systemPrompt = questionAnalyzer.buildSystemPrompt(dbContext);
            String jsonBody = createJsonRequest(systemPrompt, conversationHistory, question);
            return sendRequest(jsonBody);
        } catch (Exception e) {
            return new APIResponse("Erreur : " + e.getMessage(), 0);
        }
    }

    private String createJsonRequestForTableSelection(String schemaSummary, String userQuestion) {
        String system = "Tu es un assistant qui aide à sélectionner les tables pertinentes d'une base de données.\n" +
                "Tu ne dois pas répondre à la question de l'utilisateur.\n" +
                "Réponds uniquement par un objet JSON valide avec la clé \"tables\" contenant une liste de 1 à 3 noms de tables pertinentes (en minuscules), par exemple: {\"tables\":[\"membres\",\"faq\"]}.\n" +
                "Ne fournis aucun texte hors du JSON.\n" +
                "Voici le schéma de la base (tables, colonnes,counts,samples) :\n" + escapeJson(schemaSummary) + "\n" +
                "Question de l'utilisateur: \"" + escapeJson(userQuestion) + "\"\n" +
                "Choisis entre 1 et 3 tables maximum.\n" +
                "Retourne uniquement le JSON requis.";

        StringBuilder messages = new StringBuilder();
        messages.append("{\"role\": \"system\", \"content\": \"").append(system).append("\"}");
        messages.append(",{\"role\": \"user\", \"content\": \"").append(escapeJson(userQuestion)).append("\"}");

        return "{" +
                "\"model\": \"llama-3.1-8b-instant\"," + //
                "\"messages\": [" + messages.toString() + "]," +
                "\"max_tokens\": 200," +
                "\"temperature\": 0.0," +
                "\"top_p\": 0.1" +
                "}";
    }

    private java.util.List<String> parseTableSelectionResponse(String text) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"tables\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String inside = m.group(1);
                java.util.regex.Pattern q = java.util.regex.Pattern.compile("\\\"([^\\\"]+)\\\"");
                java.util.regex.Matcher mm = q.matcher(inside);
                while (mm.find()) {
                    String t = mm.group(1).trim();
                    if (!t.isEmpty() && result.size() < 3) result.add(t);
                }
            }
        } catch (Exception e) {
        }

        return result;
    }

    private String buildSelectedTablesContext(java.util.List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (String tableName : tables) {
            DatabaseManager.TableInfo tableInfo = DatabaseManager.getInstance().getTableInfo(tableName);
            if (tableInfo == null) {
                continue;
            }

            sb.append("[").append(tableInfo.name.toUpperCase()).append("]\n");
            if (tableInfo.rowCount >= 0) {
                sb.append("Total rows: ").append(tableInfo.rowCount).append("\n");
            }
            if (tableInfo.fullContent != null && !tableInfo.fullContent.isBlank()) {
                sb.append(tableInfo.fullContent).append("\n");
            } else if (tableInfo.sample != null && !tableInfo.sample.isBlank()) {
                sb.append(tableInfo.sample).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private java.util.List<String> selectTablesForQuestion(String schemaSummary, String question) {
        try {
            String json = createJsonRequestForTableSelection(schemaSummary, question);
            APIResponse r = sendRequest(json);
            if (r == null || r.content == null) return java.util.Collections.emptyList();
            return parseTableSelectionResponse(r.content);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private static String escapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t",
                "\\t");
    }

    private String createJsonRequest(String systemPrompt, String conversationHistory, String userQuestion) {
        StringBuilder messages = new StringBuilder();

        messages.append("{\"role\": \"system\", \"content\": \"")
                .append(escapeJson(systemPrompt))
                .append("\"}");

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            String[] lines = conversationHistory.split("\n");
            StringBuilder currentContent = new StringBuilder();
            String currentRole = null;

            for (String line : lines) {
                if (line.startsWith("User: ")) {
                    if (currentRole != null && currentContent.length() > 0) {
                        messages.append(",{\"role\": \"")
                                .append(currentRole)
                                .append("\", \"content\": \"")
                                .append(escapeJson(currentContent.toString().trim()))
                                .append("\"}");
                    }
                    currentRole = "user";
                    currentContent = new StringBuilder(line.substring(6));
                } else if (line.startsWith("MiageGPT: ")) {
                    if (currentRole != null && currentContent.length() > 0) {
                        messages.append(",{\"role\": \"")
                                .append(currentRole)
                                .append("\", \"content\": \"")
                                .append(escapeJson(currentContent.toString().trim()))
                                .append("\"}");
                    }
                    currentRole = "assistant";
                    currentContent = new StringBuilder(line.substring(10));
                } else {
                    currentContent.append("\n").append(line);
                }
            }
            if (currentRole != null && currentContent.length() > 0) {
                messages.append(",{\"role\": \"")
                        .append(currentRole)
                        .append("\", \"content\": \"")
                        .append(escapeJson(currentContent.toString().trim()))
                        .append("\"}");
            }
        }

        String wrappedQuestion = userQuestion;

        messages.append(",{\"role\": \"user\", \"content\": \"")
                .append(escapeJson(wrappedQuestion))
                .append("\"}");

        return "{" +
            "\"model\": \"llama-3.1-8b-instant\"," +
                "\"messages\": [" + messages.toString() + "]," +
                "\"max_tokens\": 1024," +
                "\"temperature\": 0.0," +
                "\"top_p\": 0.1" +
                "}";
    }

    private APIResponse sendRequest(String jsonBody) throws Exception {
        long startTime = System.currentTimeMillis();

        java.net.URLConnection urlConnection = new java.net.URI(API_URL).toURL().openConnection();
        HttpURLConnection connection = (HttpURLConnection) urlConnection;

        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorMessage = readStream(connection.getErrorStream());
                long pingMs = System.currentTimeMillis() - startTime;
                return new APIResponse("Erreur API (" + responseCode + "): " + errorMessage, pingMs);
            }

            String responseBody = readStream(connection.getInputStream());
            long pingMs = System.currentTimeMillis() - startTime;
            String content = parseResponse(responseBody);
            return new APIResponse(content, pingMs);

        } finally {
            connection.disconnect();
        }
    }

    private String readStream(java.io.InputStream stream) throws Exception {
        if (stream == null)
            return "";
        try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private String parseResponse(String responseBody) {
        try {
            if (responseBody.contains("\"error\"")) {
                int errorStart = responseBody.indexOf("\"message\"");
                if (errorStart > 0) {
                    int start = responseBody.indexOf("\"", errorStart + 10);
                    int end = responseBody.indexOf("\"", start + 1);
                    if (start > 0 && end > start) {
                        return "Erreur API: " + responseBody.substring(start + 1, end);
                    }
                }
                return "Erreur API";
            }

            int choicesStart = responseBody.indexOf("\"choices\"");
            if (choicesStart < 0)
                return "Aucune réponse reçue";

            int contentStart = responseBody.indexOf("\"content\"", choicesStart);
            if (contentStart < 0)
                return "Aucune réponse reçue";

            int textStart = responseBody.indexOf("\"", contentStart + 10);
            if (textStart < 0)
                return "Aucune réponse reçue";

            int textEnd = textStart + 1;
            while (textEnd < responseBody.length()) {
                if (responseBody.charAt(textEnd) == '"' &&
                        responseBody.charAt(textEnd - 1) != '\\') {
                    break;
                }
                textEnd++;
            }

            String text = responseBody.substring(textStart + 1, textEnd);
            text = text.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            return text;

        } catch (Exception e) {
            return "Erreur parsing: " + e.getMessage();
        }
    }

    public boolean testConnection() {
        try {
            int code = testApiKeyStatus();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public int testApiKeyStatus() throws Exception {
        java.net.URLConnection urlConnection = new java.net.URI(API_URL).toURL().openConnection();
        HttpURLConnection connection = (HttpURLConnection) urlConnection;

        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setDoOutput(true);

            String testBody = "{\"model\": \"llama-3.1-8b-instant\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1,\"temperature\":0.0,\"top_p\":0.1}";

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = testBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            return responseCode;
        } finally {
            connection.disconnect();
        }
    }
}
