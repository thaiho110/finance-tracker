CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date DATE NOT NULL,
    raw_description VARCHAR(500) NOT NULL,
    clean_merchant VARCHAR(255) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    source_type VARCHAR(10) NOT NULL CHECK (source_type IN ('CSV', 'OCR', 'MANUAL')),
    client_id VARCHAR(100),
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES users(id)
);

CREATE TABLE receipt_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    item_description VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL
);

CREATE INDEX idx_transactions_date ON transactions(date);
CREATE INDEX idx_transactions_category ON transactions(category);
CREATE INDEX idx_transactions_merchant ON transactions(clean_merchant);
CREATE INDEX idx_transactions_created_by ON transactions(created_by);
CREATE INDEX idx_receipt_items_transaction ON receipt_items(transaction_id);
