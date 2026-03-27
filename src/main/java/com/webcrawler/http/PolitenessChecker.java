package com.webcrawler.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PolitenessChecker {

    private static final Logger logger = LoggerFactory.getLogger(PolitenessChecker.class);

    private final List<String> disallowedPrefixes = new ArrayList<>();
    private final boolean loaded;

    /**
     * Fetches and parses robots.txt for the given start URL.
     * Fails open — if robots.txt doesn't exist or can't be fetched,
     * all paths are allowed. This is the correct behaviour: we should
     * only restrict ourselves if we can actually read the rules.
     */
    public PolitenessChecker(String startUrl, PageFetcher fetcher, String userAgent) {
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
     * Constructor for tests — supply the content directly without needing
     * a real HTTP call.
     */
    public PolitenessChecker(String robotsTxtContent, String userAgent) {
        parse(robotsTxtContent, userAgent);
        this.loaded = true;
    }

    /**
     * Returns true if the crawler is allowed to fetch this URL.
     * Extracts just the path component and checks against disallow rules.
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

    public boolean isLoaded() {
        return loaded;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses robots.txt content for the given user agent.
     *
     * Rules:
     * - Find User-agent blocks matching our agent name or wildcard *
     * - A specific agent match takes precedence over wildcard
     * - Collect Disallow: prefixes from the matching block
     * - Empty Disallow: means allow everything (it's a no-op)
     */
    private void parse(String content, String userAgent) {
        // Take just the name before the / e.g. "MonzoCrawler" from "MonzoCrawler/1.0"
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
     * Constructs https://host/robots.txt from any URL on that host.
     */
    private static String buildRobotsUrl(String startUrl) {
        try {
            URI uri = new URI(startUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/robots.txt";
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String extractPath(String url) {
        try {
            String path = new URI(url).getPath();
            return (path == null || path.isEmpty()) ? "/" : path;
        } catch (URISyntaxException e) {
            return "/";
        }
    }

    private static String stripComment(String line) {
        int i = line.indexOf('#');
        return i >= 0 ? line.substring(0, i) : line;
    }

    /**
     * Prints the raw robots.txt to stdout so you can see exactly what
     * rules are in play during a crawl run.
     */
    private static void printRobotsTxt(String content, String url) {
        System.out.println("\n=== robots.txt from " + url + " ===");
        System.out.println(content.trim());
        System.out.println("==========================================\n");
    }

    // Visible for testing
    List<String> getDisallowedPrefixes() {
        return List.copyOf(disallowedPrefixes);
    }
}