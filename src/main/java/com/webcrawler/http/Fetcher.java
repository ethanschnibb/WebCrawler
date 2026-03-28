package com.webcrawler.http;

import java.util.Optional;

import org.jsoup.nodes.Document;

/**
 * Abstraction for fetching web resources.
 *
 * Separating this behind an interface allows:
 *
 * - Swapping HTTP implementations (Jsoup, OkHttp, etc.)
 * - Easier unit testing via mocking
 * - Centralised retry and timeout handling
 * - Cleaner crawler logic (crawler shouldn't know HTTP details)
 *
 * Implementations are expected to:
 *
 * - Be thread safe (called by concurrent crawler workers)
 * - Handle retries internally
 * - Apply timeouts
 * - Respect crawler politeness configuration
 *
 * Failures should be represented by Optional.empty()
 * rather than throwing exceptions to keep crawler logic simple.
 */
public interface Fetcher {
    /**
     * Fetches a URL and parses it into a HTML Document.
     *
     * Intended for normal page crawling.
     *
     * @param url absolute URL to fetch
     *
     * @return parsed Document if successful, otherwise Optional.empty()
     * if:
     *
     * - request fails
     * - non-HTML response
     * - timeout occurs
     * - retries exhausted
     *
     * Implementations should NOT throw on normal network failures.
     */
    Optional<Document> fetch(String url);
    /**
     * Fetches raw text content from a URL.
     *
     * Primarily used for robots.txt retrieval where parsing into a
     * Document is unnecessary.
     *
     * @param url absolute URL to fetch
     *
     * @return response body as text if successful, otherwise Optional.empty()
     *
     * Implementations should apply the same retry and timeout behaviour
     * as {@link #fetch(String)}.
     */
    Optional<String> fetchText(String url); 
}
