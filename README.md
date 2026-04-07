# Web Crawler

A concurrent, single-domain web crawler written in Java 21.

## Quick start

```bash
# Build
mvn package --quiet

# Run against the Monzo test site
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/

# With options — maxPages and maxDepth are optional, 0 means unlimited
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/ 100 5

# Run tests
mvn test

# Run tests with coverage report
mvn verify

# View coverage report (open in browser after running mvn verify)
open target/site/jacoco/index.html
```

## CLI arguments

```
webcrawler <start-url> [maxPages] [maxDepth]
```

| Argument    | Required | Default       | Description                                                   |
| ----------- | -------- | ------------- | ------------------------------------------------------------- |
| `start-url` | Yes      | —             | The URL to start crawling from. Determines the allowed host.  |
| `maxPages`  | No       | 0 (unlimited) | Stop after crawling this many pages                           |
| `maxDepth`  | No       | 0 (unlimited) | Stop following links beyond this many hops from the start URL |

Output goes to **stdout**; logs go to **stderr** — redirect them independently:

```bash
# Results only
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/ > results.txt

# Results and logs to separate files
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/ > results.txt 2> crawl.log

# Suppress logs entirely
java -jar target/webcrawler-1.0.0-jar-with-dependencies.jar https://crawlme.monzo.com/ > results.txt 2>/dev/null
```

---

## Architecture

### Why not Spring Boot?

Spring Boot is excellent for long-running services with bean lifecycles, HTTP servers, and auto-configuration. For a CLI tool that runs once and exits, it adds startup overhead, classpath scanning, and framework concepts that obscure rather than illuminate the design. The wiring is explicit in `Main.java` — every dependency is constructed and passed down manually, making the data flow easy to follow.

### Package structure

```
com.webcrawler
├── Main.java                        Entry point — parses args, builds config, wires dependencies
├── Crawler.java                     Orchestration — queue, worker lifecycle, dedup, depth tracking
├── config/
│   └── CrawlerConfig.java           Immutable config with a fluent builder — all tunable configurations in one place
├── http/
│   ├── Fetcher.java                 Interface — decouples HTTP from crawl logic, enables test fakes
│   ├── PageFetcher.java             jsoup-backed implementation with retries and exponential backoff
│   └── PolitenessChecker.java       Fetches, parses, and enforces robots.txt rules
├── parser/
│   └── LinkExtractor.java           Extracts and filters same-domain links from a jsoup Document
└── util/
    ├── UrlValidator.java            Validates HTTP/S URLs using URI parsing, not string matching
    └── UrlNormaliser.java           Canonicalises URLs to prevent duplicate visits
```

### Concurrency model

The crawler uses **virtual threads** (Java 21) with a `LinkedBlockingQueue` as the work queue — a producer-consumer BFS:

- `Executors.newVirtualThreadPerTaskExecutor()` — virtual threads yield automatically when blocked on I/O, so no OS thread is ever wasted waiting for a network response.
- `CrawlerConfig.concurrency` controls how many worker tasks are submitted, capping concurrent outbound requests for politeness.
- `ConcurrentHashMap.newKeySet()` for the visited set — `visited.add(link)` is atomic, so N workers racing to enqueue the same URL have exactly one winner.

**Termination** An empty queue alone is not enough to terminate the app — another worker could be mid-fetch and about to produce new URLs. An `AtomicInteger activeWorkers` counter solves this: a worker exits only when both `queue.isEmpty()` and `activeWorkers == 0` hold simultaneously. Workers use `poll(200ms)` rather than `take()` so the condition is rechecked regularly.

### Depth tracking

Depth travels with the URL through the queue as a `UrlWithDepth` record:

```java
private record UrlWithDepth(String url, int depth) {}
```

Each hop increments the depth by 1. When `maxDepth` is set, workers skip fetching any URL at or beyond the limit — but still log that it was discovered. A separate `AtomicInteger pageCount` tracks successful fetches for the `maxPages` cap; this is checked before fetching, not after, so the crawler doesn't overshoot by the number of concurrent workers.

