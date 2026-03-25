package com.webcrawler.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LinkExtractorTest {

    private LinkExtractor extractor;
    private static final String ALLOWED_HOST = "crawlme.monzo.com";
    private static final String BASE_URL     = "https://crawlme.monzo.com/";

    @BeforeEach
    void setUp() { extractor = new LinkExtractor(); }

    private Document parse(String html) {
        return Jsoup.parse(html, BASE_URL);
    }

    @Test
    void extractsAbsoluteSameDomainLink() {
        List<String> links = extractor.extract(
                parse("<a href='https://crawlme.monzo.com/about'>About</a>"), ALLOWED_HOST);
        assertEquals(List.of("https://crawlme.monzo.com/about"), links);
    }

    @Test
    void resolvesRelativeLink() {
        List<String> links = extractor.extract(
                parse("<a href='/contact'>Contact</a>"), ALLOWED_HOST);
        assertEquals(List.of("https://crawlme.monzo.com/contact"), links);
    }

    @Test
    void stripsFragment() {
        List<String> links = extractor.extract(
                parse("<a href='/page#section'>Link</a>"), ALLOWED_HOST);
        assertEquals(List.of("https://crawlme.monzo.com/page"), links);
    }

    @Test
    void excludesExternalLinks() {
        assertTrue(extractor.extract(
                parse("<a href='https://facebook.com/monzo'>FB</a>"), ALLOWED_HOST).isEmpty());
    }

    @Test
    void excludesSiblingSubdomain() {
        assertTrue(extractor.extract(
                parse("<a href='https://community.monzo.com/'>Community</a>"), ALLOWED_HOST).isEmpty());
    }

    @Test
    void excludesMailtoLinks() {
        assertTrue(extractor.extract(
                parse("<a href='mailto:hello@monzo.com'>Email</a>"), ALLOWED_HOST).isEmpty());
    }

    @Test
    void excludesAnchorsWithoutHref() {
        assertTrue(extractor.extract(
                parse("<a name='top'>Back to top</a>"), ALLOWED_HOST).isEmpty());
    }

    @Test
    void returnsMultipleSameDomainLinks() {
        List<String> links = extractor.extract(parse("""
                <a href='/page1'>One</a>
                <a href='/page2'>Two</a>
                <a href='https://external.com'>Ext</a>
                """), ALLOWED_HOST);
        assertEquals(2, links.size());
        assertTrue(links.contains("https://crawlme.monzo.com/page1"));
        assertTrue(links.contains("https://crawlme.monzo.com/page2"));
    }
}