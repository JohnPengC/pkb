package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从已抓取的 HTML 文件中提取正文。
 * 策略：优先 article 标签 → main 标签 → body；移除 script/style/nav/footer 等噪音。
 * 仅支持服务端渲染页面（MVP 边界，见 spec 1.3）。
 */
@Component
public class UrlFetcher {

    public String extract(Path htmlFile) {
        try {
            String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);
            doc.select("script, style, nav, footer, header, aside, noscript").remove();
            Element main = firstNonNull(doc.selectFirst("article"),
                    doc.selectFirst("main"),
                    doc.body());
            if (main == null) {
                throw new BizException("未能从页面中提取正文内容");
            }
            String text = main.text().strip();
            if (text.isBlank()) {
                throw new BizException("页面正文为空，可能是需要 JS 渲染的页面（MVP 暂不支持）");
            }
            String title = doc.title() == null ? "" : doc.title().strip();
            return title.isEmpty() ? text : "# " + title + "\n\n" + text;
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取页面文件失败：" + e.getMessage());
        }
    }

    private Element firstNonNull(Element... candidates) {
        for (Element e : candidates) {
            if (e != null) {
                return e;
            }
        }
        return null;
    }
}
