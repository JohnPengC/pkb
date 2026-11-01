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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class IndexServiceTest extends AbstractIntegrationTest {

    @Autowired
    IndexService indexService;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    ChunkRepository chunkRepository;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void reindexReplacesChunks() {
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector(texts.indexOf(t))).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("test-embedding");

        KnowledgeDocument doc = knowledgeService.createDraft(newSource(),
                new DistilledDraft("标题", "摘要", "## 一\n" + "第一段内容。".repeat(80) + "\n\n## 二\n" + "第二段内容。".repeat(80)));

        indexService.reindex(doc.getId());
        var rows = chunkRepository.vectorSearch(fakeVector(0), 10, false);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.documentId().equals(doc.getId()));

        // 改内容后再重建，旧块应被替换
        knowledgeService.save(doc.getId(), "## 全新\n完全不同的内容。", "重写");
        indexService.reindex(doc.getId());
        var after = chunkRepository.vectorSearch(fakeVector(0), 10, false);
        assertThat(after).allMatch(r -> r.content().contains("全新") || r.content().contains("完全不同"));
    }

    private float[] fakeVector(int seed) {
        float[] v = new float[1024];
        v[seed % 1024] = 1.0f;
        return v;
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("idx-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
