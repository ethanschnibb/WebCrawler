package com.webcrawler.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UrlNormaliserTest {

    @Test
    void shouldLowercaseSchemeAndHost() {
        String url = "HTTP://EXAMPLE.COM/Page";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/Page", result);
    }

    @Test
    void shouldRemoveDefaultHttpPort() {
        String url = "http://example.com:80/page";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/page", result);
    }

    @Test
    void shouldRemoveDefaultHttpsPort() {
        String url = "https://example.com:443/page";

        String result = UrlNormaliser.normalise(url);

        assertEquals("https://example.com/page", result);
    }

    @Test
    void shouldPreserveNonDefaultPort() {
        String url = "http://example.com:8080/page";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com:8080/page", result);
    }

    @Test
    void shouldCollapseEmptyPathToRoot() {
        String url = "http://example.com";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/", result);
    }

    @Test
    void shouldRemoveTrailingSlashFromNonRootPath() {
        String url = "http://example.com/page/";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/page", result);
    }

    @Test
    void shouldKeepRootSlash() {
        String url = "http://example.com/";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/", result);
    }

    @Test
    void shouldStripEmptyQueryString() {
        String url = "http://example.com/page?";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/page", result);
    }

    @Test
    void shouldPreserveQueryString() {
        String url = "http://example.com/page?id=123";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/page?id=123", result);
    }

    @Test
    void shouldRemoveFragment() {
        String url = "http://example.com/page#section1";

        String result = UrlNormaliser.normalise(url);

        assertEquals("http://example.com/page", result);
    }

    @Test
    void shouldReturnOriginalUrlIfInvalid() {
        String url = "ht!tp://bad url";

        String result = UrlNormaliser.normalise(url);

        assertEquals(url, result);
    }

}