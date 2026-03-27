package com.webcrawler.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Optional;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PageFetcherTest {

    private final PageFetcher fetcher = new PageFetcher();

    @Test
    void shouldFetchDocumentSuccessfully() throws Exception {

        Document mockDoc = mock(Document.class);

        Connection connection = mock(Connection.class);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {

            jsoup.when(() -> Jsoup.connect("https://test.com"))
                    .thenReturn(connection);

            when(connection.userAgent(anyString())).thenReturn(connection);
            when(connection.timeout(anyInt())).thenReturn(connection);
            when(connection.get()).thenReturn(mockDoc);

            Optional<Document> result =
                    fetcher.fetch("https://test.com");

            assertTrue(result.isPresent());
            assertEquals(mockDoc, result.get());
        }
    }

    @Test
    void shouldRetryThenSucceed() throws Exception {

        Document mockDoc = mock(Document.class);

        Connection connection = mock(Connection.class);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {

            jsoup.when(() -> Jsoup.connect("https://retry.com"))
                    .thenReturn(connection);

            when(connection.userAgent(anyString()))
                    .thenReturn(connection);

            when(connection.timeout(anyInt()))
                    .thenReturn(connection);

            when(connection.get())
                    .thenThrow(new IOException("fail"))
                    .thenReturn(mockDoc);

            Optional<Document> result =
                    fetcher.fetch("https://retry.com");

            assertTrue(result.isPresent());
        }
    }

    @Test
    void shouldReturnEmptyAfterMaxRetries() throws Exception {

        Connection connection = mock(Connection.class);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {

            jsoup.when(() -> Jsoup.connect("https://fail.com"))
                    .thenReturn(connection);

            when(connection.userAgent(anyString()))
                    .thenReturn(connection);

            when(connection.timeout(anyInt()))
                    .thenReturn(connection);

            when(connection.get())
                    .thenThrow(new IOException("fail"));

            Optional<Document> result =
                    fetcher.fetch("https://fail.com");

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void shouldFetchTextSuccessfully() throws Exception {

        Connection connection = mock(Connection.class);

        Connection.Response response =
                mock(Connection.Response.class);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {

            jsoup.when(() -> Jsoup.connect("https://robots.com"))
                    .thenReturn(connection);

            when(connection.userAgent(anyString()))
                    .thenReturn(connection);

            when(connection.timeout(anyInt()))
                    .thenReturn(connection);

            when(connection.ignoreContentType(true))
                    .thenReturn(connection);

            when(connection.execute())
                    .thenReturn(response);

            when(response.body())
                    .thenReturn("User-agent: *");

            Optional<String> result =
                    fetcher.fetchText("https://robots.com");

            assertTrue(result.isPresent());

            assertEquals("User-agent: *", result.get());
        }
    }

    @Test
    void shouldReturnEmptyIfFetchTextFails() throws Exception {

        Connection connection = mock(Connection.class);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {

            jsoup.when(() -> Jsoup.connect("https://robots.com"))
                    .thenReturn(connection);

            when(connection.userAgent(anyString()))
                    .thenReturn(connection);

            when(connection.timeout(anyInt()))
                    .thenReturn(connection);

            when(connection.ignoreContentType(true))
                    .thenReturn(connection);

            when(connection.execute())
                    .thenThrow(new IOException("404"));

            Optional<String> result =
                    fetcher.fetchText("https://robots.com");

            assertTrue(result.isEmpty());
        }
    }

}