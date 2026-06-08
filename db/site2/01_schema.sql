CREATE DATABASE IF NOT EXISTS customer_db;

USE customer_db;

CREATE TABLE IF NOT EXISTS District (
    d_id INT PRIMARY KEY,
    d_w_id INT NOT NULL,
    d_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Customer (
    c_id INT PRIMARY KEY,
    c_d_id INT NOT NULL,
    c_first VARCHAR(50) NOT NULL,
    c_credit VARCHAR(2) NOT NULL,
    INDEX idx_customer_d (c_d_id),
    CONSTRAINT fk_customer_district FOREIGN KEY (c_d_id) REFERENCES District (d_id)
);

CREATE TABLE IF NOT EXISTS Orders (
    o_id INT AUTO_INCREMENT PRIMARY KEY,
    tx_id VARCHAR(64) NOT NULL UNIQUE,
    o_d_id INT NOT NULL,
    o_c_id INT NOT NULL,
    o_entry_d DATETIME NOT NULL,

    INDEX idx_orders_customer (o_c_id),
    INDEX idx_orders_district (o_d_id),

    CONSTRAINT fk_orders_customer FOREIGN KEY (o_c_id) REFERENCES Customer (c_id),
    CONSTRAINT fk_orders_district FOREIGN KEY (o_d_id) REFERENCES District (d_id)
);



CREATE TABLE IF NOT EXISTS GlobalTransactionLog (
    tx_id VARCHAR(64) PRIMARY KEY,

    item_id INT NOT NULL,
    warehouse_id INT NOT NULL,
    district_id INT NOT NULL,
    customer_id INT NOT NULL,
    order_qty INT NOT NULL,

    status VARCHAR(30) NOT NULL,
    error_message VARCHAR(500),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_global_tx_status (status),
    INDEX idx_global_tx_created (created_at)
);