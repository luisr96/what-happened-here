package io.github.luisr96.whathappenedhere.extractor;

import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.Event.Kind;
import io.github.luisr96.whathappenedhere.extractor.Event.Place;
import io.github.luisr96.whathappenedhere.extractor.Event.PlaceRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Turns one raw Wikidata entity into clean events, following the rules in CLAUDE.md.
 *
 * <p>Pure and in-memory: the same entity and clock always give the same result, and bad values
 * cost one event, never the run.
 */
public final class WikidataExtractor {

    private final Clock clock;

    /** @param clock decides what counts as a future date; dates after its current UTC day are dropped */
    public WikidataExtractor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Extracts an item's events.
     *
     * @param entity a bare entity, as one line of the dump or the inside of Special:EntityData
     * @return the item, or empty when it isn't a usable item (not an item, no Wikipedia
     *     sitelink, fictional, coordinates only off Earth) or yields no events
     */
    public Optional<ExtractedItem> extract(JsonNode entity) {
        if (entity == null || !entity.isObject() || !"item".equals(entity.path("type").asString(""))) {
            return Optional.empty();
        }
        JsonNode id = entity.path("id");
        if (!id.isString() || !WikidataValues.isItemId(id.stringValue())) {
            return Optional.empty();
        }
        JsonNode claims = entity.path("claims");
        int importance = ItemFacts.importance(entity.path("sitelinks"));
        List<String> instanceOf = ItemFacts.instanceOf(claims);
        if (importance == 0 || ItemFacts.isFictional(instanceOf)
                || ItemFacts.isGeologic(instanceOf) || ItemFacts.isSeason(instanceOf)
                || Statements.onlyOffEarthCoordinates(claims, "P625")) {
            return Optional.empty();
        }
        List<Event> events = events(claims, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExtractedItem(id.stringValue(), ItemFacts.label(entity.path("labels")),
                ItemFacts.wikiTitle(entity.path("sitelinks")), importance, instanceOf, events));
    }

    private static List<Event> events(JsonNode claims, LocalDate today) {
        Dates dates = new Dates(claims, today);
        List<Event> events = new ArrayList<>();
        add(events, happened(claims, dates));
        add(events, event(Kind.BORN, dates.best("P569"), place(claims, "P19")));
        add(events, event(Kind.DIED, dates.best("P570"), place(claims, "P20")));
        Place organisationPlace = place(claims, "P625", "P159", "P131");
        add(events, event(Kind.FOUNDED, dates.best("P571"), organisationPlace));
        add(events, event(Kind.ENDED, dates.best("P576"), organisationPlace));
        add(events, event(Kind.OPENED, dates.best("P1619"), place(claims, "P625", "P131")));
        return events;
    }

    private static Event happened(JsonNode claims, Dates dates) {
        Place where = place(claims, "P625", "P276", "P131");
        HistoricDate start = dates.best("P580");
        HistoricDate end = dates.best("P582");
        if (start != null && end != null) {
            return where == null ? null : new Event(Kind.HAPPENED, start, end, where);
        }
        HistoricDate pointInTime = dates.best("P585");
        return event(Kind.HAPPENED, pointInTime != null ? pointInTime : start, where);
    }

    private static Event event(Kind kind, HistoricDate when, Place where) {
        return when == null || where == null ? null : new Event(kind, when, null, where);
    }

    private static void add(List<Event> events, Event event) {
        if (event != null) {
            events.add(event);
        }
    }

    // P625 is the item's own coordinates; every other place property names a place item.
    private static Place place(JsonNode claims, String... propertyIds) {
        for (String propertyId : propertyIds) {
            Place place = propertyId.equals("P625")
                    ? Statements.bestPlace(claims, propertyId, Coordinates::parse)
                    : Statements.bestPlace(claims, propertyId, PlaceRef::parse);
            if (place != null) {
                return place;
            }
        }
        return null;
    }

    /** Best dates of an entity, skipping future ones so the next usable value is used instead. */
    private record Dates(JsonNode claims, LocalDate today) {

        HistoricDate best(String propertyId) {
            return Statements.bestDate(claims, propertyId, date -> !isFuture(date));
        }

        // A date is future when even its earliest possible day is after today; fields below
        // the precision are 0, meaning the start of the period (century +2100 starts in 2001).
        private boolean isFuture(HistoricDate date) {
            long year = date.earliestYear();
            if (year != today.getYear()) {
                return year > today.getYear();
            }
            int month = Math.max(date.month(), 1);
            if (month != today.getMonthValue()) {
                return month > today.getMonthValue();
            }
            return Math.max(date.day(), 1) > today.getDayOfMonth();
        }
    }
}
