package io.github.luisr96.whathappenedhere.sampler;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.Event.Kind;
import io.github.luisr96.whathappenedhere.extractor.Event.PlaceRef;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate;
import io.github.luisr96.whathappenedhere.extractor.ItemFacts;
import io.github.luisr96.whathappenedhere.extractor.Statements;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Everything the head-of-dump report measures (CLAUDE.md, Testing strategy): what came out, and
 * what got lost or looks like junk, so rules are tuned from data instead of guessed.
 */
public final class SampleReport {

    // Wikidata precisions 0-5 (10,000 years and coarser) are dropped by HistoricDate.parse.
    private static final int COARSEST_KEPT_PRECISION = 6;
    private static final Map<Kind, List<String>> DATE_PROPERTIES = Map.of(
            Kind.HAPPENED, List.of("P585", "P580"),
            Kind.BORN, List.of("P569"),
            Kind.DIED, List.of("P570"),
            Kind.FOUNDED, List.of("P571"),
            Kind.ENDED, List.of("P576"),
            Kind.OPENED, List.of("P1619"));
    // CLAUDE.md "Later candidates": properties not used yet, measured before deciding.
    private static final List<String> CANDIDATE_PROPERTIES = List.of("P575", "P619", "P620");
    private static final Pattern LANGUAGE_WIKI = Pattern.compile("[a-z]{2,3}(_[a-z0-9]+)*wiki");
    // Simple English Wikipedia: a real Wikipedia whose code isn't a language code.
    private static final Set<String> KNOWN_ODD_WIKIPEDIAS = Set.of("simplewiki");
    private static final Comparator<HistoricDate> CHRONOLOGICAL =
            Comparator.comparingLong(HistoricDate::earliestYear)
                    .thenComparingInt(HistoricDate::month)
                    .thenComparingInt(HistoricDate::day);

    private long entitiesRead;
    private long itemsWithEvents;
    private final Map<Kind, Long> eventsPerKind = new EnumMap<>(Kind.class);
    private final Set<String> places = new HashSet<>();
    private final List<String> placeRefs = new ArrayList<>();
    private final Map<Kind, Map<String, Long>> instanceOfPerKind = new EnumMap<>(Kind.class);
    private final Map<HistoricDate.Precision, Long> precisionCounts =
            new EnumMap<>(HistoricDate.Precision.class);
    private final Map<Long, Long> eventsPerCentury = new TreeMap<>();
    private final Map<Kind, Long> coarsePrecisionDrops = new EnumMap<>(Kind.class);
    private final Map<String, Long> candidatesWithoutEvents = new LinkedHashMap<>();
    private final Set<String> unknownWikiSitelinks = new TreeSet<>();
    private long rangesEndingBeforeStart;
    private long endedBeforeFounded;

    /** An empty report; candidate properties start at 0 so the report always names them. */
    public SampleReport() {
        CANDIDATE_PROPERTIES.forEach(property -> candidatesWithoutEvents.put(property, 0L));
    }

    /**
     * An entity's usable Earth coordinates (its best P625), or null. Any such entity is a place,
     * whatever its sitelinks: events elsewhere may name it.
     */
    public static Coordinates placeCoordinates(JsonNode entity) {
        return Statements.bestPlace(entity.path("claims"), "P625", Coordinates::parse);
    }

