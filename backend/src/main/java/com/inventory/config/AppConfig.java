package com.inventory.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages configuration for the Core Java Server.
 * Reads from System Environment variables, optional .env / application.properties file,
 * or fallback defaults.
 */
public class AppConfig {
    private static volatile String geminiApiKey = "";
    private static volatile String geminiModel = "gemini-1.5-flash";
    private static volatile int serverPort = 8080;

    private static volatile String dbHost = "localhost";
    private static volatile int dbPort = 3306;
    private static volatile String dbName = "inventory_db";
    private static volatile String dbUser = "root";
    private static volatile String dbPassword = "";

    static {
        loadDefaults();
    }

    private static void loadDefaults() {
        // 1. Try to load local .env or application.properties if exists
        File propsFile = new File("application.properties");
        if (propsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                Properties props = new Properties();
                props.load(fis);
                if (props.containsKey("GEMINI_API_KEY")) {
                    String key = props.getProperty("GEMINI_API_KEY").trim();
                    if (key.startsWith("${") && key.endsWith("}")) {
                        String envVar = key.substring(2, key.length() - 1).trim();
                        String envVal = System.getenv(envVar);
                        geminiApiKey = (envVal != null && !envVal.isBlank()) ? envVal.trim() : "";
                    } else if (!key.equalsIgnoreCase("your_api_key_here") && !key.equalsIgnoreCase("your_key_here")) {
                        geminiApiKey = key;
                    }
                }
                if (props.containsKey("GEMINI_MODEL")) geminiModel = props.getProperty("GEMINI_MODEL").trim();
                if (props.containsKey("SERVER_PORT")) serverPort = Integer.parseInt(props.getProperty("SERVER_PORT").trim());
                if (props.containsKey("DB_HOST")) dbHost = props.getProperty("DB_HOST").trim();
                if (props.containsKey("DB_PORT")) dbPort = Integer.parseInt(props.getProperty("DB_PORT").trim());
                if (props.containsKey("DB_NAME")) dbName = props.getProperty("DB_NAME").trim();
                if (props.containsKey("DB_USER")) dbUser = props.getProperty("DB_USER").trim();
                if (props.containsKey("DB_PASSWORD")) dbPassword = props.getProperty("DB_PASSWORD").trim();
            } catch (IOException ignored) {}
        }

        // 2. Override with System Environment Variables if present
        String envApiKey = System.getenv("GEMINI_API_KEY");
        if (envApiKey == null || envApiKey.isBlank()) {
            envApiKey = System.getenv("API_KEY");
        }
        if (envApiKey != null && !envApiKey.isBlank()) {
            geminiApiKey = envApiKey.trim();
        }

        String envModel = System.getenv("GEMINI_MODEL");
        if (envModel != null && !envModel.isBlank()) {
            geminiModel = envModel.trim();
        }

        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isBlank()) {
            try { serverPort = Integer.parseInt(envPort.trim()); } catch (NumberFormatException ignored) {}
        }

        String envDbHost = System.getenv("DB_HOST");
        if (envDbHost != null && !envDbHost.isBlank()) dbHost = envDbHost.trim();

        String envDbPort = System.getenv("DB_PORT");
        if (envDbPort != null && !envDbPort.isBlank()) {
            try { dbPort = Integer.parseInt(envDbPort.trim()); } catch (NumberFormatException ignored) {}
        }

        String envDbName = System.getenv("DB_NAME");
        if (envDbName != null && !envDbName.isBlank()) dbName = envDbName.trim();

        String envDbUser = System.getenv("DB_USER");
        if (envDbUser != null && !envDbUser.isBlank()) dbUser = envDbUser.trim();

        String envDbPassword = System.getenv("DB_PASSWORD");
        if (envDbPassword != null) dbPassword = envDbPassword;
    }

    public static String getGeminiApiKey() {
        return geminiApiKey;
    }

    public static synchronized void setGeminiApiKey(String key) {
        if (key != null) geminiApiKey = key.trim();
    }

    public static String getGeminiModel() {
        return geminiModel;
    }

    public static synchronized void setGeminiModel(String model) {
        if (model != null && !model.isBlank()) geminiModel = model.trim();
    }

    public static int getServerPort() {
        return serverPort;
    }

    public static String getDbHost() {
        return dbHost;
    }

    public static int getDbPort() {
        return dbPort;
    }

    public static String getDbName() {
        return dbName;
    }

    public static String getDbUser() {
        return dbUser;
    }

    public static String getDbPassword() {
        return dbPassword;
    }

    public static synchronized void updateDbConfig(String host, int port, String name, String user, String password) {
        if (host != null) dbHost = host;
        if (port > 0) dbPort = port;
        if (name != null) dbName = name;
        if (user != null) dbUser = user;
        if (password != null) dbPassword = password;
    }
}
