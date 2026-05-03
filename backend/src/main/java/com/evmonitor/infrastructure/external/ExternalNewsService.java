package com.evmonitor.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches German good news from RSS feeds and caches them in memory.
 * Refreshed daily at 3 AM. Falls back silently to cached list if feeds are unreachable.
 */
@Component
@Slf4j
public class ExternalNewsService {

    private static final List<String> RSS_FEEDS = List.of(
            "https://www.goodnews.eu/feed/",
            "https://www.klimareporter.de/?format=feed&type=rss"
    );

    private static final int MAX_ITEMS_PER_FEED = 5;

    private volatile List<NewsItem> cachedNews = new ArrayList<>();

    public record NewsItem(String title, String url) {}

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refreshNews();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void refreshNews() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        List<NewsItem> allItems = new ArrayList<>();

        for (String feedUrl : RSS_FEEDS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(feedUrl))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/rss+xml, application/xml, text/xml")
                        .header("User-Agent", "EV-Monitor/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<NewsItem> items = parseRssItems(response.body());
                    List<NewsItem> limited = items.stream().limit(MAX_ITEMS_PER_FEED).toList();
                    allItems.addAll(limited);
                    log.info("ExternalNewsService: loaded {} items from {}", limited.size(), feedUrl);
                } else {
                    log.warn("ExternalNewsService: feed {} returned status {}", feedUrl, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("ExternalNewsService: could not fetch {}: {}", feedUrl, e.getMessage());
            }
        }

        if (!allItems.isEmpty()) {
            cachedNews = Collections.unmodifiableList(allItems);
            log.info("ExternalNewsService: total {} news items cached", allItems.size());
        }
    }

    /**
     * Parses RSS 2.0 XML and extracts items with title and link.
     * Package-private for testing.
     */
    List<NewsItem> parseRssItems(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        NodeList items = doc.getElementsByTagName("item");
        List<NewsItem> result = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String title = getFirstTagText(item, "title");
            String link = getFirstTagText(item, "link");

            if (title != null && !title.isBlank() && link != null && !link.isBlank()) {
                result.add(new NewsItem(title.trim(), link.trim()));
            }
        }

        return result;
    }

    private String getFirstTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text != null ? text.trim() : null;
    }

    public List<NewsItem> getNewsItems() {
        return cachedNews;
    }
}
