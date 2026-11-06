package com.pkb.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentencePickerTest {

    private final SentencePicker picker = new SentencePicker();

    @Test
    void picksSentenceWithMostTermHits() {
        String content = "总则部分说明制度目的。单笔分期金额下限 500 元、上限 20 万元。"
                + "附则部分说明解释权限。例外条款见附录。";
        String evidence = picker.pick(content, "分期 上限");
        assertThat(evidence).contains("上限 20 万元");
        assertThat(evidence).doesNotContain("总则");
    }

    @Test
    void fallsBackToSnippetStartWhenNoTermHits() {
        String content = "完全无关的句子。";
        assertThat(picker.pick(content, "不存在XYZ")).isEqualTo("完全无关的句子。");
    }

    @Test
    void blankInputsYieldEmpty() {
        assertThat(picker.pick("", "查询")).isEmpty();
        assertThat(picker.pick("内容", "  ")).isEmpty();
    }
}
