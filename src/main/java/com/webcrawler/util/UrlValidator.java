package com.webcrawler.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility methods for URL validation and host matching.
 *
 * Purpose:
 * - Ensure URLs are valid HTTP(S) URLs.
 * - Prevent malicious or accidental domain mismatches when crawling.
 *
 * Rationale:
 * - Original naive approach used `startsWith` and `contains`:
 *     url.startsWith("http://") || url.startsWith("https://")
 *     url.contains("crawlme.monzo.com")
 *   This is unsafe because `contains` allows host suffix attacks:
 *     "https://evil.com/crawlme.monzo.com/phish" -> passes
 *     "https://crawlme.monzo.com.attacker.com"   -> passes
 *
 * - Using URI.getHost() ensures we only match the actual host part of the URL.
 */
public final class UrlValidator {

    private UrlValidator() {}

    /**
     * Checks if a URL is a valid HTTP or HTTPS URL.
     *
     * @param url the URL string to validate
     * @return true if the URL scheme is "http" or "https" and the string is parseable, false otherwise
     *
     * Notes:
     * - Returns false for null, blank, or malformed URLs.
     * - Does not check host validity beyond URI parsing.
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
     * Checks if a URL belongs exactly to a specified host.
     *
     * @param url         the URL to check
     * @param allowedHost the host to match (e.g., "crawlme.monzo.com")
     * @return true if the URL's host exactly matches allowedHost (case-insensitive), false otherwise
     *
     * Examples (allowedHost = "crawlme.monzo.com"):
     *   https://crawlme.monzo.com/page           -> true
     *   https://monzo.com/page                   -> false (parent domain)
     *   https://community.monzo.com/page         -> false (different subdomain)
     *   https://evil.com/crawlme.monzo.com/phish -> false (host is evil.com)
     *   https://crawlme.monzo.com.attacker.com/  -> false (host suffix attack)
     *
     * Notes:
     * - Returns false for null or malformed URLs.
     * - Protects against host spoofing and phishing by using exact host comparison.
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