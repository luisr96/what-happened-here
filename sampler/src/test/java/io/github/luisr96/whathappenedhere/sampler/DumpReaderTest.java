package io.github.luisr96.whathappenedhere.sampler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;
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
    void skipPassesOverTheFirstEntities() {
        var reader = reader(Fixtures.gzippedDump(FOUR, "\n"), 100);
        assertEquals(List.of("Q243", "Q191055"),
                reader.entities(2).map(e -> e.get("id").asString()).toList());
    }

    @Test
    void skipCountsEntitiesExactlyAsReadingDoes() {
        // Neither the malformed line, a non-object, nor an object with trailing tokens is an entity.
        var lines = List.of(Fixtures.line("Q254"), "{\"id\":\"Q1\",\"broken", "[1]", "{} {}",
                Fixtures.line("Q220"), Fixtures.line("Q243"));
        var all = ids(reader(Fixtures.gzippedDump(lines, "\n"), 100));
        var skipping = reader(Fixtures.gzippedDump(lines, "\n"), 100);

        var afterOne = skipping.entities(1).map(e -> e.get("id").asString()).toList();

        assertEquals(all.subList(1, all.size()), afterOne);
        assertEquals(List.of("Q220", "Q243"), afterOne);
        assertEquals(3, skipping.malformedLines());
    }
}
