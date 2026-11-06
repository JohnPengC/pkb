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
                new SearchHit(UUID.randomUUID(), "信贷风控", "贷前审查是核心环节。", 0.9, "贷前审查是核心环节。"),
                new SearchHit(UUID.randomUUID(), "向量检索", "余弦距离越小越相似。", 0.8, "余弦距离越小越相似。"));
        String prompt = new ChatService(null, null).buildPrompt("贷前怎么做？", hits);
        assertThat(prompt).contains("[1] 信贷风控").contains("[2] 向量检索");
        assertThat(prompt).contains("贷前审查是核心环节");
        assertThat(prompt).contains("贷前怎么做？");
    }

    @Test
    void buildPromptAppendsConversationHistory() {
        var hits = List.of(new SearchHit(UUID.randomUUID(), "信贷风控", "片段", 0.9, "证据句"));
        var history = List.of(new ChatService.Turn("之前问过什么", "之前答过"));
        String prompt = new ChatService(null, null).buildPrompt("那具体阈值呢？", hits, history);
        assertThat(prompt).contains("对话历史").contains("之前问过什么").contains("之前答过");
        assertThat(prompt).contains("那具体阈值呢？");
    }

    @Test
    void parseHistoryToleratesBadJson() {
        var service = new ChatService(null, null);
        assertThat(service.parseHistory(null)).isEmpty();
        assertThat(service.parseHistory("not json")).isEmpty();
        assertThat(service.parseHistory("[{\"question\":\"q\",\"answer\":\"a\"}]")).hasSize(1);
    }

    @Test
    void buildPromptWithoutHits() {
        String prompt = new ChatService(null, null).buildPrompt("问题", List.of());
        assertThat(prompt).contains("知识库中没有找到相关资料");
    }
}
