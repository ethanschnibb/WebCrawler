package com.webcrawler.http;

import java.io.IOException;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * With traditional threads:
     * Each blocking network call consumes an OS thread -> limited to hundreds or a few thousand threads.
     * You need CompletableFuture or NIO-based async to scale.
     * With virtual threads:
     * Each blocking call doesn’t consume OS threads.
     * Existing queue + worker + virtual thread pool design is scalable.
     * Adding CompletableFuture wrapping around Jsoup won’t improve throughput — just adds complexity.
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