package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import java.util.Objects;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import tools.jackson.databind.JsonNode;

/** Runs the extractor; filters out (returns null for) entities with neither events nor coordinates. */
public class EntityProcessor implements ItemProcessor<JsonNode, ImportRecord> {

    private final WikidataExtractor extractor;

    public EntityProcessor(WikidataExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    @Override
    public ImportRecord process(JsonNode entity) {
        ExtractedItem item = extractor.extract(entity).orElse(null);
        Event.Coordinates coords = WikidataExtractor.placeCoordinates(entity);
        if (item == null && coords == null) {
            return null;
        }
        return new ImportRecord(item, coords == null ? null : entity.path("id").asString(), coords);
    }
}
