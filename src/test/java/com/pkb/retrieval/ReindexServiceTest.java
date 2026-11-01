package com.pkb.retrieval;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class ReindexServiceTest extends AbstractIntegrationTest {

    @Autowired
    ReindexService reindexService;
    @Autowired
    IndexService indexService;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void detectsModelMismatchAndRebuilds() throws Exception {
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            java.util.List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> new float[1024]).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("model-a");

        var doc = knowledgeService.createDraft(newSource(), new DistilledDraft("t", "s", "内容内容内容。"));
        indexService.reindex(doc.getId());
        assertThat(reindexService.isIndexConsistent()).isTrue();

        when(embeddingProvider.modelId()).thenReturn("model-b");
        assertThat(reindexService.isIndexConsistent()).isFalse();

        reindexService.reindexAll();
        assertThat(reindexService.isIndexConsistent()).isTrue();
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("rx-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
