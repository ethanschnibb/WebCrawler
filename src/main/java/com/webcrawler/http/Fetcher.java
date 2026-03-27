package com.webcrawler.http;

import java.util.Optional;

import org.jsoup.nodes.Document;

public interface Fetcher {
    Optional<Document> fetch(String url);
    Optional<String> fetchText(String url); 
}
