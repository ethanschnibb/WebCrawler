package com.webcrawler.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test void isValid_acceptsHttps()  { assertTrue(UrlValidator.isValid("https://crawlme.monzo.com/")); }
    @Test void isValid_acceptsHttp()   { assertTrue(UrlValidator.isValid("http://crawlme.monzo.com/")); }
    @Test void isValid_rejectsNull()   { assertFalse(UrlValidator.isValid(null)); }
    @Test void isValid_rejectsBlank()  { assertFalse(UrlValidator.isValid("  ")); }
    @Test void isValid_rejectsMailto() { assertFalse(UrlValidator.isValid("mailto:user@example.com")); }
    @Test void isValid_rejectsTel()    { assertFalse(UrlValidator.isValid("tel:+441234567890")); }
    @Test void isValid_rejectsJs()     { assertFalse(UrlValidator.isValid("javascript:void(0)")); }

    @Test
    void isSameHost_matchesExact() {
        assertTrue(UrlValidator.isSameHost("https://crawlme.monzo.com/page", "crawlme.monzo.com"));
    }

    @Test
    void isSameHost_caseInsensitive() {
        assertTrue(UrlValidator.isSameHost("https://CRAWLME.MONZO.COM/page", "crawlme.monzo.com"));
    }

    @Test
    void isSameHost_rejectsParentDomain() {
        assertFalse(UrlValidator.isSameHost("https://monzo.com/page", "crawlme.monzo.com"));
    }

    @Test
    void isSameHost_rejectsSiblingSubdomain() {
        assertFalse(UrlValidator.isSameHost("https://community.monzo.com/page", "crawlme.monzo.com"));
    }

    @Test
    void isSameHost_rejectsHostInPath() {
        // The case url.contains() gets wrong
        assertFalse(UrlValidator.isSameHost("https://evil.com/crawlme.monzo.com/phish", "crawlme.monzo.com"));
    }

    @Test
    void isSameHost_rejectsHostSuffixAttack() {
        // Also passes contains() but fails exact host match
        assertFalse(UrlValidator.isSameHost("https://crawlme.monzo.com.attacker.com/", "crawlme.monzo.com"));
    }
}