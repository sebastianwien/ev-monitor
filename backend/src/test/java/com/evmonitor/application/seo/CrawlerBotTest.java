package com.evmonitor.application.seo;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerBotTest {

    @Test
    void recognizesAiCrawlersSeparately() {
        assertThat(CrawlerBot.of("Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko); compatible; GPTBot/1.2; +https://openai.com/gptbot"))
                .contains(CrawlerBot.GPTBOT);
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; OAI-SearchBot/1.0; +https://openai.com/searchbot)")).contains(CrawlerBot.OAI_SEARCHBOT);
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; ChatGPT-User/1.0; +https://openai.com/bot)")).contains(CrawlerBot.CHATGPT_USER);
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; ClaudeBot/1.0; +claudebot@anthropic.com)")).contains(CrawlerBot.CLAUDE);
        assertThat(CrawlerBot.of("Claude-User/1.0")).contains(CrawlerBot.CLAUDE);
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; PerplexityBot/1.0)")).contains(CrawlerBot.PERPLEXITY);
        assertThat(CrawlerBot.GPTBOT.isAi()).isTrue();
        assertThat(CrawlerBot.GOOGLEBOT.isAi()).isFalse();
    }

    @Test
    void recognizesSearchEnginesAndIgnoresOwnAuditAndBrowsers() {
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")).contains(CrawlerBot.GOOGLEBOT);
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)")).contains(CrawlerBot.BINGBOT);
        assertThat(CrawlerBot.of("facebookexternalhit/1.1")).contains(CrawlerBot.SOCIAL);
        assertThat(CrawlerBot.of("Mozilla/5.0 (compatible; Googlebot/2.1; ev-monitor-seo-audit)")).isEmpty();
        assertThat(CrawlerBot.of("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/604.1")).isEmpty();
        assertThat(CrawlerBot.of(null)).isEqualTo(Optional.empty());
    }
}
