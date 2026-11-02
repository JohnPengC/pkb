package com.pkb.ingestion;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 调用本机 MarkItDown CLI（微软文档转 Markdown 工具）做结构化抽取。
 * 任何失败都由调用方（TextExtractor）回退到 Tika。
 */
@Slf4j
public class MarkItDownConverter {

    private final boolean enabled;
    private final String command;
    private final int timeoutSeconds;

    public MarkItDownConverter(boolean enabled, String command, int timeoutSeconds) {
        this.enabled = enabled;
        this.command = command;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 把文件转换为 Markdown；失败抛异常，由调用方决定回退。 */
    public String convert(Path file) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command, file.toAbsolutePath().toString())
                .redirectErrorStream(false)
                .start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("MarkItDown 转换超时（" + timeoutSeconds + "s）");
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("MarkItDown 退出码 " + process.exitValue()
                    + (stderr.isBlank() ? "" : "：" + stderr.strip().substring(0, Math.min(200, stderr.strip().length()))));
        }
        if (stdout.isBlank()) {
            throw new IOException("MarkItDown 输出为空");
        }
        return stdout.strip();
    }
}
