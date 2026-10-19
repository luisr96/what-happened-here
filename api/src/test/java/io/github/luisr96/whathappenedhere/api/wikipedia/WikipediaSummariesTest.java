package io.github.luisr96.whathappenedhere.api.wikipedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Caching and failure handling of WikipediaSummaries over a mocked client. */
class WikipediaSummariesTest {

    private static final WikipediaSummary ROME = new WikipediaSummary("Rome", "Capital of Italy.",
            "https://en.wikipedia.org/wiki/Rome", null);
    private static final WikipediaSummary PARIS = new WikipediaSummary("Paris", "Capital of France.",
            "https://en.wikipedia.org/wiki/Paris", "https://upload.test/paris.jpg");

    /** A Ticker the test advances by hand, in nanoseconds. */
    private static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration d) {
            nanos.addAndGet(d.toNanos());
        }
    }

    private WikipediaClient client;
    private WikipediaSummaries summaries;
    private FakeTicker ticker;

    @BeforeEach
    void setUp() {
        client = mock(WikipediaClient.class);
        ticker = new FakeTicker();
        summaries = new WikipediaSummaries(client, ticker);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingTitleIsEmptyWithoutCallingClient(String title) {
        assertTrue(summaries.summary(title).isEmpty());
        verify(client, never()).fetch(any());
    }

    @Test
    void sameTitleFetchedOnce() {
        when(client.fetch("Rome")).thenReturn(Optional.of(ROME));

        assertEquals(Optional.of(ROME), summaries.summary("Rome"));
        assertEquals(Optional.of(ROME), summaries.summary("Rome"));
        verify(client, times(1)).fetch("Rome");
    }

    @Test
    void differentTitlesFetchedSeparately() {
        when(client.fetch("Rome")).thenReturn(Optional.of(ROME));
        when(client.fetch("Paris")).thenReturn(Optional.of(PARIS));

        assertEquals(Optional.of(ROME), summaries.summary("Rome"));
        assertEquals(Optional.of(PARIS), summaries.summary("Paris"));
        verify(client, times(1)).fetch("Rome");
        verify(client, times(1)).fetch("Paris");
    }

    @Test
    void notFoundIsCached() {
        when(client.fetch("Gone")).thenReturn(Optional.empty());

        assertTrue(summaries.summary("Gone").isEmpty());
        assertTrue(summaries.summary("Gone").isEmpty());
        verify(client, times(1)).fetch("Gone");
    }

    @Test
    void failureIsEmptyAndRetriedAfterBackoff() {
        when(client.fetch("Rome")).thenThrow(new IllegalStateException("HTTP 503")).thenReturn(Optional.of(ROME));

        assertTrue(summaries.summary("Rome").isEmpty());
        ticker.advance(Duration.ofSeconds(61));
        assertEquals(Optional.of(ROME), summaries.summary("Rome"));
        verify(client, times(2)).fetch("Rome");
    }

    @Test
    void failureBacksOffWithoutCallingClient() {
        when(client.fetch("Rome")).thenThrow(new WikipediaClient.WikipediaException("HTTP 429", 429));

        assertTrue(summaries.summary("Rome").isEmpty());
        ticker.advance(Duration.ofSeconds(59));
        assertTrue(summaries.summary("Rome").isEmpty());
        verify(client, times(1)).fetch("Rome");
    }

    @Test
    void successAfterBackoffIsCached() {
        when(client.fetch("Rome")).thenThrow(new IllegalStateException("HTTP 503")).thenReturn(Optional.of(ROME));

        assertTrue(summaries.summary("Rome").isEmpty());
        ticker.advance(Duration.ofSeconds(61));
        assertEquals(Optional.of(ROME), summaries.summary("Rome"));
        ticker.advance(Duration.ofSeconds(61));
        assertEquals(Optional.of(ROME), summaries.summary("Rome"));
        verify(client, times(2)).fetch("Rome");
    }

    @Test
    void backoffIsPerTitle() {
        when(client.fetch("Rome")).thenThrow(new IllegalStateException("HTTP 503"));
        when(client.fetch("Paris")).thenReturn(Optional.of(PARIS));

        assertTrue(summaries.summary("Rome").isEmpty());
        assertEquals(Optional.of(PARIS), summaries.summary("Paris"));
    }

    @Test
    void concurrentCallsForSameTitleFetchOnce() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(client.fetch("X")).thenAnswer(inv -> {
            release.await(5, TimeUnit.SECONDS);
            return Optional.of(ROME);
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Optional<WikipediaSummary>>> results = IntStream.range(0, 8)
                    .mapToObj(i -> pool.submit(() -> summaries.summary("X")))
                    .toList();
            Thread.sleep(200);
            release.countDown();
            for (Future<Optional<WikipediaSummary>> r : results) {
                assertEquals(Optional.of(ROME), r.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        verify(client, times(1)).fetch("X");
    }
}
