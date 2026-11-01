package com.pkb.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService chunking = new ChunkingService();

    @Test
    void splitsByHeadings() {
        String md = "## 第一节\n" + "内容一。".repeat(50) + "\n\n## 第二节\n" + "内容二。".repeat(50);
        List<String> chunks = chunking.split(md);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0)).contains("第一节");
        assertThat(chunks.get(chunks.size() - 1)).contains("第二节");
    }

    @Test
    void mergesTinyFragments() {
        List<String> chunks = chunking.split("短句一。\n\n短句二。\n\n短句三。");
        assertThat(chunks).hasSize(1);
    }

    @Test
    void splitsOversizedParagraph() {
        String longParagraph = "无换行长文本。".repeat(200); // 单段 1400 字符
        List<String> chunks = chunking.split(longParagraph);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(600));
    }
}
