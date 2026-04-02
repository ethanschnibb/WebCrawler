package com.webcrawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcrawler.config.CrawlerConfig;
import com.webcrawler.http.PageFetcher;
import com.webcrawler.parser.LinkExtractor;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Kept separate from main() so tests can call run() directly and
     * check the return code without triggering System.exit().
     *
     * Args:
     *   args[0] — start URL (required)
     *   args[1] — maxPages  (optional, 0 = unlimited)
     *   args[2] — maxDepth  (optional, 0 = unlimited)
     */
    static int run(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: webcrawler <start-url> [maxPages] [maxDepth]");
            return 1;
        }

        CrawlerConfig config = buildConfig(args);

        PageFetcher fetcher     = new PageFetcher(config);
        LinkExtractor extractor = new LinkExtractor();
        Crawler crawler         = new Crawler(fetcher, extractor, config);

        crawler.crawl(args[0]);
        return 0;
    }

    /**
     * Builds CrawlerConfig from CLI args.
     * Kept in its own method so it's easy to test arg parsing independently.
     */
    static CrawlerConfig buildConfig(String[] args) {
        CrawlerConfig.Builder builder = CrawlerConfig.builder();

        if (args.length > 1) {
            try {
                builder.maxPages(Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                System.err.println("Warning: invalid maxPages '" + args[1] + "', using unlimited");
                logger.warn("Invalid max pages '{}', using unlimited", args[1]);
            }
        }

        if (args.length > 2) {
            try {
                builder.maxDepth(Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                System.err.println("Warning: invalid maxDepth '" + args[2] + "', using unlimited");
                logger.warn("Invalid max depth '{}', using unlimited", args[2]);
            }
        }

        return builder.build();
    }
}