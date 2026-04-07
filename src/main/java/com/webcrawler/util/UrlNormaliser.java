package com.webcrawler.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides utility methods for normalising URLs.
 * Problem being solved:
 * - Trailing slashes: http://example.com/page and http://example.com/page/ are the same resource but would be treated as different URLs.
 * - Case sensitivity: http://example.com/Page and http://example.com/page are the same resource but would be treated as different URLs.
 */
public final class UrlNormaliser {

    private UrlNormaliser() {} // Prevent instantiation

    /**
     * Normalises a URL according to common web crawling rules.
     *
     * @param url the URL string to normalise
     * @return a canonicalised URL string
     *
     * Rules applied:
     * 1. Lowercase scheme and host: http://EXAMPLE.com/Page -> http://example.com/Page
     * 2. Remove default ports (80 for http, 443 for https)
     * 3. Collapse empty path to root "/": http://example.com -> http://example.com/
     * 4. Remove trailing slash for non-root paths: http://example.com/page/ -> http://example.com/page
     * 5. Strip empty query strings
     * 6. Strip fragment identifiers (should already be removed)
     *
     * Notes:
     * - If URL parsing fails, returns the original URL; this avoids crashing the crawler.
     * - Intended to be used after URL validation.
     */
    public static String normalise(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();
            int port = normalisePort(scheme, uri.getPort());
            String path = normalisePath(uri.getPath());
            String query = normaliseQuery(uri.getQuery());

            return new URI(scheme, null, host, port, path, query, null).toString();
        } catch (URISyntaxException e) {
            return url; // If parsing fails, return original URL (validation should catch most issues first)
        }
    }

    private static int normalisePort(String scheme, int port) {
        if (port == 80 && "http".equals(scheme)) return -1; // URI treats -1 as "no port", which means default for the scheme
        if (port == 443 && "https".equals(scheme)) return -1;
        return port;
    }

    private static String normalisePath(String path) {
        if (path == null || path.isEmpty()) return "/"; // Collapse empty path to /
        if (path.endsWith("/") && path.length() > 1) return path.substring(0, path.length() - 1); // Remove trailing slash from non-root paths
        return path;
    }

    private static String normaliseQuery(String query) {
        return (query == null || query.isEmpty()) ? null : query; // Strip empty query strings
    }
    
}
