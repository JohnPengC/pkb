package com.pkb.retrieval;

import com.pkb.knowledge.DocumentSavedEvent;
import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeDocumentRepository;
import com.pkb.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final KnowledgeDocumentRepository documentRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingProvider embeddingProvider;
    private final ChunkRepository chunkRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentSaved(DocumentSavedEvent event) {
        try {
            reindex(event.documentId());
        } catch (Exception e) {
            // 失败时旧块仍在（delete 与 insert 同事务回滚），文档保持旧索引可查
            log.error("文档 {} 向量化失败，保留旧索引: {}", event.documentId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reindex(UUID documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow();
        List<String> chunks = chunkingService.split(doc.getContent());
        if (chunks.isEmpty()) {
            log.warn("文档 {} 内容为空，跳过向量化", documentId);
            return;
        }
        List<float[]> vectors = embeddingProvider.embedAll(chunks);
        chunkRepository.deleteByDocumentId(documentId);
        chunkRepository.insertChunks(documentId, chunks, vectors, embeddingProvider.modelId());
        log.info("文档 {} 重新向量化完成，共 {} 块", documentId, chunks.size());
    }
}
