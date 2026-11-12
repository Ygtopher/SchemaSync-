-- Sample H2 in-memory initialization script
CREATE TABLE IF NOT EXISTS customers (
    cust_id INT PRIMARY KEY,
    company_name VARCHAR(100),
    contact_email VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS invoices (
    invoice_id INT PRIMARY KEY,
    cust_id INT,
    amount NUMERIC(12,2),
    invoice_date DATE
);
