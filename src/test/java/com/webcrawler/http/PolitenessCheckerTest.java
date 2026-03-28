package com.webcrawler.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;

class PolitenessCheckerTest {

    private PolitenessChecker filter(String content) {
        return new PolitenessChecker(content, "MonzoCrawler/1.0");
    }

    @Test
    void allowsEverythingWhenEmpty() {
        assertTrue(filter("").isAllowed("https://example.com/anything"));
    }

    @Test
    void disallowsMatchingPath() {
        PolitenessChecker f = filter("User-agent: *\nDisallow: /admin\n");
        assertFalse(f.isAllowed("https://example.com/admin"));
        assertFalse(f.isAllowed("https://example.com/admin/settings"));
        assertTrue(f.isAllowed("https://example.com/about"));
    }

    @Test
    void disallowsEverythingWithSlash() {
        PolitenessChecker f = filter("User-agent: *\nDisallow: /\n");
        assertFalse(f.isAllowed("https://example.com/"));
        assertFalse(f.isAllowed("https://example.com/page"));
    }

    @Test
    void emptyDisallowMeansAllowAll() {
        PolitenessChecker f = filter("User-agent: *\nDisallow:\n");
        assertTrue(f.isAllowed("https://example.com/anything"));
    }

    @Test
    void prefersSpecificAgentOverWildcard() {
        String content = """
                User-agent: *
                Disallow: /wildcard-only

                User-agent: MonzoCrawler
                Disallow: /specific-only
                """;
        PolitenessChecker f = filter(content);
        assertFalse(f.isAllowed("https://example.com/specific-only"));
        assertTrue(f.isAllowed("https://example.com/wildcard-only"));
    }

    @Test
    void stripsInlineComments() {
        PolitenessChecker f = filter("User-agent: * # all bots\nDisallow: /private # keep out\n");
        assertFalse(f.isAllowed("https://example.com/private"));
        assertTrue(f.isAllowed("https://example.com/public"));
    }

    @Test
    void handlesCrlfLineEndings() {
        PolitenessChecker f = filter("User-agent: *\r\nDisallow: /admin\r\n");
        assertFalse(f.isAllowed("https://example.com/admin"));
    }

    @Test
    void multipleDisallowRules() {
        PolitenessChecker f = filter("User-agent: *\nDisallow: /admin\nDisallow: /private\n");
        assertFalse(f.isAllowed("https://example.com/admin"));
        assertFalse(f.isAllowed("https://example.com/private"));
        assertTrue(f.isAllowed("https://example.com/public"));
    }

    @Test
    void isLoadedTrueWhenContentPresent() {
        assertTrue(filter("User-agent: *\nDisallow: /admin\n").isLoaded());
    }

    @Test
    void disallowedPrefixesVisibleForTesting() {
        PolitenessChecker f = filter("User-agent: *\nDisallow: /admin\nDisallow: /private\n");
        List<String> prefixes = f.getDisallowedPrefixes();
        assertTrue(prefixes.contains("/admin"));
        assertTrue(prefixes.contains("/private"));
    }

    @Test
    void shouldSetLoadedFalseWhenRobotsUrlInvalid() {
        // Use a Fetcher lambda instead of new PageFetcher() —
        // no config needed, no real HTTP, directly tests the invalid-URL branch
        Fetcher fakeFetcher = new Fetcher() {
            @Override
            public Optional<org.jsoup.nodes.Document> fetch(String url) {
                return Optional.empty();
            }
            @Override
            public Optional<String> fetchText(String url) {
                return Optional.empty();
            }
        };

        PolitenessChecker checker = new PolitenessChecker(
                "ht!tp://bad url",
                fakeFetcher,
                "EthansCrawler/1.0"
        );
        assertFalse(checker.isLoaded());
    }
}