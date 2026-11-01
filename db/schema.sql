-- ====================================================================
-- Inventory Database Schema & Sample Data for Gemini Inventory Agent
-- Ready to run in MySQL Workbench or MySQL CLI
-- ====================================================================

DROP DATABASE IF EXISTS inventory_db;
CREATE DATABASE inventory_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE inventory_db;

-- 1. Categories Table
CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Suppliers Table
CREATE TABLE suppliers (
    supplier_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(100),
    phone VARCHAR(50),
    city VARCHAR(100),
    country VARCHAR(100) DEFAULT 'USA'
);

-- 3. Warehouses Table
CREATE TABLE warehouses (
    warehouse_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50),
    country VARCHAR(50) DEFAULT 'USA',
    capacity_units INT NOT NULL DEFAULT 50000
);

-- 4. Products Table
CREATE TABLE products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    product_name VARCHAR(200) NOT NULL,
    category_id INT,
    supplier_id INT,
    unit_price DECIMAL(10, 2) NOT NULL,
    cost_price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE SET NULL
);

-- 5. Inventory Stock Table (stock levels per warehouse)
CREATE TABLE inventory_stock (
    stock_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    warehouse_id INT NOT NULL,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 20,
    max_capacity INT NOT NULL DEFAULT 500,
    last_restocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_warehouse (product_id, warehouse_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE CASCADE
);

-- 6. Stock Movements Table
CREATE TABLE stock_movements (
    movement_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    from_warehouse_id INT,
    to_warehouse_id INT,
    quantity INT NOT NULL,
    movement_type ENUM('INBOUND', 'OUTBOUND', 'TRANSFER', 'ADJUSTMENT') NOT NULL,
    notes VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (from_warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE SET NULL,
    FOREIGN KEY (to_warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE SET NULL
);

-- ====================================================================
-- SEED DATA
-- ====================================================================

INSERT INTO categories (category_id, name, description) VALUES
(1, 'Laptops & Computers', 'High performance enterprise and consumer computing devices'),
(2, 'Smartphones & Tablets', 'Mobile phones, cellular accessories, and tablet computers'),
(3, 'Peripherals & Accessories', 'Keyboards, mice, USB hubs, docking stations, and webcams'),
(4, 'Networking Equipment', 'Routers, switches, access points, and cabling'),
(5, 'Monitors & Displays', '4K monitors, curved gaming displays, and presentation screens');

INSERT INTO suppliers (supplier_id, name, contact_email, phone, city, country) VALUES
(1, 'TechGlobal Logistics Inc.', 'sales@techglobal.com', '+1-555-0199', 'San Jose', 'USA'),
(2, 'Shenzhen Apex Microelectronics', 'export@apexmicro.cn', '+86-755-8833', 'Shenzhen', 'China'),
(3, 'Nordic Component Supply', 'orders@nordicsupply.se', '+46-8-123456', 'Stockholm', 'Sweden'),
(4, 'Tokyo Precision Parts', 'contact@tokyoprecision.jp', '+81-3-987654', 'Tokyo', 'Japan');

INSERT INTO warehouses (warehouse_id, name, code, city, state, country, capacity_units) VALUES
(1, 'Central Distribution Center', 'WH-CHI-01', 'Chicago', 'IL', 'USA', 100000),
(2, 'West Coast Hub', 'WH-LAX-02', 'Los Angeles', 'CA', 'USA', 75000),
(3, 'East Coast Fulfillment', 'WH-NYC-03', 'Newark', 'NJ', 'USA', 80000),
(4, 'Texas Logistics Hub', 'WH-DFW-04', 'Dallas', 'TX', 'USA', 60000);

INSERT INTO products (product_id, sku, product_name, category_id, supplier_id, unit_price, cost_price) VALUES
(1, 'LAP-DELL-XPS15', 'Dell XPS 15 (32GB RAM, 1TB SSD)', 1, 1, 1899.99, 1450.00),
(2, 'LAP-MBP-M3PRO', 'Apple MacBook Pro 16" M3 Pro', 1, 1, 2499.00, 1950.00),
(3, 'LAP-LEN-T14', 'Lenovo ThinkPad T14s Gen 4', 1, 1, 1249.50, 920.00),
(4, 'PHN-APL-IP15P', 'iPhone 15 Pro 256GB Titanium', 2, 2, 1099.00, 820.00),
(5, 'PHN-SAM-S24U', 'Samsung Galaxy S24 Ultra 512GB', 2, 2, 1299.99, 940.00),
(6, 'TAB-APL-IPADPRO', 'iPad Pro 12.9" M2 Wi-Fi 256GB', 2, 2, 1199.00, 890.00),
(7, 'PER-LOG-MXM3S', 'Logitech MX Master 3S Wireless Mouse', 3, 3, 99.99, 58.00),
(8, 'PER-LOG-MXKEYS', 'Logitech MX Keys Advanced Keyboard', 3, 3, 119.99, 68.00),
(9, 'PER-ANK-USBCHUB', 'Anker 10-in-1 USB-C Docking Station', 3, 3, 79.99, 42.00),
(10, 'NET-CIS-C9200', 'Cisco Catalyst 9200 48-Port Switch', 4, 1, 3850.00, 2900.00),
(11, 'NET-UBI-U6PRO', 'Ubiquiti UniFi 6 Pro Access Point', 4, 3, 179.00, 120.00),
(12, 'DIS-DELL-U2723QE', 'Dell UltraSharp 27" 4K USB-C Hub Monitor', 5, 4, 579.99, 410.00),
(13, 'DIS-LG-38WN95C', 'LG 38" UltraWide Curved Nano IPS Monitor', 5, 4, 1399.00, 1020.00);

INSERT INTO inventory_stock (product_id, warehouse_id, quantity_on_hand, reorder_level, max_capacity) VALUES
-- Dell XPS 15
(1, 1, 45, 15, 100),
(1, 2, 12, 15, 100), -- Below reorder level!
(1, 3, 28, 15, 100),
-- MacBook Pro 16
(2, 1, 60, 20, 150),
(2, 2, 8, 20, 100),  -- Critical low stock!
(2, 3, 35, 20, 100),
-- ThinkPad T14s
(3, 1, 80, 25, 200),
(3, 4, 14, 25, 150), -- Below reorder level!
-- iPhone 15 Pro
(4, 1, 120, 40, 300),
(4, 2, 95, 40, 250),
(4, 3, 5, 40, 200),  -- Severely out of stock soon!
-- Galaxy S24 Ultra
(5, 1, 85, 30, 200),
(5, 4, 42, 30, 150),
-- iPad Pro
(6, 2, 50, 15, 100),
(6, 3, 11, 15, 100), -- Below reorder level!
-- Logitech Mouse
(7, 1, 340, 50, 600),
(7, 2, 180, 50, 400),
(7, 3, 210, 50, 400),
-- Logitech Keyboard
(8, 1, 220, 50, 500),
(8, 2, 30, 50, 300), -- Below reorder level!
-- Anker USB-C Hub
(9, 1, 150, 40, 400),
(9, 2, 15, 40, 300), -- Below reorder level!
(9, 4, 75, 40, 200),
-- Cisco Switch
(10, 1, 14, 5, 30),
(10, 3, 3, 5, 20),   -- Low stock!
-- Ubiquiti AP
(11, 1, 95, 25, 200),
(11, 2, 88, 25, 150),
(11, 4, 18, 25, 100),-- Below reorder level!
-- Dell 27" 4K Monitor
(12, 1, 75, 20, 150),
(12, 2, 22, 20, 100),
(12, 3, 9, 20, 100),  -- Low stock!
-- LG 38" Curved Monitor
(13, 1, 25, 10, 50),
(13, 2, 4, 10, 40);   -- Critical low stock!

INSERT INTO stock_movements (product_id, from_warehouse_id, to_warehouse_id, quantity, movement_type, notes) VALUES
(1, 1, 2, 10, 'TRANSFER', 'Urgent rebalance from Chicago to LAX'),
(4, NULL, 1, 100, 'INBOUND', 'Received shipment from Shenzhen Apex'),
(2, 1, 3, 15, 'TRANSFER', 'Routine distribution'),
(10, NULL, 1, 20, 'INBOUND', 'Direct manufacturer purchase from Cisco'),
(13, 1, 2, 5, 'TRANSFER', 'VIP customer reservation transfer');
