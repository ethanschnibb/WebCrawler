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
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 1_000;

    /**
     * Fetches the URL and returns its parsed HTML.
     * Returns Optional.empty() if the fetch fails for any reason.
     * With traditional threads:
     * Each blocking network call consumes an OS thread -> limited to hundreds or a few thousand threads.
     * You need CompletableFuture or NIO-based async to scale.
     * With virtual threads:
     * Each blocking call doesn’t consume OS threads.
     * Existing queue + worker + virtual thread pool design is scalable.
     * Adding CompletableFuture wrapping around Jsoup is possible but unnecessary complexity for no real benefit.
     */
    public Optional<Document> fetch(String url) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();
                return Optional.of(doc);
            } catch (IOException e) {
                logger.warn("Attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, url, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    // Exponential backoff before retrying
                    // Rather than all threads retrying at the same time, we stagger them to reduce load on the server and increase chances of success on retry.
                    long backoffMs = RETRY_BACKOFF_MS * (1L << (attempt - 1));
                    logger.debug("Backing off {}ms before retry", backoffMs);
                    sleep(backoffMs);
                }
            }
        }
        return Optional.empty();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}