### URL normalisation

Without normalisation, the same page can appear under multiple spellings and be visited multiple times:

```
https://crawlme.monzo.com/about
https://crawlme.monzo.com/about/     ← trailing slash
https://CRAWLME.MONZO.COM/about      ← uppercase host
https://crawlme.monzo.com/about?     ← empty query string
```

`UrlNormaliser` canonicalises by lowercasing scheme and host, stripping default ports, removing trailing slashes from non-root paths, stripping empty query strings, and removing fragment identifiers.

### Host restriction

`UrlValidator.isSameHost` does exact host equality (case-insensitive) using `URI.getHost()`, not `String.contains()`. The `contains` approach is exploitable:

```
https://evil.com/crawlme.monzo.com/phish   ← contains the target host in the path
https://crawlme.monzo.com.attacker.com/    ← target host is a substring of attacker host
```

Both pass a `contains` check but fail an exact host match. Only `crawlme.monzo.com` is allowed — `monzo.com`, `community.monzo.com`, and `www.crawlme.monzo.com` are all rejected.

### robots.txt

`PolitenessChecker` is constructed once on the main thread before any workers start, then passed to each worker as a read-only reference — no synchronisation needed on `isAllowed()` calls.

It implements the common subset of RFC 9309:

- `User-agent` matching — specific agent name takes precedence over the `*` wildcard
- `Disallow` prefix matching

It intentionally does not implement `Allow:`, `Crawl-delay:`, or wildcard path patterns. These cover the vast majority of real `robots.txt` files. If `robots.txt` cannot be fetched, the crawler fails open (allows all paths) — for sites that simply don't have one.

### Retry and backoff

`PageFetcher` retries failed fetches up to `maxRetries` times with exponential backoff:

Exponential backoff avoids the thundering herd problem — if a server is briefly overloaded, retrying at fixed intervals means all threads hit it simultaneously again. Staggering them increases the chance of success and reduces additional load.

---

## Testing

### Running tests

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=CrawlerTest

