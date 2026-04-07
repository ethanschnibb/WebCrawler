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
 * Responsibilities:
 * - Given a Document, returns a list of absolute URLs belonging to the allowed host.
 * - Strips URL fragments so that /page#section and /page are considered the same resource.
 * - Decoupled from crawling logic: does not handle visited sets, queues, or output.
 *
 * Design decisions:
 * - Allowed host is a parameter for flexibility; works with any domain.
 * - Only anchors with href attributes are considered.
 * - Skips invalid URLs or URLs outside the allowed host.
 * - Logging at DEBUG level for skipped URLs to aid debugging without polluting normal logs.
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
    /**
     * Removes the fragment component of a URL (the part after #), if any.
     */
    private static String stripFragment(String url) {
        int i = url.indexOf('#');
        return i >= 0 ? url.substring(0, i) : url;
    }
}