package com.pkb.retrieval;

import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceType;
import com.pkb.ingestion.TextExtractor;
import com.pkb.ingestion.UrlFetcher;
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

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 事件驱动的双池索引：
 * - DISTILLED 池：提炼文档切块，语义密集，提供"全局结构"
 * - RAW 池：原始资料重新抽取切块，逐字细节，提供"证据"
 * 检索时两池并行混合，回答既有结构又有细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final KnowledgeDocumentRepository documentRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingProvider embeddingProvider;
    private final ChunkRepository chunkRepository;
    private final TextExtractor textExtractor;
    private final UrlFetcher urlFetcher;

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
        String modelId = embeddingProvider.modelId();

        int distilledCount = reindexDistilledPool(documentId, doc, modelId);
        int rawCount = reindexRawPool(documentId, doc, modelId);
        log.info("文档 {} 重新向量化完成：提炼 {} 块，原文 {} 块", documentId, distilledCount, rawCount);
    }

    private int reindexDistilledPool(UUID documentId, KnowledgeDocument doc, String modelId) {
        List<String> chunks = chunkingService.split(doc.getContent());
        if (chunks.isEmpty()) {
            log.warn("文档 {} 提炼内容为空，跳过提炼池向量化", documentId);
            return 0;
        }
        List<float[]> vectors = embeddingProvider.embedAll(chunks);
        chunkRepository.deleteByDocumentIdAndChunkType(documentId, ChunkRepository.TYPE_DISTILLED);
        chunkRepository.insertChunks(documentId, chunks, vectors, modelId, ChunkRepository.TYPE_DISTILLED);
        return chunks.size();
    }

    /** 原文池：从源文件重新抽取（与导入时同一抽取器）。失败仅降级为无原文索引，不阻断提炼池。 */
    private int reindexRawPool(UUID documentId, KnowledgeDocument doc, String modelId) {
        try {
            String rawText = extractRaw(doc.getSourceDocument());
            List<String> chunks = chunkingService.split(rawText);
            if (chunks.isEmpty()) {
                return 0;
            }
            List<float[]> vectors = embeddingProvider.embedAll(chunks);
            chunkRepository.deleteByDocumentIdAndChunkType(documentId, ChunkRepository.TYPE_RAW);
            chunkRepository.insertChunks(documentId, chunks, vectors, modelId, ChunkRepository.TYPE_RAW);
            return chunks.size();
        } catch (Exception e) {
            log.warn("文档 {} 原文索引失败，本次仅保留提炼索引: {}", documentId, e.getMessage());
            return 0;
        }
    }

    private String extractRaw(SourceDocument source) {
        Path stored = Path.of(source.getStoragePath());
        return source.getSourceType() == SourceType.FILE
                ? textExtractor.extract(stored)
                : urlFetcher.extract(stored);
    }
}
