CREATE DATABASE IF NOT EXISTS warehouse_db;

USE warehouse_db;

CREATE TABLE IF NOT EXISTS Warehouse (
    w_id INT PRIMARY KEY,
    w_name VARCHAR(50) NOT NULL,
    w_street VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Item (
    i_id INT PRIMARY KEY,
    i_name VARCHAR(50) NOT NULL,
    i_price DECIMAL(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS Stock (
    s_i_id INT NOT NULL,
    s_w_id INT NOT NULL,
    s_quantity INT NOT NULL,
    PRIMARY KEY (s_i_id, s_w_id),
    INDEX idx_stock_w (s_w_id),
    CONSTRAINT fk_stock_item FOREIGN KEY (s_i_id) REFERENCES Item (i_id),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (s_w_id) REFERENCES Warehouse (w_id)
);

CREATE TABLE IF NOT EXISTS StockReservation (
    tx_id VARCHAR(64) PRIMARY KEY,
    s_i_id INT NOT NULL,
    s_w_id INT NOT NULL,
    qty INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_reservation_stock (s_i_id, s_w_id),
    INDEX idx_reservation_status (status),

    CONSTRAINT fk_reservation_stock
        FOREIGN KEY (s_i_id, s_w_id)
        REFERENCES Stock (s_i_id, s_w_id)
);