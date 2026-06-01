package com.miage.miagegpt.model;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final DatabaseSettings DB_SETTINGS = tryLoadDatabaseSettings();
    private static final String DB_URL = DB_SETTINGS != null ? DB_SETTINGS.url : null;
    private static final String DB_USER = DB_SETTINGS != null ? DB_SETTINGS.user : null;
    private static final String DB_PASSWORD = DB_SETTINGS != null ? DB_SETTINGS.password : null;

    private static DatabaseSettings tryLoadDatabaseSettings() {
        String raw = "postgresql://user_read:Paris1Sorbonne@ep-long-block-aln0qwfo-pooler.c-3.eu-central-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require";

        if (raw == null || raw.isBlank()) {
            System.err.println("[DB] URL codée en dur manquante.");
            return null;
        }

        String url = raw;
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            String protocol = url.startsWith("postgresql://") ? "postgresql://" : "postgres://";
            String withoutProto = url.substring(protocol.length());
            String[] parts = withoutProto.split("@", 2);
            if (parts.length < 2) {
                System.err.println("[DB] URL PostgreSQL invalide.");
                return null;
            }
            String creds = parts[0];
            String hostpart = parts[1];
            String[] credParts = creds.split(":", 2);
            String user = credParts[0];
            String password = credParts.length > 1 ? credParts[1] : "";
            String[] hostParts = hostpart.split("/", 2);
            String hostPort = hostParts[0];
            String dbName = hostParts.length > 1 ? hostParts[1] : "";
            String jdbcUrl = "jdbc:postgresql://" + hostPort + "/" + dbName
                    + "?sslmode=require&channel_binding=require";
            return new DatabaseSettings(jdbcUrl, user, password);
        }

        if (url.startsWith("jdbc:postgresql:")) {
            return new DatabaseSettings(url, "", "");
        }

        System.err.println("[DB] URL PostgreSQL codée en dur invalide.");
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
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public synchronized void initializeAfterApiKeyValidation() {
        initDatabase();
        try {
            loadSchema();
        } catch (Exception e) {
            System.err.println("[DB] Impossible de charger le schéma après validation de la clé API: " + e.getMessage());
        }
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

    public String getAllData() {
        StringBuilder data = new StringBuilder();

        for (TableInfo tableInfo : schemaCache.values()) {
            if (tableInfo == null) {
                continue;
            }

            data.append("[").append(tableInfo.name.toUpperCase()).append("]\n");
            if (tableInfo.rowCount >= 0) {
                data.append("Total rows: ").append(tableInfo.rowCount).append("\n");
            }
            if (tableInfo.fullContent != null && !tableInfo.fullContent.isBlank()) {
                data.append(tableInfo.fullContent).append("\n\n");
            } else if (tableInfo.sample != null && !tableInfo.sample.isBlank()) {
                data.append(tableInfo.sample).append("\n\n");
            }
        }

        return data.length() > 0 ? data.toString().trim() : null;
    }

    private List<String> listPublicTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs = metaData.getTables(null, "public", "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName != null && !tableName.isBlank()) {
                    tables.add(tableName);
                }
            }
        }

        return tables;
    }

    private static final int SCHEMA_PREVIEW_ROW_LIMIT = 2;

    private String buildTableSnapshot(Connection conn, String tableName) throws SQLException {
        List<String> columns = listColumnNames(conn, tableName);
        return buildTableContent(conn, tableName, columns, SCHEMA_PREVIEW_ROW_LIMIT, true);
    }

    private String buildFullTableContent(Connection conn, String tableName, List<String> columns) throws SQLException {
        return buildTableContent(conn, tableName, columns, 0, false);
    }

    private String buildTableContent(Connection conn, String tableName, List<String> columns, int rowLimit, boolean includeTotalRows) throws SQLException {
        if (columns == null || columns.isEmpty()) {
            return null;
        }

        String quotedTable = "\"" + tableName.replace("\"", "\"\"") + "\"";
        StringBuilder result = new StringBuilder();

        long totalRows = -1;
        if (includeTotalRows) {
            String countSql = "SELECT COUNT(*) AS cnt FROM " + quotedTable;
            try (Statement countStmt = conn.createStatement(); ResultSet countRs = countStmt.executeQuery(countSql)) {
                if (countRs.next()) {
                    totalRows = countRs.getLong("cnt");
                }
            } catch (SQLException ignored) {
                totalRows = -1;
            }
        }

        String sql = "SELECT * FROM " + quotedTable;
        if (rowLimit > 0) {
            sql += " LIMIT " + rowLimit;
        }

        java.util.Set<String> uniqueRows = new java.util.LinkedHashSet<>();

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String rowStr = formatRowBlock(columns, rs);
                if (!rowStr.isEmpty()) {
                    uniqueRows.add(rowStr);
                }
            }
        }

        if (includeTotalRows && totalRows >= 0) {
            result.append("Total rows: ").append(totalRows).append("\n");
        }

        int idx = 0;
        for (String r : uniqueRows) {
            idx++;
            result.append("ROW ").append(idx).append(":\n");
            result.append(r).append("\n");
        }

        if (includeTotalRows && totalRows > SCHEMA_PREVIEW_ROW_LIMIT) {
            result.append("(Affiché: ").append(Math.min((long) SCHEMA_PREVIEW_ROW_LIMIT, totalRows)).append(" lignes échantillonnées et dédupliquées)");
        }

        return result.length() > 0 ? result.toString().trim() : null;
    }

    private List<String> listColumnNames(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet rs = metaData.getColumns(null, "public", tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null && !columnName.isBlank() && !isTechnicalIdColumn(columnName)) {
                    columns.add(columnName);
                }
            }
        }

        return columns;
    }

    private boolean isTechnicalIdColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        String normalized = columnName.trim().toLowerCase();
        return normalized.equals("id");
    }

    private String formatRowBlock(List<String> columns, ResultSet rs) throws SQLException {
        StringBuilder row = new StringBuilder();
        boolean hasContent = false;

        for (String column : columns) {
            if (isTechnicalIdColumn(column)) {
                continue;
            }

            String value = rs.getString(column);
            if (value != null && !value.isBlank()) {
                row.append("  ").append(column).append(": ").append(value.trim().replace("\n", " ")).append("\n");
                hasContent = true;
            }
        }

        return hasContent ? row.toString().trim() : "";
    }

    public static class TableInfo {
        public final String name;
        public final List<String> columns;
        public final long rowCount;
        public final String sample;
        public final String fullContent;

        public TableInfo(String name, List<String> columns, long rowCount, String sample, String fullContent) {
            this.name = name;
            this.columns = columns;
            this.rowCount = rowCount;
            this.sample = sample;
            this.fullContent = fullContent;
        }
    }

    private final java.util.Map<String, TableInfo> schemaCache = new java.util.LinkedHashMap<>();

    public synchronized void loadSchema() {
        try (Connection conn = getConnection()) {
            List<String> tables = listPublicTables(conn);
            for (String table : tables) {
                List<String> columns = listColumnNames(conn, table);
                long count = -1;
                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM \"" + table.replace("\"", "\"\"") + "\"")) {
                    if (rs.next()) count = rs.getLong("cnt");
                } catch (SQLException ignored) {
                    count = -1;
                }

                String sample = null;
                String fullContent = null;
                try {
                    sample = buildTableSnapshot(conn, table);
                    fullContent = buildFullTableContent(conn, table, columns);
                } catch (SQLException ignored) {
                    sample = null;
                    fullContent = null;
                }

                TableInfo ti = new TableInfo(table, columns, count, sample, fullContent);
                schemaCache.put(table, ti);
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur loadSchema: " + e.getMessage());
        }
    }

    public synchronized java.util.List<TableInfo> getAllTableInfos() {
        return new ArrayList<>(schemaCache.values());
    }

    public synchronized TableInfo getTableInfo(String tableName) {
        return schemaCache.get(tableName);
    }

    public synchronized String getCachedTableContent(String tableName) {
        TableInfo tableInfo = schemaCache.get(tableName);
        return tableInfo != null ? tableInfo.fullContent : null;
    }

    public synchronized String getSchemaSummary() {
        StringBuilder sb = new StringBuilder();
        for (TableInfo ti : schemaCache.values()) {
            sb.append("TABLE: ").append(ti.name).append("\n");
            sb.append("COLUMNS: ");
            if (ti.columns != null && !ti.columns.isEmpty()) {
                sb.append(String.join(", ", ti.columns));
            }
            sb.append("\n");
            if (ti.rowCount >= 0) sb.append("ROWS: ").append(ti.rowCount).append("\n");
            if (ti.sample != null && !ti.sample.isBlank()) sb.append("SAMPLE: \n").append(ti.sample).append("\n");
            sb.append("---\n");
        }
        return sb.toString().trim();
    }

    public synchronized String getTableNamesSummary() {
        StringBuilder sb = new StringBuilder();
        for (TableInfo ti : schemaCache.values()) {
            if (ti == null || ti.name == null || ti.name.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(ti.name);
        }
        return sb.toString().trim();
    }

    public java.util.List<String> getTableRows(String tableName, String whereClause, int limit) throws SQLException {
        List<String> columns;
        try (Connection conn = getConnection()) {
            columns = listColumnNames(conn, tableName);
            if (columns.isEmpty()) return java.util.Collections.emptyList();

            String quotedTable = "\"" + tableName.replace("\"", "\"\"") + "\"";
            String sql = "SELECT * FROM " + quotedTable;
            if (whereClause != null && !whereClause.isBlank()) {
                sql += " WHERE " + whereClause;
            }
            if (limit > 0) sql += " LIMIT " + limit;

            List<String> rows = new ArrayList<>();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String s = formatRowBlock(columns, rs);
                    if (!s.isEmpty()) rows.add(s);
                }
            }
            return rows;
        }
    }

}
