-- Deploy hardening: drop the legacy free-text categorisation that V5 replaced with categories/category_id.
-- V5 kept transactions.category and the category_mappings table so the V5 migration could be checked
-- against real data first; that check has passed (phase 13), and nothing in the code reads them any more,
-- so they go now. transactions.category_id (NOT NULL since V5) is the single source of a transaction's
-- category.

ALTER TABLE transactions DROP COLUMN IF EXISTS category;
DROP TABLE IF EXISTS category_mappings;
