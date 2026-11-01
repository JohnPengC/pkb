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
    }
}
