-- V4__trgm_indexes.sql
-- 中文检索升级：pg_trgm 三元组索引加速子串匹配（ILIKE 走 GIN），
-- 分词级全文检索（jieba 等）作为后续候选，见 docs/roadmap.md
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_document_chunk_content_trgm ON document_chunk USING GIN (content gin_trgm_ops);
CREATE INDEX idx_knowledge_document_title_trgm ON knowledge_document USING GIN (title gin_trgm_ops);
CREATE INDEX idx_knowledge_document_summary_trgm ON knowledge_document USING GIN (summary gin_trgm_ops);
