package io.github.luisr96.whathappenedhere.extractor;

import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.coordinate;
import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.item;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.JULIAN;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.MAPPER;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.entity;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.time;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class StatementsTest {

    private static ObjectNode statement(String property, String rank, String datatype, JsonNode value) {
        ObjectNode statement = MAPPER.createObjectNode();
        ObjectNode mainsnak = statement.putObject("mainsnak");
        mainsnak.put("snaktype", "value");
        mainsnak.put("property", property);
        ObjectNode datavalue = mainsnak.putObject("datavalue");
        datavalue.set("value", value);
        datavalue.put("type", datatype);
        statement.put("type", "statement");
        if (rank != null) {
            statement.put("rank", rank);
        }
        return statement;
    }

    private static ObjectNode dateStatement(String property, String rank, JsonNode timeValue) {
        return statement(property, rank, "time", timeValue);
    }

    private static ObjectNode itemStatement(String property, String rank, String qid) {
        return statement(property, rank, "wikibase-entityid", item(qid));
    }

    private static ObjectNode withEndTime(ObjectNode statement) {
        ObjectNode qualifier = MAPPER.createObjectNode();
        qualifier.put("snaktype", "value");
        qualifier.put("property", "P582");
        ObjectNode datavalue = qualifier.putObject("datavalue");
        datavalue.set("value", time("+1900-00-00T00:00:00Z", 9));
        datavalue.put("type", "time");
        statement.putObject("qualifiers").putArray("P582").add(qualifier);
        return statement;
    }

    private static ObjectNode withEndSnak(ObjectNode statement, String snaktype) {
        ObjectNode qualifier = MAPPER.createObjectNode();
        qualifier.put("snaktype", snaktype);
        qualifier.put("property", "P582");
        statement.putObject("qualifiers").putArray("P582").add(qualifier);
        return statement;
    }

    private static ObjectNode snakWithoutValue(String property, String rank, String snaktype) {
        ObjectNode statement = MAPPER.createObjectNode();
        ObjectNode mainsnak = statement.putObject("mainsnak");
        mainsnak.put("snaktype", snaktype);
        mainsnak.put("property", property);
        statement.put("type", "statement");
        statement.put("rank", rank);
        return statement;
    }

    private static ObjectNode claims(String property, JsonNode... statements) {
        ObjectNode claims = MAPPER.createObjectNode();
        ArrayNode array = claims.putArray(property);
        for (JsonNode statement : statements) {
            array.add(statement);
        }
        return claims;
    }

    private static void assertDate(long year, int month, int day, HistoricDate.Precision precision,
            HistoricDate actual) {
        assertNotNull(actual);
        assertEquals(year, actual.year());
        assertEquals(month, actual.month());
        assertEquals(day, actual.day());
        assertEquals(precision, actual.precision());
    }

    @Nested
    class SavedEntities {

        @Test
        void mozartBirthDate() {
            assertDate(1756, 1, 27, HistoricDate.Precision.DAY,
                    Statements.bestDate(entity("Q254").path("claims"), "P569"));
        }

        @Test
        void mozartDeathPlacePrefersPreferredRank() {
            Event.PlaceRef place = Statements.bestPlace(entity("Q254").path("claims"), "P20", Event.PlaceRef::parse);
            assertNotNull(place);
            assertEquals("Q1741", place.qid());
        }

        @Test
        void eiffelTowerOpeningTakesEarliestOfTwoNormalValues() {
            assertDate(1889, 3, 31, HistoricDate.Precision.DAY,
                    Statements.bestDate(entity("Q243").path("claims"), "P1619"));
        }

        @Test
        void shakespeareBirthIgnoresDeprecatedDayPrecisionValues() {
            HistoricDate date = Statements.bestDate(entity("Q692").path("claims"), "P569");
            assertDate(1564, 4, 0, HistoricDate.Precision.MONTH, date);
            assertEquals(HistoricDate.Calendar.JULIAN, date.calendar());
        }

        @Test
        void onlyDeprecatedValuesGiveNull() {
            assertNull(Statements.bestDate(entity("synthetic-deprecated-only").path("claims"), "P569"));
        }

        @Test
        void earlierDeprecatedValueDoesNotWinTieBreak() {
            assertDate(1756, 1, 27, HistoricDate.Precision.DAY,
                    Statements.bestDate(entity("synthetic-deprecated-earlier").path("claims"), "P569"));
        }

        @Test
        void preferredBeatsEarlierNormalListedFirst() {
            assertDate(1756, 1, 27, HistoricDate.Precision.DAY,
                    Statements.bestDate(entity("synthetic-two-birth-ranks").path("claims"), "P569"));
        }

        @Test
        void tychoOffEarthCoordinatesGiveNull() {
            assertNull(Statements.bestPlace(entity("Q631696").path("claims"), "P625", Event.Coordinates::parse));
        }
    }

    @Nested
    class Dates {

        @Test
        void earliestWinsRegardlessOfStatementOrder() {
            ObjectNode later = dateStatement("P569", "normal", time("+1800-05-02T00:00:00Z", 11));
            ObjectNode earlier = dateStatement("P569", "normal", time("+1800-05-01T00:00:00Z", 11));
            assertDate(1800, 5, 1, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", later, earlier), "P569"));
            assertDate(1800, 5, 1, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", earlier, later), "P569"));
        }

        @Test
        void tieBreakComparesMonthWhenYearsEqual() {
            ObjectNode march = dateStatement("P569", "normal", time("+1800-03-30T00:00:00Z", 11));
            ObjectNode february = dateStatement("P569", "normal", time("+1800-02-28T00:00:00Z", 11));
            assertDate(1800, 2, 28, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", march, february), "P569"));
        }

        @Test
        void bcDateIsEarlierThanAdDate() {
            ObjectNode ad = dateStatement("P569", "normal", time("+0010-00-00T00:00:00Z", 9));
            ObjectNode bc = dateStatement("P569", "normal", time("-0044-00-00T00:00:00Z", 9));
            HistoricDate date = Statements.bestDate(claims("P569", ad, bc), "P569");
            assertNotNull(date);
            assertEquals(-44, date.year());
        }

        @Test
        void tieBreakUsesEarliestYearForCoarsePrecision() {
            // The 19th century (stored as 1900, precision 7) starts in 1801, before 1850.
            ObjectNode year = dateStatement("P569", "normal", time("+1850-00-00T00:00:00Z", 9));
            ObjectNode century = dateStatement("P569", "normal", time("+1900-00-00T00:00:00Z", 7));
            HistoricDate date = Statements.bestDate(claims("P569", year, century), "P569");
            assertNotNull(date);
            assertEquals(HistoricDate.Precision.CENTURY, date.precision());
        }

        @Test
        void preferredWinsOverEarlierNormal() {
            ObjectNode normal = dateStatement("P569", "normal", time("+1700-01-01T00:00:00Z", 11));
            ObjectNode preferred = dateStatement("P569", "preferred", time("+1750-01-01T00:00:00Z", 11));
            assertDate(1750, 1, 1, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", normal, preferred), "P569"));
        }

        @Test
        void brokenPreferredFallsBackToNormal() {
            ObjectNode broken = dateStatement("P569", "preferred", time("+1887-00-00T00:00:00Z", 11));
            ObjectNode normal = dateStatement("P569", "normal", time("+1890-06-15T00:00:00Z", 11));
            assertDate(1890, 6, 15, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", broken, normal), "P569"));
        }

        @Test
        void brokenNormalValueSkippedForAnotherNormal() {
            ObjectNode broken = dateStatement("P569", "normal", time("+1700-00-00T00:00:00Z", 11));
            ObjectNode valid = dateStatement("P569", "normal", time("+1890-06-15T00:00:00Z", 11));
            assertDate(1890, 6, 15, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", broken, valid), "P569"));
        }

        @Test
        void brokenPreferredNeverFallsBackToDeprecated() {
            ObjectNode broken = dateStatement("P569", "preferred", time("+1887-00-00T00:00:00Z", 11));
            ObjectNode deprecated = dateStatement("P569", "deprecated", time("+1890-06-15T00:00:00Z", 11));
            assertNull(Statements.bestDate(claims("P569", broken, deprecated), "P569"));
        }

        @Test
        void somevalueAndNovalueAreSkipped() {
            ObjectNode somevalue = snakWithoutValue("P569", "preferred", "somevalue");
            ObjectNode novalue = snakWithoutValue("P569", "preferred", "novalue");
            ObjectNode valid = dateStatement("P569", "normal", time("+1890-06-15T00:00:00Z", 11));
            assertDate(1890, 6, 15, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", somevalue, novalue, valid), "P569"));
        }

        @Test
        void onlySomevalueGivesNull() {
            assertNull(Statements.bestDate(claims("P569", snakWithoutValue("P569", "normal", "somevalue")), "P569"));
        }

        @Test
        void statementWithValueSnaktypeButNoDatavalueIsSkipped() {
            ObjectNode noDatavalue = snakWithoutValue("P569", "preferred", "value");
            ObjectNode valid = dateStatement("P569", "normal", time("+1890-06-15T00:00:00Z", 11));
            assertDate(1890, 6, 15, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", noDatavalue, valid), "P569"));
        }

        @Test
        void keepsJulianCalendar() {
            ObjectNode julian = dateStatement("P569", "normal", time("+1564-04-26T00:00:00Z", 11, JULIAN));
            HistoricDate date = Statements.bestDate(claims("P569", julian), "P569");
            assertNotNull(date);
            assertEquals(HistoricDate.Calendar.JULIAN, date.calendar());
        }
    }

    @Nested
    class Places {

        @Test
        void valueWithoutEndTimeBeatsEarlierListedValueWithEndTime() {
            ObjectNode claims = claims("P131",
                    withEndTime(itemStatement("P131", "normal", "Q1")),
                    itemStatement("P131", "normal", "Q2"));
            assertEquals("Q2", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void firstInStatementOrderWinsWhenNoneHasEndTime() {
            ObjectNode claims = claims("P131",
                    itemStatement("P131", "normal", "Q1"),
                    itemStatement("P131", "normal", "Q2"));
            assertEquals("Q1", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void firstInStatementOrderWinsWhenAllHaveEndTime() {
            ObjectNode claims = claims("P131",
                    withEndTime(itemStatement("P131", "normal", "Q1")),
                    withEndTime(itemStatement("P131", "normal", "Q2")));
            assertEquals("Q1", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void novalueEndTimeMeansNotEnded() {
            ObjectNode claims = claims("P131",
                    withEndSnak(itemStatement("P131", "normal", "Q1"), "novalue"),
                    itemStatement("P131", "normal", "Q2"));
            assertEquals("Q1", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void somevalueEndTimeMeansEnded() {
            ObjectNode claims = claims("P131",
                    withEndSnak(itemStatement("P131", "normal", "Q1"), "somevalue"),
                    itemStatement("P131", "normal", "Q2"));
            assertEquals("Q2", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void novalueEndTimeBeatsValueEndTime() {
            ObjectNode claims = claims("P131",
                    withEndSnak(itemStatement("P131", "normal", "Q1"), "novalue"),
                    withEndTime(itemStatement("P131", "normal", "Q2")));
            assertEquals("Q1", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void novalueEndTimeBeatsEarlierListedValueEndTime() {
            ObjectNode claims = claims("P131",
                    withEndTime(itemStatement("P131", "normal", "Q2")),
                    withEndSnak(itemStatement("P131", "normal", "Q1"), "novalue"));
            assertEquals("Q1", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void preferredWithEndTimeBeatsNormalWithout() {
            ObjectNode claims = claims("P131",
                    itemStatement("P131", "normal", "Q1"),
                    withEndTime(itemStatement("P131", "preferred", "Q2")));
            assertEquals("Q2", Statements.bestPlace(claims, "P131", Event.PlaceRef::parse).qid());
        }

        @Test
        void deprecatedPlaceNeverUsed() {
            ObjectNode claims = claims("P19", itemStatement("P19", "deprecated", "Q1"));
            assertNull(Statements.bestPlace(claims, "P19", Event.PlaceRef::parse));
        }

        @Test
        void offEarthPreferredFallsBackToNormalCoordinates() {
            ObjectNode moon = coordinate(10, 20, 0.1);
            moon.put("globe", PlaceValues.MOON);
            ObjectNode claims = claims("P625",
                    statement("P625", "preferred", "globecoordinate", moon),
                    statement("P625", "normal", "globecoordinate", coordinate(38.7, -9.1, 1)));
            Event.Coordinates coordinates = Statements.bestPlace(claims, "P625", Event.Coordinates::parse);
            assertNotNull(coordinates);
            assertEquals(38.7, coordinates.latitude());
        }

        @Test
        void parserReceivesDatavalueValueObject() {
            ObjectNode claims = claims("P19", itemStatement("P19", "normal", "Q90"));
            JsonNode seen = Statements.bestPlace(claims, "P19", value -> value);
            assertNotNull(seen);
            assertEquals("Q90", seen.path("id").asString());
        }

        @Test
        void novaluePlaceSkipped() {
            ObjectNode claims = claims("P19",
                    snakWithoutValue("P19", "preferred", "novalue"),
                    itemStatement("P19", "normal", "Q90"));
            assertEquals("Q90", Statements.bestPlace(claims, "P19", Event.PlaceRef::parse).qid());
        }
    }

    @Nested
    class MissingOrMalformed {

        @Test
        void missingPropertyGivesNull() {
            ObjectNode claims = claims("P570", dateStatement("P570", "normal", time("+1890-06-15T00:00:00Z", 11)));
            assertNull(Statements.bestDate(claims, "P569"));
            assertNull(Statements.bestPlace(claims, "P19", Event.PlaceRef::parse));
        }

        @Test
        void emptyArrayGivesNull() {
            assertNull(Statements.bestDate(claims("P569"), "P569"));
        }

        @Test
        void nullClaimsGiveNull() {
            assertNull(Statements.bestDate(null, "P569"));
            assertNull(Statements.bestPlace(null, "P19", Event.PlaceRef::parse));
        }

        @Test
        void missingClaimsNodeGivesNull() {
            JsonNode missing = MAPPER.createObjectNode().path("claims");
            assertNull(Statements.bestDate(missing, "P569"));
            assertNull(Statements.bestPlace(missing, "P19", Event.PlaceRef::parse));
        }

        @Test
        void nonObjectClaimsGiveNull() {
            assertNull(Statements.bestDate(MAPPER.createArrayNode(), "P569"));
            assertNull(Statements.bestDate(MAPPER.readTree("\"x\""), "P569"));
        }

        @Test
        void propertyNotAnArrayGivesNull() {
            ObjectNode claims = MAPPER.createObjectNode();
            claims.put("P569", "oops");
            assertDoesNotThrow(() -> assertNull(Statements.bestDate(claims, "P569")));
        }

        @Test
        void statementWithoutMainsnakIsSkipped() {
            ObjectNode broken = MAPPER.createObjectNode();
            broken.put("rank", "preferred");
            ObjectNode valid = dateStatement("P569", "normal", time("+1890-06-15T00:00:00Z", 11));
            assertDate(1890, 6, 15, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", broken, valid), "P569"));
        }

        @Test
        void statementWithMissingOrUnknownRankIsSkipped() {
            ObjectNode noRank = dateStatement("P569", null, time("+1700-01-01T00:00:00Z", 11));
            ObjectNode oddRank = dateStatement("P569", "weird", time("+1600-01-01T00:00:00Z", 11));
            ObjectNode valid = dateStatement("P569", "normal", time("+1890-06-15T00:00:00Z", 11));
            assertDate(1890, 6, 15, HistoricDate.Precision.DAY,
                    Statements.bestDate(claims("P569", noRank, oddRank, valid), "P569"));
        }
    }
}
