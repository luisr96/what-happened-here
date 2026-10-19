package io.github.luisr96.whathappenedhere.api.events;

import java.util.List;

/**
 * Body of {@code GET /events}.
 *
 * @param truncated true when more events match than were returned (raise minImportance or zoom in)
 */
public record EventsResponse(List<EventView> events, boolean truncated) {

    /**
     * One event as the map shows it.
     *
     * @param end null when the event is a single point in time
     * @param earliestYear first year the event may fall in, given its precision
     * @param coordPrecisionDeg null when Wikidata states no precision
     * @param approximate shown as approximate on the map: precision unknown or at least 0.1°
     * @param placeQid the place whose coordinates were used, or null for the item's own P625
     *     coordinates
     */
    public record EventView(String id, String itemId, String kind, String label, int importance,
            DateView when, DateView end, long earliestYear, long latestYear,
            double latitude, double longitude, Double coordPrecisionDeg, boolean approximate,
            String placeQid) {}

    /** A date as stored: fields below {@code precision} are 0; year is negative for BC. */
    public record DateView(long year, int month, int day, String precision, String calendar) {}
}
