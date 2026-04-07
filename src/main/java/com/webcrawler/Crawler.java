package com.webcrawler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcrawler.config.CrawlerConfig;
import com.webcrawler.http.Fetcher;
import com.webcrawler.http.PolitenessChecker;
import com.webcrawler.parser.LinkExtractor;
import com.webcrawler.util.UrlNormaliser;
import com.webcrawler.util.UrlValidator;

/**
 * Multi-threaded, same-domain web crawler.
 *
 * Responsibilities:
 * - Maintain a queue of URLs to crawl, along with depth metadata.
 * - Respect max pages and max depth limits from CrawlerConfig.
 * - Respect robots.txt politeness rules.
 * - Normalize URLs and avoid revisiting pages.
 *
 * Threading:
 * - Uses virtual threads (Java 21+) for scalability.
 * - pageCount and visitedUrls are thread-safe to allow concurrent access.
 *
 * Design choices:
 * - Queue stores UrlWithDepth records to carry depth with each URL.
 * - processUrl handles a single URL fetch, extraction, normalization, and enqueue.
 */
public class Crawler {

    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);

    private final Fetcher fetcher;
    private final LinkExtractor extractor;
    private final CrawlerConfig config;

    // Thread-safe set of URLs already visited to prevent revisits.
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    // Tracks how many pages have been processed — compared against config.getMaxPages()
    // AtomicInteger because multiple worker threads increment it concurrently
    private final AtomicInteger pageCount = new AtomicInteger(0);

    public Crawler(Fetcher fetcher, LinkExtractor extractor, CrawlerConfig config) {
        this.fetcher   = fetcher;
        this.extractor = extractor;
        this.config    = config;
    }

    /**
     * Starts a crawl from the given URL.
     *
     * @param startUrl The seed URL to begin crawling.
     *                 Must be a valid HTTP/HTTPS URL.
     *
     * Behavior:
     * - Normalizes start URL.
     * - Extracts allowed host for same-domain link enforcement.
     * - Initializes politeness checker (robots.txt).
     * - Spawns virtual-thread workers (concurrency defined in CrawlerConfig).
     * - Each worker dequeues a URL, checks max depth and politeness, fetches HTML,
     *   extracts same-domain links, and enqueues new URLs if not visited.
     *
     * Thread safety:
     * - visitedUrls and pageCount are thread-safe.
     * - Queue operations are blocking and safe for multiple consumers.
     *
     * Termination:
     * - Workers terminate when queue is empty and no active worker is processing.
     * - Executor waits up to 10 minutes; forced shutdown occurs if exceeded.
     */
    public void crawl(String startUrl) {
        if (!UrlValidator.isValid(startUrl)) {
            logger.error("Invalid start URL: {}", startUrl);
            return;
        }

        String allowedHost = extractHost(startUrl);
        if (allowedHost == null) {
            logger.error("Could not determine host from start URL: {}", startUrl);
            return;
        }

        String normalisedStart = UrlNormaliser.normalise(startUrl);
        logger.info("Starting crawl — config: {}", config);

        PolitenessChecker politenessChecker = new PolitenessChecker(normalisedStart, fetcher, config.getUserAgent());

        // Each entry in the queue is a pair of (url, depth).
        // Depth starts at 0 for the seed URL and increments with each hop.
        // We store depth alongside the URL so workers know how deep they are
        // without needing a separate map lookup.
        BlockingQueue<UrlWithDepth> queue = new LinkedBlockingQueue<>();
        AtomicInteger activeWorkers = new AtomicInteger(0);

        Semaphore pageSlots = (config.hasMaxPages()) ? new Semaphore(config.getMaxPages()) : null;
        // if (config.getMaxPages() > 0) {
        //     pageSlots = new Semaphore(config.getMaxPages());
        // } else {
        //     // If no max pages, use a semaphore with a large number of permits to avoid blocking
        //     pageSlots = new Semaphore(Integer.MAX_VALUE);
        // }

        visitedUrls.add(normalisedStart);
        queue.add(new UrlWithDepth(normalisedStart, 0));

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < config.getConcurrency(); i++) {
            executor.submit(() -> runWorker(queue, activeWorkers, allowedHost, politenessChecker, pageSlots));
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                logger.warn("Crawl timed out, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Crawl interrupted");
        }

        logger.info("Crawl complete. Visited {} pages.", visitedUrls.size());
    }

    /**
     * Worker loop for crawling pages from the queue.
     *
     * @param queue Queue of URLs paired with depth
     * @param activeWorkers Tracks number of currently active workers for termination
     * @param allowedHost Restricts links to this host
     * @param politenessChecker Enforces robots.txt rules
     */
    private void runWorker(
        BlockingQueue<UrlWithDepth> queue,
        AtomicInteger activeWorkers,
        String allowedHost,
        PolitenessChecker politenessChecker,
        Semaphore pageSlots) {

        while (true) {
            UrlWithDepth item;
            try {
                item = queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (item == null) {
                if (activeWorkers.get() == 0 && queue.isEmpty()) return;
                continue;
            }

            activeWorkers.incrementAndGet();
            try {
                processUrl(item, allowedHost, queue, politenessChecker, pageSlots);
            } finally {
                activeWorkers.decrementAndGet();
            }
        }
    }

    /**
     * Process a single URL: fetch, parse, extract links, enqueue new URLs.
     *
     * @param item URL and depth
     * @param allowedHost host restriction
     * @param queue queue to enqueue new URLs
     * @param politenessChecker robots.txt enforcement
     */
    private void processUrl(
            UrlWithDepth item,
            String allowedHost,
            BlockingQueue<UrlWithDepth> queue,
            PolitenessChecker politenessChecker,
            Semaphore pageSlots) {

        String url = item.url();
        int depth  = item.depth();

        if (!politenessChecker.isAllowed(url)) {
            logger.info("Skipping disallowed URL: {}", url);
            return;
        }

        // Max depth check — don't fetch the page if we're already at the limit.
        // Note: we still enqueue URLs at maxDepth so they appear as discovered links
        // in the output, but we don't crawl deeper from them.
        if (config.hasMaxDepth() && depth >= config.getMaxDepth()) {
            logger.info("Max depth ({}) reached at: {}", config.getMaxDepth(), url);
            return;
        }

        // Max pages check — stop this worker if the global page cap is reached.
        // pageCount is checked BEFORE incrementing so we don't overshoot by
        // the number of concurrent workers.
        if (config.hasMaxPages() && !pageSlots.tryAcquire()) {
            logger.info("Max pages ({}) reached — worker stopping", config.getMaxPages());
            return;
        }

        Optional<Document> doc = fetcher.fetch(url);
        if (doc.isEmpty()) {
            logger.error("Failed to fetch: {}", url);
            if (config.hasMaxPages()) pageSlots.release(); // Release the page slot since this fetch didn't succeed
            return;
        }

        // Increment after a successful fetch — failed fetches don't count
        pageCount.incrementAndGet();

        List<String> links = extractor.extract(doc.get(), allowedHost);
        printResult(url, depth, links);

        for (String link : links) {
            String normalised = UrlNormaliser.normalise(link);
            if (visitedUrls.add(normalised)) {
                try {
                    // Pass depth + 1 so each hop increments the depth counter
                    queue.put(new UrlWithDepth(normalised, depth + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Prints results of a crawl for a single page.
     * Synchronized to prevent jumbled output across threads.
     *
     * Should be extracteed to ResultPrinter class as outputting is separate concern from crawling
     * 
     * @param url The page URL
     * @param depth Depth of crawl for this page
     * @param links List of same-domain links discovered
     */
    private void printResult(String url, int depth, List<String> links) {
        synchronized (this) {
            System.out.printf("%nVisited: %s (depth %d)%n", url, depth);
            if (links.isEmpty()) {
                System.out.println("  No same-domain links found.");
            } else {
                System.out.printf("  Links found (%d):%n", links.size());
                links.forEach(link -> System.out.println("    -> " + link));
            }
        }
    }

    /**
     * Extracts the host from a URL string.
     *
     * @param url URL string
     * @return host, or null if URL is malformed
     */
    static String extractHost(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Pairs a URL with the depth at which it was discovered.
     * A record is perfect here — it's just a named tuple with no behaviour.
     *
     * Why not a Map<String, Integer>? Because we need depth to travel with
     * the URL through the queue. A map would require a separate lookup per
     * dequeue, and wouldn't be correct under concurrency without extra locking.
     */
    private record UrlWithDepth(String url, int depth) {}
}