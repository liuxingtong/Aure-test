package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class SearchLatestNewsTool implements AiTool {
    @Override public String name() { return "search_latest_news"; }
    @Override public String description() { return "搜索指定主题的最新新闻；需要配置外部新闻服务后启用"; }

    @Override
    public String execute(JsonNode arguments) {
        var query = arguments.path("query").asText("").trim();
        if (query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "已生成外部新闻搜索入口：\n\n"
                + "- [使用 Google News 搜索「" + query + "」](https://news.google.com/search?q=" + encoded + ")\n"
                + "- [使用 Bing News 搜索「" + query + "」](https://www.bing.com/news/search?q=" + encoded + ")\n\n"
                + "请点击链接在系统默认浏览器中查看最新结果。";
    }
}
