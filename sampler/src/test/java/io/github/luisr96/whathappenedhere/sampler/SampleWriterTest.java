package io.github.luisr96.whathappenedhere.sampler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SampleWriterTest {

    private static final HistoricDate ROME_FOUNDED = new HistoricDate(
            -753, 4, 21, HistoricDate.Precision.DAY, HistoricDate.Calendar.JULIAN);

    private static JsonNode writeOne(ExtractedItem item) {
        var out = new StringWriter();
        SampleWriter.writeItem(out, item);
        String text = out.toString();
        assertTrue(text.endsWith("\n"), "each item is one newline-terminated line");
        assertEquals(1, text.lines().count());
        return Fixtures.json(text);
    }

    @Test
    void itemRoundTripsWithBcYearAndNullEndDate() {
        var event = new Event(Event.Kind.FOUNDED, ROME_FOUNDED, null, new Event.Coordinates(41.89, 12.48, 0.0001));
        var json = writeOne(new ExtractedItem("Q220", "Rome", "Rome", 274, List.of("Q515"), List.of(event)));

        assertEquals("Q220", json.get("itemId").asString());
        assertEquals("Rome", json.get("label").asString());
        assertEquals(274, json.get("importance").asInt());
        assertEquals("Q515", json.get("instanceOf").get(0).asString());
        var written = json.get("events").get(0);
        assertEquals("FOUNDED", written.get("kind").asString());
        assertEquals(-753, written.at("/when/year").asLong());
        assertEquals(4, written.at("/when/month").asInt());
        assertEquals(21, written.at("/when/day").asInt());
        assertEquals("DAY", written.at("/when/precision").asString());
        assertEquals("JULIAN", written.at("/when/calendar").asString());
        assertTrue(written.get("endDate").isNull());
        assertEquals(41.89, written.at("/where/latitude").asDouble());
        assertEquals(12.48, written.at("/where/longitude").asDouble());
        assertEquals(0.0001, written.at("/where/precision").asDouble());
        assertFalse(written.get("where").has("qid"));
    }

    @Test
    void placeRefIsWrittenAsQidAndEndDateAsObject() {
        var start = new HistoricDate(1914, 0, 0, HistoricDate.Precision.YEAR, HistoricDate.Calendar.GREGORIAN);
        var end = new HistoricDate(1918, 11, 11, HistoricDate.Precision.DAY, HistoricDate.Calendar.GREGORIAN);
        var event = new Event(Event.Kind.HAPPENED, start, end, new Event.PlaceRef("Q46"));
        var written = writeOne(new ExtractedItem("Q361", "World War I", "World War I", 200, List.of(), List.of(event)))
                .get("events").get(0);

        assertEquals("Q46", written.at("/where/qid").asString());
        assertFalse(written.get("where").has("latitude"));
        assertEquals(1918, written.at("/endDate/year").asLong());
        assertEquals(11, written.at("/endDate/day").asInt());
        assertEquals("YEAR", written.at("/when/precision").asString());
        assertEquals(0, written.at("/when/month").asInt());
    }

    @Test
    void unknownCoordinatePrecisionIsNull() {
        var event = new Event(Event.Kind.HAPPENED, ROME_FOUNDED, null, new Event.Coordinates(1.5, -2.5, null));
        var written = writeOne(new ExtractedItem("Q1", "x", null, 1, List.of(), List.of(event))).get("events").get(0);
        assertTrue(written.at("/where/precision").isNull());
        assertEquals(-2.5, written.at("/where/longitude").asDouble());
    }

    @Test
    void placeLinesHaveQidCoordinatesAndNullablePrecision() {
        var out = new StringWriter();
        SampleWriter.writePlace(out, "Q34713", new Event.Coordinates(47.8, 13.04, 1.0));
        SampleWriter.writePlace(out, "Q1741", new Event.Coordinates(48.2, 16.37, null));
        var lines = out.toString().lines().toList();
        assertEquals(2, lines.size());

        var first = Fixtures.json(lines.get(0));
        assertEquals("Q34713", first.get("qid").asString());
        assertEquals(47.8, first.get("latitude").asDouble());
        assertEquals(13.04, first.get("longitude").asDouble());
        assertEquals(1.0, first.get("precision").asDouble());
        var second = Fixtures.json(lines.get(1));
        assertEquals("Q1741", second.get("qid").asString());
        assertTrue(second.get("precision").isNull());
    }

    @Test
    void instanceOfIsAnEmptyArrayWhenItemHasNoP31() {
        var event = new Event(Event.Kind.FOUNDED, ROME_FOUNDED, null, new Event.PlaceRef("Q1"));
        var json = writeOne(new ExtractedItem("Q2", "x", null, 1, List.of(), List.of(event)));
        assertTrue(json.get("instanceOf").isArray());
        assertEquals(0, json.get("instanceOf").size());
    }

    @Test
    void wikiTitleIsWritten() {
        var event = new Event(Event.Kind.FOUNDED, ROME_FOUNDED, null, new Event.Coordinates(41.89, 12.48, 0.0001));
        var json = writeOne(new ExtractedItem("Q220", "Rome", "Café \"Le Dôme\", Paris", 274,
                List.of(), List.of(event)));
        assertEquals("Café \"Le Dôme\", Paris", json.get("wikiTitle").asString());
    }

    @Test
    void missingWikiTitleIsWrittenAsJsonNull() {
        var event = new Event(Event.Kind.FOUNDED, ROME_FOUNDED, null, new Event.Coordinates(41.89, 12.48, 0.0001));
        var json = writeOne(new ExtractedItem("Q50829", "Ivanovka", null, 3, List.of(), List.of(event)));
        assertTrue(json.has("wikiTitle"), "wikiTitle key is present even when null");
        assertTrue(json.get("wikiTitle").isNull());
    }
}
