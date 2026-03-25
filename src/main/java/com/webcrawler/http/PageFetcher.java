package com.webcrawler.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Fetches a single URL and returns its parsed HTML document.
 *
 * Changes from the original retrieveHtml() method:
 *
 * - Returns Optional<Document> instead of nullable Document. The original
 *   returned null on failure, relying on every caller to remember to
 *   null-check. Optional makes the contract explicit in the type signature.
 *
 * - Moved to its own class. HTTP fetching is a distinct concern from crawl
 *   orchestration — this can be swapped or faked in tests independently.
 *
 * - Added User-Agent and timeout. A polite crawler identifies itself.
 *   A timeout prevents hanging indefinitely on slow servers.
 *
 * - Logs warn (not error) on failure. A failed fetch is recoverable —
 *   the crawler skips that URL and continues. Error overstates the severity.
 */
public class PageFetcher {

    private static final Logger logger = LoggerFactory.getLogger(PageFetcher.class);

    private static final String USER_AGENT = "MonzoCrawler/1.0";
    private static final int TIMEOUT_MS = 10_000;

    /**
     * Fetches the URL and returns its parsed HTML.
     * Returns Optional.empty() if the fetch fails for any reason.
     */
    public Optional<Document> fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            logger.info("Fetched: {}", url);
            return Optional.of(doc);
        } catch (IOException e) {
            logger.warn("Failed to fetch {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}