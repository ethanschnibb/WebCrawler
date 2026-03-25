package com.webcrawler;

import com.webcrawler.http.PageFetcher;
import com.webcrawler.parser.LinkExtractor;

/**
 * Entry point. Responsible for exactly one thing: reading arguments and
 * wiring up the crawler. No crawl logic lives here.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: webcrawler <start-url>");
            System.exit(1);
        }

        String startUrl = args[0];

        PageFetcher fetcher = new PageFetcher();
        LinkExtractor extractor = new LinkExtractor();
        Crawler crawler = new Crawler(fetcher, extractor);

        crawler.crawl(startUrl);
    }
}