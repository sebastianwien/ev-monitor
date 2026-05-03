package com.evmonitor.infrastructure.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalNewsServiceTest {

    private ExternalNewsService service;

    @BeforeEach
    void setUp() {
        service = new ExternalNewsService();
    }

    @Test
    void parseRssItems_returnsItemsWithTitleAndUrl() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Good News Test Feed</title>
                    <item>
                      <title>Solarausbau in Deutschland erreicht neues Rekordniveau</title>
                      <link>https://example.com/solar-rekord</link>
                      <description>Beschreibung des Artikels</description>
                    </item>
                    <item>
                      <title>Windkraft liefert 60% des deutschen Stroms</title>
                      <link>https://example.com/windkraft-60</link>
                    </item>
                  </channel>
                </rss>
                """;

        List<ExternalNewsService.NewsItem> result = service.parseRssItems(xml);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Solarausbau in Deutschland erreicht neues Rekordniveau");
        assertThat(result.get(0).url()).isEqualTo("https://example.com/solar-rekord");
        assertThat(result.get(1).title()).isEqualTo("Windkraft liefert 60% des deutschen Stroms");
        assertThat(result.get(1).url()).isEqualTo("https://example.com/windkraft-60");
    }

    @Test
    void parseRssItems_skipsItemsWithMissingTitle() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <link>https://example.com/no-title</link>
                    </item>
                    <item>
                      <title>Gute Nachricht</title>
                      <link>https://example.com/with-title</link>
                    </item>
                  </channel>
                </rss>
                """;

        List<ExternalNewsService.NewsItem> result = service.parseRssItems(xml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Gute Nachricht");
    }

    @Test
    void parseRssItems_skipsItemsWithMissingLink() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Artikel ohne Link</title>
                    </item>
                    <item>
                      <title>Artikel mit Link</title>
                      <link>https://example.com/with-link</link>
                    </item>
                  </channel>
                </rss>
                """;

        List<ExternalNewsService.NewsItem> result = service.parseRssItems(xml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Artikel mit Link");
    }

    @Test
    void parseRssItems_handlesEmptyFeed() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Leerer Feed</title>
                  </channel>
                </rss>
                """;

        List<ExternalNewsService.NewsItem> result = service.parseRssItems(xml);

        assertThat(result).isEmpty();
    }

    @Test
    void parseRssItems_trimsTitlesAndUrls() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>  Nachricht mit Leerzeichen  </title>
                      <link>  https://example.com/spaces  </link>
                    </item>
                  </channel>
                </rss>
                """;

        List<ExternalNewsService.NewsItem> result = service.parseRssItems(xml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Nachricht mit Leerzeichen");
        assertThat(result.get(0).url()).isEqualTo("https://example.com/spaces");
    }

    @Test
    void getNewsItems_returnsEmptyListInitially() {
        assertThat(service.getNewsItems()).isEmpty();
    }
}
