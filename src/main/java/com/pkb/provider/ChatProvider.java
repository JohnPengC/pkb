package com.pkb.provider;

import reactor.core.publisher.Flux;

public interface ChatProvider {
    String chat(String systemPrompt, String userPrompt);
    Flux<String> chatStream(String systemPrompt, String userPrompt);
}
