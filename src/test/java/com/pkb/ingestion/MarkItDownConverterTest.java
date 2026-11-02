package com.pkb.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkItDownConverterTest {

    private final MarkItDownConverter converter = new MarkItDownConverter(true, "cat", 10);

    @Test
    void returnsStdoutOnSuccess(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.txt");
        Files.writeString(file, "# 标题\n\n正文");
        assertThat(converter.convert(file)).contains("# 标题").contains("正文");
    }

    @Test
    void throwsOnNonZeroExit(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("b.txt");
        Files.writeString(file, "x");
        var failing = new MarkItDownConverter(true, "false", 10);
        assertThatThrownBy(() -> failing.convert(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("退出码");
    }

    @Test
    void throwsOnBlankOutput(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("c.txt");
        Files.writeString(file, "x");
        var silent = new MarkItDownConverter(true, "true", 10);
        assertThatThrownBy(() -> silent.convert(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("输出为空");
    }

    @Test
    void throwsWhenProcessCannotStart() {
        var missing = new MarkItDownConverter(true, "/nonexistent/markitdown", 10);
        assertThatThrownBy(() -> missing.convert(Path.of("/tmp/whatever.txt")))
                .isInstanceOf(IOException.class);
    }
}
