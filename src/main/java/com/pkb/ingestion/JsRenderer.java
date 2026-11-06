package com.pkb.ingestion;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JS 渲染页面的无头浏览器兜底：普通页面走 jsoup 直取；
 * 正文为空（SPA）时才触发 Playwright 渲染，渲染结果交给 UrlFetcher 重新提取。
 * 未启用（默认 false，容器内置 true）或渲染失败时返回 empty，调用方回退原错误。
 */
@Slf4j
@Component
public class JsRenderer {

    private final boolean enabled;
    private volatile Playwright playwright;
    private volatile Browser browser;

    public JsRenderer(@Value("${pkb.render.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 渲染页面并返回最终 DOM（networkidle 后取 content）。失败返回 empty。 */
    public Optional<String> render(String url) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            BrowserContext context = browser().newContext();
            try {
                Page page = context.newPage();
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(30_000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));
                return Optional.of(page.content());
            } finally {
                context.close();
            }
        } catch (Exception e) {
            log.warn("JS 渲染失败（{}）：{}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private Browser browser() {
        if (browser == null || !browser.isConnected()) {
            synchronized (this) {
                if (browser == null || !browser.isConnected()) {
                    if (playwright == null) {
                        playwright = Playwright.create();
                    }
                    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                    log.info("Playwright chromium 已启动");
                }
            }
        }
        return browser;
    }
}
