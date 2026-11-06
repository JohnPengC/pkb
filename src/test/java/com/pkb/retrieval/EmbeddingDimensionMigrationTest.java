package com.pkb.retrieval;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 启动时配置维度（768）与库结构维度（1024）不一致：
 * 校验器应修改向量列类型、清空旧块，重建后新维度向量可正常写入与检索。
 * 注意：本类启动独立 Spring 上下文（覆盖 dimension 属性），与默认维度用例互不干扰。
 */
@SpringBootTest(properties = "pkb.model.embedding.dimension=768")
class EmbeddingDimensionMigrationTest extends AbstractIntegrationTest {

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    ChunkRepository chunkRepository;
    @Autowired
    IndexService indexService;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void migratesColumnAndIndexesWithNewDimension() throws Exception {
        // 启动校验器已把列改为 768（见类级 properties）
        Integer dim = jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute "
                        + "WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'",
                Integer.class);
        assertThat(dim).isEqualTo(768);

        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> {
                float[] v = new float[768];
                v[0] = 1.0f;
                return v;
            }).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("test-embedding-768");

        SourceDocument source = new SourceDocument();
        source.setSourceType(SourceType.FILE);
        source.setFileName("dim-" + System.nanoTime() + ".txt");
        source.setContentHash(HashUtil.sha256(source.getFileName()));
        source.setStoragePath("/tmp/" + source.getFileName());
        source = sourceRepository.save(source);

        KnowledgeDocument doc = knowledgeService.createDraft(source,
                new DistilledDraft("维度迁移", "摘要", "迁移后的向量写入测试内容。"));
        indexService.reindex(doc.getId());

        float[] query = new float[768];
        query[0] = 1.0f;
        var rows = chunkRepository.vectorSearch(query, 5, false);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.documentId().equals(doc.getId()));
    }
}