# Run with coverage report
mvn verify
open target/site/jacoco/index.html
```

### Coverage with JaCoCo

Add JaCoCo to `pom.xml` if not already present:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

`mvn verify` generates a report at `target/site/jacoco/index.html` showing line, branch, and method coverage per class.

### Testing strategy

| Layer               | Approach                                  | Why                                                           |
| ------------------- | ----------------------------------------- | ------------------------------------------------------------- |
| `UrlValidator`      | Pure unit tests                           | No I/O — just URI parsing logic                               |
| `UrlNormaliser`     | Pure unit tests                           | No I/O — just string transformations                          |
| `PolitenessChecker` | Unit tests with inline robots.txt strings | Test constructor bypasses HTTP entirely                       |
| `LinkExtractor`     | Unit tests with inline HTML strings       | jsoup parses in-memory, no network needed                     |
| `PageFetcher`       | Mockito static mocks on `Jsoup`           | Tests retry logic and backoff without real HTTP               |
| `Crawler`           | Mockito mock of `Fetcher` interface       | Tests queue logic, dedup, depth, page cap without any network |
| `Main`              | Unit tests on `run()` and `buildConfig()` | `run()` returns an int so tests avoid `System.exit()`         |

The `Fetcher` interface is to make testing easier. `Crawler` never imports `PageFetcher` — it only knows about `Fetcher`. Tests inject a Mockito mock or a simple lambda:

```java
// Lambda fake — no framework needed
Fetcher fake = url -> Optional.of(Jsoup.parse("<a href='/about'>link</a>", url));
Crawler crawler = new Crawler(fake, new LinkExtractor(), config);
```

---

## Trade-offs

### Blocking I/O vs true async

The crawler uses blocking jsoup calls on virtual threads rather than `CompletableFuture` with a non-blocking HTTP client. Virtual threads yield to the JVM scheduler when blocked on I/O, so no OS thread is wasted — this gives the effective concurrency of async code with the readability of synchronous code. True async (non-blocking) would add significant complexity (callback chains, difficult error handling, unreadable stack traces) for no measurable gain at the concurrency levels appropriate for a single-domain crawler.

### Fixed concurrency cap vs unbounded virtual threads

Workers are capped at `CrawlerConfig.concurrency` even though virtual threads are cheap enough to run thousands. The cap exists for politeness — not for thread overhead. Sending 1000 simultaneous requests to one server is rude and will trigger rate limiting regardless of how cheaply your side handles the threads.

### BFS vs DFS

BFS (queue) is used rather than DFS (stack). BFS visits pages at depth 1 before depth 2, so `maxDepth` is a meaningful concept and shallow pages are always visited first. DFS would explore one branch to its full depth before backtracking, which makes depth limiting less predictable and means important top-level pages might be visited last.

### Fail open on robots.txt

If `robots.txt` cannot be fetched, all paths are allowed. The alternative — blocking all crawling until robots.txt is confirmed — would break on the majority of sites that simply don't have one. Failing open is the standard convention for polite crawlers.

### String URLs vs URI objects throughout

URLs are carried as `String` rather than `URI` through the queue and visited set. URIs are constructed when needed for host extraction and normalisation then discarded. The alternative (carrying `URI` objects) would save repeated parsing but adds complexity to the `UrlWithDepth` record and the concurrent collections. For a crawler where network I/O dominates, the parsing cost is negligible.

---

## Steps to productionise

### 1. Structured logging and observability

Replace `System.out.println` result output with a structured format (JSON lines) so results can be ingested by a log aggregator. Add OpenTelemetry tracing so you can see per-URL fetch latency, retry rates, and queue depth over time. Expose a Prometheus metrics endpoint for crawl rate, error rate, and pages visited.

### 2. Persistent queue

The current `LinkedBlockingQueue` is in-memory — if the process crashes, the crawl state is lost. For a production crawler you'd back the queue with a persistent store (Redis, Kafka, or a database) so a crashed worker can resume from where it left off rather than restarting from scratch.

### 3. Distributed crawling

A single JVM hitting one domain at 100 concurrent requests is fine for small sites. For large sites you'd shard the URL space across multiple crawler instances, with a shared visited set (Redis `SADD` is atomic across instances) and a shared queue (Kafka topic per domain). Each instance would consume from the same topic and publish discovered URLs back to it.

### 4. Crawl-delay from robots.txt

The current implementation ignores `Crawl-delay:` directives. A production crawler should parse this field and introduce the requested delay between requests to that host, overriding the configured default.

### 5. Politeness per domain

The current delay applies globally across all workers. If you extend the crawler to multiple domains, each domain should have its own delay and rate limit tracked separately — one slow domain shouldn't block fetches for others.

### 6. Redirect handling and canonical URLs

The current crawler follows redirects via jsoup but treats the requested URL and the final URL as different entries if they differ. A production crawler would record the final URL after redirects and deduplicate against it, preventing the same content from being crawled via both `/old-path` (301 → `/new-path`) and `/new-path` directly.

### 7. Content deduplication

Two different URLs can return identical content. A production crawler would hash the response body (SHA-256) and skip processing pages whose content has already been seen, even if the URL is new.

### 8. Graceful shutdown

The current crawler calls `executor.shutdownNow()` on timeout or interrupt, which may leave in-flight fetches abandoned. A production crawler would catch `SIGTERM`, drain the active workers cleanly, persist the current queue state, and exit with a clear log of how far it got.

### 9. Circuit breaker per host

If a host returns 5xx errors repeatedly, the crawler should back off and stop hammering it rather than retrying at full rate. A circuit breaker (half-open after a backoff period) is the standard pattern — the current implementation only has per-URL retries, not per-host circuit breaking.

### 10. Rate limiting enforcement

Currently concurrency is the only throttle. A token bucket or leaky bucket rate limiter per domain would provide finer-grained control — for example, allowing bursts of up to 5 requests but sustaining no more than 2 requests per second over time.
