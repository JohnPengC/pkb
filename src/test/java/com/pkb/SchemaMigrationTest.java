package com.pkb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAllTablesAndVectorExtension() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables).contains(
                "source_document", "import_task", "knowledge_document",
                "knowledge_document_version", "document_chunk", "tag", "document_tag");

        String ext = jdbc.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class);
        assertThat(ext).isEqualTo("vector");

        Integer dim = jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'",
                Integer.class);
        // pgvector 把维度存为 atttypmod
        assertThat(dim).isEqualTo(1024);

        // V2：原文旁路索引（提炼块 / 原文块）
        String chunkType = jdbc.queryForObject(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'document_chunk' AND column_name = 'chunk_type'",
                String.class);
        assertThat(chunkType).isEqualTo("chunk_type");

        // V3：导入处理方式（AI 提炼 / 仅转 Markdown）
        for (String table : new String[]{"import_task", "knowledge_document"}) {
            String processMode = jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_name = '" + table + "' AND column_name = 'process_mode'",
                    String.class);
            assertThat(processMode).isEqualTo("process_mode");
        }
    }
}
