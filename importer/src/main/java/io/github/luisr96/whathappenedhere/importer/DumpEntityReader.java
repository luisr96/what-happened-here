package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.sampler.DumpReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import tools.jackson.databind.JsonNode;

/**
 * Reads dump entities for the extract step and survives two kinds of interruption:
 * <ul>
 *   <li>a dropped connection: reopen from the start and skip the entities already returned
 *       (a gzip stream can't be entered in the middle), waiting twice as long after each failed
 *       attempt (up to {@link #MAX_DELAY}), at most {@code maxReconnects} attempts in a row
 *       without progress;</li>
 *   <li>a crashed run: Spring Batch saves {@code entities.read} with every committed chunk, and a
 *       restart skips that many before reading on.</li>
 * </ul>
 * Entities are counted after DumpReader's parsing, so malformed lines never shift the count.
 * Skipping still decompresses and tokenizes everything before the resume point: late in the full
 * dump that is hours, the price of a format that can only be read from the start.
 */
public class DumpEntityReader implements ItemStreamReader<JsonNode> {

    static final String READ_KEY = "entities.read";
    static final Duration MAX_DELAY = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(DumpEntityReader.class);

    private final DumpOpener opener;
    private final String source;
    private final long limit;
    private final int maxReconnects;
    private final Duration reconnectDelay;

    private long read;
    private Stream<JsonNode> stream;
    private Iterator<JsonNode> entities;

    /**
     * @param limit total entities to return, counting ones skipped on restart; {@code <= 0}: all
     * @param maxReconnects reconnect attempts in a row without returning an entity before giving up
     * @param reconnectDelay wait before the first reconnect; doubled after each failed one
     */
    public DumpEntityReader(DumpOpener opener, String source, long limit, int maxReconnects,
            Duration reconnectDelay) {
        this.opener = Objects.requireNonNull(opener, "opener");
        this.source = Objects.requireNonNull(source, "source");
        this.limit = limit;
        this.maxReconnects = maxReconnects;
        this.reconnectDelay = Objects.requireNonNull(reconnectDelay, "reconnectDelay");
    }

    @Override
    public void open(ExecutionContext context) {
        read = context.getLong(READ_KEY, 0L);
        int failures = 0;
        while (true) {
            try {
                connect();
                return;
            } catch (IOException | UncheckedIOException e) {
                failures = afterFailure(failures, e);
            }
        }
    }

    @Override
    public JsonNode read() {
        if (limit > 0 && read >= limit) {
            return null;
        }
        int failures = 0;
        while (true) {
            try {
                if (entities == null) {
                    connect();
                }
                if (!entities.hasNext()) {
                    return null;
                }
                JsonNode entity = entities.next();
                read++;
                return entity;
            } catch (IOException | UncheckedIOException e) {
                failures = afterFailure(failures, e);
            }
        }
    }

    @Override
    public void update(ExecutionContext context) {
        context.putLong(READ_KEY, read);
    }

    @Override
    public void close() {
        disconnect();
    }

    /** Opens the source, positioned to pass over the {@code read} entities already returned. */
    private void connect() throws IOException {
        disconnect();
        InputStream in = opener.open(source);
        stream = new DumpReader(in, Long.MAX_VALUE).entities(read);
        entities = stream.iterator();
    }

    /**
     * Drops the broken stream and waits before the next attempt; gives up after
     * {@code maxReconnects} attempts in a row. Returns the new failure count.
     */
    private int afterFailure(int failures, Exception cause) {
        disconnect();
        if (failures >= maxReconnects) {
            throw new ItemStreamException("Gave up on " + source + " after " + failures
                    + " reconnects at entity " + read, cause);
        }
        Duration delay = backoff(failures);
        log.warn("Reading {} failed at entity {} ({}); reconnect {} of {} in {}", source, read,
                cause.toString(), failures + 1, maxReconnects, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ItemStreamException("Interrupted while reconnecting to " + source, e);
        }
        return failures + 1;
    }

    private Duration backoff(int failures) {
        Duration delay = reconnectDelay;
        for (int i = 0; i < failures && delay.compareTo(MAX_DELAY) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    private void disconnect() {
        if (stream != null) {
            try {
                stream.close();
            } catch (UncheckedIOException ignored) {
                // Already broken; a fresh connection replaces it.
            }
        }
        stream = null;
        entities = null;
    }
}
