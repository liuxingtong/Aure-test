package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class SearchLatestNewsTool implements AiTool {
    @Override public String name() { return "search_latest_news"; }
    @Override public String description() { return "搜索指定主题的最新新闻；需要配置外部新闻服务后启用"; }

    @Override
    public String execute(JsonNode arguments) {
        var query = arguments.path("query").asText("").trim();
        if (query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        return "新闻搜索服务尚未配置，无法实时检索：" + query;
    }
}
