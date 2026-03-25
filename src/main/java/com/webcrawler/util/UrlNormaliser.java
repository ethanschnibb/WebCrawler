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

    private UrlNormaliser() {}

    /**
     * Rules applied:
     * - Lowercase scheme and host: http://EXAMPLE.com/Page -> http://example.com/Page
     * - Remove default ports (80 for http, 443 for https)
     * - Collapse empty path to /: http://example.com -> http://example.com/
     * - Remove trailing slash from non-root paths: http://example.com/page/ -> http://example.com/page
     * - Strip empty query strings
     * - Strip fragments (should already be gone, but just in case)
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
