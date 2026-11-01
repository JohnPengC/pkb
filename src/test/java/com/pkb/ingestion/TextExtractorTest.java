package com.pkb.ingestion;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsTxt() throws Exception {
        URL resource = getClass().getResource("/samples/sample.txt");
        String text = extractor.extract(Path.of(resource.toURI()));
        assertThat(text).contains("个人知识库系统").contains("检索与问答");
    }

    @Test
    void extractsMarkdown() throws Exception {
        URL resource = getClass().getResource("/samples/sample.md");
        String text = extractor.extract(Path.of(resource.toURI()));
        assertThat(text).contains("示例文档").contains("要点一");
    }
}
