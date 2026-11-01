package com.pkb.retrieval;

import com.pkb.provider.ModelConfigHolder;
import com.pkb.provider.ModelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ModelConfigHolder holder;
    private final ReindexService reindexService;

    @GetMapping("/model")
    public Map<String, Object> model() {
        ModelProperties.ModelEndpoint chat = holder.currentChat();
        ModelProperties.ModelEndpoint embedding = holder.currentEmbedding();
        return Map.of(
                "chat", Map.of("baseUrl", chat.baseUrl(), "model", chat.model()),
                "embedding", Map.of("model", embedding.model()),
                "indexConsistent", reindexService.isIndexConsistent());
    }

    @PutMapping("/model/chat")
    public Map<String, Object> switchChat(@RequestBody Map<String, String> body) {
        holder.switchChat(body.get("baseUrl"), body.get("apiKey"), body.get("model"));
        ModelProperties.ModelEndpoint chat = holder.currentChat();
        return Map.of("baseUrl", chat.baseUrl(), "model", chat.model());
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        return Map.of("reindexed", reindexService.reindexAll(),
                "indexConsistent", reindexService.isIndexConsistent());
    }
}
