package com.pkb.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 前端路由回退：直接访问或刷新 /docs、/import、/chat 等前端路由时
 * 返回 index.html，由 Vue Router 接管。仅覆盖前端路由，不影响 /api、/sse 与静态资源。
 */
@Controller
public class SpaFallbackController {

    @GetMapping({"/docs", "/docs/", "/docs/{id}", "/import", "/chat"})
    public String index() {
        return "forward:/index.html";
    }
}
