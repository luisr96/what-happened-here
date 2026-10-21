package io.github.luisr96.whathappenedhere.extractor;

import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.MOON;
import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.coordinate;
import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.item;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.MAPPER;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.entity;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.time;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.Event.Kind;
import io.github.luisr96.whathappenedhere.extractor.Event.PlaceRef;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate.Calendar;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate.Precision;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class WikidataExtractorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);
    private static final WikidataExtractor EXTRACTOR = new WikidataExtractor(CLOCK);

    private static ExtractedItem extracted(String id) {
        return extracted(entity(id));
    }

    private static ExtractedItem extracted(JsonNode node) {
        Optional<ExtractedItem> result = EXTRACTOR.extract(node);
        assertTrue(result.isPresent(), "expected events");
        return result.get();
    }

    private static Event event(ExtractedItem item, Kind kind) {
        return item.events().stream().filter(e -> e.kind() == kind).findFirst()
            .orElseThrow(() -> new AssertionError("no " + kind + " in " + item.events()));
    }

    @Test
    void romeCarriesEnglishWikipediaTitle() {
        assertEquals("Rome", extracted("Q220").wikiTitle());
    }

    @Test
    void ivanovkaWithoutEnwikiHasNullTitleButStillHasEvents() {
        var item = extracted("Q50829");
        assertNull(item.wikiTitle());
        assertFalse(item.events().isEmpty());
    }

    private static void assertKinds(ExtractedItem item, Kind... kinds) {
        assertEquals(List.of(kinds), item.events().stream().map(Event::kind).toList());
    }

    private static void assertDate(HistoricDate date, long year, int month, int day, Precision precision,
        Calendar calendar) {
        assertEquals(year, date.year(), "year");
        assertEquals(month, date.month(), "month");
        assertEquals(day, date.day(), "day");
        assertEquals(precision, date.precision(), "precision");
        assertEquals(calendar, date.calendar(), "calendar");
    }

    private static void assertDay(HistoricDate date, long year, int month, int day) {
        assertDate(date, year, month, day, Precision.DAY, Calendar.GREGORIAN);
    }

    private static void assertDayJ(HistoricDate date, long year, int month, int day) {
        assertDate(date, year, month, day, Precision.DAY, Calendar.JULIAN);
    }

    private static void assertAt(Event event, String qid) {
        assertEquals(qid, assertInstanceOf(PlaceRef.class, event.where()).qid());
    }

    private static Coordinates assertOwnCoords(Event event) {
        return assertInstanceOf(Coordinates.class, event.where());
    }

    private static void assertNothing(JsonNode node) {
        assertEquals(Optional.empty(), EXTRACTOR.extract(node));
    }

    @Nested
    class People {

        @Test
        void mozartPreferredDeathPlace() {
            var item = extracted("Q254");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDay(event(item, Kind.BORN).when(), 1756, 1, 27);
            assertAt(event(item, Kind.BORN), "Q34713");
            assertDay(event(item, Kind.DIED).when(), 1791, 12, 5);
            assertAt(event(item, Kind.DIED), "Q1741");
            item.events().forEach(e -> assertNull(e.endDate()));
        }

        @Test
        void curieFirstOfEqualRankBirthPlaces() {
            var item = extracted("Q7186");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDay(event(item, Kind.BORN).when(), 1867, 11, 7);
            assertAt(event(item, Kind.BORN), "Q9365159");
            assertDay(event(item, Kind.DIED).when(), 1934, 7, 4);
            assertAt(event(item, Kind.DIED), "Q2364887");
        }

        @Test
        void einstein() {
            var item = extracted("Q937");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDay(event(item, Kind.BORN).when(), 1879, 3, 14);
            assertAt(event(item, Kind.BORN), "Q3012");
            assertDay(event(item, Kind.DIED).when(), 1955, 4, 18);
            assertAt(event(item, Kind.DIED), "Q138518");
        }

        @Test
        void genghisKhanJulian() {
            var item = extracted("Q720");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDayJ(event(item, Kind.BORN).when(), 1162, 5, 31);
            assertAt(event(item, Kind.BORN), "Q2068087");
            assertDayJ(event(item, Kind.DIED).when(), 1227, 8, 25);
            assertAt(event(item, Kind.DIED), "Q211243");
        }

        @Test
        void caesarBcDates() {
            var item = extracted("Q1048");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDate(event(item, Kind.BORN).when(), -100, 7, 0, Precision.MONTH, Calendar.JULIAN);
            assertAt(event(item, Kind.BORN), "Q220");
            assertDayJ(event(item, Kind.DIED).when(), -44, 3, 15);
            assertAt(event(item, Kind.DIED), "Q944814");
        }

        @Test
        void confuciusPreferredCoarseBeatsNormalExact() {
            var item = extracted("Q4604");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDate(event(item, Kind.BORN).when(), -551, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertAt(event(item, Kind.BORN), "Q210287");
            assertDate(event(item, Kind.DIED).when(), -479, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertAt(event(item, Kind.DIED), "Q1012627");
        }

        @Test
        void shakespeareJulianDeprecatedDaysIgnored() {
            var item = extracted("Q692");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDate(event(item, Kind.BORN).when(), 1564, 4, 0, Precision.MONTH, Calendar.JULIAN);
            assertAt(event(item, Kind.BORN), "Q189288");
            assertDayJ(event(item, Kind.DIED).when(), 1616, 4, 23);
            assertAt(event(item, Kind.DIED), "Q189288");
        }

        @Test
        void macronAliveHasNoDeath() {
            var item = extracted("Q3052772");
            assertKinds(item, Kind.BORN);
            assertDay(event(item, Kind.BORN).when(), 1977, 12, 21);
            assertAt(event(item, Kind.BORN), "Q41604");
        }

        @Test
        void balbinusWithoutBirthPlaceOnlyDied() {
            var item = extracted("Q1805");
            assertKinds(item, Kind.DIED);
            assertDayJ(event(item, Kind.DIED).when(), 238, 7, 29);
            assertAt(event(item, Kind.DIED), "Q220");
        }

        @Test
        void sherlockHolmesNothing() {
            assertNothing(entity("Q4653"));
        }

        @Test
        void kingArthurDisputedHumanKeptWithOnlyDied() {
            var item = extracted("Q45792");
            assertKinds(item, Kind.DIED);
            assertDate(event(item, Kind.DIED).when(), 600, 0, 0, Precision.CENTURY, Calendar.JULIAN);
            assertAt(event(item, Kind.DIED), "Q1028477");
        }

        @Test
        void hagarBiblicalHumanKeptWithOnlyBorn() {
            var item = extracted("Q214617");
            assertKinds(item, Kind.BORN);
            assertDate(event(item, Kind.BORN).when(), -1930, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertAt(event(item, Kind.BORN), "Q11768");
        }

        @Test
        void zeusNothing() {
            assertNothing(entity("Q34201"));
        }
    }

    @Nested
    class Geologic {

        // Q146094 has P580/P582 and Earth P625 (its GSSP), so only the type exclusion stops a Happened.
        @Test
        void zancleanNothing() {
            assertNothing(entity("Q146094"));
        }
    }

    @Nested
    class Season {

        // Q144644 is typed only as a United Rugby Championship season; the type alone excludes it.
        @Test
        void pro12SeasonNothing() {
            assertNothing(entity("Q144644"));
        }
    }

    @Nested
    class Happened {

        @Test
        void lisbonEarthquakeApproximateCoords() {
            var item = extracted("Q191055");
            assertKinds(item, Kind.HAPPENED);
            Event e = event(item, Kind.HAPPENED);
            assertDay(e.when(), 1755, 11, 1);
            assertNull(e.endDate());
            Coordinates c = assertOwnCoords(e);
            assertEquals(36, c.latitude(), 1e-9);
            assertEquals(-11, c.longitude(), 1e-9);
            assertTrue(c.isApproximate());
        }

        @Test
        void vesuviusEarliestNormalNotDeprecated() {
            var item = extracted("Q2411998");
            assertKinds(item, Kind.HAPPENED);
            assertDayJ(event(item, Kind.HAPPENED).when(), 79, 10, 24);
            assertOwnCoords(event(item, Kind.HAPPENED));
        }

        @Test
        void magnaCartaNothing() {
            assertNothing(entity("Q12519"));
        }

        @Test
        void versaillesPointBeatsStartWithoutEnd() {
            var item = extracted("Q8736");
            assertKinds(item, Kind.HAPPENED);
            Event e = event(item, Kind.HAPPENED);
            assertDay(e.when(), 1919, 6, 28);
            assertNull(e.endDate());
            assertAt(e, "Q1583447");
        }

        @Test
        void berlinWall() {
            var item = extracted("Q69163529");
            assertKinds(item, Kind.HAPPENED);
            assertDay(event(item, Kind.HAPPENED).when(), 1989, 11, 9);
            assertOwnCoords(event(item, Kind.HAPPENED));
        }

        @Test
        void apolloNothing() {
            assertNothing(entity("Q43653"));
        }

        @Test
        void chernobyl() {
            var item = extracted("Q486");
            assertKinds(item, Kind.HAPPENED);
            assertDay(event(item, Kind.HAPPENED).when(), 1986, 4, 26);
            assertOwnCoords(event(item, Kind.HAPPENED));
        }

        @Test
        void titanicRangeBeatsPointInTime() {
            var item = extracted("Q2577588");
            assertKinds(item, Kind.HAPPENED);
            Event e = event(item, Kind.HAPPENED);
            assertDay(e.when(), 1912, 4, 14);
            assertDay(e.endDate(), 1912, 4, 15);
            assertFalse(assertOwnCoords(e).isApproximate());
        }

        @Test
        void indianOceanTsunami() {
            var item = extracted("Q130754");
            assertKinds(item, Kind.HAPPENED);
            assertDay(event(item, Kind.HAPPENED).when(), 2004, 12, 26);
            assertOwnCoords(event(item, Kind.HAPPENED));
        }

        // Q284163 is a tournament also typed as the generic sports season, which is not excluded.
        @Test
        void worldCup2022IsKept() {
            var item = extracted("Q284163");
            assertKinds(item, Kind.HAPPENED);
            assertEquals(1, item.events().size());
            Event e = event(item, Kind.HAPPENED);
            assertDay(e.when(), 2022, 11, 20);
            assertDay(e.endDate(), 2022, 12, 18);
            assertAt(e, "Q1186333");
        }

        @Test
        void frenchRevolutionRange() {
            var item = extracted("Q6534");
            assertKinds(item, Kind.HAPPENED);
            Event e = event(item, Kind.HAPPENED);
            assertDay(e.when(), 1789, 7, 14);
            assertDay(e.endDate(), 1799, 11, 9);
            assertAt(e, "Q142");
        }

        @Test
        void hastings() {
            var item = extracted("Q83224");
            assertKinds(item, Kind.HAPPENED);
            assertDayJ(event(item, Kind.HAPPENED).when(), 1066, 10, 14);
            assertOwnCoords(event(item, Kind.HAPPENED));
        }

        @Test
        void sellasiaFallsBackToLocation() {
            var item = extracted("Q204343");
            assertKinds(item, Kind.HAPPENED);
            Event e = event(item, Kind.HAPPENED);
            assertEquals(-222, e.when().year());
            assertEquals(Precision.YEAR, e.when().precision());
            assertAt(e, "Q15709786");
        }

        @Test
        void tutankhamunTombNothing() {
            assertNothing(entity("Q835309"));
        }

        @Test
        void blackDeathYearRange() {
            var item = extracted("Q42005");
            assertKinds(item, Kind.HAPPENED);
            Event e = event(item, Kind.HAPPENED);
            assertDate(e.when(), 1346, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertDate(e.endDate(), 1352, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertAt(e, "Q48");
        }

        @Test
        void helmsDeepNothing() {
            assertNothing(entity("Q1764229"));
        }
    }

    @Nested
    class Foundings {

        @Test
        void romeBcFounding() {
            var item = extracted("Q220");
            assertKinds(item, Kind.FOUNDED);
            assertDayJ(event(item, Kind.FOUNDED).when(), -753, 4, 21);
            assertOwnCoords(event(item, Kind.FOUNDED));
        }

        @Test
        void newYork() {
            var item = extracted("Q60");
            assertKinds(item, Kind.FOUNDED);
            assertDate(event(item, Kind.FOUNDED).when(), 1624, 0, 0, Precision.YEAR, Calendar.GREGORIAN);
            assertOwnCoords(event(item, Kind.FOUNDED));
        }

        @Test
        void bolognaApproximateCoords() {
            var item = extracted("Q131262");
            assertKinds(item, Kind.FOUNDED);
            assertDate(event(item, Kind.FOUNDED).when(), 1088, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertTrue(assertOwnCoords(event(item, Kind.FOUNDED)).isApproximate());
        }

        @Test
        void harvard() {
            var item = extracted("Q13371");
            assertKinds(item, Kind.FOUNDED);
            assertDay(event(item, Kind.FOUNDED).when(), 1636, 9, 8);
            assertOwnCoords(event(item, Kind.FOUNDED));
        }

        @Test
        void eiffelTowerEarliestOpening() {
            var item = extracted("Q243");
            assertKinds(item, Kind.FOUNDED, Kind.OPENED);
            assertDay(event(item, Kind.FOUNDED).when(), 1887, 1, 28);
            assertDay(event(item, Kind.OPENED).when(), 1889, 3, 31);
            item.events().forEach(e -> {
                assertOwnCoords(e);
                assertNull(e.endDate());
            });
        }

        @Test
        void goldenGate() {
            var item = extracted("Q44440");
            assertKinds(item, Kind.FOUNDED, Kind.OPENED);
            assertDay(event(item, Kind.FOUNDED).when(), 1937, 5, 27);
            assertDay(event(item, Kind.OPENED).when(), 1937, 5, 28);
            item.events().forEach(WikidataExtractorTest::assertOwnCoords);
        }

        @Test
        void worldTradeCenterEnded() {
            var item = extracted("Q11235");
            assertKinds(item, Kind.FOUNDED, Kind.ENDED);
            assertDay(event(item, Kind.FOUNDED).when(), 1966, 8, 25);
            assertDay(event(item, Kind.ENDED).when(), 2001, 9, 11);
            assertNull(event(item, Kind.ENDED).endDate());
            item.events().forEach(WikidataExtractorTest::assertOwnCoords);
        }

        @Test
        void sovietUnionApproximateCentroid() {
            var item = extracted("Q15180");
            assertKinds(item, Kind.FOUNDED, Kind.ENDED);
            assertDay(event(item, Kind.FOUNDED).when(), 1922, 12, 30);
            assertDay(event(item, Kind.ENDED).when(), 1991, 12, 26);
            for (Event e : item.events()) {
                Coordinates c = assertOwnCoords(e);
                assertEquals(65, c.latitude(), 1e-9);
                assertEquals(90, c.longitude(), 1e-9);
                assertTrue(c.isApproximate());
            }
        }

        @Test
        void eastIndiaCompanyHeadquarters() {
            var item = extracted("Q83164");
            assertKinds(item, Kind.FOUNDED, Kind.ENDED);
            assertDay(event(item, Kind.FOUNDED).when(), 1600, 12, 31);
            assertDay(event(item, Kind.ENDED).when(), 1874, 6, 1);
            item.events().forEach(e -> assertAt(e, "Q84"));
        }

        @Test
        void applePreferredHeadquarters() {
            var item = extracted("Q312");
            assertKinds(item, Kind.FOUNDED);
            assertDay(event(item, Kind.FOUNDED).when(), 1976, 4, 1);
            assertAt(event(item, Kind.FOUNDED), "Q189471");
        }

        @Test
        void libraryOfAlexandriaCenturies() {
            var item = extracted("Q435");
            assertKinds(item, Kind.FOUNDED, Kind.ENDED);
            assertDate(event(item, Kind.FOUNDED).when(), -300, 0, 0, Precision.CENTURY, Calendar.JULIAN);
            assertDate(event(item, Kind.ENDED).when(), 400, 0, 0, Precision.CENTURY, Calendar.JULIAN);
            item.events().forEach(WikidataExtractorTest::assertOwnCoords);
        }

        @Test
        void hagiaSophiaAllThree() {
            var item = extracted("Q12506");
            assertKinds(item, Kind.FOUNDED, Kind.ENDED, Kind.OPENED);
            assertDayJ(event(item, Kind.FOUNDED).when(), 532, 2, 23);
            assertDate(event(item, Kind.ENDED).when(), 404, 0, 0, Precision.YEAR, Calendar.JULIAN);
            assertDate(event(item, Kind.OPENED).when(), 537, 0, 0, Precision.YEAR, Calendar.JULIAN);
            item.events().forEach(WikidataExtractorTest::assertOwnCoords);
        }

        @Test
        void ivanovkaLowImportance() {
            var item = extracted("Q50829");
            assertKinds(item, Kind.FOUNDED);
            assertDate(event(item, Kind.FOUNDED).when(), 1738, 0, 0, Precision.YEAR, Calendar.GREGORIAN);
            assertOwnCoords(event(item, Kind.FOUNDED));
            assertTrue(item.importance() > 0);
        }
    }

    @Nested
    class EdgeCaseFixtures {

        @ParameterizedTest
        @ValueSource(strings = {"Q1895685", "Q16711", "Q631696", "synthetic-off-earth", "synthetic-fictional",
            "synthetic-mythical", "synthetic-no-sitelinks"})
        void nothing(String id) {
            assertNothing(entity(id));
        }

        @Test
        void humanFilmCharacterKeptLikeMozart() {
            var item = extracted("synthetic-human-film-character");
            assertKinds(item, Kind.BORN, Kind.DIED);
            assertDay(event(item, Kind.BORN).when(), 1756, 1, 27);
            assertAt(event(item, Kind.BORN), "Q34713");
            assertDay(event(item, Kind.DIED).when(), 1791, 12, 5);
            assertAt(event(item, Kind.DIED), "Q1741");
        }

        @Test
        void deprecatedOnlyBirthGivesDiedOnly() {
            assertKinds(extracted("synthetic-deprecated-only"), Kind.DIED);
        }

        @Test
        void earlierDeprecatedBirthIgnored() {
            assertDay(event(extracted("synthetic-deprecated-earlier"), Kind.BORN).when(), 1756, 1, 27);
        }

        @Test
        void preferredBirthBeatsEarlierNormal() {
            assertDay(event(extracted("synthetic-two-birth-ranks"), Kind.BORN).when(), 1756, 1, 27);
        }

        @Test
        void redirectReportsTargetId() {
            var item = extracted("Q19710815");
            assertEquals("Q207", item.itemId());
            assertDay(event(item, Kind.BORN).when(), 1946, 7, 6);
            assertAt(event(item, Kind.BORN), "Q12073283");
        }

        @Test
        void itemLevelFactsComeFromItemFacts() {
            JsonNode mozart = entity("Q254");
            var item = extracted(mozart);
            assertEquals("Q254", item.itemId());
            assertEquals(ItemFacts.label(mozart.path("labels")), item.label());
            assertEquals("Wolfgang Amadeus Mozart", item.label());
            assertEquals(ItemFacts.importance(mozart.path("sitelinks")), item.importance());
            assertTrue(item.importance() > 0);
            assertEquals(ItemFacts.instanceOf(mozart.path("claims")), item.instanceOf());
            assertTrue(item.instanceOf().contains("Q5"));
        }
    }

    /** Hand-built entities for rules that are awkward to find in live data. */
    @Nested
    class Synthetic {

        private static ObjectNode newEntity() {
            ObjectNode e = MAPPER.createObjectNode();
            e.put("type", "item");
            e.put("id", "Q999");
            ObjectNode en = e.putObject("labels").putObject("en");
            en.put("language", "en");
            en.put("value", "Test item");
            e.putObject("claims");
            ObjectNode enwiki = e.putObject("sitelinks").putObject("enwiki");
            enwiki.put("site", "enwiki");
            enwiki.put("title", "Test item");
            return e;
        }

        private static ObjectNode add(ObjectNode entity, String property, String rank, String type, JsonNode value) {
            ObjectNode claim = entity.withObject("claims").withArray(property).addObject();
            claim.put("type", "statement");
            claim.put("rank", rank);
            ObjectNode snak = claim.putObject("mainsnak");
            snak.put("snaktype", "value");
            snak.put("property", property);
            ObjectNode dv = snak.putObject("datavalue");
            dv.put("type", type);
            dv.set("value", value);
            return entity;
        }

        private static void date(ObjectNode e, String property, String time, int precision) {
            date(e, property, "normal", time, precision);
        }

        private static void date(ObjectNode e, String property, String rank, String time, int precision) {
            add(e, property, rank, "time", time(time, precision));
        }

        private static void place(ObjectNode e, String property, String qid) {
            add(e, property, "normal", "wikibase-entityid", item(qid));
        }

        private static void coords(ObjectNode e, double lat, double lon, String globe) {
            ObjectNode c = coordinate(lat, lon, 0.0001);
            c.put("globe", globe);
            add(e, "P625", "normal", "globecoordinate", c);
        }

        private static void coords(ObjectNode e, double lat, double lon) {
            coords(e, lat, lon, PlaceValues.EARTH);
        }

        private static Event happened(ObjectNode e) {
            return event(extracted(e), Kind.HAPPENED);
        }

        @Test
        void offEarthOnlyCoordinatesExcludeWholeItem() {
            ObjectNode e = newEntity();
            coords(e, 10, 10, MOON);
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            place(e, "P276", "Q1");
            date(e, "P569", "+1900-01-01T00:00:00Z", 11);
            place(e, "P19", "Q2");
            assertNothing(e);
        }

        @Test
        void earthCoordinatesBesideMoonOnesAreUsed() {
            ObjectNode e = newEntity();
            coords(e, 10, 10, MOON);
            coords(e, 20, 30);
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            Coordinates c = assertOwnCoords(happened(e));
            assertEquals(20, c.latitude(), 1e-9);
            assertEquals(30, c.longitude(), 1e-9);
        }

        @Test
        void malformedEarthCoordinatesFallThroughToLocation() {
            ObjectNode e = newEntity();
            ObjectNode bad = MAPPER.createObjectNode();
            bad.put("latitude", 500.0);
            bad.put("longitude", 10.0);
            bad.put("precision", 0.0001);
            bad.put("globe", PlaceValues.EARTH);
            add(e, "P625", "normal", "globecoordinate", bad);
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            place(e, "P276", "Q1");
            assertAt(happened(e), "Q1");
        }

        @Test
        void malformedEarthPlusMoonCoordinatesIsNotOffEarthOnly() {
            ObjectNode e = newEntity();
            ObjectNode bad = MAPPER.createObjectNode();
            bad.put("latitude", 500.0);
            bad.put("longitude", 10.0);
            bad.put("precision", 0.0001);
            bad.put("globe", PlaceValues.EARTH);
            add(e, "P625", "normal", "globecoordinate", bad);
            coords(e, 10, 10, MOON);
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            place(e, "P276", "Q1");
            assertAt(happened(e), "Q1");
        }

        @Test
        void futureUsesUtcDateNotClockZone() {
            var kiritimati = new WikidataExtractor(Clock.fixed(Instant.parse("2026-10-18T23:00:00Z"),
                java.time.ZoneId.of("Pacific/Kiritimati")));
            ObjectNode tomorrow = newEntity();
            place(tomorrow, "P276", "Q1");
            date(tomorrow, "P585", "+2026-10-19T00:00:00Z", 11);
            assertEquals(Optional.empty(), kiritimati.extract(tomorrow));

            ObjectNode today = newEntity();
            place(today, "P276", "Q1");
            date(today, "P585", "+2026-10-18T00:00:00Z", 11);
            var item = kiritimati.extract(today).orElseThrow();
            assertKinds(item, Kind.HAPPENED);
        }

        @Test
        void deprecatedMoonCoordinatesDoNotExclude() {
            ObjectNode e = newEntity();
            ObjectNode moon = coordinate(10, 10, 0.0001);
            moon.put("globe", MOON);
            add(e, "P625", "deprecated", "globecoordinate", moon);
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            place(e, "P276", "Q1");
            assertAt(happened(e), "Q1");
        }

        @Test
        void happenedFallsBackFromLocationToAdminArea() {
            ObjectNode e = newEntity();
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            place(e, "P131", "Q3");
            assertAt(happened(e), "Q3");
        }

        @Test
        void coordinatesBeatLocation() {
            ObjectNode e = newEntity();
            date(e, "P585", "+1900-01-01T00:00:00Z", 11);
            place(e, "P276", "Q1");
            coords(e, 1, 2);
            assertOwnCoords(happened(e));
        }

        @Test
        void rangeBeatsPointInTime() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P585", "+1900-06-01T00:00:00Z", 11);
            date(e, "P580", "+1900-01-01T00:00:00Z", 11);
            date(e, "P582", "+1901-01-01T00:00:00Z", 11);
            Event h = happened(e);
            assertDay(h.when(), 1900, 1, 1);
            assertDay(h.endDate(), 1901, 1, 1);
        }

        @Test
        void pointInTimeBeatsStartAlone() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+1900-01-01T00:00:00Z", 11);
            date(e, "P585", "+1900-06-01T00:00:00Z", 11);
            Event h = happened(e);
            assertDay(h.when(), 1900, 6, 1);
            assertNull(h.endDate());
        }

        @Test
        void startAloneIsUsedWithoutEnd() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+1900-01-01T00:00:00Z", 11);
            Event h = happened(e);
            assertDay(h.when(), 1900, 1, 1);
            assertNull(h.endDate());
        }

        @Test
        void endAloneGivesNothing() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P582", "+1900-01-01T00:00:00Z", 11);
            assertNothing(e);
        }

        @Test
        void brokenEndDateMeansNoRange() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+1900-01-01T00:00:00Z", 11);
            date(e, "P582", "+1887-00-00T00:00:00Z", 11);
            date(e, "P585", "+1900-06-01T00:00:00Z", 11);
            Event h = happened(e);
            assertDay(h.when(), 1900, 6, 1);
            assertNull(h.endDate());
        }

        @Test
        void futureEndFallsBackToPointInTime() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+2020-01-01T00:00:00Z", 11);
            date(e, "P582", "+2030-01-01T00:00:00Z", 11);
            date(e, "P585", "+2021-01-01T00:00:00Z", 11);
            Event h = happened(e);
            assertDay(h.when(), 2021, 1, 1);
            assertNull(h.endDate());
        }

        @Test
        void futureEndFallsBackToStartAlone() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+2020-01-01T00:00:00Z", 11);
            date(e, "P582", "+2030-01-01T00:00:00Z", 11);
            Event h = happened(e);
            assertDay(h.when(), 2020, 1, 1);
            assertNull(h.endDate());
        }

        @Test
        void futurePreferredValueFallsBackToOlderNormal() {
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            date(e, "P569", "preferred", "+2030-01-01T00:00:00Z", 11);
            date(e, "P569", "normal", "+1950-05-05T00:00:00Z", 11);
            assertDay(event(extracted(e), Kind.BORN).when(), 1950, 5, 5);
        }

        @Test
        void onlyFutureDatesGiveNothing() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+2030-01-01T00:00:00Z", 11);
            assertNothing(e);
        }

        @Test
        void tomorrowIsFuture() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P1619", "+2026-10-19T00:00:00Z", 11);
            assertNothing(e);
        }

        @Test
        void todayIsNotFuture() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P1619", "+2026-10-18T00:00:00Z", 11);
            assertDay(event(extracted(e), Kind.OPENED).when(), 2026, 10, 18);
        }

        @Test
        void currentMonthIsNotFuture() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+2026-10-00T00:00:00Z", 10);
            assertEquals(Precision.MONTH, event(extracted(e), Kind.FOUNDED).when().precision());
        }

        @Test
        void nextMonthIsFuture() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+2026-11-00T00:00:00Z", 10);
            assertNothing(e);
        }

        @Test
        void currentYearIsNotFuture() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+2026-00-00T00:00:00Z", 9);
            assertEquals(2026, event(extracted(e), Kind.FOUNDED).when().year());
        }

        @Test
        void nextYearIsFuture() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+2027-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void twentyFirstCenturyIsNotFutureBecauseItStarted() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+2100-00-00T00:00:00Z", 7);
            assertEquals(Precision.CENTURY, event(extracted(e), Kind.FOUNDED).when().precision());
        }

        @Test
        void palaeolithicFoundingIsTooOld() {
            // Q1673859 Isernia la Pineta
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-698000-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void yearBeforeTenThousandBcIsTooOld() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-10001-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void tenThousandBcIsKept() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-10000-00-00T00:00:00Z", 9);
            assertEquals(-10000, event(extracted(e), Kind.FOUNDED).when().year());
        }

        @Test
        void dayInTenThousandBcIsKept() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-10000-12-31T00:00:00Z", 11);
            assertEquals(-10000, event(extracted(e), Kind.FOUNDED).when().year());
        }

        @Test
        void yearAfterTenThousandBcIsKept() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-9999-00-00T00:00:00Z", 9);
            assertEquals(-9999, event(extracted(e), Kind.FOUNDED).when().year());
        }

        @Test
        void millenniumReachingBeforeTenThousandBcIsTooOld() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-10500-00-00T00:00:00Z", 6);
            assertNothing(e);
        }

        @Test
        void tenthMillenniumBcIsKept() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-10000-00-00T00:00:00Z", 6);
            assertEquals(Precision.MILLENNIUM, event(extracted(e), Kind.FOUNDED).when().precision());
        }

        @Test
        void centuryReachingBeforeTenThousandBcIsTooOld() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-10200-00-00T00:00:00Z", 7);
            assertNothing(e);
        }

        @Test
        void centuryWithinLimitIsKept() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-9800-00-00T00:00:00Z", 7);
            HistoricDate when = event(extracted(e), Kind.FOUNDED).when();
            assertEquals(Precision.CENTURY, when.precision());
            assertTrue(when.earliestYear() >= -10000);
        }

        @Test
        void rangeStartingTooEarlyIsDroppedEvenIfItEndsLater() {
            // Q202390 pre-Columbian era: -48000 to 1492
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "-48000-00-00T00:00:00Z", 9);
            date(e, "P582", "+1492-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void tooOldPreferredValueFallsBackToNormal() {
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            date(e, "P569", "preferred", "-20000-00-00T00:00:00Z", 9);
            date(e, "P569", "normal", "-500-00-00T00:00:00Z", 9);
            assertEquals(-500, event(extracted(e), Kind.BORN).when().year());
        }

        @Test
        void tooOldFoundingLeavesEndedIntact() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "-20000-00-00T00:00:00Z", 9);
            date(e, "P576", "+1900-00-00T00:00:00Z", 9);
            assertKinds(extracted(e), Kind.ENDED);
        }

        @Test
        void secondMillenniumBirthIsTooVague() {
            // Q6137558 James L. Fields: P569 +1900 at millennium precision
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            date(e, "P569", "+1900-00-00T00:00:00Z", 6);
            assertNothing(e);
        }

        @Test
        void yearThousandMillenniumIsFirstMillenniumAndKept() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+1000-00-00T00:00:00Z", 6);
            HistoricDate when = event(extracted(e), Kind.FOUNDED).when();
            assertEquals(Precision.MILLENNIUM, when.precision());
            assertEquals(1, when.earliestYear());
            assertEquals(1000, when.year());
        }

        @Test
        void tooVagueStartFallsBackToPointInTime() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+1900-00-00T00:00:00Z", 6);
            date(e, "P582", "+1950-00-00T00:00:00Z", 9);
            date(e, "P585", "+1920-00-00T00:00:00Z", 9);
            Event h = happened(e);
            assertEquals(1920, h.when().year());
            assertNull(h.endDate());
        }

        @ParameterizedTest
        @ValueSource(strings = {"+1001-00-00T00:00:00Z", "+2000-00-00T00:00:00Z", "+2001-00-00T00:00:00Z"})
        void secondMillenniumOrLaterIsTooVague(String time) {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", time, 6);
            assertNothing(e);
        }

        @ParameterizedTest
        @ValueSource(strings = {"-3000-00-00T00:00:00Z", "+0500-00-00T00:00:00Z"})
        void bcAndFirstMillenniumAdMillenniaAreKept(String time) {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", time, 6);
            assertEquals(Precision.MILLENNIUM, event(extracted(e), Kind.FOUNDED).when().precision());
        }

        @Test
        void twentiethCenturyIsNotTooVague() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P571", "+1900-00-00T00:00:00Z", 7);
            HistoricDate when = event(extracted(e), Kind.FOUNDED).when();
            assertEquals(Precision.CENTURY, when.precision());
            assertEquals(1900, when.year());
        }

        @Test
        void tooVaguePreferredBirthFallsBackToNormal() {
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            date(e, "P569", "preferred", "+1900-00-00T00:00:00Z", 6);
            date(e, "P569", "normal", "+1923-00-00T00:00:00Z", 9);
            HistoricDate when = event(extracted(e), Kind.BORN).when();
            assertEquals(1923, when.year());
            assertEquals(Precision.YEAR, when.precision());
        }

        @Test
        void tooVagueEndFallsBackToStartAlone() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            date(e, "P580", "+1500-00-00T00:00:00Z", 9);
            date(e, "P582", "+1900-00-00T00:00:00Z", 6);
            Event h = happened(e);
            assertEquals(1500, h.when().year());
            assertNull(h.endDate());
        }

        @Test
        void tooVagueBirthLeavesDiedIntact() {
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            place(e, "P20", "Q2");
            date(e, "P569", "+1900-00-00T00:00:00Z", 6);
            date(e, "P570", "+1975-00-00T00:00:00Z", 9);
            ExtractedItem item = extracted(e);
            assertKinds(item, Kind.DIED);
            assertEquals(1975, event(item, Kind.DIED).when().year());
        }

        @Test
        void importanceZeroExcluded() {
            ObjectNode e = newEntity();
            e.putObject("sitelinks").putObject("commonswiki").put("site", "commonswiki");
            coords(e, 1, 2);
            date(e, "P571", "+1900-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void fictionalExcluded() {
            ObjectNode e = newEntity();
            place(e, "P31", "Q15632617");
            place(e, "P19", "Q1");
            date(e, "P569", "+1900-01-01T00:00:00Z", 11);
            assertNothing(e);
        }

        @ParameterizedTest
        @ValueSource(strings = {"property", "lexeme"})
        void nonItemEntityExcluded(String type) {
            ObjectNode e = newEntity();
            e.put("type", type);
            coords(e, 1, 2);
            date(e, "P571", "+1900-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void missingIdExcluded() {
            ObjectNode e = newEntity();
            e.remove("id");
            coords(e, 1, 2);
            date(e, "P571", "+1900-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void malformedIdExcluded() {
            ObjectNode e = newEntity();
            e.put("id", 42);
            coords(e, 1, 2);
            date(e, "P571", "+1900-00-00T00:00:00Z", 9);
            assertNothing(e);
        }

        @Test
        void nullAndNonObjectInputsGiveEmpty() {
            assertEquals(Optional.empty(), EXTRACTOR.extract(null));
            assertEquals(Optional.empty(), EXTRACTOR.extract(MAPPER.createArrayNode()));
            assertEquals(Optional.empty(), EXTRACTOR.extract(MAPPER.getNodeFactory().textNode("Q1")));
            assertEquals(Optional.empty(), EXTRACTOR.extract(MAPPER.createObjectNode()));
        }

        @Test
        void dateWithoutPlaceNotEmitted() {
            ObjectNode e = newEntity();
            date(e, "P569", "+1900-01-01T00:00:00Z", 11);
            date(e, "P570", "+1950-01-01T00:00:00Z", 11);
            place(e, "P20", "Q5");
            assertKinds(extracted(e), Kind.DIED);
        }

        @Test
        void placeWithoutDateNotEmitted() {
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            place(e, "P20", "Q5");
            date(e, "P570", "+1950-01-01T00:00:00Z", 11);
            assertKinds(extracted(e), Kind.DIED);
        }

        @Test
        void personPlacesDoNotFallBackToCoordinatesOrAdminArea() {
            ObjectNode e = newEntity();
            coords(e, 1, 2);
            place(e, "P131", "Q3");
            date(e, "P569", "+1900-01-01T00:00:00Z", 11);
            assertNothing(e);
        }

        @Test
        void openedDoesNotUseHeadquarters() {
            ObjectNode e = newEntity();
            place(e, "P159", "Q4");
            date(e, "P1619", "+1900-01-01T00:00:00Z", 11);
            date(e, "P571", "+1890-01-01T00:00:00Z", 11);
            var item = extracted(e);
            assertKinds(item, Kind.FOUNDED);
            assertAt(event(item, Kind.FOUNDED), "Q4");
        }

        @Test
        void endedUsesHeadquartersBeforeAdminArea() {
            ObjectNode e = newEntity();
            place(e, "P131", "Q3");
            place(e, "P159", "Q4");
            date(e, "P576", "+1900-01-01T00:00:00Z", 11);
            assertAt(event(extracted(e), Kind.ENDED), "Q4");
        }

        @Test
        void somevalueDateSkipped() {
            ObjectNode e = newEntity();
            place(e, "P19", "Q1");
            ObjectNode claim = e.withObject("claims").withArray("P569").addObject();
            claim.put("rank", "preferred");
            claim.putObject("mainsnak").put("snaktype", "somevalue").put("property", "P569");
            date(e, "P569", "+1900-01-01T00:00:00Z", 11);
            assertDay(event(extracted(e), Kind.BORN).when(), 1900, 1, 1);
        }

        @Test
        void allKindsInKindOrderAndOnlyRangeHasEndDate() {
            ObjectNode e = newEntity();
            date(e, "P1619", "+1905-01-01T00:00:00Z", 11);
            date(e, "P576", "+1904-01-01T00:00:00Z", 11);
            date(e, "P571", "+1903-01-01T00:00:00Z", 11);
            place(e, "P20", "Q5");
            date(e, "P570", "+1902-01-01T00:00:00Z", 11);
            place(e, "P19", "Q1");
            date(e, "P569", "+1901-01-01T00:00:00Z", 11);
            date(e, "P582", "+1900-12-31T00:00:00Z", 11);
            date(e, "P580", "+1900-01-01T00:00:00Z", 11);
            coords(e, 1, 2);
            var item = extracted(e);
            assertKinds(item, Kind.HAPPENED, Kind.BORN, Kind.DIED, Kind.FOUNDED, Kind.ENDED, Kind.OPENED);
            assertDay(event(item, Kind.HAPPENED).endDate(), 1900, 12, 31);
            item.events().stream().filter(ev -> ev.kind() != Kind.HAPPENED)
                .forEach(ev -> assertNull(ev.endDate()));
        }

        @Test
        void extractedItemCarriesItemFacts() {
            ObjectNode e = newEntity();
            place(e, "P31", "Q839954");
            place(e, "P31", "Q5");
            coords(e, 1, 2);
            date(e, "P571", "+1900-00-00T00:00:00Z", 9);
            var item = extracted(e);
            assertEquals("Q999", item.itemId());
            assertEquals("Test item", item.label());
            assertEquals(1, item.importance());
            assertEquals(List.of("Q839954", "Q5"), item.instanceOf());
        }

        /** Works of art without own Earth coordinates only have where they're kept today: no events. */
        @Nested
        class Artwork {

            private static ObjectNode typed(String type) {
                ObjectNode e = newEntity();
                place(e, "P31", type);
                return e;
            }

            private static void malformedCoords(ObjectNode e) {
                ObjectNode bad = MAPPER.createObjectNode();
                bad.put("latitude", 500.0);
                bad.put("longitude", 10.0);
                bad.put("precision", 0.0001);
                bad.put("globe", PlaceValues.EARTH);
                add(e, "P625", "normal", "globecoordinate", bad);
            }

            // Q20201150 (The Cloisters Apocalypse): illuminated manuscript kept in New York City.
            @Test
            void illuminatedManuscriptWithOnlyAdminAreaProducesNothing() {
                ObjectNode e = typed("Q48498");
                date(e, "P571", "+1330-00-00T00:00:00Z", 9);
                place(e, "P131", "Q60");
                assertNothing(e);
            }

            @Test
            void paintingWithOnlyLocationProducesNothing() {
                ObjectNode e = typed("Q3305213");
                date(e, "P571", "+1503-00-00T00:00:00Z", 9);
                date(e, "P585", "+1503-00-00T00:00:00Z", 9);
                place(e, "P276", "Q19675");
                place(e, "P131", "Q90");
                assertNothing(e);
            }

            @ParameterizedTest
            @ValueSource(strings = {"Q3305213", "Q87167", "Q48498", "Q93184", "Q184296", "Q220659", "Q179700",
                "Q245117", "Q178743", "Q191851", "Q1066288"})
            void eachArtworkRootWithoutCoordinatesProducesNothing(String type) {
                ObjectNode e = typed(type);
                date(e, "P571", "+1900-01-01T00:00:00Z", 11);
                place(e, "P131", "Q60");
                assertNothing(e);
            }

            // The art type need not be the first P31 value.
            @Test
            void artworkTypeAmongOthersProducesNothing() {
                ObjectNode e = typed("Q16970");
                place(e, "P31", "Q179700");
                date(e, "P571", "+1900-01-01T00:00:00Z", 11);
                place(e, "P131", "Q60");
                assertNothing(e);
            }

            // Every kind is dropped, not just Founded.
            @Test
            void artworkWithoutCoordinatesDropsHappenedOpenedAndEndedToo() {
                ObjectNode e = typed("Q179700");
                date(e, "P585", "+1900-01-01T00:00:00Z", 11);
                date(e, "P1619", "+1901-01-01T00:00:00Z", 11);
                date(e, "P576", "+1950-01-01T00:00:00Z", 11);
                place(e, "P276", "Q1");
                place(e, "P131", "Q60");
                assertNothing(e);
            }

            @Test
            void artworkWithOnlyMalformedCoordinatesProducesNothing() {
                ObjectNode e = typed("Q3305213");
                malformedCoords(e);
                date(e, "P571", "+1900-01-01T00:00:00Z", 11);
                place(e, "P131", "Q1");
                assertNothing(e);
            }

            @Test
            void artworkWithOnlyDeprecatedEarthCoordinatesProducesNothing() {
                ObjectNode e = typed("Q179700");
                add(e, "P625", "deprecated", "globecoordinate", coordinate(40.69, -74.04, 0.0001));
                date(e, "P571", "+1886-10-28T00:00:00Z", 11);
                place(e, "P131", "Q60");
                assertNothing(e);
            }

            // Statue of Liberty style: an outdoor statue with its own coordinates keeps its events.
            @Test
            void statueWithOwnCoordinatesKeepsFounded() {
                ObjectNode e = typed("Q179700");
                coords(e, 40.69, -74.04);
                date(e, "P571", "+1886-10-28T00:00:00Z", 11);
                place(e, "P131", "Q60");
                var item = extracted(e);
                assertKinds(item, Kind.FOUNDED);
                Coordinates c = assertOwnCoords(event(item, Kind.FOUNDED));
                assertEquals(40.69, c.latitude(), 1e-9);
                assertEquals(-74.04, c.longitude(), 1e-9);
            }

            // A malformed value beside a usable Earth one still counts as having own coordinates.
            @Test
            void artworkWithMalformedThenUsableCoordinatesKeepsEvents() {
                ObjectNode e = typed("Q178743");
                malformedCoords(e);
                coords(e, 30, 31);
                date(e, "P571", "-0196-00-00T00:00:00Z", 9);
                var item = extracted(e);
                assertKinds(item, Kind.FOUNDED);
                assertEquals(30, assertOwnCoords(event(item, Kind.FOUNDED)).latitude(), 1e-9);
            }

            @Test
            void artworkWithMoonAndEarthCoordinatesKeepsEvents() {
                ObjectNode e = typed("Q179700");
                coords(e, 10, 10, MOON);
                coords(e, 20, 30);
                date(e, "P571", "+1900-01-01T00:00:00Z", 11);
                assertKinds(extracted(e), Kind.FOUNDED);
            }

            @Test
            void nonArtBuildingWithoutCoordinatesKeepsFounded() {
                ObjectNode e = typed("Q16970");
                date(e, "P571", "+1330-00-00T00:00:00Z", 9);
                place(e, "P131", "Q60");
                var item = extracted(e);
                assertKinds(item, Kind.FOUNDED);
                assertAt(event(item, Kind.FOUNDED), "Q60");
            }

            // A photograph's date and place usually say where and when it was taken: a real event.
            @Test
            void photographWithoutCoordinatesKeepsHappened() {
                ObjectNode e = typed("Q125191");
                date(e, "P585", "+1945-02-23T00:00:00Z", 11);
                place(e, "P276", "Q1");
                place(e, "P131", "Q3");
                var item = extracted(e);
                assertKinds(item, Kind.HAPPENED);
                assertAt(event(item, Kind.HAPPENED), "Q1");
            }

            // Mummies sit under archaeological artifact but are people: birth and death are real events.
            @Test
            void mummyWithoutCoordinatesKeepsBornAndDied() {
                ObjectNode e = typed("Q43616");
                date(e, "P569", "-1341-00-00T00:00:00Z", 9);
                place(e, "P19", "Q1");
                date(e, "P570", "-1323-00-00T00:00:00Z", 9);
                place(e, "P20", "Q2");
                var item = extracted(e);
                assertKinds(item, Kind.BORN, Kind.DIED);
                assertAt(event(item, Kind.BORN), "Q1");
                assertAt(event(item, Kind.DIED), "Q2");
            }

            @Test
            void humanMummyWithoutCoordinatesKeepsBornAndDied() {
                ObjectNode e = typed("Q5");
                place(e, "P31", "Q43616");
                date(e, "P569", "-1341-00-00T00:00:00Z", 9);
                place(e, "P19", "Q1");
                date(e, "P570", "-1323-00-00T00:00:00Z", 9);
                place(e, "P20", "Q2");
                assertKinds(extracted(e), Kind.BORN, Kind.DIED);
            }

            // Birth and death places are places in a life, not where the work is kept today.
            @Test
            void paintingTypedPersonWithoutCoordinatesKeepsBornAndDiedOnly() {
                ObjectNode e = typed("Q5");
                place(e, "P31", "Q3305213");
                date(e, "P569", "+1370-00-00T00:00:00Z", 9);
                place(e, "P19", "Q1");
                date(e, "P570", "+1443-01-16T00:00:00Z", 11);
                place(e, "P20", "Q2");
                date(e, "P571", "+1453-00-00T00:00:00Z", 9);
                place(e, "P131", "Q3");
                var item = extracted(e);
                assertKinds(item, Kind.BORN, Kind.DIED);
                assertAt(event(item, Kind.BORN), "Q1");
                assertAt(event(item, Kind.DIED), "Q2");
            }

            // Loschbour woman (Q25583326): human remains typed under archaeological artifact.
            @Test
            void archaeologicalArtifactWithDeathKeepsDied() {
                ObjectNode e = typed("Q220659");
                date(e, "P570", "-6000-00-00T00:00:00Z", 9);
                place(e, "P20", "Q2");
                var item = extracted(e);
                assertKinds(item, Kind.DIED);
                assertAt(event(item, Kind.DIED), "Q2");
            }

            // Only P31 counts: an art class elsewhere (e.g. P279 on a class item) is not a type.
            @Test
            void artClassOutsideInstanceOfDoesNotExclude() {
                ObjectNode e = typed("Q16970");
                place(e, "P279", "Q179700");
                date(e, "P571", "+1330-00-00T00:00:00Z", 9);
                place(e, "P131", "Q60");
                assertKinds(extracted(e), Kind.FOUNDED);
            }
        }

        /** P582 certainly earlier than P580 (compared at the coarser precision) is an unusable end. */
        @Nested
        class ReversedRange {

            private static ObjectNode range(String start, int startPrecision, String end, int endPrecision) {
                ObjectNode e = newEntity();
                coords(e, 1, 2);
                date(e, "P580", start, startPrecision);
                date(e, "P582", end, endPrecision);
                return e;
            }

            @Test
            void reversedDayEndFallsBackToStartAlone() {
                // Q101208683: start 2015-04-27, end 2013-04-29.
                Event h = happened(range("+2015-04-27T00:00:00Z", 11, "+2013-04-29T00:00:00Z", 11));
                assertDay(h.when(), 2015, 4, 27);
                assertNull(h.endDate());
            }

            @Test
            void reversedYearEndAgainstDayStartFallsBackToStartAlone() {
                // Q11516855: start 1899-07-05, end 1890 (year).
                Event h = happened(range("+1899-07-05T00:00:00Z", 11, "+1890-00-00T00:00:00Z", 9));
                assertDay(h.when(), 1899, 7, 5);
                assertNull(h.endDate());
            }

            @Test
            void reversedDayEndAgainstMonthStartFallsBackToStartAlone() {
                // Q118902351: start 1878-07 (month), end 1877-08-07.
                Event h = happened(range("+1878-07-00T00:00:00Z", 10, "+1877-08-07T00:00:00Z", 11));
                assertDate(h.when(), 1878, 7, 0, Precision.MONTH, Calendar.GREGORIAN);
                assertNull(h.endDate());
            }

            @Test
            void reversedEndFallsBackToPointInTime() {
                ObjectNode e = range("+1900-05-01T00:00:00Z", 11, "+1900-04-30T00:00:00Z", 11);
                date(e, "P585", "+1900-06-01T00:00:00Z", 11);
                Event h = happened(e);
                assertDay(h.when(), 1900, 6, 1);
                assertNull(h.endDate());
            }

            @Test
            void earlierMonthSameYearIsReversed() {
                Event h = happened(range("+1900-05-00T00:00:00Z", 10, "+1900-04-00T00:00:00Z", 10));
                assertNull(h.endDate());
            }

            @Test
            void earlierDaySameMonthIsReversed() {
                Event h = happened(range("+1900-05-02T00:00:00Z", 11, "+1900-05-01T00:00:00Z", 11));
                assertNull(h.endDate());
            }

            @Test
            void yearEndContainingDayStartIsKept() {
                Event h = happened(range("+2015-04-27T00:00:00Z", 11, "+2015-00-00T00:00:00Z", 9));
                assertDay(h.when(), 2015, 4, 27);
                assertEquals(2015, h.endDate().year());
                assertEquals(Precision.YEAR, h.endDate().precision());
            }

            @Test
            void earlierDayInsideSameMonthAsMonthStartIsKept() {
                Event h = happened(range("+1900-05-00T00:00:00Z", 10, "+1900-05-01T00:00:00Z", 11));
                assertDay(h.endDate(), 1900, 5, 1);
            }

            @Test
            void dayEndInsideSameYearAsYearStartIsKept() {
                Event h = happened(range("+1900-00-00T00:00:00Z", 9, "+1900-01-01T00:00:00Z", 11));
                assertDay(h.endDate(), 1900, 1, 1);
            }

            @Test
            void sameDayStartAndEndIsKept() {
                Event h = happened(range("+1900-05-01T00:00:00Z", 11, "+1900-05-01T00:00:00Z", 11));
                assertDay(h.when(), 1900, 5, 1);
                assertDay(h.endDate(), 1900, 5, 1);
            }

            @Test
            void forwardBcRangeIsKept() {
                Event h = happened(range("-0500-00-00T00:00:00Z", 9, "-0400-00-00T00:00:00Z", 9));
                assertEquals(-500, h.when().year());
                assertEquals(-400, h.endDate().year());
            }

            @Test
            void reversedBcRangeFallsBackToStartAlone() {
                Event h = happened(range("-0400-00-00T00:00:00Z", 9, "-0500-00-00T00:00:00Z", 9));
                assertEquals(-400, h.when().year());
                assertNull(h.endDate());
            }

            @Test
            void calendarIsIgnoredInComparison() {
                // Julian 1900-01-01 is Gregorian 1900-01-14, but the rule compares fields as written.
                ObjectNode e = newEntity();
                coords(e, 1, 2);
                date(e, "P580", "+1900-01-05T00:00:00Z", 11);
                add(e, "P582", "normal", "time", time("+1900-01-01T00:00:00Z", 11, TimeValues.JULIAN));
                assertNull(happened(e).endDate());
            }

            @Test
            void julianEndSameFieldsAsGregorianStartIsKept() {
                ObjectNode e = newEntity();
                coords(e, 1, 2);
                date(e, "P580", "+1900-01-05T00:00:00Z", 11);
                add(e, "P582", "normal", "time", time("+1900-01-05T00:00:00Z", 11, TimeValues.JULIAN));
                assertDayJ(happened(e).endDate(), 1900, 1, 5);
            }

            @Test
            void decadeEndOverlappingYearStartIsKept() {
                Event h = happened(range("+1905-00-00T00:00:00Z", 9, "+1900-00-00T00:00:00Z", 8));
                assertEquals(Precision.DECADE, h.endDate().precision());
            }

            @Test
            void decadeEndBeforeYearStartIsReversed() {
                Event h = happened(range("+1915-00-00T00:00:00Z", 9, "+1900-00-00T00:00:00Z", 8));
                assertNull(h.endDate());
            }

            @Test
            void centuryEndBeforeYearStartIsReversed() {
                Event h = happened(range("+1950-00-00T00:00:00Z", 9, "+1800-00-00T00:00:00Z", 7));
                assertEquals(1950, h.when().year());
                assertNull(h.endDate());
            }

            @Test
            void centuryEndOverlappingYearStartIsKept() {
                Event h = happened(range("+1850-00-00T00:00:00Z", 9, "+1900-00-00T00:00:00Z", 7));
                assertEquals(Precision.CENTURY, h.endDate().precision());
            }

            @Test
            void reversedPreferredEndFallsBackToUsableNormalEnd() {
                ObjectNode e = newEntity();
                coords(e, 1, 2);
                date(e, "P580", "+1900-05-01T00:00:00Z", 11);
                date(e, "P582", "preferred", "+1899-01-01T00:00:00Z", 11);
                date(e, "P582", "normal", "+1901-01-01T00:00:00Z", 11);
                Event h = happened(e);
                assertDay(h.when(), 1900, 5, 1);
                assertDay(h.endDate(), 1901, 1, 1);
            }

            @Test
            void reversedEarliestNormalEndFallsBackToLaterNormalEnd() {
                ObjectNode e = newEntity();
                coords(e, 1, 2);
                date(e, "P580", "+1900-05-01T00:00:00Z", 11);
                date(e, "P582", "+1901-01-01T00:00:00Z", 11);
                date(e, "P582", "+1899-01-01T00:00:00Z", 11);
                assertDay(happened(e).endDate(), 1901, 1, 1);
            }
        }
    }

    @Nested
    class EventConstruction {

        private final HistoricDate date = new HistoricDate(1900, 1, 1, Precision.DAY, Calendar.GREGORIAN);
        private final PlaceRef place = new PlaceRef("Q1");

        @Test
        void nullKindRejected() {
            assertThrows(NullPointerException.class, () -> new Event(null, date, null, place));
        }

        @Test
        void nullWhenRejected() {
            assertThrows(NullPointerException.class, () -> new Event(Kind.BORN, null, null, place));
        }

        @Test
        void nullWhereRejected() {
            assertThrows(NullPointerException.class, () -> new Event(Kind.BORN, date, null, null));
        }

        @Test
        void nullEndDateAllowed() {
            assertNull(new Event(Kind.BORN, date, null, place).endDate());
        }
    }
}
