-- V5__add_traceid_to_orders.sql

ALTER TABLE orders
ADD COLUMN trace_id VARCHAR(64) NULL AFTER idempotency_key;