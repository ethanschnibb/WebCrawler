package com.webcrawler.config;

/**
 * Immutable configuration describing how a crawl should be executed.
 *
 * Centralising all crawler tuning parameters here keeps the crawler logic
 * focused on traversal rather than configuration management and makes the
 * behaviour easy to test and reason about.
 *
 * Design notes:
 *
 * - Immutable to guarantee thread safety (shared across virtual worker threads)
 * - Builder pattern allows optional configuration with safe defaults
 * - Zero values for limits represent "unbounded" behaviour
 *
 * This object is intended to be constructed once at application startup
 * and passed to all crawler components.
 */
public final class CrawlerConfig {

    // Maximum concurrent worker threads processing URLs.
    // Virtual threads are cheap but this prevents overwhelming servers.
    private final int concurrency;

    // Maximum number of pages to fetch (0 = unlimited)
    private final int maxPages;

    // Maximum crawl depth from the seed URL (0 = unlimited)
    private final int maxDepth;

    // User agent sent with HTTP requests for identification/politeness
    private final String userAgent;

    // HTTP behaviour
    // HTTP request timeout to prevent workers blocking indefinitely
    private final int timeoutMs;

    // Retry attempts for transient network failures
    private final int maxRetries;

    // Delay between retries to avoid hammering servers
    private final long retryBackoffMs;

    private CrawlerConfig(Builder builder) {
        this.concurrency    = builder.concurrency;
        this.maxPages       = builder.maxPages;
        this.maxDepth       = builder.maxDepth;
        this.userAgent      = builder.userAgent;
        this.timeoutMs      = builder.timeoutMs;
        this.maxRetries     = builder.maxRetries;
        this.retryBackoffMs = builder.retryBackoffMs;
    }

    public int getConcurrency()      { return concurrency; }
    public int getMaxPages()         { return maxPages; }
    public int getMaxDepth()         { return maxDepth; }
    public String getUserAgent()     { return userAgent; }
    public int getTimeoutMs()        { return timeoutMs; }
    public int getMaxRetries()       { return maxRetries; }
    public long getRetryBackoffMs()  { return retryBackoffMs; }

    /**
     * @return true if a maximum page limit is configured.
     */
    public boolean hasMaxPages() { return maxPages > 0; }
    /**
     * @return true if a maximum crawl depth is configured.
     */
    public boolean hasMaxDepth() { return maxDepth > 0; }

    @Override
    public String toString() {
        return String.format(
            "CrawlerConfig{concurrency=%d, maxPages=%s, maxDepth=%s, timeoutMs=%d, maxRetries=%d}",
            concurrency,
            maxPages  == 0 ? "unlimited" : maxPages,
            maxDepth  == 0 ? "unlimited" : maxDepth,
            timeoutMs,
            maxRetries
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CrawlerConfig}.
     *
     * Provides sensible defaults so callers only need to specify values they
     * want to override. Validation is performed on each setter to fail fast
     * on invalid configuration.
     */
    public static final class Builder {
        // Sensible defaults — override only what you need
        private int    concurrency    = 100;
        private int    maxPages       = 0;       // unlimited
        private int    maxDepth       = 0;       // unlimited
        private String userAgent      = "MonzoCrawler/1.0";
        private int    timeoutMs      = 10_000;
        private int    maxRetries     = 3;
        private long   retryBackoffMs = 1_000;

        /**
         * Sets maximum concurrent crawl workers.
         *
         * @param concurrency number of workers (must be >= 1)
         * @return builder
         * @throws IllegalArgumentException if concurrency < 1
         */
        public Builder concurrency(int concurrency) {
            if (concurrency < 1) throw new IllegalArgumentException("concurrency must be >= 1");
            this.concurrency = concurrency;
            return this;
        }

        /**
         * Sets maximum number of pages to crawl.
         *
         * A value of 0 means unlimited crawling.
         *
         * @param maxPages max pages (>=0)
         */
        public Builder maxPages(int maxPages) {
            if (maxPages < 0) throw new IllegalArgumentException("maxPages must be >= 0");
            this.maxPages = maxPages;
            return this;
        }

        /**
         * Sets maximum crawl depth.
         *
         * Depth is measured in hops from the starting URL:
         *
         * depth 0 = start page
         * depth 1 = links from start
         * depth 2 = links from depth 1
         *
         * A value of 0 disables depth limiting.
         */
        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must be >= 0");
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
            return this;
        }

        /**
         * Builds the immutable configuration instance.
         *
         * @return immutable crawler configuration
         */
        public CrawlerConfig build() {
            return new CrawlerConfig(this);
        }
    }
}