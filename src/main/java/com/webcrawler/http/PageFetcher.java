package com.webcrawler.http;

import java.io.IOException;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcrawler.config.CrawlerConfig;

/**
 * HTTP implementation of {@link Fetcher} responsible for retrieving web pages.
 *
 * Responsibilities:
 *
 * - Fetch HTML documents for crawler processing
 * - Apply retry logic for transient failures
 * - Apply request timeouts
 * - Identify crawler via User-Agent
 * - Provide raw text fetching for robots.txt
 *
 * Design decisions:
 *
 * - Returns Optional instead of throwing on failure to simplify crawler logic
 * - Stateless and thread safe (shared across concurrent crawler workers)
 * - Retry logic lives here to keep crawler traversal logic clean
 *
 * This class intentionally contains no crawl logic — only HTTP concerns.
 */
public class PageFetcher implements Fetcher {

    private static final Logger logger = LoggerFactory.getLogger(PageFetcher.class);

    // All HTTP config comes from CrawlerConfig — no hardcoded constants here
    private final CrawlerConfig config;

    /**
     * Creates a fetcher using the provided crawler configuration.
     *
     * @param config HTTP and retry configuration shared across crawler components
     */
    public PageFetcher(CrawlerConfig config) {
        this.config = config;
    }

    /**
     * Fetches and parses an HTML page.
     *
     * Retries transient network failures using exponential backoff.
     *
     * @param url absolute URL to fetch
     *
     * @return parsed Document if successful, otherwise Optional.empty() if:
     *
     * - network failure occurs
     * - request times out
     * - retries exhausted
     * - non-HTML response
     *
     * Thread safety:
     * Safe for concurrent use by multiple crawler workers.
     *
     * Design note:
     * Blocking I/O is acceptable here because crawler uses virtual threads,
     * allowing thousands of concurrent requests without OS thread exhaustion.
     */
    @Override
    public Optional<Document> fetch(String url) {
        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(config.getUserAgent())
                        .timeout(config.getTimeoutMs())
                        .get();
                logger.info("Fetched: {}", url);
                return Optional.of(doc);

            } catch (IOException e) {
                logger.warn("Attempt {}/{} failed for {}: {}",
                        attempt, config.getMaxRetries(), url, e.getMessage());

                if (attempt < config.getMaxRetries()) {
                    // Exponential backoff: baseDelay * 2^(attempt-1)
                    // Prevents aggressive retry storms against failing servers
                    long backoffMs = config.getRetryBackoffMs() * (1L << (attempt - 1));
                    logger.debug("Backing off {}ms before retry", backoffMs);
                    sleep(backoffMs);
                }
            }
        }
        logger.error("Giving up on {} after {} attempts", url, config.getMaxRetries());
        return Optional.empty();
    }

    /**
     * Fetches raw text content.
     *
     * Used primarily for robots.txt retrieval where HTML parsing is unnecessary.
     *
     * @param url absolute URL
     *
     * @return response body if successful, otherwise Optional.empty()
     *
     * Failure behaviour:
     * Missing robots.txt should not block crawling, so failures are logged
     * at info level rather than warning/error.
     */
    @Override
    public Optional<String> fetchText(String url) {
        try {
            String body = Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .timeout(config.getTimeoutMs())
                    .ignoreContentType(true) // robots.txt served as text/plain so disable HTML content enforcement
                    .execute()
                    .body();

            logger.info("Fetched text resource: {}", url);
            return Optional.of(body);
        } catch (IOException e) {
            logger.info("Could not fetch {} ({}), will allow all paths", url, e.getMessage());
            return Optional.empty();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}