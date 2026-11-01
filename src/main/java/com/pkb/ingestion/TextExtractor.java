package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TextExtractor {

    private final Tika tika = new Tika();

    public String extract(Path file) {
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
