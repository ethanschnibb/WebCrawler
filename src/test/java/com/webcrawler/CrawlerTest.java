package com.webcrawler;

import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.webcrawler.http.PageFetcher;
import com.webcrawler.parser.LinkExtractor;

@ExtendWith(MockitoExtension.class)
class CrawlerTest {

    @Mock private PageFetcher fetcher;
    private Crawler crawler;

    @BeforeEach
    void setUp() {
        crawler = new Crawler(fetcher, new LinkExtractor());
    }

    private static Document html(String baseUrl, String... hrefs) {
        StringBuilder body = new StringBuilder();
        for (String href : hrefs) body.append("<a href='").append(href).append("'>link</a>");
        return Jsoup.parse("<html><body>" + body + "</body></html>", baseUrl);
    }

    @Test
    void crawlsSinglePageWithNoLinks() {
        String start = "https://crawlme.monzo.com/";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start)));
        crawler.crawl(start);
        verify(fetcher, times(1)).fetch(start);
    }

    @Test
    void followsSameDomainLink() {
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
        String start = "https://crawlme.monzo.com/";
        when(fetcher.fetch(start))
                .thenReturn(Optional.of(html(start, "https://facebook.com/page")));
        crawler.crawl(start);
        verify(fetcher, times(1)).fetch(any());
    }

    @Test
    void doesNotVisitSameUrlTwice() {
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
        String start  = "https://crawlme.monzo.com/";
        String broken = "https://crawlme.monzo.com/broken";
        String good   = "https://crawlme.monzo.com/good";
        when(fetcher.fetch(start)).thenReturn(Optional.of(html(start, "/broken", "/good")));
        when(fetcher.fetch(broken)).thenReturn(Optional.empty());
        when(fetcher.fetch(good)).thenReturn(Optional.of(html(good)));
        crawler.crawl(start);
        verify(fetcher).fetch(start);
        // verify(fetcher, times(Crawler.MAX_RETRIES)).fetch(broken);
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
    void extractHostReturnsCorrectHost() {
        assertEquals("crawlme.monzo.com",
                Crawler.extractHost("https://crawlme.monzo.com/page"));
    }

    @Test
    void extractHostReturnsNullForInvalidUrl() {
        assertNull(Crawler.extractHost("not a url"));
    }
}