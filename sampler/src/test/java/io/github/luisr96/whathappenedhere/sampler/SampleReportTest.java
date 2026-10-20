package io.github.luisr96.whathappenedhere.sampler;

import static io.github.luisr96.whathappenedhere.sampler.Fixtures.claim;
import static io.github.luisr96.whathappenedhere.sampler.Fixtures.coords;
import static io.github.luisr96.whathappenedhere.sampler.Fixtures.enwiki;
import static io.github.luisr96.whathappenedhere.sampler.Fixtures.item;
import static io.github.luisr96.whathappenedhere.sampler.Fixtures.sitelink;
import static io.github.luisr96.whathappenedhere.sampler.Fixtures.synthetic;
import static io.github.luisr96.whathappenedhere.sampler.Fixtures.time;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luisr96.whathappenedhere.extractor.Event.Kind;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate.Precision;
import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SampleReportTest {

    private static final List<String> REAL = List.of("Q254", "Q220", "Q4653", "Q191055", "Q243", "Q12506");

    private final WikidataExtractor extractor = new WikidataExtractor(Fixtures.CLOCK);

    private SampleReport reportOf(List<JsonNode> entities) {
        var report = new SampleReport();
        entities.forEach(e -> report.add(e, extractor.extract(e)));
        return report;
    }

    private SampleReport realReport(JsonNode... extra) {
        var entities = new java.util.ArrayList<>(REAL.stream().map(Fixtures::entity).toList());
        entities.addAll(List.of(extra));
        return reportOf(entities);
    }

    /** A person born on a fixed day in place Q1, with the given P31 types. */
    private static JsonNode person(String id, String... types) {
        var p31 = java.util.Arrays.stream(types).map(t -> item("P31", t)).toArray(String[]::new);
        return synthetic(id, enwiki(id), String.join(",",
                claim("P31", p31),
                claim("P569", time("P569", "+1900-01-02T00:00:00Z", 11, "normal")),
                claim("P19", item("P19", "Q1"))));
    }

    private static long count(Map<?, Long> map, Object key) {
        return map.getOrDefault(key, 0L);
    }

    @Test
    void countsEntitiesAndItemsWithEvents() {
        var report = realReport();
        assertEquals(6, report.entitiesRead());
        assertEquals(5, report.itemsWithEvents(), "Sherlock Holmes yields nothing");
    }

    @Test
    void countsEventsPerKind() {
        var perKind = realReport().eventsPerKind();
        assertEquals(1, count(perKind, Kind.BORN));
        assertEquals(1, count(perKind, Kind.DIED));
        assertEquals(3, count(perKind, Kind.FOUNDED), "Rome, Eiffel Tower, Hagia Sophia");
        assertEquals(2, count(perKind, Kind.OPENED), "Eiffel Tower, Hagia Sophia");
        assertEquals(1, count(perKind, Kind.ENDED));
        assertEquals(1, count(perKind, Kind.HAPPENED));
    }

    @Test
    void countsPrecisionOfEveryEventWhen() {
        var precisions = realReport().precisionCounts();
        assertEquals(7, count(precisions, Precision.DAY));
        assertEquals(2, count(precisions, Precision.YEAR), "Hagia Sophia Ended 404 and Opened 537");
        assertEquals(0, count(precisions, Precision.MONTH));
    }

    @Test
    void bucketsEventsByCenturyWithBcAsNegative() {
        var centuries = realReport().eventsPerCentury();
        assertEquals(1, count(centuries, -8L), "Rome founded -753");
        assertEquals(3, count(centuries, 18L), "Mozart x2, Lisbon 1755");
        assertEquals(2, count(centuries, 19L), "Eiffel Tower 1887, 1889");
        assertEquals(2, count(centuries, 6L), "Hagia Sophia 532, 537");
        assertEquals(1, count(centuries, 5L), "Hagia Sophia ended 404");
        assertEquals(9, centuries.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void centuryBoundariesFollowNoYearZeroConvention() {
        var centuries = reportOf(List.of(
                synthetic("Q901", enwiki("a"), String.join(",",
                        claim("P571", time("P571", "+1900-00-00T00:00:00Z", 9, "normal")), claim("P625", coords(1, 1)))),
                synthetic("Q902", enwiki("b"), String.join(",",
                        claim("P571", time("P571", "+1801-00-00T00:00:00Z", 9, "normal")), claim("P625", coords(1, 1)))),
                synthetic("Q903", enwiki("c"), String.join(",",
                        claim("P571", time("P571", "-0100-00-00T00:00:00Z", 9, "normal")), claim("P625", coords(1, 1)))),
                synthetic("Q904", enwiki("d"), String.join(",",
                        claim("P571", time("P571", "+0100-00-00T00:00:00Z", 9, "normal")), claim("P625", coords(1, 1))))))
                .eventsPerCentury();
        assertEquals(2, count(centuries, 19L), "1801 and 1900 are both 19th century");
        assertEquals(1, count(centuries, -1L), "-100 is 1st century BC");
        assertEquals(1, count(centuries, 1L), "100 is 1st century AD");
        assertEquals(0, count(centuries, 0L));
    }

    @Test
    void placeRefsResolveAgainstPlacesSeenInTheSlice() {
        var salzburg = synthetic("Q34713", "", claim("P625", coords(47.8, 13.04)));
        assertEquals(0.5, realReport(salzburg).resolvedPlaceRefShare(), 1e-9,
                "Mozart's birthplace resolves, Vienna does not");
    }

    @Test
    void noResolvedPlaceRefsWhenNoPlaceMatches() {
        assertEquals(0.0, realReport().resolvedPlaceRefShare(), 1e-9);
    }

    @Test
    void resolvedShareIsNanWithoutPlaceRefEvents() {
        assertTrue(Double.isNaN(reportOf(List.of(Fixtures.entity("Q220"))).resolvedPlaceRefShare()),
                "own-coordinate events don't count either way");
    }

    @Test
    void placeWithoutSitelinksOrFromFictionalItemStillCountsAsPlace() {
        var fictionalPlace = synthetic("Q1", "", String.join(",",
                claim("P31", item("P31", "Q15632617")), claim("P625", coords(0.5, 0.5))));
        assertEquals(1.0, reportOf(List.of(fictionalPlace, person("Q900", "Q5"))).resolvedPlaceRefShare(), 1e-9);
    }

    @Test
    void topInstanceOfPerKindOrdersByCountThenQid() {
        var died = synthetic("Q905", enwiki("Q905"), String.join(",",
                claim("P31", item("P31", "Q36180")),
                claim("P570", time("P570", "+1950-01-02T00:00:00Z", 11, "normal")),
                claim("P20", item("P20", "Q1"))));
        var report = reportOf(List.of(
                person("Q901", "Q5"), person("Q902", "Q5", "Q36180"), person("Q903", "Q5", "Q33999"), died));

        var born = report.topInstanceOf(3).get(Kind.BORN);
        assertEquals(List.of("Q5", "Q33999", "Q36180"), born.stream().map(Map.Entry::getKey).toList());
        assertEquals(List.of(3L, 1L, 1L), born.stream().map(Map.Entry::getValue).toList());
        assertEquals(List.of("Q5", "Q33999"),
                report.topInstanceOf(2).get(Kind.BORN).stream().map(Map.Entry::getKey).toList());
        assertEquals(List.of(Map.entry("Q36180", 1L)), report.topInstanceOf(3).get(Kind.DIED));
    }

    @Test
    void coarsePrecisionDropsCountOnlyNonDeprecatedValuesBelowDecade() {
        var coarseBirth = synthetic("Q910", enwiki("Q910"), String.join(",",
                claim("P569", time("P569", "+1000-00-00T00:00:00Z", 3, "normal")),
                claim("P570", time("P570", "+1000-00-00T00:00:00Z", 2, "deprecated")),
                claim("P19", item("P19", "Q1"))));
        var coarseHappened = synthetic("Q911", enwiki("Q911"), String.join(",",
                claim("P585", time("P585", "-50000-00-00T00:00:00Z", 5, "normal")), claim("P625", coords(1, 1))));
        var millennium = synthetic("Q912", enwiki("Q912"), String.join(",",
                claim("P571", time("P571", "+1000-00-00T00:00:00Z", 6, "normal")), claim("P625", coords(1, 1))));

        var drops = realReport(coarseBirth, coarseHappened, millennium).coarsePrecisionDrops();
        assertEquals(1, count(drops, Kind.BORN));
        assertEquals(0, count(drops, Kind.DIED), "deprecated statements are ignored");
        assertEquals(1, count(drops, Kind.HAPPENED));
        assertEquals(0, count(drops, Kind.FOUNDED), "millennium precision (6) is kept, not dropped");
    }

    @Test
    void candidatePropertiesCountedOnlyForEntitiesWithoutEvents() {
        var discovery = synthetic("Q920", enwiki("Q920"),
                claim("P575", time("P575", "+1846-09-23T00:00:00Z", 11, "normal")));
        var launch = synthetic("Q921", enwiki("Q921"),
                claim("P619", time("P619", "+1977-09-05T00:00:00Z", 11, "normal")));
        var landing = synthetic("Q922", enwiki("Q922"),
                claim("P620", time("P620", "+1969-07-20T00:00:00Z", 11, "normal")));
        var discoveryWithEvents = synthetic("Q923", enwiki("Q923"), String.join(",",
                claim("P575", time("P575", "+1900-01-01T00:00:00Z", 11, "normal")),
                claim("P569", time("P569", "+1850-01-02T00:00:00Z", 11, "normal")),
                claim("P19", item("P19", "Q1"))));
        var deprecatedOnly = synthetic("Q924", enwiki("Q924"),
                claim("P620", time("P620", "+1970-01-01T00:00:00Z", 11, "deprecated")));

        var candidates = reportOf(List.of(discovery, launch, landing, discoveryWithEvents, deprecatedOnly))
                .candidatePropertiesWithoutEvents();
        assertEquals(1, count(candidates, "P575"));
        assertEquals(1, count(candidates, "P619"));
        assertEquals(1, count(candidates, "P620"));
    }

    @Test
    void unknownWikiSitelinksExcludeProjectWikisAndLanguageCodes() {
        var entity = synthetic("Q930", String.join(",",
                enwiki("x"), sitelink("zh_yuewiki", "x"), sitelink("be_x_oldwiki", "x"),
                sitelink("abstractwiki", "x"), sitelink("commonswiki", "x"),
                sitelink("enwikivoyage", "x"), sitelink("fooprojectwiki", "x")), "");
        assertEquals(Set.of("fooprojectwiki"), reportOf(List.of(entity)).unknownWikiSitelinks());
    }

    @Test
    void countsHappenedRangesEndingBeforeStart() {
        var yearsReversed = synthetic("Q940", enwiki("Q940"), String.join(",",
                claim("P580", time("P580", "+1950-05-01T00:00:00Z", 11, "normal")),
                claim("P582", time("P582", "+1940-05-01T00:00:00Z", 11, "normal")),
                claim("P625", coords(1, 1))));
        var daysReversed = synthetic("Q941", enwiki("Q941"), String.join(",",
                claim("P580", time("P580", "+1900-03-10T00:00:00Z", 11, "normal")),
                claim("P582", time("P582", "+1900-03-05T00:00:00Z", 11, "normal")),
                claim("P625", coords(1, 1))));
        var ordered = synthetic("Q942", enwiki("Q942"), String.join(",",
                claim("P580", time("P580", "+1900-03-05T00:00:00Z", 11, "normal")),
                claim("P582", time("P582", "+1900-03-10T00:00:00Z", 11, "normal")),
                claim("P625", coords(1, 1))));
        assertEquals(2, reportOf(List.of(yearsReversed, daysReversed, ordered)).rangesEndingBeforeStart());
        assertEquals(0, realReport().rangesEndingBeforeStart());
    }

    @Test
    void countsItemsEndedBeforeFounded() {
        assertEquals(1, realReport().endedBeforeFounded(), "Hagia Sophia: Ended 404 < Founded 532");
        assertEquals(0, reportOf(List.of(Fixtures.entity("Q243"))).endedBeforeFounded());
    }

    @Test
    void markdownHasASectionPerMetricAndTheNumbers() {
        var markdown = realReport().toMarkdown();
        long headings = markdown.lines().filter(l -> l.startsWith("#")).count();
        assertTrue(headings >= 8, "expected a heading per section, got " + headings);
        for (String expected : List.of("FOUNDED", "BORN", "HAPPENED", "DAY", "YEAR", "-8", "P575", "6")) {
            assertTrue(markdown.contains(expected), "markdown should mention " + expected);
        }
    }

    @Test
    void coarsePrecisionDropsUseTheRankTheExtractorWouldChoose() {
        var preferredFine = synthetic("Q950", enwiki("Q950"), String.join(",",
                claim("P571", time("P571", "+1500-00-00T00:00:00Z", 9, "preferred"),
                        time("P571", "+1000-00-00T00:00:00Z", 3, "normal")),
                claim("P625", coords(1, 1))));
        var normalCoarse = synthetic("Q951", enwiki("Q951"), String.join(",",
                claim("P576", time("P576", "+1500-00-00T00:00:00Z", 4, "normal")), claim("P625", coords(1, 1))));
        assertEquals(0, count(reportOf(List.of(preferredFine)).coarsePrecisionDrops(), Kind.FOUNDED),
                "preferred fine value wins; the normal coarse one is never chosen");
        assertEquals(1, count(reportOf(List.of(normalCoarse)).coarsePrecisionDrops(), Kind.ENDED));
    }

    @Test
    void coarseEndOfRangeIsNotADrop() {
        var entity = synthetic("Q952", enwiki("Q952"), String.join(",",
                claim("P585", time("P585", "+1900-01-01T00:00:00Z", 11, "normal")),
                claim("P582", time("P582", "+1000-00-00T00:00:00Z", 3, "normal")),
                claim("P625", coords(1, 1))));
        assertEquals(0, count(reportOf(List.of(entity)).coarsePrecisionDrops(), Kind.HAPPENED));
    }

    @Test
    void coarsePrecisionDropsSkipItemsFailingTheGates() {
        var coarseBirth = String.join(",",
                claim("P569", time("P569", "+1000-00-00T00:00:00Z", 3, "normal")), claim("P19", item("P19", "Q1")));
        var noWikipedia = synthetic("Q953", sitelink("commonswiki", "x"), coarseBirth);
        var fictional = synthetic("Q954", enwiki("Q954"),
                coarseBirth + "," + claim("P31", item("P31", "Q15632617")));
        assertEquals(0, count(reportOf(List.of(noWikipedia, fictional)).coarsePrecisionDrops(), Kind.BORN));
    }

    @Test
    void candidatePropertiesSkipItemsFailingTheGates() {
        var discovery = claim("P575", time("P575", "+1846-09-23T00:00:00Z", 11, "normal"));
        var commonsOnly = synthetic("Q960", sitelink("commonswiki", "x"), discovery);
        var fictional = synthetic("Q961", enwiki("Q961"),
                discovery + "," + claim("P31", item("P31", "Q15632617")));
        assertEquals(0, count(reportOf(List.of(commonsOnly, fictional)).candidatePropertiesWithoutEvents(), "P575"));
    }

    @Test
    void placeCoordinatesReturnsUsableEarthCoordinatesOrNull() {
        var rome = WikidataExtractor.placeCoordinates(Fixtures.entity("Q220"));
        assertEquals(41.9, rome.latitude(), 0.1);
        assertEquals(12.5, rome.longitude(), 0.1);
        var noSitelinksPlace = WikidataExtractor.placeCoordinates(synthetic("Q970", "", claim("P625", coords(47.8, 13.04))));
        assertEquals(47.8, noSitelinksPlace.latitude(), 1e-9);
        assertNull(WikidataExtractor.placeCoordinates(Fixtures.entity("Q254")), "no P625");
        assertNull(WikidataExtractor.placeCoordinates(Fixtures.entity("synthetic-off-earth")), "globe is not Earth");
    }
}
