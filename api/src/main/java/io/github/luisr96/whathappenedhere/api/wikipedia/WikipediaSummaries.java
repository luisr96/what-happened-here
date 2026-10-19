package io.github.luisr96.whathappenedhere.api.wikipedia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wikipedia summaries with a bounded in-memory cache keyed by title, so an item's several events
 * share one fetch. Found and missing articles are cached for a day (a 404 means the sitelink is
 * stale; the follower will bring a new title, hence a new key). After a failure the title is left
 * alone for a minute, so an outage or rate limit isn't hammered by every request.
 */
@Component
public class WikipediaSummaries {

    private static final Logger log = LoggerFactory.getLogger(WikipediaSummaries.class);
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);

    private final WikipediaClient client;
    private final Cache<String, Optional<WikipediaSummary>> summaries;
    private final Cache<String, Boolean> recentFailures;

    @Autowired
    public WikipediaSummaries(WikipediaClient client) {
        this(client, Ticker.systemTicker());
    }

    /** With a controllable clock, for tests. */
    public WikipediaSummaries(WikipediaClient client, Ticker ticker) {
        this.client = client;
        this.summaries = Caffeine.newBuilder().ticker(ticker)
                .maximumSize(10_000).expireAfterWrite(Duration.ofDays(1)).build();
        this.recentFailures = Caffeine.newBuilder().ticker(ticker)
                .maximumSize(10_000).expireAfterWrite(FAILURE_BACKOFF).build();
    }

    /** The article's summary, or empty when there is no title, no article, or Wikipedia failed. */
    public Optional<WikipediaSummary> summary(String title) {
        if (title == null || title.isBlank() || recentFailures.getIfPresent(title) != null) {
            return Optional.empty();
        }
        try {
            // Caffeine runs the loader once per key; concurrent callers wait for its result, and
            // a loader that throws caches nothing.
            return summaries.get(title, client::fetch);
        } catch (RuntimeException e) {
            recentFailures.put(title, Boolean.TRUE);
            log.warn("Wikipedia summary for {} failed: {}", title, e.toString());
            return Optional.empty();
        }
    }
}
