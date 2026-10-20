package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import io.github.luisr96.whathappenedhere.sampler.DumpReader;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import tools.jackson.databind.JsonNode;

/**
 * Parses one dump line and runs the extractor. Filters out (returns null for) malformed lines and
 * entities with neither events nor coordinates. Stateless, so the extract step runs it on several
 * threads at once.
 */
public class EntityProcessor implements ItemProcessor<String, ImportRecord> {

    private static final Logger log = LoggerFactory.getLogger(EntityProcessor.class);
    private static final int LOGGED_CHARS = 80;

    private final WikidataExtractor extractor;

    public EntityProcessor(WikidataExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    @Override
    public ImportRecord process(String line) {
        JsonNode entity = parse(line);
        if (entity == null) {
            return null;
        }
        ExtractedItem item = extractor.extract(entity).orElse(null);
        Event.Coordinates coords = WikidataExtractor.placeCoordinates(entity);
        if (item == null && coords == null) {
            return null;
        }
        return new ImportRecord(item, coords == null ? null : entity.path("id").asString(), coords);
    }

    /** The entity object, or null (logged) for a line that isn't one: one bad line costs one entity. */
    private static JsonNode parse(String line) {
        JsonNode node = DumpReader.parseEntity(line);
        if (node != null) {
            return node;
        }
        log.warn("Skipping a dump line that is not a JSON object: {}",
                line.length() > LOGGED_CHARS ? line.substring(0, LOGGED_CHARS) + "…" : line);
        return null;
    }
}
