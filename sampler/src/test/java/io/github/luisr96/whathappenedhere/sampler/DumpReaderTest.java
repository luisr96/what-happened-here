package io.github.luisr96.whathappenedhere.sampler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class DumpReaderTest {

    private static final List<String> FOUR = List.of(
            Fixtures.line("Q254"), Fixtures.line("Q220"), Fixtures.line("Q243"), Fixtures.line("Q191055"));

    private static List<String> ids(DumpReader reader) {
        return reader.entities().map(e -> e.get("id").asString()).toList();
    }

    private static DumpReader reader(byte[] gz, long limit) {
        return new DumpReader(new ByteArrayInputStream(gz), limit);
    }

    @Test
    void readsEveryEntityIncludingLastLineWithoutComma() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\n"), 100);
        assertEquals(List.of("Q254", "Q220", "Q243", "Q191055"), ids(reader));
        assertEquals(0, reader.malformedLines());
    }

    @Test
    void limitStopsEarly() {
        assertEquals(List.of("Q254", "Q220"), ids(reader(Fixtures.gzippedDump(FOUR, "\n"), 2)));
    }

    @Test
    void entitiesAreParsedWithTheirClaims() {
        JsonNode first = reader(Fixtures.gzippedDump(FOUR, "\n"), 1).entities().findFirst().orElseThrow();
        assertEquals("Q34713", first.at("/claims/P19/0/mainsnak/datavalue/value/id").asString());
    }

    @Test
    void malformedLineIsSkippedAndCounted() {
        var lines = List.of(Fixtures.line("Q254"), "{\"id\":\"Q1\",\"broken", Fixtures.line("Q220"));
        var reader = reader(Fixtures.gzippedDump(lines, "\n"), 100);
        assertEquals(List.of("Q254", "Q220"), ids(reader));
        assertEquals(1, reader.malformedLines());
    }

    @Test
    void emptyArrayYieldsNothing() {
        var reader = reader(Fixtures.gzip("[\n]\n"), 100);
        assertEquals(List.of(), ids(reader));
        assertEquals(0, reader.malformedLines());
    }

    @Test
    void crlfLineEndingsAreTolerated() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\r\n"), 100);
        assertEquals(List.of("Q254", "Q220", "Q243", "Q191055"), ids(reader));
        assertEquals(0, reader.malformedLines());
    }

    @Test
    void entityLinesReturnsEachLineAsTextWithoutCommaOrBrackets() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\n"), 100);
        assertEquals(FOUR, reader.entityLines(0).toList());
    }

    @Test
    void entityLinesLeavesOutBlankLines() {
        var reader = reader(Fixtures.gzip("[\n" + Fixtures.line("Q254") + ",\n\n   \n" + Fixtures.line("Q220") + "\n]\n"), 100);
        assertEquals(List.of(Fixtures.line("Q254"), Fixtures.line("Q220")), reader.entityLines(0).toList());
    }

    @Test
    void entityLinesStripsCrlf() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\r\n"), 100);
        assertEquals(FOUR, reader.entityLines(0).toList());
    }

    @Test
    void entityLinesSkipPassesOverTheFirstLines() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\n"), 100);
        assertEquals(FOUR.subList(2, 4), reader.entityLines(2).toList());
    }

    @Test
    void entityLinesSkipCountsMalformedLinesAndReturnsThemAsText() {
        var lines = List.of(Fixtures.line("Q254"), "{\"id\":\"Q1\",\"broken", "[1]", "{} {}",
                Fixtures.line("Q220"), Fixtures.line("Q243"));
        var reader = reader(Fixtures.gzippedDump(lines, "\n"), 100);

        assertEquals(List.of("[1]", "{} {}", Fixtures.line("Q220"), Fixtures.line("Q243")),
                reader.entityLines(2).toList());
    }

    @Test
    void entityLinesLimitCountsLinesAfterTheSkip() {
        var lines = List.of(Fixtures.line("Q254"), "{broken", Fixtures.line("Q220"), Fixtures.line("Q243"));
        var reader = reader(Fixtures.gzippedDump(lines, "\n"), 2);

        assertEquals(List.of("{broken", Fixtures.line("Q220")), reader.entityLines(1).toList());
    }

    @Test
    void entityLinesSkipPastTheEndYieldsNothing() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\n"), 100);
        assertEquals(List.of(), reader.entityLines(10).toList());
    }

    @Test
    void entityLinesHandsOverDecodedLinesBeforeAMidStreamFailure() throws Exception {
        // Two complete lines arrive, then the connection dies: both must be returned before the error.
        // Sync-flushed but never closed: no gzip trailer, so the reader has to go back for more.
        var bytes = new ByteArrayOutputStream();
        var gz = new GZIPOutputStream(bytes, true);
        gz.write(("[\n" + Fixtures.line("Q254") + ",\n" + Fixtures.line("Q220") + ",\n")
                .getBytes(StandardCharsets.UTF_8));
        gz.flush();
        byte[] flushed = bytes.toByteArray();
        var failing = new SequenceInputStream(new ByteArrayInputStream(flushed), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset (simulated)");
            }
        });
        var seen = new ArrayList<String>();

        assertThrows(UncheckedIOException.class, () -> {
            try (var lines = new DumpReader(failing, 100).entityLines(0)) {
                lines.forEach(seen::add);
            }
        });
        assertEquals(List.of(Fixtures.line("Q254"), Fixtures.line("Q220")), seen);
    }
}
