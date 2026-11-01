package com.pkb.distillation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftParserTest {

    private final DraftParser parser = new DraftParser();

    @Test
    void parsesWellFormedOutput() {
        String output = """
                <<<TITLE>>>
                信贷系统架构要点
                <<<SUMMARY>>>
                介绍新一代信贷系统的模块划分。
                <<<CONTENT>>>
                ## 摘要
                正文内容。
                """;
        DistilledDraft draft = parser.parse(output, "兜底标题");
        assertThat(draft.title()).isEqualTo("信贷系统架构要点");
        assertThat(draft.summary()).isEqualTo("介绍新一代信贷系统的模块划分。");
        assertThat(draft.content()).contains("## 摘要");
    }

    @Test
    void fallsBackWhenMarkersMissing() {
        DistilledDraft draft = parser.parse("没有任何标记的输出", "兜底标题");
        assertThat(draft.title()).isEqualTo("兜底标题");
        assertThat(draft.summary()).isEmpty();
        assertThat(draft.content()).isEqualTo("没有任何标记的输出");
    }

    @Test
    void trimsBlankTitleToFallback() {
        String output = """
                <<<TITLE>>>
                <<<SUMMARY>>>
                摘要。
                <<<CONTENT>>>
                正文。
                """;
        DistilledDraft draft = parser.parse(output, "兜底标题");
        assertThat(draft.title()).isEqualTo("兜底标题");
    }
}
