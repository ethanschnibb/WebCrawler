package com.webcrawler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcrawler.http.PageFetcher;
import com.webcrawler.parser.LinkExtractor;
import com.webcrawler.util.UrlNormaliser;
import com.webcrawler.util.UrlValidator;

/**
 * Orchestrates the crawl: maintains the queue of URLs to visit, tracks
 * which URLs have already been seen, and prints results.
 *
 * Changes from the original App class:
 *
 * - No longer static. visitedUrls is an instance field so each Crawler
 *   instance starts with a clean slate. The original static field meant
 *   two crawls in the same JVM would share visited state.
 *
 * - Iterative BFS replaces recursion. An explicit Queue makes the
 *   work-list visible and avoids stack overflow on deep sites. It also
 *   makes adding a thread pool later a straightforward change.
 *
 * - Depth tracking removed. The visited set already prevents infinite
 *   loops — MAX_DEPTH was only needed to stop the recursion running
 *   forever, which the visited set handles cleanly.
 *
 * - PageFetcher and LinkExtractor are injected so tests can supply fakes
 *   without making real network calls.
 *
 * - ConcurrentHashMap.newKeySet() instead of HashSet. Costs nothing now
 *   and makes adding a thread pool later safe without changing this line.
 */
public class Crawler {

    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);

    private final PageFetcher fetcher;
    private final LinkExtractor extractor;

    // Fresh per Crawler instance — no shared static state
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    public Crawler(PageFetcher fetcher, LinkExtractor extractor) {
        this.fetcher = fetcher;
        this.extractor = extractor;
    }

    /**
     * Starts a crawl from startUrl. The allowed host is derived from the
     * start URL so the same Crawler works for any target site.
     */
    public void crawl(String startUrl) {
        // Check if url syntatically valid
        // isValid() checks for valid scheme (protocol) like http/https - doesn't check if if URL is reachable
        // Also checks if valid authority (hostname and IP address match normal format)
        if (!UrlValidator.isValid(startUrl)) {
            logger.error("Invalid start URL: {}", startUrl);
            System.err.println("Invalid start URL: " + startUrl);
            return;
        }

        String allowedHost = extractHost(startUrl);
        if (allowedHost == null) {
            logger.error("Could not determine host from start URL: {}", startUrl);
            return;
        }

        logger.info("Starting crawl of {} (allowed host: {})", startUrl, allowedHost);

        Queue<String> queue = new LinkedList<>();
        String normalisedStartUrl = UrlNormaliser.normalise(startUrl);
        queue.add(normalisedStartUrl);
        visitedUrls.add(normalisedStartUrl);

        while (!queue.isEmpty()) {
            String url = queue.poll();
            logger.info("Crawling: {}", url);

            Optional<Document> doc = fetcher.fetch(url);
            if (doc.isEmpty()) {
                // fetch() already logged the reason — nothing more to do here
                continue;
            }

            List<String> links = extractor.extract(doc.get(), allowedHost);
            printResult(url, links);

            for (String link : links) {
                String normalisedLink = UrlNormaliser.normalise(link);
                // add() returns false if already present — atomic check-and-add
                if (visitedUrls.add(normalisedLink)) {
                    queue.add(normalisedLink);
                }
            }
        }

        logger.info("Crawl complete. Visited {} pages.", visitedUrls.size());
    }

    /**
     * Prints the visited URL and the links found on that page to stdout.
     * Kept as a separate method so the output format is easy to change later.
     */
    private void printResult(String url, List<String> links) {
        System.out.println("\nVisited: " + url);
        if (links.isEmpty()) {
            System.out.println("  No same-domain links found.");
        } else {
            System.out.println("  Links found (" + links.size() + "):");
            links.forEach(link -> System.out.println("    -> " + link));
        }
    }

    /**
     * Extracts the host from a URL string, or null if unparseable.
     * Package-private for testing.
     */
    static String extractHost(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}