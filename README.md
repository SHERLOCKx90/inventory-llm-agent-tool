# 📦 Inventory Intelligence Agent (Full-Stack MVP)

A full-stack, enterprise-grade Inventory Intelligence Agent demonstrating **Gemini Function Calling (MCP tool pattern)** with a **Pure Core Java Backend** (`com.sun.net.httpserver` + JDBC, **no Spring Boot**), **MySQL Database**, and an **Angular Frontend**.

---

## 🏗️ Architecture Overview

```
[ User in Angular UI ]
         │  1. Any dynamic natural language prompt (e.g., "Which products are below reorder level in Chicago?")
         ▼
[ Core Java Server (:8080) ]
         │  2. Forwards prompt + Tool Declarations (functionDeclarations)
         ▼
[ Google Gemini LLM ]
         │  3. Model decides to call tool: `execute_inventory_query(sql)`
         ▼
[ Java Tool Agent Executor ]
         │  4. Validates read-only SQL & runs query via JDBC
         ▼
[ MySQL Database (inventory_db) ]
         │  5. Returns matching row records (extracted sub-table)
         ▼
[ Core Java Server ]
         │  6. Sends tool response back into conversation context
         ▼
[ Google Gemini LLM ]
         │  7. Synthesizes final grounded natural language answer
         ▼
[ Angular Frontend (:4200) ]
            8. Displays AI answer + Collapsible "Tool Call Trace" (SQL & Sub-Table)
```

---

## 🔑 1. How to Get a Free Gemini API Key (Step-by-Step)

Google provides a generous **free tier** for developers:

1. Open your browser and go to: **[Google AI Studio](https://aistudio.google.com/)**
2. Sign in with your Google account.
3. In the left navigation or top bar, click the **"Get API key"** button (or "Create API key").
4. Click **"Create API key in new project"** (or select an existing Google Cloud project).
5. Copy your newly generated API key.
6. You can use it in 3 convenient ways:
   - **Option A (In the UI directly)**: Paste it into the settings bar in the Angular UI.
   - **Option B (Environment Variable)**:
     ```bash
     export GEMINI_API_KEY="your_api_key_here"
     ```
   - **Option C (Backend properties)**: Add `GEMINI_API_KEY=your_key` in `backend/application.properties`.

---

## 🗄️ 2. Setting Up MySQL Database

### In MySQL Workbench:
1. Open **MySQL Workbench** and connect to your local MySQL server (default `localhost:3306`).
2. Click **File -> Open SQL Script...** and select `db/schema.sql` (located in this project at `db/schema.sql`).
3. Click the **Execute (Lightning icon ⚡)** to run the entire script.
4. This will:
   - Create the `inventory_db` database.
   - Create 6 tables: `categories`, `suppliers`, `warehouses`, `products`, `inventory_stock`, `stock_movements`.
   - Seed realistic inventory data across multiple warehouses (Chicago, Los Angeles, Newark, Dallas).

### Or via Terminal / Command Line:
```bash
mysql -u root -p < db/schema.sql
```

---

## ☕ 3. Running the Core Java Server (Pure Core Java, No Spring Boot)

Ensure Java 21+ and Maven are installed.

```bash
cd backend

# Option A: Run directly with Java
mvn compile
java -cp "target/classes:target/dependency/*" com.inventory.App

# Or Option B: Run the standalone shaded JAR
mvn package -DskipTests
java -jar target/inventory-agent-backend-1.0.0.jar
```

The server starts on **http://localhost:8080**.

### Core Java Backend Endpoints:
- `POST /api/chat`: Handles dynamic prompts, executes function calling loop, and returns final answer + SQL tool trace.
- `GET /api/db/status`: Health check for MySQL connectivity and table row counts.
- `GET /api/config`: Inspects current model and API key status.
- `GET /api/inventory/sample`: Returns sample inventory rows.
- `GET /api/inventory/schema`: Returns full schema summary.
- `GET /api/health`: Server health check.

---

## 🅰️ 4. Running the Angular Frontend

```bash
cd frontend
npm start
```
Open **http://localhost:4200** in your browser.

---

## 🎯 5. Dynamic Sample Prompts to Showcase to Your Team

You are not limited to predefined queries! Here are great examples to showcase dynamic SQL generation & tool execution:

1. *"Which products are currently below their reorder level, and in which warehouse?"*
2. *"What is the total value of inventory currently held in the West Coast Hub?"*
3. *"List all Apple devices we have, along with their unit price and total quantity on hand across all warehouses."*
4. *"Who is our supplier for Cisco switches, and what is their contact email and city?"*
5. *"Show me the top 3 most expensive products and their stock count in Chicago."*
6. *"Are there any recent stock transfers recorded between warehouses?"*
