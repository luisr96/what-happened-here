package io.github.luisr96.whathappenedhere.sampler;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.Event.PlaceRef;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes sampler output as JSON Lines: one object per line, ready for Postgres COPY via a
 * staging table. Enums are written by name; date fields below the precision are 0.
 */
public final class SampleWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SampleWriter() {}

    /** One line per item, with its events nested. */
    public static void writeItem(Writer out, ExtractedItem item) {
        ObjectNode line = MAPPER.createObjectNode();
        line.put("itemId", item.itemId());
        line.put("label", item.label());
        line.put("importance", item.importance());
        var types = line.putArray("instanceOf");
        item.instanceOf().forEach(types::add);
        var events = line.putArray("events");
        for (Event event : item.events()) {
            ObjectNode node = events.addObject();
            node.put("kind", event.kind().name());
            node.set("when", date(event.when()));
            node.set("endDate", event.endDate() == null ? MAPPER.nullNode() : date(event.endDate()));
            node.set("where", switch (event.where()) {
                case PlaceRef ref -> MAPPER.createObjectNode().put("qid", ref.qid());
                case Coordinates coords -> coordinates(coords);
            });
        }
        write(out, line);
    }

    /** One line per place: an item with usable Earth coordinates, whatever its sitelinks. */
    public static void writePlace(Writer out, String qid, Coordinates coords) {
        write(out, coordinates(coords).put("qid", qid));
    }

    private static ObjectNode date(HistoricDate date) {
        return MAPPER.createObjectNode()
                .put("year", date.year())
                .put("month", date.month())
                .put("day", date.day())
                .put("precision", date.precision().name())
                .put("calendar", date.calendar().name());
    }

    private static ObjectNode coordinates(Coordinates coords) {
        return MAPPER.createObjectNode()
                .put("latitude", coords.latitude())
                .put("longitude", coords.longitude())
                .put("precision", coords.precisionDegrees());
    }

    private static void write(Writer out, ObjectNode line) {
        try {
            out.write(MAPPER.writeValueAsString(line));
            out.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
