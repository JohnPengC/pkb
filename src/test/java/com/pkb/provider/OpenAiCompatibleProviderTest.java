package com.pkb.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleProviderTest {

    private MockWebServer server;
    private ModelConfigHolder holder;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/v1").toString().replaceAll("/$", "");
        holder = new ModelConfigHolder(new ModelProperties(
                new ModelProperties.ModelEndpoint(base, "test-key", "chat-model"),
                new ModelProperties.ModelEndpoint(base, "test-key", "embed-model")));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void chatReturnsContent() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(om.writeValueAsString(Map.of("choices", List.of(
                        Map.of("message", Map.of("content", "你好")))))));
        var provider = new OpenAiCompatibleChatProvider(holder, RestClient.builder(), WebClient.builder());
        assertThat(provider.chat("sys", "user")).isEqualTo("你好");
    }

    @Test
    void embedReturnsFloatArray() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(om.writeValueAsString(Map.of("data", List.of(
                        Map.of("embedding", List.of(0.1, 0.2, 0.3)))))));
        var provider = new OpenAiCompatibleEmbeddingProvider(holder, RestClient.builder());
        assertThat(provider.embed("文本")).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(provider.modelId()).isEqualTo("embed-model");
    }
}
