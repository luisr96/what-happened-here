package io.github.luisr96.whathappenedhere.importer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

/**
 * Gzipped in-memory dumps in Wikidata's layout ("[" line, entities with trailing commas, "]"),
 * plus streams that fail at an exact entity boundary.
 */
final class TestDumps {

    private TestDumps() {}

    /**
     * A dump whose gzip stream is sync-flushed after every line, so {@code bytesAfter(k)} is the
     * exact compressed length that decodes to the "[" line and the first k entity lines.
     */
    record Dump(byte[] bytes, long[] offsets) {

        long bytesAfter(int entities) {
            return offsets[entities];
        }

        InputStream stream() {
            return new ByteArrayInputStream(bytes);
        }

        /** A stream that delivers the first {@code entities} entities, then throws on the next read. */
        InputStream failingAfter(int entities, Supplier<RuntimeException> unchecked) {
            return new FailingStream(stream(), bytesAfter(entities), unchecked);
        }

        InputStream failingAfter(int entities) {
            return failingAfter(entities, null);
        }
    }

    /**
     * Builds a dump; every element of {@code lines} counts as one entity line for the offsets,
     * so malformed lines are counted too.
     */
    static Dump dump(List<String> lines) {
        var bytes = new ByteArrayOutputStream();
        var offsets = new ArrayList<Long>();
        try (var gz = new GZIPOutputStream(bytes, true)) {
            gz.write("[\n".getBytes(StandardCharsets.UTF_8));
            gz.flush();
            offsets.add((long) bytes.size());
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i) + (i < lines.size() - 1 ? "," : "") + "\n";
                gz.write(line.getBytes(StandardCharsets.UTF_8));
                gz.flush();
                offsets.add((long) bytes.size());
            }
            gz.write("]\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Dump(bytes.toByteArray(), offsets.stream().mapToLong(Long::longValue).toArray());
    }

    static String entity(String id) {
        return "{\"type\":\"item\",\"id\":\"%s\"}".formatted(id);
    }

    static List<String> entities(int count) {
        var lines = new ArrayList<String>();
        for (int i = 1; i <= count; i++) {
            lines.add(entity("Q" + i));
        }
        return List.copyOf(lines);
    }

    /** Throws (IOException, or the given unchecked one) once {@code limit} bytes have been delivered. */
    static final class FailingStream extends FilterInputStream {

        private final long limit;
        private final Supplier<RuntimeException> unchecked;
        private long delivered;

        FailingStream(InputStream in, long limit, Supplier<RuntimeException> unchecked) {
            super(in);
            this.limit = limit;
            this.unchecked = unchecked;
        }

        @Override
        public int read() throws IOException {
            failIfExhausted();
            int b = super.read();
            if (b >= 0) {
                delivered++;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            failIfExhausted();
            int n = super.read(buffer, offset, (int) Math.min(length, limit - delivered));
            if (n > 0) {
                delivered += n;
            }
            return n;
        }

        private void failIfExhausted() throws IOException {
            if (delivered >= limit) {
                if (unchecked != null) {
                    throw unchecked.get();
                }
                throw new IOException("connection reset (simulated)");
            }
        }
    }
}
