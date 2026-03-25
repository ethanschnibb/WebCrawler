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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcrawler.http.PageFetcher;
import com.webcrawler.parser.LinkExtractor;
import com.webcrawler.util.UrlNormaliser;
import com.webcrawler.util.UrlValidator;

public class Crawler {

    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);

    // How many URLs to process concurrently.
    // Virtual threads are cheap but we still want to be polite to the server —
    // 20 concurrent requests is aggressive enough to be fast without hammering it.
    private static final int CONCURRENCY = 100;

    private final PageFetcher fetcher;
    private final LinkExtractor extractor;

    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    public Crawler(PageFetcher fetcher, LinkExtractor extractor) {
        this.fetcher = fetcher;
        this.extractor = extractor;
    }

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
        logger.info("Starting crawl of {} (allowed host: {})", normalisedStart, allowedHost);

        // BlockingQueue + AtomicInteger is the correct termination pattern for
        // concurrent BFS. See comment on runWorker() for why.
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        AtomicInteger activeWorkers = new AtomicInteger(0);

        queue.add(normalisedStart);
        visitedUrls.add(normalisedStart);

        // newVirtualThreadPerTaskExecutor: each submitted task gets its own virtual
        // thread. Virtual threads are cheap (~few KB vs ~1MB for platform threads)
        // and yield automatically when blocked on I/O — exactly what fetch() does.
        // We submit exactly CONCURRENCY workers to cap concurrent requests.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < CONCURRENCY; i++) {
            executor.submit(() -> runWorker(queue, activeWorkers, allowedHost));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Crawl interrupted");
        }

        logger.info("Crawl complete. Visited {} pages.", visitedUrls.size());
    }

    /**
     * Each worker runs this loop independently on its own virtual thread.
     *
     * Termination condition: the queue being empty is NOT enough to stop —
     * another worker might be mid-fetch and about to produce new URLs.
     * We use activeWorkers to count threads currently processing a URL
     * (not just waiting). A worker exits only when BOTH are true:
     *   - the queue is empty (poll timed out)
     *   - no other worker is actively processing a URL
     *
     * poll(timeout) rather than take() lets us re-check this condition
     * regularly without blocking forever.
     */
    private void runWorker(
            BlockingQueue<String> queue,
            AtomicInteger activeWorkers,
            String allowedHost) {

        while (true) {
            String url;
            try {
                url = queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (url == null) {
                // Queue was empty for our entire poll window.
                // If no other thread is active, we're done.
                if (activeWorkers.get() == 0) {
                    return;
                }
                // Another thread is still working and may add URLs — keep polling.
                continue;
            }

            activeWorkers.incrementAndGet();
            try {
                processUrl(url, allowedHost, queue);
            } finally {
                // Must be in finally — if processUrl throws, we still need to
                // decrement so other workers can detect termination correctly.
                activeWorkers.decrementAndGet();
            }
        }
    }

    private void processUrl(String url, String allowedHost, BlockingQueue<String> queue) {
        logger.debug("Fetching: {}", url);

        Optional<Document> doc = fetcher.fetch(url);
        if (doc.isEmpty()) {
            return; // fetch() already logged the reason
        }

        List<String> links = extractor.extract(doc.get(), allowedHost);
        printResult(url, links);

        for (String link : links) {
            String normalised = UrlNormaliser.normalise(link);
            // visitedUrls.add() is atomic — returns false if already present.
            // This is the correct way to do check-then-act under concurrency;
            // a separate contains() + add() would have a race condition.
            if (visitedUrls.add(normalised)) {
                try {
                    queue.put(normalised);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void printResult(String url, List<String> links) {
        // synchronized so concurrent workers don't interleave their output
        synchronized (this) {
            System.out.println("\nVisited: " + url);
            if (links.isEmpty()) {
                System.out.println("  No same-domain links found.");
            } else {
                System.out.printf("  Links found (%d):%n", links.size());
                links.forEach(link -> System.out.println("    -> " + link));
            }
        }
    }

    static String extractHost(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}