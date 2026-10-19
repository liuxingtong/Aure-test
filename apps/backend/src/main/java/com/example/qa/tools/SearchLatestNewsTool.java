package com.example.qa.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SearchLatestNewsTool implements AiTool {
    private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title><!\\[CDATA\\[(.*?)]]></title>|<title>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("<link>(.*?)</link>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    @Override public String name() { return "search_latest_news"; }
    @Override public String description() { return "搜索指定主题的最新新闻；需要配置外部新闻服务后启用"; }

    @Override
    public String execute(JsonNode arguments) {
        var query = arguments.path("query").asText("").trim();
        if (query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        var results = fetchResults(query, encoded);
        if (!results.isBlank()) return results + "\n\n---\n也可以继续在浏览器中打开：\n\n"
                + "- [Google News 搜索「" + query + "」](https://news.google.com/search?q=" + encoded + ")\n"
                + "- [Bing News 搜索「" + query + "」](https://www.bing.com/news/search?q=" + encoded + ")";
        return "已生成外部新闻搜索入口：\n\n"
                + "- [使用 Google News 搜索「" + query + "」](https://news.google.com/search?q=" + encoded + ")\n"
                + "- [使用 Bing News 搜索「" + query + "」](https://www.bing.com/news/search?q=" + encoded + ")\n\n"
                + "请点击链接在系统默认浏览器中查看最新结果。";
    }

    private String fetchResults(String query, String encoded) {
        try {
            var request = HttpRequest.newBuilder(URI.create("https://www.bing.com/news/search?q=" + encoded + "&format=rss"))
                    .timeout(Duration.ofSeconds(12)).header("User-Agent", "yunhe/0.1").GET().build();
            var xml = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            var items = new ArrayList<String>();
            Matcher matcher = ITEM.matcher(xml);
            while (matcher.find() && items.size() < 5) {
                var item = matcher.group(1);
                var title = first(TITLE, item);
                var link = first(LINK, item);
                if (!title.isBlank() && !link.isBlank()) items.add("- [" + clean(title) + "](" + clean(link) + ")");
            }
            return items.isEmpty() ? "" : "Bing News 实时结果（最多 5 条）：\n\n" + String.join("\n", items);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String first(Pattern pattern, String input) {
        var matcher = pattern.matcher(input);
        if (!matcher.find()) return "";
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static String clean(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").trim();
    }
}
