package com.pkb.knowledge;

import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.SourceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VersionRepository versionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 由提炼结果创建 v1 草稿，并触发首次向量化事件。 */
    @Transactional
    public KnowledgeDocument createDraft(SourceDocument source, DistilledDraft draft) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setSourceDocument(source);
        doc.setTitle(draft.title());
        doc.setSummary(draft.summary());
        doc.setContent(draft.content());
        documentRepository.save(doc);

        KnowledgeDocumentVersion v1 = new KnowledgeDocumentVersion();
        v1.setDocument(doc);
        v1.setVersionNo(1);
        v1.setContent(draft.content());
        v1.setChangeNote("AI 提炼生成");
        versionRepository.save(v1);

        eventPublisher.publishEvent(new DocumentSavedEvent(doc.getId()));
        return doc;
    }
}
