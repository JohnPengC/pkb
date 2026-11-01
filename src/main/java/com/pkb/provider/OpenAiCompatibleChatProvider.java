package com.pkb.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkb.common.error.BizException;
import com.pkb.provider.dto.OpenAiDtos.ChatRequest;
import com.pkb.provider.dto.OpenAiDtos.ChatResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleChatProvider implements ChatProvider {

    private final ModelConfigHolder holder;
    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleChatProvider(ModelConfigHolder holder,
                                        RestClient.Builder restBuilder,
                                        WebClient.Builder webBuilder) {
        this.holder = holder;
        this.restClient = restBuilder.build();
        this.webClient = webBuilder.build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        var ep = holder.currentChat();
        ChatRequest body = new ChatRequest(ep.model(), List.of(
                new ChatRequest.Message("system", systemPrompt),
                new ChatRequest.Message("user", userPrompt)), false);
        try {
            ChatResponse resp = restClient.post()
                    .uri(ep.baseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ep.apiKey())
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                throw new BizException("模型返回为空，请稍后重试");
            }
            return resp.choices().get(0).message().content();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用模型服务失败：" + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        var ep = holder.currentChat();
        Map<String, Object> body = Map.of(
                "model", ep.model(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        return webClient.post()
                .uri(ep.baseUrl() + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ep.apiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<String>() {
                })
                .takeUntil("[DONE]"::equals)
                .filter(line -> !"[DONE]".equals(line) && !line.isBlank())
                .mapNotNull(this::extractDelta)
                .onErrorMap(e -> new BizException("调用模型服务失败：" + e.getMessage()));
    }

    private String extractDelta(String dataLine) {
        try {
            JsonNode node = objectMapper.readTree(dataLine);
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
