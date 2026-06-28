// Detects whether the app is running inside the prerender service (headless
// Chrome) or is being fetched by a crawler.
//
// Why this exists: the German SEO routes (/, /modelle, ...) redirect first-time
// non-German visitors to the English variant for UX. That redirect must NEVER
// fire for the prerender/bot - otherwise every German URL is served as English
// with an English canonical, collapsing all German pages onto the English ones
// and destroying their indexing. Real users keep the redirect; crawlers receive
// the content of the exact URL they requested.
//
// Two independent signals are checked so detection holds regardless of which one
// the prerender exposes:
//  - navigator.webdriver === true: set by CDP-controlled headless Chrome (prerender).
//  - bot / prerender token in the user agent: nginx forwards the original bot UA
//    to the prerender unchanged.
const CRAWLER_UA = /bot|crawl|spider|slurp|prerender|googlebot|bingbot|yandex|duckduck|baidu|facebookexternalhit|twitterbot|linkedinbot|whatsapp|applebot|ahrefs|semrush|gptbot|claudebot|anthropic-ai|perplexity|chatgpt|oai-searchbot|cohere/i

interface CrawlerNavigator {
    userAgent?: string
    webdriver?: boolean
}

export function isCrawler(
    nav: CrawlerNavigator = typeof navigator !== 'undefined' ? navigator : {},
): boolean {
    if (nav.webdriver === true) return true
    return typeof nav.userAgent === 'string' && CRAWLER_UA.test(nav.userAgent)
}
