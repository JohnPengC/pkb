package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文本抽取：优先 MarkItDown（结构化 Markdown，表格/标题保留），
 * 不可用时回退 Apache Tika（纯文本）。对 URL 类型来源不走此类。
 */
@Slf4j
@Component
public class TextExtractor {

    private final Tika tika = new Tika();
    private final MarkItDownConverter converter;

    /** 测试便捷构造：按默认配置（启用，命令缺失时自动回退 Tika）。 */
    public TextExtractor() {
        this(new MarkItDownConverter(true, "markitdown", 120));
    }

    @Autowired
    public TextExtractor(@Value("${pkb.converter.enabled:true}") boolean converterEnabled,
                         @Value("${pkb.converter.command:markitdown}") String converterCommand,
                         @Value("${pkb.converter.timeout-seconds:120}") int converterTimeoutSeconds) {
        this(new MarkItDownConverter(converterEnabled, converterCommand, converterTimeoutSeconds));
    }

    public TextExtractor(MarkItDownConverter converter) {
        this.converter = converter;
    }

    public String extract(Path file) {
        if (converter.isEnabled()) {
            try {
                String markdown = converter.convert(file);
                log.info("MarkItDown 抽取成功：{}（{} 字符）", file.getFileName(), markdown.length());
                return markdown;
            } catch (Exception e) {
                log.warn("MarkItDown 抽取失败，回退 Tika：{}", e.getMessage());
            }
        }
        try (InputStream in = Files.newInputStream(file)) {
            String text = tika.parseToString(in);
            if (text == null || text.isBlank()) {
                throw new BizException("未能从文件中抽取到文本内容");
            }
            return text.strip();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文本抽取失败：" + e.getMessage());
        }
    }
}
