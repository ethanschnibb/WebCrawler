package com.webcrawler.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility methods for URL validation and host matching.
 *
 * The original used:
 *   url.startsWith("http://") || url.startsWith("https://")
 *   url.contains("crawlme.monzo.com")
 *
 * The contains() check is the dangerous one:
 *   "https://evil.com/crawlme.monzo.com/phish" passes it.
 *   "https://crawlme.monzo.com.attacker.com"   passes it.
 *
 * Using URI.getHost() and comparing exactly avoids both cases.
 */
public final class UrlValidator {

    private UrlValidator() {}

    /**
     * Returns true if the URL is a valid HTTP or HTTPS URL.
     */
    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String scheme = new URI(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Returns true if the URL's host exactly matches allowedHost.
     *
     * Case-insensitive. Examples with allowedHost = "crawlme.monzo.com":
     *   https://crawlme.monzo.com/page           -> true
     *   https://monzo.com/page                   -> false  (parent domain)
     *   https://community.monzo.com/page         -> false  (sibling subdomain)
     *   https://evil.com/crawlme.monzo.com/phish -> false  (host is evil.com)
     *   https://crawlme.monzo.com.attacker.com/  -> false  (host suffix attack)
     */
    public static boolean isSameHost(String url, String allowedHost) {
        try {
            String host = new URI(url).getHost();
            return allowedHost.equalsIgnoreCase(host);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}