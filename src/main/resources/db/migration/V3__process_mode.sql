-- V3__process_mode.sql
-- 导入处理方式（AI 提炼 / 仅转 Markdown）随任务与知识文档记录，驱动执行与索引策略
ALTER TABLE import_task ADD COLUMN process_mode VARCHAR(16) NOT NULL DEFAULT 'DISTILL'
    CHECK (process_mode IN ('DISTILL','RAW'));

ALTER TABLE knowledge_document ADD COLUMN process_mode VARCHAR(16) NOT NULL DEFAULT 'DISTILL'
    CHECK (process_mode IN ('DISTILL','RAW'));
