-- V1__init.sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE source_document (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type   VARCHAR(10)  NOT NULL CHECK (source_type IN ('FILE', 'URL')),
    file_name     VARCHAR(512),
    url           TEXT,
    content_hash  VARCHAR(64)  NOT NULL,
    storage_path  VARCHAR(1024) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_source_document_hash ON source_document (content_hash);

CREATE TABLE import_task (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id  UUID NOT NULL REFERENCES source_document (id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','EXTRACTING','DISTILLING','COMPLETED','FAILED')),
    error_message       TEXT,
    retry_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_task_status ON import_task (status);

CREATE TABLE knowledge_document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id  UUID NOT NULL UNIQUE REFERENCES source_document (id),
    title               VARCHAR(512) NOT NULL,
    summary             TEXT,
    content             TEXT NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CONFIRMED')),
    current_version     INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_document_status ON knowledge_document (status);

CREATE TABLE knowledge_document_version (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID NOT NULL REFERENCES knowledge_document (id),
    version_no   INT NOT NULL,
    content      TEXT NOT NULL,
    change_note  VARCHAR(512),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, version_no)
);

CREATE TABLE document_chunk (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    chunk_index      INT NOT NULL,
    content          TEXT NOT NULL,
    embedding        vector(1024),
    embedding_model  VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_chunk_document ON document_chunk (document_id);
CREATE INDEX idx_document_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE tag (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE document_tag (
    document_id UUID NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);
