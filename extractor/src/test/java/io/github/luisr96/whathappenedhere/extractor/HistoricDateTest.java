package io.github.luisr96.whathappenedhere.extractor;

import static io.github.luisr96.whathappenedhere.extractor.TimeValues.JULIAN;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.MAPPER;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.firstTimeValue;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.luisr96.whathappenedhere.extractor.HistoricDate.Calendar;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate.Precision;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class HistoricDateTest {

    private static HistoricDate parseOk(JsonNode value) {
        HistoricDate date = HistoricDate.parse(value);
        assertNotNull(date, () -> "expected a date for " + value);
        return date;
    }

    @Nested
    class PrecisionMapping {

        @ParameterizedTest(name = "precision {0} -> {1}")
        @CsvSource({
            "6, MILLENNIUM", "7, CENTURY", "8, DECADE", "9, YEAR", "10, MONTH", "11, DAY",
            "12, DAY", "13, DAY", "14, DAY"
        })
        void mapsWikidataPrecision(int wikidata, Precision expected) {
            assertEquals(expected, parseOk(time("+1756-01-27T00:00:00Z", wikidata)).precision());
        }

        @Test
        void subDayPrecisionTruncatesToTheDate() {
            HistoricDate date = parseOk(time("+1756-01-27T14:30:15Z", 14));
            assertAll(
                () -> assertEquals(Precision.DAY, date.precision()),
                () -> assertEquals(1756, date.year()),
                () -> assertEquals(1, date.month()),
                () -> assertEquals(27, date.day()));
        }

        @ParameterizedTest(name = "precision {0} is coarser than millennium")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5})
        void coarserThanMillenniumIsNull(int precision) {
            assertAll(
                () -> assertNull(HistoricDate.parse(time("-13798000000-00-00T00:00:00Z", precision))),
                () -> assertNull(HistoricDate.parse(time("+1756-00-00T00:00:00Z", precision))));
        }

        @ParameterizedTest(name = "precision {0} is out of range")
        @ValueSource(ints = {-1, 15, 99})
        void outOfRangePrecisionIsNull(int precision) {
            assertNull(HistoricDate.parse(time("+1756-01-27T00:00:00Z", precision)));
        }
    }

    @Nested
    class Years {

        @Test
        void bcYearIsNegativeWithHistoricalNumbering() {
            assertEquals(-44, parseOk(time("-0044-03-15T00:00:00Z", 11, JULIAN)).year());
        }

        @ParameterizedTest
        @ValueSource(strings = {"+0000-00-00T00:00:00Z", "-0000-00-00T00:00:00Z"})
        void yearZeroIsNull(String raw) {
            assertNull(HistoricDate.parse(time(raw, 9)));
        }

        @Test
        void fiveDigitBcYearParsesAtMillennium() {
            HistoricDate date = parseOk(time("-10000-00-00T00:00:00Z", 6));
            assertAll(
                () -> assertEquals(-10_000L, date.year()),
                () -> assertEquals(Precision.MILLENNIUM, date.precision()));
        }

        @Test
        void yearBeyondIntRangeParsesAsLong() {
            assertEquals(-13_798_000_000L, parseOk(time("-13798000000-00-00T00:00:00Z", 6)).year());
        }
    }

    @Nested
    class ClearingBelowPrecision {

        @ParameterizedTest(name = "precision {0} -> month {1}, day {2}")
        @CsvSource({"6, 0, 0", "7, 0, 0", "8, 0, 0", "9, 0, 0", "10, 1, 0", "11, 1, 27"})
        void fieldsBelowPrecisionAreZero(int precision, int month, int day) {
            HistoricDate date = parseOk(time("+1756-01-27T00:00:00Z", precision));
            assertAll(
                () -> assertEquals(1756, date.year()),
                () -> assertEquals(month, date.month()),
                () -> assertEquals(day, date.day()));
        }
    }

    @Nested
    class BrokenValues {

        @ParameterizedTest(name = "{0} at precision {1}")
        @CsvSource({
            "+1887-00-00T00:00:00Z, 11",
            "+1887-05-00T00:00:00Z, 11",
            "+1887-00-12T00:00:00Z, 11",
            "+1887-00-00T00:00:00Z, 10",
            "+1887-00-00T00:00:00Z, 12"
        })
        void moreClaimedPrecisionThanCarriedIsNull(String raw, int precision) {
            assertNull(HistoricDate.parse(time(raw, precision)));
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
            "", "garbage", "1756-01-27T00:00:00Z", "+17x6-01-27T00:00:00Z",
            "+1756-13-01T00:00:00Z", "+1756-01-32T00:00:00Z", "+1756-01"
        })
        void malformedTimeStringIsNull(String raw) {
            assertNull(HistoricDate.parse(time(raw, 11)));
        }

        @Test
        void missingTimeIsNull() {
            ObjectNode value = time("+1756-01-27T00:00:00Z", 11);
            value.remove("time");
            assertNull(HistoricDate.parse(value));
        }

        @Test
        void missingPrecisionIsNull() {
            ObjectNode value = time("+1756-01-27T00:00:00Z", 11);
            value.remove("precision");
            assertNull(HistoricDate.parse(value));
        }

        @Test
        void nonNumericPrecisionIsNull() {
            ObjectNode value = time("+1756-01-27T00:00:00Z", 11);
            value.put("precision", "day");
            assertNull(HistoricDate.parse(value));
        }

        @Test
        void nonObjectOrEmptyInputIsNull() {
            assertAll(
                () -> assertNull(HistoricDate.parse(MAPPER.createObjectNode())),
                () -> assertNull(HistoricDate.parse(MAPPER.getNodeFactory().stringNode("+1756"))),
                () -> assertNull(HistoricDate.parse(MAPPER.getNodeFactory().nullNode())),
                () -> assertNull(HistoricDate.parse(MAPPER.getNodeFactory().missingNode())));
        }
    }

    @Nested
    class Calendars {

        @Test
        void gregorianModel() {
            assertEquals(Calendar.GREGORIAN, parseOk(time("+1756-01-27T00:00:00Z", 11)).calendar());
        }

        @Test
        void julianModelKeepsDateAsWritten() {
            HistoricDate date = parseOk(time("+1564-04-23T00:00:00Z", 11, JULIAN));
            assertAll(
                () -> assertEquals(Calendar.JULIAN, date.calendar()),
                () -> assertEquals(1564, date.year()),
                () -> assertEquals(4, date.month()),
                () -> assertEquals(23, date.day()));
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", "http://www.wikidata.org/entity/Q12138", "Q1985727"})
        void unknownCalendarModelIsNull(String model) {
            assertNull(HistoricDate.parse(time("+1756-01-27T00:00:00Z", 11, model)));
        }

        @Test
        void missingCalendarModelIsNull() {
            ObjectNode value = time("+1756-01-27T00:00:00Z", 11);
            value.remove("calendarmodel");
            assertNull(HistoricDate.parse(value));
        }

        @Test
        void beforeAndAfterAreIgnored() {
            ObjectNode value = time("+1756-01-27T00:00:00Z", 11);
            value.put("before", 5);
            value.put("after", 3);
            HistoricDate date = parseOk(value);
            assertAll(
                () -> assertEquals(1756, date.earliestYear()),
                () -> assertEquals(1756, date.latestYear()));
        }
    }

    @Nested
    class YearRange {

        @ParameterizedTest(name = "{0} at precision {1} -> {2}..{3}")
        @CsvSource({
            "+1756-01-27T00:00:00Z, 11, 1756, 1756",
            "+1756-01-00T00:00:00Z, 10, 1756, 1756",
            "+1756-00-00T00:00:00Z, 9, 1756, 1756",
            "-0044-00-00T00:00:00Z, 9, -44, -44",
            "+1885-00-00T00:00:00Z, 8, 1880, 1889",
            "+1880-00-00T00:00:00Z, 8, 1880, 1889",
            "+0005-00-00T00:00:00Z, 8, 1, 9",
            "-0044-00-00T00:00:00Z, 8, -49, -40",
            "-0005-00-00T00:00:00Z, 8, -9, -1",
            "+1801-00-00T00:00:00Z, 7, 1801, 1900",
            "+1900-00-00T00:00:00Z, 7, 1801, 1900",
            "+1901-00-00T00:00:00Z, 7, 1901, 2000",
            "-0044-00-00T00:00:00Z, 7, -100, -1",
            "-0150-00-00T00:00:00Z, 7, -200, -101",
            "-0100-00-00T00:00:00Z, 7, -100, -1",
            "+2000-00-00T00:00:00Z, 6, 1001, 2000",
            "+1001-00-00T00:00:00Z, 6, 1001, 2000",
            "-0500-00-00T00:00:00Z, 6, -1000, -1"
        })
        void rangeIsThePeriodContainingTheStatedYear(String raw, int precision, long earliest, long latest) {
            HistoricDate date = parseOk(time(raw, precision));
            assertAll(
                () -> assertEquals(earliest, date.earliestYear()),
                () -> assertEquals(latest, date.latestYear()));
        }
    }

    @Nested
    class SavedEntities {

        private void assertDate(JsonNode value, long year, int month, int day,
                Precision precision, Calendar calendar) {
            HistoricDate date = parseOk(value);
            assertAll(
                () -> assertEquals(year, date.year()),
                () -> assertEquals(month, date.month()),
                () -> assertEquals(day, date.day()),
                () -> assertEquals(precision, date.precision()),
                () -> assertEquals(calendar, date.calendar()));
        }

        @Test
        void romeFoundingIsJulianBcDay() {
            assertDate(firstTimeValue("Q220", "P571"), -753, 4, 21, Precision.DAY, Calendar.JULIAN);
        }

        @Test
        void lisbonEarthquakeIsGregorianDay() {
            assertDate(firstTimeValue("Q191055", "P585"), 1755, 11, 1, Precision.DAY, Calendar.GREGORIAN);
        }

        @Test
        void mozartBirthIsGregorianDay() {
            assertDate(firstTimeValue("Q254", "P569"), 1756, 1, 27, Precision.DAY, Calendar.GREGORIAN);
        }

        @Test
        void shakespeareBirthIsJulianMonth() {
            // Q692's day-precision P569 values are deprecated; the normal one is April 1564.
            assertDate(firstTimeValue("Q692", "P569"), 1564, 4, 0, Precision.MONTH, Calendar.JULIAN);
        }
    }
}
