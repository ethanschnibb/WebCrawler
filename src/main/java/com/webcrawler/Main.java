
package com.webcrawler;

import com.webcrawler.http.PageFetcher;
import com.webcrawler.parser.LinkExtractor;

public class Main {

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: webcrawler <start-url>");
            return 1;
        }

        String startUrl = args[0];

        PageFetcher fetcher = new PageFetcher();
        LinkExtractor extractor = new LinkExtractor();
        Crawler crawler = new Crawler(fetcher, extractor);

        crawler.crawl(startUrl);

        return 0;
    }
}