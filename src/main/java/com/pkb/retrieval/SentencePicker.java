package com.pkb.retrieval;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 证据句提取：从命中块中挑出与查询最相关的原句，供引用卡片高亮展示。
 * 评分：查询按空白拆词，统计各词项在句子中的出现次数（全串命中额外加分），取最高句。
 */
@Component
public class SentencePicker {

    public String pick(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> terms = Arrays.stream(query.split("\\s+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (terms.isEmpty()) {
            return "";
        }
        String best = null;
        int bestScore = -1;
        for (String sentence : content.split("[。！？!?\n]")) {
            String s = sentence.strip();
            if (s.isEmpty()) {
                continue;
            }
            int score = 0;
            for (String term : terms) {
                int from = 0;
                while ((from = s.indexOf(term, from)) >= 0) {
                    score++;
                    from += term.length();
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null || bestScore == 0) {
            // 无词项命中时退回片段开头，保证引用总有内容
            String oneLine = content.replace('\n', ' ').strip();
            return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "…";
        }
        return best;
    }
}
