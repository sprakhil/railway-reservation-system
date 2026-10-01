-- MySQL 8. Fresh database: run this file. Original database: run 001 first.
-- Deliberately fail if tables already exist; do not blindly repeat a partial migration.
CREATE TABLE payment_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT,
    pnr VARCHAR(10) NOT NULL,
    username VARCHAR(255) NOT NULL,
    razorpay_order_id VARCHAR(255),
    razorpay_payment_id VARCHAR(255),
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    receipt VARCHAR(40),
    status ENUM('INITIATING','CREATED','CAPTURED','SUCCESS','FAILED'),
    hold_expires_at DATETIME(6),
    order_lease_until DATETIME(6),
    next_reconcile_at DATETIME(6),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_pnr UNIQUE (pnr),
    CONSTRAINT uk_payment_order UNIQUE (razorpay_order_id),
    CONSTRAINT uk_payment_success_id UNIQUE (razorpay_payment_id),
    CONSTRAINT uk_payment_receipt UNIQUE (receipt),
    INDEX idx_payment_reconcile (next_reconcile_at)
);
CREATE TABLE payment_captures (
    payment_id VARCHAR(64) NOT NULL,
    version BIGINT,
    pnr VARCHAR(10) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    confirmed BIT NOT NULL,
    refund_amount DECIMAL(12,2),
    refund_key VARCHAR(40),
    refund_id VARCHAR(64),
    refund_status VARCHAR(32),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_refund_key UNIQUE (refund_key),
    CONSTRAINT uk_refund_id UNIQUE (refund_id)
);
CREATE TABLE payment_webhooks (
    body_hash VARCHAR(64) NOT NULL,
    version BIGINT,
    body LONGTEXT NOT NULL,
    processed BIT NOT NULL,
    next_attempt_at DATETIME(6),
    received_at DATETIME(6),
    PRIMARY KEY (body_hash),
    INDEX idx_webhook_reconcile (processed, next_attempt_at)
);
