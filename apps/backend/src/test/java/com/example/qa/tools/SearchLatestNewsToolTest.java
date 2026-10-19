package com.example.qa.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchLatestNewsToolTest {
    @Test
    void parsesGoogleNewsRssIntoSearchResults() {
        var rss = "<rss><channel><item><title><![CDATA[AI News]]></title><link>https://example.com/news</link><source url=\"https://example.com\">Example</source><pubDate>Mon, 19 Oct 2026 03:00:00 GMT</pubDate></item></channel></rss>";

        assertThat(new SearchLatestNewsTool().parseRss(rss)).contains("AI News", "https://example.com/news", "Example");
    }
}
