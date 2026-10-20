package io.github.luisr96.whathappenedhere.sampler;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Streams entities from a gzipped Wikidata JSON dump, one line at a time, so the dump is never
 * held in memory. The dump is one JSON array with an entity per line: {@code [}, then
 * {@code {...},} lines (the last without a comma), then {@code ]}.
 */
public final class DumpReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InputStream gzipped;
    private final long limit;
    private long malformedLines;

    /** @param limit maximum number of entities to read; reading stops there without draining the rest */
    public DumpReader(InputStream gzipped, long limit) {
        this.gzipped = Objects.requireNonNull(gzipped, "gzipped");
        this.limit = limit;
    }

    /**
     * The entities, parsed lazily. Close the stream (try-with-resources) to release the input,
     * which also aborts an HTTP download early.
     */
    public Stream<JsonNode> entities() {
        return entities(0);
    }

    /**
     * Like {@link #entities()}, after passing over the first {@code skip} entities without
     * building them (resuming a long import). Each skipped line is still checked to be exactly one
     * JSON object, so the count matches what {@link #entities()} would have returned; that check
     * streams through the tokens, much cheaper than building the tree. {@code limit} counts the
     * entities returned after the skip.
     */
    public Stream<JsonNode> entities(long skip) {
        BufferedReader lines;
        try {
            lines = new BufferedReader(
                    new InputStreamReader(new NoReadAhead(new GZIPInputStream(gzipped)), StandardCharsets.UTF_8));
        } catch (IOException notGzip) {
            try {
                gzipped.close();
            } catch (IOException suppressed) {
                notGzip.addSuppressed(suppressed);
            }
            throw new UncheckedIOException(notGzip);
        }
        long[] toSkip = {skip};
        return lines.lines()
                .map(DumpReader::entityText)
                .filter(Objects::nonNull)
                .filter(entity -> {
                    if (toSkip[0] > 0 && isOneObject(entity)) {
                        toSkip[0]--;
                        return false;
                    }
                    return true;
                })
                .map(this::parse)
                .filter(Objects::nonNull)
                .limit(limit)
                .onClose(() -> {
                    try {
                        lines.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /** Lines that looked like entities but didn't parse; complete only once the stream is consumed. */
    public long malformedLines() {
        return malformedLines;
    }

    /** The line without its trailing comma, or null for the array's brackets and blank lines. */
    private static String entityText(String line) {
        String entity = line.strip();
        if (entity.endsWith(",")) {
            entity = entity.substring(0, entity.length() - 1);
        }
        if (entity.isEmpty() || entity.equals("[") || entity.equals("]")) {
            return null;
        }
        return entity;
    }

    /** Whether {@link #parse} would accept the text, checked without building the tree. */
    private static boolean isOneObject(String entity) {
        try (JsonParser parser = MAPPER.createParser(entity)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return false;
            }
            parser.skipChildren();
            return parser.nextToken() == null;
        } catch (JacksonException malformed) {
            return false;
        }
    }

    private JsonNode parse(String entity) {
        try {
            JsonNode node = MAPPER.readTree(entity);
            if (node.isObject()) {
                return node;
            }
        } catch (JacksonException malformed) {
            // Counted below: one bad line costs one entity, not the run.
        }
        malformedLines++;
        return null;
    }

    /**
     * Reports nothing available, so InputStreamReader hands over what it has decoded instead of
     * reading on: GZIPInputStream reports 1 until the end, which would make it keep reading until
     * its buffer is full, and a dropped connection would then also lose complete lines.
     */
    private static final class NoReadAhead extends FilterInputStream {

        NoReadAhead(InputStream in) {
            super(in);
        }

        @Override
        public int available() {
            return 0;
        }
    }
}
