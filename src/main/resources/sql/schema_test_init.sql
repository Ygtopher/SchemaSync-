-- Schema test initialization
CREATE TABLE IF NOT EXISTS test_products (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    price DECIMAL(10,2)
);
