package com.pkb.chat;

import com.pkb.retrieval.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    @Test
    void buildPromptNumbersSources() {
        var hits = List.of(
                new SearchHit(UUID.randomUUID(), "信贷风控", "贷前审查是核心环节。", 0.9),
                new SearchHit(UUID.randomUUID(), "向量检索", "余弦距离越小越相似。", 0.8));
        String prompt = new ChatService(null, null).buildPrompt("贷前怎么做？", hits);
        assertThat(prompt).contains("[1] 信贷风控").contains("[2] 向量检索");
        assertThat(prompt).contains("贷前审查是核心环节");
        assertThat(prompt).contains("贷前怎么做？");
    }

    @Test
    void buildPromptWithoutHits() {
        String prompt = new ChatService(null, null).buildPrompt("问题", List.of());
        assertThat(prompt).contains("知识库中没有找到相关资料");
    }
}
