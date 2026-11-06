package com.pkb.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkb.provider.ChatProvider;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    static final String SYSTEM_PROMPT = """
            你是个人知识库助手。仅依据下方"知识库资料"回答问题：
            - 回答中使用 [编号] 标注引用来源（如 [1][2]）。
            - 资料不足以回答时，明确说明"知识库中没有足够信息"，不要编造。
            - 可使用"对话历史"理解指代与上下文，但答案仍须以知识库资料为准。
            - 使用简体中文，回答简洁。
            """;

    static final int MAX_HISTORY_TURNS = 5;

    private final RetrievalService retrievalService;
    private final ChatProvider chatProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 对话轮次：question 为当时提问，answer 为最终回答（流式结束后由前端回传）。 */
    public record Turn(String question, String answer) {
    }

    public Flux<ServerSentEvent<Object>> ask(String question, List<Turn> history, boolean includeDraft) {
        List<Turn> recent = history == null ? List.of()
                : history.size() > MAX_HISTORY_TURNS ? history.subList(history.size() - MAX_HISTORY_TURNS, history.size())
                : history;
        List<SearchHit> hits = retrievalService.search(question, 5, includeDraft);
        String prompt = buildPrompt(question, hits, recent);

        ServerSentEvent<Object> sourcesEvent = ServerSentEvent.<Object>builder()
                .event("sources")
                .data(hits.stream().map(h -> Map.of(
                        "documentId", h.documentId().toString(),
                        "title", h.title(),
                        "snippet", h.snippet(),
                        "evidence", h.evidence(),
                        "score", h.score())).toList())
                .build();
        ServerSentEvent<Object> doneEvent = ServerSentEvent.<Object>builder()
                .event("done").data("{}").build();

        Flux<ServerSentEvent<Object>> tokens = chatProvider
                .chatStream(SYSTEM_PROMPT, prompt)
                .map(token -> ServerSentEvent.<Object>builder().event("token").data(token).build());

        return Flux.concat(Flux.just(sourcesEvent), tokens, Flux.just(doneEvent));
    }

    /** history 为 URL 传来的 JSON 字符串，解析失败按空历史处理。 */
    public List<Turn> parseHistory(String historyJson) {
        if (historyJson == null || historyJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(historyJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Turn.class));
        } catch (Exception e) {
            log.warn("对话历史解析失败，按空历史处理: {}", e.getMessage());
            return List.of();
        }
    }

    public String buildPrompt(String question, List<SearchHit> hits) {
        return buildPrompt(question, hits, List.of());
    }

    public String buildPrompt(String question, List<SearchHit> hits, List<Turn> history) {
        StringBuilder sb = new StringBuilder("知识库资料：\n");
        if (hits.isEmpty()) {
            sb.append("（知识库中没有找到相关资料）\n");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                SearchHit h = hits.get(i);
                sb.append("[").append(i + 1).append("] ").append(h.title())
                  .append("：").append(h.snippet()).append("\n");
            }
        }
        if (history != null && !history.isEmpty()) {
            sb.append("\n对话历史（仅供参考上下文，答案以知识库资料为准）：\n");
            for (Turn t : history) {
                sb.append("问：").append(t.question()).append("\n");
                sb.append("答：").append(t.answer() == null ? "" : t.answer()).append("\n");
            }
        }
        sb.append("\n用户问题：").append(question);
        return sb.toString();
    }
}