    /** Adds one entity read from the dump, with the extractor's result for it (empty if none). */
    public void add(JsonNode entity, Optional<ExtractedItem> extracted) {
        entitiesRead++;
        JsonNode claims = entity.path("claims");
        if (placeCoordinates(entity) != null) {
            places.add(entity.path("id").asString(""));
        }
        countUnknownWikis(entity.path("sitelinks"));
        List<Event> events = extracted.map(ExtractedItem::events).orElse(List.of());
        // Losses only matter for items the extractor would otherwise keep.
        boolean passesGates = ItemFacts.importance(entity.path("sitelinks")) > 0
                && !ItemFacts.isFictional(ItemFacts.instanceOf(claims));
        if (passesGates) {
            Set<Kind> emitted = EnumSet.noneOf(Kind.class);
            events.forEach(event -> emitted.add(event.kind()));
            countCoarseDates(claims, emitted);
            if (events.isEmpty()) {
                countCandidates(claims);
            }
        }
        if (events.isEmpty()) {
            return;
        }
        itemsWithEvents++;
        ExtractedItem item = extracted.orElseThrow();
        Map<Kind, Event> byKind = new EnumMap<>(Kind.class);
        for (Event event : events) {
            byKind.put(event.kind(), event);
            eventsPerKind.merge(event.kind(), 1L, Long::sum);
            precisionCounts.merge(event.when().precision(), 1L, Long::sum);
            eventsPerCentury.merge(century(event.when().earliestYear()), 1L, Long::sum);
            if (event.where() instanceof PlaceRef ref) {
                placeRefs.add(ref.qid());
            }
            if (event.endDate() != null && CHRONOLOGICAL.compare(event.endDate(), event.when()) < 0) {
                rangesEndingBeforeStart++;
            }
        }
        for (Kind kind : byKind.keySet()) {
            Map<String, Long> types = instanceOfPerKind.computeIfAbsent(kind, k -> new HashMap<>());
            item.instanceOf().forEach(type -> types.merge(type, 1L, Long::sum));
        }
        Event founded = byKind.get(Kind.FOUNDED);
        Event ended = byKind.get(Kind.ENDED);
        if (founded != null && ended != null && CHRONOLOGICAL.compare(ended.when(), founded.when()) < 0) {
            endedBeforeFounded++;
        }
    }

    public long entitiesRead() {
        return entitiesRead;
    }

    public long itemsWithEvents() {
        return itemsWithEvents;
    }

    public Map<Kind, Long> eventsPerKind() {
        return Map.copyOf(eventsPerKind);
    }

    /**
     * Share (0..1) of place-item events whose place has coordinates in this slice. Low values are
     * expected on a head-of-dump slice: the place may simply lie outside it.
     *
     * @return the share, or NaN when no event names a place item
     */
    public double resolvedPlaceRefShare() {
        if (placeRefs.isEmpty()) {
            return Double.NaN;
        }
        return (double) placeRefs.stream().filter(places::contains).count() / placeRefs.size();
    }

    /** Per kind, the {@code n} most common P31 types of items with that kind of event: count desc, then QID. */
    public Map<Kind, List<Map.Entry<String, Long>>> topInstanceOf(int n) {
        Map<Kind, List<Map.Entry<String, Long>>> top = new EnumMap<>(Kind.class);
        instanceOfPerKind.forEach((kind, types) -> top.put(kind, types.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(n)
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList()));
        return top;
    }

    public Map<HistoricDate.Precision, Long> precisionCounts() {
        return Map.copyOf(precisionCounts);
    }

    /** Events per century of their start; 1..100 is century 1, -100..-1 is century -1 (no 0). */
    public Map<Long, Long> eventsPerCentury() {
        return Map.copyOf(eventsPerCentury);
    }

    /**
     * Per kind, items passing the gates that lost that kind of event because the date the
     * extractor would choose is coarser than a millennium (HAPPENED: P585 or P580).
     */
    public Map<Kind, Long> coarsePrecisionDrops() {
        return Map.copyOf(coarsePrecisionDrops);
    }

    /** Items passing the gates that have a candidate property (P575, P619, P620) but no events. */
    public Map<String, Long> candidatePropertiesWithoutEvents() {
        return Map.copyOf(candidatesWithoutEvents);
    }

    /** Sitelink keys counted as Wikipedias that don't look like a language code: new projects? */
    public Set<String> unknownWikiSitelinks() {
        return Set.copyOf(unknownWikiSitelinks);
    }

    public long rangesEndingBeforeStart() {
        return rangesEndingBeforeStart;
    }

    public long endedBeforeFounded() {
        return endedBeforeFounded;
    }

