package io.github.luisr96.whathappenedhere.api.stats;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Body of {@code GET /stats/summary}: the whole dataset in numbers, for the About dialog. */
public record StatsSummary(
        @Schema(description = "All events on the map") long events,
        @Schema(description = "Places in the database (items whose coordinates events use)")
        long places,
        @Schema(description = "Events per kind; every kind is listed, 0 when it has none")
        Map<Event.Kind, Long> eventsByKind,
        @Schema(description = "Earliest year of any event (negative for BC); null without events",
                nullable = true) Long earliestYear,
        @Schema(description = "Latest year of any event; null without events", nullable = true)
        Long latestYear,
        @Schema(description = "Date of the Wikidata dump the data comes from (YYYY-MM-DD); null "
                + "when not configured", nullable = true, example = "2026-10-12") String dumpDate) {}
