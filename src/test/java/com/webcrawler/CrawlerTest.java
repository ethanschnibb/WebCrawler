package com.webcrawler;

import com.webcrawler.config.CrawlerConfig;
import com.webcrawler.http.Fetcher;
import com.webcrawler.parser.LinkExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlerTest {

    // Mock the interface, not the concrete class — this is the point of having
    // the interface. Mockito can mock either, but coding to the interface is
    // correct and means the test doesn't depend on PageFetcher's internals.
    @Mock private Fetcher fetcher;

    private Crawler crawler;

    // Minimal config for tests — no depth/page limits, low concurrency so
    // tests are deterministic and don't spin up unnecessary threads
    private final CrawlerConfig config = CrawlerConfig.builder()
            .concurrency(1)   // single worker makes test ordering predictable
            .maxPages(0)      // unlimited
            .maxDepth(0)      // unlimited
            .build();

    @BeforeEach
    void setUp() {
        crawler = new Crawler(fetcher, new LinkExtractor(), config);
    }

    // Every crawl starts by fetching robots.txt via fetchText().
    // This helper stubs that call to return empty (no robots.txt) so tests
    // don't need to stub it manually unless they're specifically testing robots behaviour.
    private void noRobotsTxt() {
        when(fetcher.fetchText(any())).thenReturn(Optional.empty());
    }

    private static Document html(String baseUrl, String... hrefs) {
        StringBuilder body = new StringBuilder();
        for (String href : hrefs) {
            body.append("<a href='").append(href).append("'>link</a>");
        }
        return Jsoup.parse("<html><body>" + body + "</body></html>", baseUrl);
    }

    @Test
    void crawlsSinglePageWithNoLinks() {
        noRobotsTxt();
        String start = "https://crawlme.monzo.com/";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start)));

        crawler.crawl(start);

        verify(fetcher, times(1)).fetch(start);
    }

    @Test
    void followsSameDomainLink() {
        noRobotsTxt();
        String start = "https://crawlme.monzo.com/";
        String about = "https://crawlme.monzo.com/about";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start, "/about")));
        when(fetcher.fetch(about)).thenReturn(Optional.of(html(about)));

        crawler.crawl(start);

        verify(fetcher).fetch(start);
        verify(fetcher).fetch(about);
    }

    @Test
    void doesNotFollowExternalLink() {
        noRobotsTxt();
        String start = "https://crawlme.monzo.com/";
        when(fetcher.fetch(start))
                .thenReturn(Optional.of(html(start, "https://facebook.com/page")));

        crawler.crawl(start);

        verify(fetcher, times(1)).fetch(any());
    }

    @Test
    void doesNotVisitSameUrlTwice() {
        noRobotsTxt();
        String start = "https://crawlme.monzo.com/";
        String about = "https://crawlme.monzo.com/about";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start, "/about")));
        when(fetcher.fetch(about)).thenReturn(Optional.of(html(about, "/")));

        crawler.crawl(start);

        verify(fetcher, times(1)).fetch(start);
        verify(fetcher, times(1)).fetch(about);
    }

    @Test
    void continuesCrawlAfterFetchFailure() {
        noRobotsTxt();
        String start  = "https://crawlme.monzo.com/";
        String broken = "https://crawlme.monzo.com/broken";
        String good   = "https://crawlme.monzo.com/good";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start, "/broken", "/good")));
        when(fetcher.fetch(broken)).thenReturn(Optional.empty());
        when(fetcher.fetch(good)).thenReturn(Optional.of(html(good)));

        crawler.crawl(start);

        verify(fetcher).fetch(start);
        verify(fetcher).fetch(broken);
        verify(fetcher).fetch(good);
    }

    @Test
    void rejectsInvalidStartUrl() {
        crawler.crawl("not-a-url");
        verifyNoInteractions(fetcher);
    }

    @Test
    void rejectsNonHttpStartUrl() {
        crawler.crawl("ftp://crawlme.monzo.com/");
        verifyNoInteractions(fetcher);
    }

    @Test
    void respectsMaxPagesLimit() {
        noRobotsTxt();

        String start = "https://crawlme.monzo.com/";

        when(fetcher.fetch(start))
                .thenReturn(Optional.of(html(start, "/a", "/b", "/c")));

        when(fetcher.fetch("https://crawlme.monzo.com/a"))
                .thenReturn(Optional.of(html(start)));

        CrawlerConfig limitedConfig = CrawlerConfig.builder()
                .concurrency(1)
                .maxPages(2)
                .build();

        Crawler limitedCrawler =
                new Crawler(fetcher, new LinkExtractor(), limitedConfig);

        limitedCrawler.crawl(start);

        verify(fetcher, atMost(2)).fetch(any());
    }

    @Test
    void respectsMaxDepthLimit() {
        noRobotsTxt();

        String start = "https://crawlme.monzo.com/";
        String depth1 = "https://crawlme.monzo.com/depth1";
        String depth2 = "https://crawlme.monzo.com/depth2";

        when(fetcher.fetch(start))
                .thenReturn(Optional.of(html(start, "/depth1")));

        CrawlerConfig depthConfig = CrawlerConfig.builder()
                .concurrency(1)
                .maxDepth(1)
                .build();

        Crawler depthCrawler =
                new Crawler(fetcher, new LinkExtractor(), depthConfig);

        depthCrawler.crawl(start);

        verify(fetcher).fetch(start);

        // depth1 discovered but not fetched
        verify(fetcher, never()).fetch(depth1);

        // depth2 never reached
        verify(fetcher, never()).fetch(depth2);
    }

    @Test
    void skipsUrlDisallowedByRobots() {
        // Stub fetchText to return a robots.txt that blocks /secret
        when(fetcher.fetchText(any())).thenReturn(
                Optional.of("User-agent: *\nDisallow: /secret\n"));

        String start  = "https://crawlme.monzo.com/";
        String secret = "https://crawlme.monzo.com/secret";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start, "/secret")));

        crawler.crawl(start);

        verify(fetcher).fetch(start);
        // /secret was discovered but robots.txt blocked it — should never be fetched
        verify(fetcher, never()).fetch(secret);
    }

    @Test
    void extractHostReturnsCorrectHost() {
        assertEquals("crawlme.monzo.com",
                Crawler.extractHost("https://crawlme.monzo.com/page"));
    }

    @Test
    void extractHostReturnsNullForInvalidUrl() {
        assertNull(Crawler.extractHost("not a url"));
    }
}