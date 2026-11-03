package com.pkb.retrieval;

import com.pkb.common.error.BizException;
import com.pkb.knowledge.KnowledgeDocumentRepository;
import com.pkb.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingProvider embeddingProvider;
    private final ChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;

    /**
     * 混合检索：向量 topK*2 + 关键词 topK*2，同块合并取高分。
     * includeDraft=false 时仅检索 CONFIRMED 文档（默认质量闸门）。
     */
    public List<SearchHit> search(String query, int topK, boolean includeDraft) {
        if (query == null || query.isBlank()) {
            throw new BizException("请输入检索内容");
        }
        boolean confirmedOnly = !includeDraft;
        float[] queryVector = embeddingProvider.embed(query.strip());

        List<ChunkRow> vectorHits = chunkRepository.vectorSearch(queryVector, topK * 2, confirmedOnly);
        List<ChunkRow> keywordHits = chunkRepository.keywordSearch(query.strip(), topK * 2, confirmedOnly);

        // 按 chunk id 合并：向量分 = 1 - cosine distance；关键词分 = 0.5
        Map<UUID, Scored> merged = new LinkedHashMap<>();
        for (ChunkRow row : vectorHits) {
            merged.put(row.id(), new Scored(row, 1.0 - row.distance()));
        }
        for (ChunkRow row : keywordHits) {
            merged.merge(row.id(), new Scored(row, 0.5),
                    (a, b) -> a.score() >= b.score() ? a : b);
        }

        List<Scored> sorted = merged.values().stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();

        // 双索引配比：提炼块与原文块各保至少 quota 个（结构 + 细节），其余名额按分补齐
        int quota = Math.max(1, topK / 2);
        List<Scored> top = new ArrayList<>();
        Map<String, Integer> typeCount = new HashMap<>();
        for (Scored s : sorted) {
            if (top.size() >= Math.min(topK, 2 * quota)) {
                break;
            }
            int seen = typeCount.merge(s.row().chunkType(), 1, Integer::sum);
            if (seen <= quota) {
                top.add(s);
            }
        }
        for (Scored s : sorted) {
            if (top.size() >= topK) {
                break;
            }
            if (!top.contains(s)) {
                top.add(s);
            }
        }

        Map<UUID, String> titles = documentRepository.findAllById(
                        top.stream().map(s -> s.row().documentId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(d -> d.getId(), d -> d.getTitle()));

        List<SearchHit> hits = new ArrayList<>();
        for (Scored s : top) {
            hits.add(new SearchHit(s.row().documentId(),
                    titles.getOrDefault(s.row().documentId(), "（未知文档）"),
                    snippet(s.row().content()),
                    s.score()));
        }
        return hits;
    }

    private String snippet(String content) {
        String oneLine = content.replace('\n', ' ').strip();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "…";
    }

    private record Scored(ChunkRow row, double score) {
    }
}
