package com.pkb.chat;

import com.pkb.provider.ChatProvider;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    static final String SYSTEM_PROMPT = """
            你是个人知识库助手。仅依据下方"知识库资料"回答问题：
            - 回答中使用 [编号] 标注引用来源（如 [1][2]）。
            - 资料不足以回答时，明确说明"知识库中没有足够信息"，不要编造。
            - 使用简体中文，回答简洁。
            """;

    private final RetrievalService retrievalService;
    private final ChatProvider chatProvider;

    public Flux<ServerSentEvent<Object>> ask(String question, boolean includeDraft) {
        List<SearchHit> hits = retrievalService.search(question, 5, includeDraft);
        String prompt = buildPrompt(question, hits);

        ServerSentEvent<Object> sourcesEvent = ServerSentEvent.<Object>builder()
                .event("sources")
                .data(hits.stream().map(h -> Map.of(
                        "documentId", h.documentId().toString(),
                        "title", h.title(),
                        "snippet", h.snippet())).toList())
                .build();
        ServerSentEvent<Object> doneEvent = ServerSentEvent.<Object>builder()
                .event("done").data("{}").build();

        Flux<ServerSentEvent<Object>> tokens = chatProvider
                .chatStream(SYSTEM_PROMPT, prompt)
                .map(token -> ServerSentEvent.<Object>builder().event("token").data(token).build());

        return Flux.concat(Flux.just(sourcesEvent), tokens, Flux.just(doneEvent));
    }

    public String buildPrompt(String question, List<SearchHit> hits) {
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
        sb.append("\n用户问题：").append(question);
        return sb.toString();
    }
}
