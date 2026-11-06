package com.pkb.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 向量维度迁移：启动时对比配置维度（pkb.model.embedding.dimension）与
 * document_chunk.embedding 列实际维度，不一致时修改列类型、清空旧向量并全量重建。
 * 换用不同维度的 embedding 模型只需改 EMBEDDING_DIMENSION 并重启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingDimensionVerifier implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ReindexService reindexService;

    @Value("${pkb.model.embedding.dimension:1024}")
    private int configuredDimension;

    @Override
    public void run(ApplicationArguments args) {
        Integer current = jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute "
                        + "WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'",
                Integer.class);
        if (current == null || current == configuredDimension) {
            return;
        }
        log.info("向量维度变更：{} → {}，修改向量列并全量重建索引", current, configuredDimension);
        jdbc.execute("ALTER TABLE document_chunk ALTER COLUMN embedding TYPE vector(" + configuredDimension + ")");
        jdbc.execute("REINDEX INDEX idx_document_chunk_embedding");
        int deleted = jdbc.update("DELETE FROM document_chunk");
        log.info("旧向量块已清空（{} 行），开始全量重建", deleted);
        int rebuilt = reindexService.reindexAll();
        log.info("向量维度迁移完成：重建 {} 篇文档", rebuilt);
    }
}
