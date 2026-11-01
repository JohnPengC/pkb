package com.pkb.retrieval;

import com.pkb.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexService {

    private final ChunkRepository chunkRepository;
    private final EmbeddingProvider embeddingProvider;
    private final IndexService indexService;

    /** 库中向量的模型标识与当前配置一致则为 true；无向量数据时视为一致。 */
    public boolean isIndexConsistent() {
        String used = chunkRepository.findDistinctEmbeddingModel();
        return used == null || used.equals(embeddingProvider.modelId());
    }

    /** 全量重建（同步逐文档执行，失败单个跳过并记日志）。返回成功重建的文档数。 */
    public int reindexAll() {
        List<UUID> ids = chunkRepository.findAllDocumentIds();
        int ok = 0;
        for (UUID id : ids) {
            try {
                indexService.reindex(id);
                ok++;
            } catch (Exception e) {
                log.error("重建文档 {} 索引失败: {}", id, e.getMessage());
            }
        }
        return ok;
    }
}
