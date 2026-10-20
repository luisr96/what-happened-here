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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Streams entities from a gzipped Wikidata JSON dump, one line at a time, so the dump is never
 * held in memory. The dump is one JSON array with an entity per line: {@code [}, then
 * {@code {...},} lines (the last without a comma), then {@code ]}.
 */
public final class DumpReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER = 64 * 1024;

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
        return lines()
                .map(this::parse)
                .filter(Objects::nonNull)
                .limit(limit);
    }

    /**
     * The entities as unparsed text, after passing over the first {@code skip}: every line except
     * blank ones and the array's brackets counts, valid JSON or not, so skipping costs only
     * decompression. For callers that parse elsewhere (the importer parses on several threads);
     * {@code limit} counts the lines returned after the skip.
     */
    public Stream<String> entityLines(long skip) {
        return lines().skip(skip).limit(limit);
    }

    /** Entity lines without their trailing commas; closing the stream closes the input. */
    private Stream<String> lines() {
        BufferedReader lines;
        try {
            lines = new BufferedReader(new InputStreamReader(
                    new NoReadAhead(new GZIPInputStream(gzipped, BUFFER)), StandardCharsets.UTF_8), BUFFER);
        } catch (IOException notGzip) {
            try {
                gzipped.close();
            } catch (IOException suppressed) {
                notGzip.addSuppressed(suppressed);
            }
            throw new UncheckedIOException(notGzip);
        }
        return lines.lines()
                .map(DumpReader::entityText)
                .filter(Objects::nonNull)
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

    /**
     * One entity line (as {@link #entityLines} returns it) parsed, or null when it isn't a JSON
     * object. The one definition of "an entity" for everything that reads the dump.
     */
    public static JsonNode parseEntity(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            return node != null && node.isObject() ? node : null;
        } catch (JacksonException malformed) {
            return null;
        }
    }

    private JsonNode parse(String entity) {
        JsonNode node = parseEntity(entity);
        if (node == null) {
            malformedLines++; // one bad line costs one entity, not the run
        }
        return node;
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
