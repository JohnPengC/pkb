package com.pkb.distillation;

import com.pkb.provider.ChatProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DistillationServiceTest {

    @Test
    void shortTextDistillsInSingleCall() {
        AtomicInteger calls = new AtomicInteger();
        ChatProvider chat = stub(calls, """
                <<<TITLE>>>
                标题
                <<<SUMMARY>>>
                摘要
                <<<CONTENT>>>
                正文
                """);
        var service = new DistillationService(chat, new DraftParser());
        DistilledDraft draft = service.distill("一段不长的资料内容", "a.txt");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(draft.title()).isEqualTo("标题");
    }

    @Test
    void longTextUsesSegmentThenAggregate() {
        AtomicInteger calls = new AtomicInteger();
        ChatProvider chat = stub(calls, """
                <<<TITLE>>>
                汇总标题
                <<<SUMMARY>>>
                汇总摘要
                <<<CONTENT>>>
                汇总正文
                """);
        var service = new DistillationService(chat, new DraftParser());
        String longText = "长文本段落。".repeat(3000); // > 单段阈值
        DistilledDraft draft = service.distill(longText, "b.txt");
        assertThat(calls.get()).isGreaterThan(2); // 多段提炼 + 1 次汇总
        assertThat(draft.title()).isEqualTo("汇总标题");
    }

    private ChatProvider stub(AtomicInteger calls, String response) {
        return new ChatProvider() {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                return response;
            }

            @Override
            public Flux<String> chatStream(String systemPrompt, String userPrompt) {
                return Flux.just(response);
            }
        };
    }
}
