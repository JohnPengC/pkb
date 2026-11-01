package com.pkb.distillation;

import com.pkb.provider.ChatProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DistillationService {

    /** 单段直接提炼的字符阈值；超过则分段提炼后汇总 */
    static final int SINGLE_SHOT_THRESHOLD = 12_000;
    /** 分段大小（字符） */
    static final int SEGMENT_SIZE = 8_000;

    private final ChatProvider chatProvider;
    private final DraftParser draftParser;

    public DistilledDraft distill(String rawText, String sourceHint) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.length() <= SINGLE_SHOT_THRESHOLD) {
            String userPrompt = "资料来源：" + sourceHint + "\n\n原始资料：\n" + text;
            return draftParser.parse(chatProvider.chat(PromptTemplates.DISTILL_SYSTEM, userPrompt), sourceHint);
        }
        List<String> segments = split(text);
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String system = PromptTemplates.SEGMENT_SYSTEM.formatted(i + 1, segments.size());
            notes.add(chatProvider.chat(system, segments.get(i)));
        }
        StringBuilder joined = new StringBuilder("资料来源：" + sourceHint + "\n\n");
        for (int i = 0; i < notes.size(); i++) {
            joined.append("【第 ").append(i + 1).append(" 段笔记】\n").append(notes.get(i)).append("\n\n");
        }
        return draftParser.parse(
                chatProvider.chat(PromptTemplates.AGGREGATE_SYSTEM, joined.toString()), sourceHint);
    }

    private List<String> split(String text) {
        List<String> segments = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int to = Math.min(from + SEGMENT_SIZE, text.length());
            // 尽量在换行处切断，避免句子被拦腰截断
            if (to < text.length()) {
                int newline = text.lastIndexOf('\n', to);
                if (newline > from + SEGMENT_SIZE / 2) {
                    to = newline;
                }
            }
            segments.add(text.substring(from, to));
            from = to;
        }
        return segments;
    }
}
