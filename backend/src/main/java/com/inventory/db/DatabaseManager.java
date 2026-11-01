package com.inventory.db;

import com.inventory.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.Locale;

/**
 * Manages pure JDBC connectivity with MySQL using HikariCP.
 * Provides safe, read-only query execution and schema metadata extraction.
 */
public class DatabaseManager {
    private static volatile DatabaseManager instance;
    private HikariDataSource dataSource;
    private String lastConnectionError = null;

    private DatabaseManager() {
        initDataSource();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public synchronized void reloadDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception ignored) {}
        }
        this.dataSource = null;
        initDataSource();
    }

    private void initDataSource() {
        this.dataSource = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=3000&socketTimeout=10000",
                    AppConfig.getDbHost(), AppConfig.getDbPort(), AppConfig.getDbName());

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(AppConfig.getDbUser());
            config.setPassword(AppConfig.getDbPassword());
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(3000);
            config.setPoolName("InventoryPool");

            this.dataSource = new HikariDataSource(config);
            this.lastConnectionError = null;
        } catch (Throwable e) {
            this.dataSource = null;
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            this.lastConnectionError = (cause.getMessage() != null && !cause.getMessage().isBlank())
                    ? cause.getMessage()
                    : e.getMessage();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initDataSource();
        }
        if (dataSource == null) {
            throw new SQLException("Cannot connect to MySQL database: " + (lastConnectionError != null ? lastConnectionError : "Unknown initialization error"));
        }
        return dataSource.getConnection();
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean valid = conn.isValid(2);
            if (valid) {
                this.lastConnectionError = null;
            }
            return valid;
        } catch (Throwable e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            this.lastConnectionError = (cause.getMessage() != null && !cause.getMessage().isBlank())
                    ? cause.getMessage()
                    : e.getMessage();
            return false;
        }
    }

    public String getLastConnectionError() {
        return lastConnectionError;
    }

    /**
     * Executes a strictly read-only SELECT query against the MySQL database.
     * Prevents destructive operations (DROP, DELETE, UPDATE, INSERT, ALTER, TRUNCATE).
     */
    public JSONArray executeReadOnlyQuery(String sql) throws SQLException, SecurityException {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }

        String trimmed = sql.trim();
        // Remove trailing semicolon if present
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // Strict read-only check
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH") && !upper.startsWith("SHOW") && !upper.startsWith("DESC")) {
            throw new SecurityException("Security Violation: Only SELECT/SHOW/DESCRIBE queries are permitted. Query attempted: " + sql);
        }

        // Blacklist destructive DDL/DML keywords
        String[] forbiddenKeywords = {"DROP ", "DELETE ", "UPDATE ", "INSERT ", "ALTER ", "TRUNCATE ", "CREATE ", "GRANT ", "REVOKE ", "RENAME "};
        for (String forbidden : forbiddenKeywords) {
            if (upper.contains(forbidden)) {
                throw new SecurityException("Security Violation: Destructive command detected (" + forbidden.trim() + ").");
            }
        }

        JSONArray results = new JSONArray();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Set query timeout to 10 seconds to protect against runaway queries
            stmt.setQueryTimeout(10);

            try (ResultSet rs = stmt.executeQuery(trimmed)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                int rowCount = 0;
                while (rs.next() && rowCount < 100) { // Safety limit: max 100 rows per tool extraction
                    JSONObject row = new JSONObject();
                    for (int i = 1; i <= columnCount; i++) {
                        String colName = metaData.getColumnLabel(i);
                        Object val = rs.getObject(i);
                        row.put(colName, val == null ? JSONObject.NULL : val);
                    }
                    results.put(row);
                    rowCount++;
                }
            }
        }
        return results;
    }

    /**
     * Provides comprehensive schema context for the Gemini system instruction.
     */
    public String getDatabaseSchemaSummary() {
        return """
            DATABASE: inventory_db (MySQL)
            
            TABLES & COLUMNS:
            1. categories (
                 category_id INT AUTO_INCREMENT PRIMARY KEY,
                 name VARCHAR(100) NOT NULL UNIQUE,
                 description TEXT
               )
            2. suppliers (
                 supplier_id INT AUTO_INCREMENT PRIMARY KEY,
                 name VARCHAR(150) NOT NULL,
                 contact_email VARCHAR(100),
                 phone VARCHAR(50),
                 city VARCHAR(100),
                 country VARCHAR(100)
               )
            3. warehouses (
                 warehouse_id INT AUTO_INCREMENT PRIMARY KEY,
                 name VARCHAR(100) NOT NULL,
                 code VARCHAR(20) NOT NULL UNIQUE,
                 city VARCHAR(100) NOT NULL,
                 state VARCHAR(50),
                 country VARCHAR(50),
                 capacity_units INT NOT NULL
               )
            4. products (
                 product_id INT AUTO_INCREMENT PRIMARY KEY,
                 sku VARCHAR(50) NOT NULL UNIQUE,
                 product_name VARCHAR(200) NOT NULL,
                 category_id INT REFERENCES categories(category_id),
                 supplier_id INT REFERENCES suppliers(supplier_id),
                 unit_price DECIMAL(10, 2) NOT NULL,
                 cost_price DECIMAL(10, 2) NOT NULL
               )
            5. inventory_stock (
                 stock_id INT AUTO_INCREMENT PRIMARY KEY,
                 product_id INT REFERENCES products(product_id),
                 warehouse_id INT REFERENCES warehouses(warehouse_id),
                 quantity_on_hand INT NOT NULL,
                 reorder_level INT NOT NULL,
                 max_capacity INT NOT NULL,
                 last_restocked_at TIMESTAMP
               )
            6. stock_movements (
                 movement_id INT AUTO_INCREMENT PRIMARY KEY,
                 product_id INT REFERENCES products(product_id),
                 from_warehouse_id INT REFERENCES warehouses(warehouse_id),
                 to_warehouse_id INT REFERENCES warehouses(warehouse_id),
                 quantity INT NOT NULL,
                 movement_type ENUM('INBOUND', 'OUTBOUND', 'TRANSFER', 'ADJUSTMENT'),
                 notes VARCHAR(255),
                 created_at TIMESTAMP
               )
            
            COMMON QUERY PATTERNS:
            - Low stock check: JOIN products p ON s.product_id = p.product_id WHERE s.quantity_on_hand <= s.reorder_level
            - Stock per warehouse: JOIN warehouses w ON s.warehouse_id = w.warehouse_id
            - Total inventory value: SUM(s.quantity_on_hand * p.unit_price)
            """;
    }

    public JSONObject getSystemStatus() {
        JSONObject status = new JSONObject();
        boolean connected = testConnection();
        status.put("connected", connected);
        status.put("host", AppConfig.getDbHost());
        status.put("port", AppConfig.getDbPort());
        status.put("database", AppConfig.getDbName());
        status.put("user", AppConfig.getDbUser());

        if (connected) {
            try {
                // Check if inventory tables exist
                JSONArray tables = executeReadOnlyQuery("SHOW TABLES");
                status.put("tableCount", tables.length());

                if (tables.length() == 0) {
                    status.put("tablesExist", false);
                    status.put("message", "Connected to MySQL successfully, but database '" + AppConfig.getDbName() + "' is empty. Please run db/schema.sql in MySQL Workbench.");
                } else {
                    status.put("tablesExist", true);
                    JSONArray prodCount = executeReadOnlyQuery("SELECT COUNT(*) AS total_products FROM products");
                    JSONArray stockCount = executeReadOnlyQuery("SELECT SUM(quantity_on_hand) AS total_units FROM inventory_stock");
                    Object prodNum = prodCount.getJSONObject(0).opt("total_products");
                    Object stockNum = stockCount.getJSONObject(0).opt("total_units");
                    status.put("totalProducts", prodNum);
                    status.put("totalStockUnits", stockNum);
                    status.put("message", "Connected! Verified live database: " + prodNum + " products, " + stockNum + " stock units across warehouses.");
                }
            } catch (Exception e) {
                status.put("tablesExist", false);
                status.put("message", "Connected to MySQL, but tables could not be read: " + e.getMessage() + ". Run db/schema.sql in MySQL Workbench.");
            }
        } else {
            status.put("error", lastConnectionError != null ? lastConnectionError : "Unable to establish connection to MySQL server.");
        }
        return status;
    }
}
