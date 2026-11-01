package com.pkb.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UrlFetcherTest {

    private final UrlFetcher fetcher = new UrlFetcher();

    @Test
    void extractsArticleText(@TempDir Path dir) throws Exception {
        String html = """
                <html><head><title>测试页面</title></head><body>
                <nav>导航噪音</nav>
                <article><h1>文章标题</h1><p>正文第一段。</p><p>正文第二段。</p></article>
                <script>var x = 1;</script>
                </body></html>
                """;
        Path file = dir.resolve("page.html");
        Files.writeString(file, html);
        String result = fetcher.extract(file);
        assertThat(result).startsWith("# 测试页面");
        assertThat(result).contains("正文第一段");
        assertThat(result).doesNotContain("导航噪音");
        assertThat(result).doesNotContain("var x");
    }
}
