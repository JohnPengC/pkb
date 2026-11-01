package com.pkb.knowledge;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class KnowledgeServiceTest extends AbstractIntegrationTest {

    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    VersionRepository versionRepository;

    @Test
    void saveCreatesNewVersionAndConfirms() {
        KnowledgeDocument doc = knowledgeService.createDraft(newSource(), 
                new DistilledDraft("标题", "摘要", "v1 内容"));
        assertThat(doc.getStatus()).isEqualTo(DocStatus.DRAFT);

        knowledgeService.save(doc.getId(), "v2 内容", "修正错别字");

        KnowledgeDocument saved = knowledgeService.get(doc.getId());
        assertThat(saved.getCurrentVersion()).isEqualTo(2);
        assertThat(saved.getContent()).isEqualTo("v2 内容");
        assertThat(saved.getStatus()).isEqualTo(DocStatus.CONFIRMED);
        assertThat(versionRepository.findByDocumentIdOrderByVersionNoDesc(doc.getId()))
                .hasSize(2);
    }

    @Test
    void confirmWithoutEdit() {
        KnowledgeDocument doc = knowledgeService.createDraft(newSource(),
                new DistilledDraft("标题", "摘要", "内容"));
        KnowledgeDocument confirmed = knowledgeService.confirm(doc.getId());
        assertThat(confirmed.getStatus()).isEqualTo(DocStatus.CONFIRMED);
        assertThat(confirmed.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void setTagsCreatesAndLinks() {
        KnowledgeDocument doc = knowledgeService.createDraft(newSource(),
                new DistilledDraft("标题", "摘要", "内容"));
        knowledgeService.setTags(doc.getId(), List.of("java", " rag ", "java"));
        assertThat(knowledgeService.get(doc.getId())).isNotNull();
        // 通过 list 按标签过滤验证
        var page = knowledgeService.list(null, "rag", null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(KnowledgeDocument::getId).contains(doc.getId());
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("k-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
