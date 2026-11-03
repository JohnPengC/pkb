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
        var rows = chunkRepository.vectorSearch(fakeVector(0), 10, false).stream()
                .filter(r -> r.documentId().equals(doc.getId())).toList();
        assertThat(rows).isNotEmpty();

        // 改内容后再重建，旧提炼块应被替换
        knowledgeService.save(doc.getId(), "## 全新\n完全不同的内容。", "重写");
        indexService.reindex(doc.getId());
        var after = chunkRepository.vectorSearch(fakeVector(0), 10, false).stream()
                .filter(r -> r.documentId().equals(doc.getId()) && r.chunkType().equals("DISTILLED")).toList();
        assertThat(after).allMatch(r -> r.content().contains("全新") || r.content().contains("完全不同"));
    }

    @Test
    void reindexBuildsDistilledAndRawPools() throws Exception {
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector(texts.indexOf(t))).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("test-embedding");

        // 真实存在的源文件（原文池重新抽取的前提）
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "pkb-test-storage");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path file = dir.resolve("raw-pool-" + System.nanoTime() + ".txt");
        java.nio.file.Files.writeString(file, "原文细节内容：第一段。".repeat(40));

        SourceDocument source = new SourceDocument();
        source.setSourceType(SourceType.FILE);
        source.setFileName(file.getFileName().toString());
        source.setContentHash(HashUtil.sha256(file.toString()));
        source.setStoragePath(file.toString());
        source = sourceRepository.save(source);

        KnowledgeDocument doc = knowledgeService.createDraft(source,
                new DistilledDraft("标题", "摘要", "## 一\n" + "提炼段落内容。".repeat(100)));

        indexService.reindex(doc.getId());

        var types = chunkRepository.vectorSearch(fakeVector(0), 50, false).stream()
                .filter(r -> r.documentId().equals(doc.getId()))
                .map(ChunkRow::chunkType)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(types).contains("DISTILLED", "RAW");
    }

    @Test
    void rawModeBuildsOnlyContentPool() throws Exception {
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector(texts.indexOf(t))).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("test-embedding");

        // 真实存在的源文件（RAW 模式虽跳过来源池，抽取器仍可能被调用于兜底，保持场景真实）
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "pkb-test-storage");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path file = dir.resolve("raw-mode-" + System.nanoTime() + ".txt");
        java.nio.file.Files.writeString(file, "RAW 模式原文内容。".repeat(10));

        SourceDocument source = new SourceDocument();
        source.setSourceType(SourceType.FILE);
        source.setFileName(file.getFileName().toString());
        source.setContentHash(HashUtil.sha256(file.toString()));
        source.setStoragePath(file.toString());
        source = sourceRepository.save(source);

        KnowledgeDocument doc = knowledgeService.createDraft(source,
                new DistilledDraft("标题", "摘要", "RAW 模式正文内容。".repeat(50)),
                com.pkb.ingestion.ProcessMode.RAW);
        indexService.reindex(doc.getId());

        var types = chunkRepository.vectorSearch(fakeVector(0), 50, false).stream()
                .filter(r -> r.documentId().equals(doc.getId()))
                .map(ChunkRow::chunkType)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(types).containsExactly("DISTILLED");
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
