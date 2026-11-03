-- V2__chunk_type.sql
-- 原文旁路索引：chunk 区分「提炼块」与「原文块」，检索时双池并行（结构 + 细节）
ALTER TABLE document_chunk ADD COLUMN chunk_type VARCHAR(16) NOT NULL DEFAULT 'DISTILLED';