    /** The report as Markdown, one section per measure. */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder("# Head-of-dump report\n\n");
        md.append("- Entities read: ").append(entitiesRead).append('\n');
        md.append("- Items with events: ").append(itemsWithEvents).append('\n');
        md.append("- Places (entities with Earth coordinates): ").append(places.size()).append("\n\n");
        section(md, "Events per kind", eventsPerKind);
        md.append("## Place references resolved in this slice\n\n")
                .append(String.format("%.1f%% of %d place-item events%n%n",
                        resolvedPlaceRefShare() * 100, placeRefs.size()));
        md.append("## Most common P31 types per kind (junk detector)\n\n");
        topInstanceOf(20).forEach((kind, types) -> {
            md.append("### ").append(kind).append("\n\n");
            types.forEach(t -> md.append("- ").append(t.getKey()).append(": ").append(t.getValue()).append('\n'));
            md.append('\n');
        });
        section(md, "Date precision", precisionCounts);
        section(md, "Events per century (BC negative)", eventsPerCentury);
        section(md, "Dates dropped for precision coarser than a millennium", coarsePrecisionDrops);
        section(md, "Candidate properties on items without events", candidatesWithoutEvents);
        md.append("## Unknown *wiki sitelink keys\n\n");
        unknownWikiSitelinks.forEach(site -> md.append("- ").append(site).append('\n'));
        md.append(unknownWikiSitelinks.isEmpty() ? "None\n\n" : "\n");
        md.append("## Suspicious data\n\n")
                .append("- Happened ranges ending before they start: ").append(rangesEndingBeforeStart).append('\n')
                .append("- Items ended before founded: ").append(endedBeforeFounded).append('\n');
        return md.toString();
    }

    private void countCoarseDates(JsonNode claims, Set<Kind> emitted) {
        DATE_PROPERTIES.forEach((kind, properties) -> {
            if (!emitted.contains(kind)
                    && properties.stream().anyMatch(property -> hasCoarseChosenValue(claims, property))) {
                coarsePrecisionDrops.merge(kind, 1L, Long::sum);
            }
        });
    }

    // Looks only at the rank the extractor would choose: preferred values if any, else normal.
    private static boolean hasCoarseChosenValue(JsonNode claims, String property) {
        List<JsonNode> preferred = new ArrayList<>();
        List<JsonNode> normal = new ArrayList<>();
        for (JsonNode statement : claims.path(property)) {
            if (!"value".equals(statement.path("mainsnak").path("snaktype").asString(""))) {
                continue;
            }
            switch (statement.path("rank").asString("")) {
                case "preferred" -> preferred.add(statement);
                case "normal" -> normal.add(statement);
                default -> { }
            }
        }
        return (preferred.isEmpty() ? normal : preferred).stream()
                .mapToInt(statement -> statement.path("mainsnak").path("datavalue").path("value")
                        .path("precision").asInt(COARSEST_KEPT_PRECISION))
                .anyMatch(precision -> precision < COARSEST_KEPT_PRECISION);
    }

    private void countCandidates(JsonNode claims) {
        for (String property : CANDIDATE_PROPERTIES) {
            for (JsonNode statement : claims.path(property)) {
                if (isUsableRank(statement)) {
                    candidatesWithoutEvents.merge(property, 1L, Long::sum);
                    break;
                }
            }
        }
    }

    private void countUnknownWikis(JsonNode sitelinks) {
        for (String site : sitelinks.propertyNames()) {
            if (ItemFacts.isWikipediaSitelink(site) && !LANGUAGE_WIKI.matcher(site).matches()
                    && !KNOWN_ODD_WIKIPEDIAS.contains(site)) {
                unknownWikiSitelinks.add(site);
            }
        }
    }

    private static boolean isUsableRank(JsonNode statement) {
        String rank = statement.path("rank").asString("");
        return rank.equals("preferred") || rank.equals("normal");
    }

    private static long century(long year) {
        long magnitude = (Math.abs(year) + 99) / 100;
        return year > 0 ? magnitude : -magnitude;
    }

    private static void section(StringBuilder md, String title, Map<?, Long> counts) {
        md.append("## ").append(title).append("\n\n");
        counts.forEach((key, count) -> md.append("- ").append(key).append(": ").append(count).append('\n'));
        md.append(counts.isEmpty() ? "None\n\n" : "\n");
    }
}
