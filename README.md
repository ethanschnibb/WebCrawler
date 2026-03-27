# Web Crawler

A concurrent, single-domain web crawler written in plain Java 17.

## Quick start

```bash
# Build
mvn package -q

# Run against the Monzo test site
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/

# With options
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/ \
  --threads 8 \
  --delay-ms 50 \
  --max-pages 200 \
  --no-robots

# Run tests
mvn test
```

## CLI options

| Flag | Default | Description |
|------|---------|-------------|
| `--threads N` | 4 | Number of concurrent worker threads |
| `--delay-ms N` | 100 | Polite delay between requests per thread (ms) |
| `--max-pages N` | 0 (unlimited) | Hard cap on pages crawled |
| `--no-robots` | off | Ignore `robots.txt` directives |

Output goes to **stdout**; logs go to **stderr** — so you can redirect them independently:

```bash
java -jar webcrawler.jar https://crawlme.monzo.com/ > results.txt 2> crawl.log
```

---

## Design

### Why not Spring Boot?

Spring Boot is excellent for long-running services with bean lifecycles, HTTP servers, and auto-configuration. For a CLI tool that runs once and exits, it adds ~200ms startup overhead, classpath scanning, and framework concepts that obscure rather than illuminate the actual design. The wiring is explicit in `Main.java` — every dependency is clearly constructed and passed down.

### Package structure

```
com.monzo.crawler
├── Main.java                   Entry point; wires dependencies together
├── config/
│   └── CrawlerConfig.java      Immutable config with a fluent builder
├── core/
│   ├── Crawler.java            Orchestration: concurrency, termination, dedup
│   └── CrawlResult.java        Immutable value object for one visited page
├── http/
│   ├── PageFetcher.java        Interface — enables test doubles
│   ├── HttpPageFetcher.java    Real impl using java.net.http.HttpClient
│   ├── FetchResponse.java      Thin wrapper over HTTP response
│   └── RobotsFilter.java       Parses and enforces robots.txt
├── parser/
│   ├── LinkExtractor.java      Interface — enables test doubles
│   └── JsoupLinkExtractor.java jsoup-backed HTML parser
├── output/
│   └── ResultPrinter.java      Formats results to a PrintStream
└── util/
    └── UrlNormalizer.java      URL canonicalisation and host matching
```

### Concurrency model

`Crawler` uses a **fixed thread pool + `LinkedBlockingQueue`** (producer-consumer BFS):

- Workers dequeue a URL, fetch it, extract links, enqueue new ones.
- A `ConcurrentHashMap`-backed set tracks seen URLs; `visited.add(link)` is atomic — so even with N threads racing to enqueue the same link, only one succeeds.
- **Termination detection** is the subtle part: the queue being empty is necessary but not sufficient (another thread might be mid-fetch and about to produce new URLs). We use an `AtomicInteger activeWorkers` counter. A worker exits only when both conditions hold simultaneously: `queue.isEmpty() && activeWorkers.get() == 0`. Workers poll with a 200ms timeout rather than blocking forever, so the condition is re-evaluated regularly.

Alternative considered: **reactive streams (Project Reactor/RxJava)**. Rejected because the overhead of learning the mental model isn't justified for I/O-bound work where a simple thread pool is already effective, and because it makes the code harder to reason about under test.

Alternative considered: **virtual threads (Java 21)**. The model would simplify to one-thread-per-URL, which is appealing. Kept as a noted extension rather than a hard requirement so the code compiles on Java 17.

### URL deduplication and normalisation

Without normalisation, `/about`, `/about/`, and `HTTPS://EXAMPLE.COM/about` would each be treated as distinct URLs and visited multiple times. `UrlNormalizer` canonicalises:

- Lowercase scheme + host
- Strip default ports (80/http, 443/https)
- Strip trailing slash from non-root paths
- Strip fragment identifiers (already stripped by the extractor, but defensive here)
- Strip empty query strings

Fragment stripping is done in `JsoupLinkExtractor` using jsoup's `abs:href` attribute, which resolves relative links, then stripping via `URI` reconstruction.

### Host restriction

`UrlNormalizer.isSameHost` does **exact host equality** (case-insensitive). This means:

- `crawlme.monzo.com` ✓ (allowed)
- `monzo.com` ✗ (parent domain — different host)
- `community.monzo.com` ✗ (sibling subdomain — different host)
- `www.crawlme.monzo.com` ✗ (child subdomain — different host)

This is the correct interpretation of the spec ("limited to one subdomain").

### robots.txt

`RobotsFilter` is loaded once before any workers start (on the main thread). It implements the common subset of [RFC 9309](https://www.rfc-editor.org/rfc/rfc9309): `User-agent` matching (specific agent preferred over wildcard `*`) and `Disallow` prefix matching. It does **not** implement `Allow:`, `Crawl-delay:`, or wildcard patterns beyond prefix — sufficient for the vast majority of real sites. If `robots.txt` is unreachable, we fail open (allow all paths).

### Library choices

| Library | Reason |
|---------|--------|
| `jsoup` | Lenient, battle-tested HTML parser. Handles malformed HTML, charset detection, and relative URL resolution. Re-implementing this would be a distraction from the crawler design. |
| `java.net.http.HttpClient` | Built into Java 11+. Supports HTTP/2, connection pooling, and redirect-following. No extra dependency. |
| `slf4j` + `logback` | Standard logging facade. Logs go to stderr; results to stdout. |
| `junit-jupiter` + `mockito` | Industry-standard test stack. |
| `okhttp3 mockwebserver` | Lets us test the real HTTP layer (headers, status codes, content-type) without hitting the network. |

### Testing strategy

| Layer | Approach |
|-------|----------|
| `UrlNormalizer` | Pure unit tests — no I/O, just URI transformations |
| `JsoupLinkExtractor` | Unit tests with inline HTML strings |
| `RobotsFilter` | Unit tests with inline robots.txt strings |
| `Crawler` | Unit tests with `PageFetcher` mocked via Mockito — tests concurrency, dedup, error handling, page cap |
| `HttpPageFetcher` | Integration tests with OkHttp `MockWebServer` — tests real HTTP behaviour |

### Known limitations and possible extensions

- **`Crawl-delay` from robots.txt** is not respected (we use a fixed configured delay). A future version could parse and honour it.
- **`sitemap.xml`** is not consulted as a seed source.
- **Content-based deduplication** (detecting pages that redirect to the same canonical URL) is handled via redirect-following in `HttpClient`, but two different paths with identical content would still both be visited.
- **JavaScript-rendered content** is not supported — the crawler only sees server-rendered HTML. A Playwright/Selenium integration would be the extension point.
- **Politeness per-domain** — currently the delay applies globally, not per-host. For multi-domain crawls this would need refinement, but is fine for single-host.
- **Virtual threads**: replacing `Executors.newFixedThreadPool` with `Executors.newVirtualThreadPerTaskExecutor` (Java 21+) would let us increase concurrency dramatically with negligible overhead, at the cost of needing a per-request delay at the `HttpClient` level.
