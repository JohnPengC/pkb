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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RetrievalServiceTest extends AbstractIntegrationTest {

    @Autowired
    RetrievalService retrievalService;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    IndexService indexService;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void draftExcludedByDefaultIncludedWhenRequested() throws Exception {
        // 让 embed 返回"内容哈希"方向向量：相同内容的向量接近
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(RetrievalServiceTest::vectorOf).toList();
        });
        when(embeddingProvider.embed(anyString())).thenAnswer(inv -> vectorOf(inv.getArgument(0)));
        when(embeddingProvider.modelId()).thenReturn("test-embedding");

        KnowledgeDocument draft = knowledgeService.createDraft(newSource(),
                new DistilledDraft("草稿文档", "摘要", "信贷风控的核心是贷前审查。"));
        // createDraft 提交后会异步触发事件驱动的 reindex，等它完成再显式重建，
        // 避免两个 reindex 交错（delete+insert 非原子）产生重复块
        awaitChunksExist(draft.getId());
        indexService.reindex(draft.getId());

        List<SearchHit> confirmedOnly = retrievalService.search("贷前审查", 5, false);
        assertThat(confirmedOnly).isEmpty();

        List<SearchHit> withDraft = retrievalService.search("贷前审查", 5, true);
        assertThat(withDraft).hasSize(1);
        assertThat(withDraft.get(0).title()).isEqualTo("草稿文档");

        knowledgeService.confirm(draft.getId());
        List<SearchHit> afterConfirm = retrievalService.search("贷前审查", 5, false);
        assertThat(afterConfirm).hasSize(1);
    }

    /** 把文本映射为确定性向量：按内容哈希散布，保证同内容向量一致。 */
    private static float[] vectorOf(String text) {
        float[] v = new float[1024];
        int h = text.hashCode();
        for (int i = 0; i < 8; i++) {
            v[Math.floorMod(h + i * 31, 1024)] = 1.0f / (i + 1);
        }
        return v;
    }

    private void awaitChunksExist(UUID documentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM document_chunk WHERE document_id = ?", Integer.class, documentId);
            if (count != null && count > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等待异步向量化超时：" + documentId);
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("rt-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
