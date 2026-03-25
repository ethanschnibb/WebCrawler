package com.webcrawler.parser;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcrawler.util.UrlValidator;

/**
 * Extracts same-domain links from a parsed HTML document.
 *
 * Changes from the original:
 *
 * - Moved to its own class. Link extraction knows about HTML and jsoup
 *   but nothing about queues, visited sets, or output. You can test
 *   "does this HTML produce these links?" without running a crawl at all.
 *
 * - The allowed host is a parameter, not hardcoded. The same extractor
 *   works for any target site.
 *
 * - Fragment stripping. /page#section and /page are the same resource.
 *   The original would enqueue both and visit the page twice.
 */
public class LinkExtractor {

    private static final Logger logger = LoggerFactory.getLogger(LinkExtractor.class);

    /**
     * Returns all valid, same-domain absolute URLs found in anchor elements,
     * with fragments stripped.
     *
     * @param doc         the parsed HTML document
     * @param allowedHost the host links must belong to (e.g. "crawlme.monzo.com")
     * @return a list of absolute URL strings, possibly empty, never null
     */
    public List<String> extract(Document doc, String allowedHost) {
        // Select all anchor elements with href attributes. This includes <a> tags but also <area>, <link>, etc.
        Elements anchors = doc.select("a[href]");
        List<String> links = new ArrayList<>();

        for (Element anchor : anchors) {
            // abs:href resolves relative links to absolute using the document's base URL
            String absUrl = anchor.absUrl("href");

            if (absUrl.isEmpty()) {
                continue;
            }

            // Strip fragment — /page#section and /page are the same resource
            String url = stripFragment(absUrl);

            if (UrlValidator.isValid(url) && UrlValidator.isSameHost(url, allowedHost)) {
                links.add(url);
            } else {
                logger.debug("Skipping: {}", absUrl);
            }
        }

        return links;
    }

    private static String stripFragment(String url) {
        int i = url.indexOf('#');
        return i >= 0 ? url.substring(0, i) : url;
    }
}