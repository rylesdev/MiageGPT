package com.miage.miagegpt.model;

import com.miage.miagegpt.service.PathResolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatabaseManager {

    private static final DatabaseSettings DB_SETTINGS = tryLoadDatabaseSettings();
    private static final String DB_URL = DB_SETTINGS != null ? DB_SETTINGS.url : null;
    private static final String DB_USER = DB_SETTINGS != null ? DB_SETTINGS.user : null;
    private static final String DB_PASSWORD = DB_SETTINGS != null ? DB_SETTINGS.password : null;

    private static DatabaseSettings tryLoadDatabaseSettings() {
        Properties properties = new Properties();

        File dataDir = PathResolver.getDataDir();
        File configFile = new File(dataDir, "database.properties");
        
        if (!configFile.exists()) {
            return null;
        }

        try (InputStream inputStream = new FileInputStream(configFile)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger MiageGPT-Data/database.properties", e);
        }

        String envUrl = System.getenv("NEON_DATABASE_URL");
        if (envUrl == null || envUrl.isEmpty()) envUrl = System.getenv("DATABASE_URL");
        String envUser = System.getenv("DATABASE_USER");
        String envPassword = System.getenv("DATABASE_PASSWORD");

        String url = firstNonEmpty(envUrl, properties.getProperty("database.url"));
        String user = firstNonEmpty(envUser, properties.getProperty("database.user"));
        String password = firstNonEmpty(envPassword, properties.getProperty("database.password"));

        if (url == null || url.isEmpty()) {
            System.err.println("[DB] URL de la base absente dans database.properties et variables d'environnement.");
            return null;
        }

        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            String protocol = url.startsWith("postgresql://") ? "postgresql://" : "postgres://";
            String withoutProto = url.substring(protocol.length());
            String[] parts = withoutProto.split("@", 2);
            String creds = parts[0];
            String hostpart = parts[1];
            String[] credParts = creds.split(":", 2);
            if (user == null || user.isEmpty()) user = credParts[0];
            if (password == null || password.isEmpty()) password = credParts.length > 1 ? credParts[1] : "";
            String[] hostParts = hostpart.split("/", 2);
            String hostPort = hostParts[0];
            String dbName = hostParts.length > 1 ? hostParts[1] : "";
            url = "jdbc:postgresql://" + hostPort + "/" + dbName + "?sslmode=require&channel_binding=require";
        }

        if (!url.startsWith("jdbc:postgresql:")) {
            System.err.println("[DB] DATABASE_URL doit être une URL PostgreSQL.");
            return null;
        }

        return new DatabaseSettings(url, user != null ? user : "", password != null ? password : "");
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return null;
    }

    private static final class DatabaseSettings {
        private final String url;
        private final String user;
        private final String password;

        private DatabaseSettings(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }

    private static DatabaseManager instance;

    private DatabaseManager() {
        initDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Driver Postgres introuvable : " + e.getMessage());
        }
        if (DB_URL == null || DB_URL.isEmpty()) {
            throw new SQLException("Base de données non configurée (DB_URL manquante)");
        }
        if (DB_USER != null && !DB_USER.isEmpty()) {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        }
        return DriverManager.getConnection(DB_URL);
    }

    private void initDatabase() {
        if (DB_URL == null || DB_URL.isEmpty()) {
            System.err.println("[DB] Connexion ignorée : base de données non configurée.");
            return;
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
        } catch (SQLException e) {
            System.err.println("[DB] Erreur lors de l'initialisation : " + e.getMessage());
        }
    }

    public String getAssociationInfo() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM association_info LIMIT 1")) {

            if (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Association : ").append(rs.getString("nom")).append("\n");
                sb.append("Description : ").append(rs.getString("description")).append("\n");
                sb.append("Adresse : ").append(rs.getString("adresse")).append("\n");
                try {
                    String universite = rs.getString("universite");
                    if (universite != null && !universite.isEmpty()) sb.append("Université/Campus : ").append(universite).append("\n");
                    String typeAsso = rs.getString("type_asso");
                    if (typeAsso != null && !typeAsso.isEmpty()) sb.append("Type : ").append(typeAsso).append("\n");
                } catch (SQLException ignored) {}
                return sb.toString().trim();
            }
        } catch (SQLException e) {
            System.err.println("[DB] " + e.getMessage());
        }
        return null;
    }

    public String searchMemberByRole(String role) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM membres WHERE LOWER(role) LIKE ?")) {

            ps.setString(1, "%" + role.toLowerCase() + "%");
            ResultSet rs = ps.executeQuery();

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                result.append(String.format(
                    "Membre : %s %s\nRôle : %s\nEmail : %s\nDescription : %s\n---\n",
                    rs.getString("prenom"),
                    rs.getString("nom"),
                    rs.getString("role"),
                    rs.getString("email"),
                    rs.getString("description")
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            System.err.println("[DB] " + e.getMessage());
        }
        return null;
    }

    public String searchMemberByName(String name) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM membres WHERE LOWER(nom) LIKE ? OR LOWER(prenom) LIKE ?")) {

            ps.setString(1, "%" + name.toLowerCase() + "%");
            ps.setString(2, "%" + name.toLowerCase() + "%");
            ResultSet rs = ps.executeQuery();

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                result.append(String.format(
                    "Membre : %s %s\nRôle : %s\nEmail : %s\nDescription : %s\n---\n",
                    rs.getString("prenom"),
                    rs.getString("nom"),
                    rs.getString("role"),
                    rs.getString("email"),
                    rs.getString("description")
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            System.err.println("[DB] " + e.getMessage());
        }
        return null;
    }

    public String getAllMembers() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM membres ORDER BY role")) {

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                String desc = rs.getString("description");
                result.append(String.format(
                    "- %s %s : %s (%s)%s\n",
                    rs.getString("prenom"),
                    rs.getString("nom"),
                    rs.getString("role"),
                    rs.getString("email"),
                    (desc != null && !desc.isEmpty()) ? " — " + desc : ""
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            System.err.println("[DB] " + e.getMessage());
        }
        return null;
    }

    public String searchFAQ(String keyword) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM faq WHERE LOWER(question) LIKE ? OR LOWER(reponse) LIKE ? OR LOWER(categorie) LIKE ?")) {

            ps.setString(1, "%" + keyword.toLowerCase() + "%");
            ps.setString(2, "%" + keyword.toLowerCase() + "%");
            ps.setString(3, "%" + keyword.toLowerCase() + "%");
            ResultSet rs = ps.executeQuery();

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                result.append(String.format(
                    "Q: %s\nR: %s\n(Catégorie: %s)\n---\n",
                    rs.getString("question"),
                    rs.getString("reponse"),
                    rs.getString("categorie")
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            System.err.println("[DB] " + e.getMessage());
        }
        return null;
    }

    public String getAllReseaux() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM reseaux_sociaux ORDER BY type")) {

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                String libelle = rs.getString("libelle");
                result.append(String.format(
                    "- %s : %s%s\n",
                    rs.getString("type"),
                    rs.getString("valeur"),
                    (libelle != null && !libelle.isEmpty()) ? " (" + libelle + ")" : ""
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getAllFAQ() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM faq ORDER BY categorie")) {

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                result.append(String.format(
                    "Q: %s\nR: %s\n(Catégorie: %s)\n---\n",
                    rs.getString("question"),
                    rs.getString("reponse"),
                    rs.getString("categorie")
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getAllData() {
        try (Connection conn = getConnection()) {
            StringBuilder data = new StringBuilder();
            List<String> tables = listPublicTables(conn);

            for (String table : tables) {
                String tableData = buildTableSnapshot(conn, table, 5);
                if (tableData != null && !tableData.isBlank()) {
                    data.append("[").append(table.toUpperCase()).append("]\n")
                        .append(tableData)
                        .append("\n\n");
                }
            }

            return data.length() > 0 ? data.toString().trim() : null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<String> listPublicTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName != null && !tableName.isBlank()) {
                    tables.add(tableName);
                }
            }
        }

        return tables;
    }

    private String buildTableSnapshot(Connection conn, String tableName, int limit) throws SQLException {
        List<String> columns = listColumnNames(conn, tableName);
        if (columns.isEmpty()) {
            return null;
        }

        String sql = "SELECT * FROM \"" + tableName.replace("\"", "\"\"") + "\" LIMIT " + limit;
        StringBuilder result = new StringBuilder();

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
                result.append("- ligne ").append(rowCount).append(": ");
                boolean wroteField = false;
                for (String column : columns) {
                    String value = rs.getString(column);
                    if (value != null && !value.isBlank()) {
                        if (wroteField) {
                            result.append(" | ");
                        }
                        result.append(column).append("=").append(value.trim().replace("\n", " "));
                        wroteField = true;
                    }
                }
                result.append("\n");
            }
        }

        return result.length() > 0 ? result.toString().trim() : null;
    }

    private List<String> listColumnNames(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs = metaData.getColumns(null, "public", tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null && !columnName.isBlank()) {
                    columns.add(columnName);
                }
            }
        }

        return columns;
    }

    public String searchReseaux(String keyword) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM reseaux_sociaux WHERE LOWER(type) LIKE ? OR LOWER(valeur) LIKE ? OR LOWER(libelle) LIKE ?")) {

            ps.setString(1, "%" + keyword.toLowerCase() + "%");
            ps.setString(2, "%" + keyword.toLowerCase() + "%");
            ps.setString(3, "%" + keyword.toLowerCase() + "%");
            ResultSet rs = ps.executeQuery();

            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                String libelle = rs.getString("libelle");
                result.append(String.format(
                    "%s : %s%s\n",
                    rs.getString("type"),
                    rs.getString("valeur"),
                    (libelle != null && !libelle.isEmpty()) ? " (" + libelle + ")" : ""
                ));
            }
            return result.length() > 0 ? result.toString() : null;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}
