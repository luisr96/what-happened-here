package io.github.luisr96.whathappenedhere.api.events;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Body of {@code GET /events} and {@code GET /places/nearby}. */
public record EventsResponse(
        @Schema(description = "Matching events, most important first")
        List<EventView> events,
        @Schema(description = "True when more events matched than were returned "
                + "(raise minImportance or zoom in)")
        boolean truncated) {

    /**
     * One event as the map shows it. Enum-like fields are strings with their values listed, so
     * the API's contract doesn't hang on the extractor's types; OpenApiDocsTest keeps the lists
     * equal to the extractor's enums.
     */
    public record EventView(
            @Schema(description = "<QID>:<KIND>, e.g. Q254:BORN") String id,
            @Schema(description = "Wikidata item, e.g. Q254") String itemId,
            @Schema(description = "Kind of moment", allowableValues = {"HAPPENED", "BORN", "DIED",
                    "FOUNDED", "ENDED", "OPENED"}) String kind,
            @Schema(description = "English label, or null", nullable = true) String label,
            @Schema(description = "Number of Wikipedias linking to the item") int importance,
            @Schema(description = "When it happened, at the precision Wikidata states")
            DateView when,
            @Schema(description = "End of a range, or null for a point in time", nullable = true) DateView end,
            @Schema(description = "First year the event may fall in, given its precision "
                    + "(a century spans 100 years); negative for BC") long earliestYear,
            @Schema(description = "Last year the event may fall in (of the end date if any)")
            long latestYear,
            @Schema(description = "WGS84 degrees: the item's own or its place's coordinates")
            double latitude,
            @Schema(description = "WGS84 degrees: the item's own or its place's coordinates")
            double longitude,
            @Schema(description = "Coordinate precision in degrees, or null when unknown",
                    nullable = true)
            Double coordPrecisionDeg,
            @Schema(description = "Show as approximate: precision unknown or at least 0.1 degrees")
            boolean approximate,
            @Schema(description = "Place whose coordinates were used, or null for the item's "
                    + "own P625 coordinates", nullable = true) String placeQid) {}

    /** A date as stored: fields below {@code precision} are 0; year is negative for BC. */
    public record DateView(long year, int month, int day,
            @Schema(description = "Finest unit the date states", allowableValues = {"MILLENNIUM",
                    "CENTURY", "DECADE", "YEAR", "MONTH", "DAY"}) String precision,
            @Schema(description = "Calendar as written (not converted)",
                    allowableValues = {"GREGORIAN", "JULIAN"})
            String calendar) {}
}
