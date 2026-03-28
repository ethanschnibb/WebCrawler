package com.webcrawler.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Encapsulates robots.txt parsing and politeness enforcement for a crawler.
 *
 * Responsibilities:
 * - Fetches robots.txt for a host and parses Disallow rules
 * - Determines if a URL is allowed to be fetched based on rules
 * - Supports wildcard (*) and agent-specific User-agent blocks
 *
 * Design decisions:
 * - Fails open: if robots.txt cannot be fetched, all URLs are allowed
 * - Only considers Disallow rules; Allow, Crawl-delay, Sitemap are ignored
 * - Specific agent rules override wildcard rules
 * - Immutable after construction aside from internal disallowedPrefixes list for safety
 */
public class PolitenessChecker {

    private static final Logger logger = LoggerFactory.getLogger(PolitenessChecker.class);

    private final List<String> disallowedPrefixes = new ArrayList<>();
    private final boolean loaded;

    /**
     * Fetches robots.txt for a host and parses the rules for the given user agent.
     * Fails open — all paths are allowed if robots.txt cannot be fetched or parsed.
     *
     * @param startUrl the initial URL to derive the host and robots.txt location
     * @param fetcher  HTTP fetcher to retrieve robots.txt content
     * @param userAgent user agent string identifying this crawler
     */
    public PolitenessChecker(String startUrl, Fetcher fetcher, String userAgent) {
        String robotsUrl = buildRobotsUrl(startUrl);
        if (robotsUrl == null) {
            logger.warn("Could not construct robots.txt URL from {}", startUrl);
            this.loaded = false;
            return;
        }

        Optional<String> content = fetcher.fetchText(robotsUrl);
        if (content.isEmpty()) {
            logger.info("No robots.txt found at {} — all paths allowed", robotsUrl);
            this.loaded = false;
            return;
        }

        logger.info("robots.txt found at {}", robotsUrl);
        printRobotsTxt(content.get(), robotsUrl);
        parse(content.get(), userAgent);
        this.loaded = true;
    }

    /**
     * Test-friendly constructor that parses robots.txt content directly.
     *
     * @param robotsTxtContent the robots.txt content
     * @param userAgent the crawler's user agent string
     */
    public PolitenessChecker(String robotsTxtContent, String userAgent) {
        parse(robotsTxtContent, userAgent);
        this.loaded = true;
    }

    /**
     * Checks if a given URL is allowed to be fetched according to robots.txt rules.
     *
     * @param url absolute URL to check
     * @return true if allowed, false if the path matches a Disallow prefix
     */
    public boolean isAllowed(String url) {
        String path = extractPath(url);
        for (String prefix : disallowedPrefixes) {
            if (path.startsWith(prefix)) {
                logger.debug("robots.txt disallows: {}", url);
                return false;
            }
        }
        return true;
    }

    /** Returns true if robots.txt was successfully loaded and parsed. */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Parses robots.txt content for a specific user agent.
     *
     * Rules:
     * - Match blocks for the crawler's user agent first; fallback to wildcard (*) blocks
     * - Collect Disallow prefixes from matched blocks
     * - Empty Disallow lines are ignored (allow all)
     *
     * @param content the raw robots.txt content
     * @param userAgent the crawler's user agent string
     */
    private void parse(String content, String userAgent) {
        // Take just the name before the / e.g. "EthansCrawler" from "EthansCrawler/1.0"
        String agentName = userAgent.split("/")[0].trim().toLowerCase();

        List<String> wildcardDisallows = new ArrayList<>();
        List<String> specificDisallows = new ArrayList<>();

        boolean inWildcardBlock = false;
        boolean inSpecificBlock = false;
        boolean foundSpecific   = false;

        for (String rawLine : content.split("\\r?\\n")) {
            String line  = stripComment(rawLine).trim();

            // Blank line signals the end of the current user-agent block
            if (line.isEmpty()) {
                inWildcardBlock = false;
                inSpecificBlock = false;
                continue;
            }

            String lower = line.toLowerCase();

            if (lower.startsWith("user-agent:")) {
                String agent = line.substring("user-agent:".length()).trim().toLowerCase();
                inWildcardBlock = "*".equals(agent);
                inSpecificBlock = agent.equals(agentName) || agent.contains(agentName);
                if (inSpecificBlock) foundSpecific = true;

            } else if (lower.startsWith("disallow:")) {
                String path = line.substring("disallow:".length()).trim();
                if (path.isEmpty()) continue; // empty Disallow = allow everything

                if (inWildcardBlock)       wildcardDisallows.add(path);
                else if (inSpecificBlock)  specificDisallows.add(path);
            }
            // Intentionally ignore Allow:, Crawl-delay:, Sitemap: for now
        }

        // Specific agent rules win over wildcard
        List<String> rules = foundSpecific ? specificDisallows : wildcardDisallows;
        disallowedPrefixes.addAll(rules);

        logger.info("robots.txt parsed: {} disallow rule(s) loaded ({})",
                disallowedPrefixes.size(),
                foundSpecific ? "matched agent '" + agentName + "'" : "wildcard match");

        if (!disallowedPrefixes.isEmpty()) {
            logger.info("Disallowed paths: {}", disallowedPrefixes);
        }
    }

    /**
     * Builds a robots.txt URL from any URL on that host.
     *
     * @param startUrl any URL on the host
     * @return the robots.txt URL, or null if startUrl is invalid
     */
    private static String buildRobotsUrl(String startUrl) {
        try {
            URI uri = new URI(startUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/robots.txt";
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Extracts the path component of a URL; "/" if empty or invalid. */
    private static String extractPath(String url) {
        try {
            String path = new URI(url).getPath();
            return (path == null || path.isEmpty()) ? "/" : path;
        } catch (URISyntaxException e) {
            return "/";
        }
    }

    /** Strips a comment (#) from a line. */
    private static String stripComment(String line) {
        int i = line.indexOf('#');
        return i >= 0 ? line.substring(0, i) : line;
    }

    /**
     * Prints robots.txt to stdout for visibility during crawl runs.
     * Useful for debugging rules.
     */
    private static void printRobotsTxt(String content, String url) {
        System.out.println("\n=== robots.txt from " + url + " ===");
        System.out.println(content.trim());
        System.out.println("==========================================\n");
    }

    /** Exposed for testing to verify parsed disallow rules. */
    List<String> getDisallowedPrefixes() {
        return List.copyOf(disallowedPrefixes);
    }
}