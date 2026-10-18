package io.github.luisr96.whathappenedhere.sampler;

import java.io.BufferedReader;
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
        BufferedReader lines;
        try {
            lines = new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(gzipped), StandardCharsets.UTF_8));
        } catch (IOException notGzip) {
            try {
                gzipped.close();
            } catch (IOException suppressed) {
                notGzip.addSuppressed(suppressed);
            }
            throw new UncheckedIOException(notGzip);
        }
        return lines.lines()
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

    private JsonNode parse(String line) {
        String entity = line.strip();
        if (entity.endsWith(",")) {
            entity = entity.substring(0, entity.length() - 1);
        }
        if (entity.isEmpty() || entity.equals("[") || entity.equals("]")) {
            return null;
        }
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
}
