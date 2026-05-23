package com.miage.miagegpt.model;

import com.miage.miagegpt.service.PathResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ConversationManager {

    private static final String META_SEPARATOR = "---HISTORY---";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final File conversationsFolder;

    public ConversationManager() {
        this.conversationsFolder = findConversationsFolder();
    }

    private File findConversationsFolder() {
        File folder = new File(PathResolver.getDataDir(), "conversations");
        folder.mkdirs();
        return folder;
    }

    public void saveConversation(String name, LocalDateTime date, String history,
            String language, int messageCount) {
        String safeFileName = toSafeFileName(name) + ".conv";
        File file = new File(conversationsFolder, safeFileName);

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("name=" + name);
            writer.newLine();
            writer.write("date=" + (date != null ? date.format(DATE_FORMAT) : ""));
            writer.newLine();
            writer.write("messageCount=" + messageCount);
            writer.newLine();
            writer.write(META_SEPARATOR);
            writer.newLine();
            if (history != null && !history.isEmpty()) {
                writer.write(history);
            }
        } catch (IOException e) {
            System.err.println("[ConversationManager] Erreur sauvegarde '" + name + "': " + e.getMessage());
        }
    }

    public List<ConversationData> loadAll() {
        List<ConversationData> result = new ArrayList<>();
        File[] files = conversationsFolder.listFiles((dir, fname) -> fname.endsWith(".conv"));

        if (files == null)
            return result;

        for (File file : files) {
            try {
                ConversationData data = loadFromFile(file);
                if (data != null) {
                    result.add(data);
                }
            } catch (Exception e) {
                System.err.println(
                        "[ConversationManager] Erreur chargement '" + file.getName() + "': " + e.getMessage());
            }
        }

        result.sort((a, b) -> {
            if (a.date == null && b.date == null)
                return 0;
            if (a.date == null)
                return 1;
            if (b.date == null)
                return -1;
            return b.date.compareTo(a.date);
        });

        return result;
    }

    private ConversationData loadFromFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        int separatorIndex = content.indexOf(META_SEPARATOR);
        if (separatorIndex < 0)
            return null;

        String metaPart = content.substring(0, separatorIndex);
        String historyPart = content.substring(separatorIndex + META_SEPARATOR.length());

        if (historyPart.startsWith("\r\n")) {
            historyPart = historyPart.substring(2);
        } else if (historyPart.startsWith("\n")) {
            historyPart = historyPart.substring(1);
        }

        Map<String, String> meta = new HashMap<>();
        for (String line : metaPart.split("\\r?\\n")) {
            int eqIndex = line.indexOf('=');
            if (eqIndex > 0) {
                meta.put(line.substring(0, eqIndex).trim(), line.substring(eqIndex + 1).trim());
            }
        }

        ConversationData data = new ConversationData();
        data.name = meta.getOrDefault("name", file.getName().replace(".conv", ""));

        String dateStr = meta.getOrDefault("date", "");
        if (!dateStr.isEmpty()) {
            try {
                data.date = LocalDateTime.parse(dateStr, DATE_FORMAT);
            } catch (Exception e) {
                data.date = LocalDateTime.now();
            }
        }

        data.language = meta.getOrDefault("language", "\uD83C\uDF10 Détection...");

        try {
            data.messageCount = Integer.parseInt(meta.getOrDefault("messageCount", "0"));
        } catch (NumberFormatException e) {
            data.messageCount = 0;
        }

        data.history = historyPart;
        return data;
    }

    public void deleteConversation(String name) {
        String safeFileName = toSafeFileName(name) + ".conv";
        File file = new File(conversationsFolder, safeFileName);
        if (file.exists()) {
            file.delete();
        }
    }

    public void renameConversation(String oldName, String newName, LocalDateTime date,
            String history, String language, int messageCount) {
        deleteConversation(oldName);
        saveConversation(newName, date, history, language, messageCount);
    }

    public void exportConversationFile(String name, File destinationFile) throws IOException {
        String safeFileName = toSafeFileName(name) + ".conv";
        File sourceFile = new File(conversationsFolder, safeFileName);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Fichier source introuvable pour la conversation : " + name);
        }
        Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private String toSafeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    public static class ConversationData {
        public String name;
        public LocalDateTime date;
        public String history;
        public String language;
        public int messageCount;
    }
}
