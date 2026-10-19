package io.github.luisr96.whathappenedhere.api.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fixtures in {@code src/test/resources/load/}. {@code items.jsonl} has six events: Q1 HAPPENED
 * (own coords, 1914-1918 range, importance 50), Q2 BORN (decade, ref Q90) and DIED (-44 century,
 * ref Q597; null label, importance 20), Q3 FOUNDED (own coords, null precision) and ENDED
 * (unresolved ref Q405; importance 5), Q4 OPENED (unresolved ref Q404, importance 30).
 * {@code places.jsonl} has four lines: Q90, Q597 (null precision), unused Q999, and Q90 again.
 */
class SampleLoaderTest extends PostgisIntegrationTest {

    private static final double EPS = 1e-9;

    @Autowired
    SampleLoader loader;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
    }

    @Test
    void loadWithoutCutoffCountsEverything() {
        assertThat(loadMain(0)).isEqualTo(new LoadResult(6, 0, 2, 4, 4, 2));
        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:HAPPENED", "Q2:BORN", "Q2:DIED", "Q3:FOUNDED");
    }

    @Test
    void cutoffDropsLowImportanceItemsBeforeResolution() {
        // Q3:ENDED is both below cutoff and unresolved; it counts only as below cutoff.
        assertThat(loadMain(10)).isEqualTo(new LoadResult(6, 2, 1, 3, 4, 2));
        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:HAPPENED", "Q2:BORN", "Q2:DIED");
    }

    @Test
    void cutoffEqualToImportanceKeepsItem() {
        assertThat(loadMain(5).belowCutoff()).isZero();
        assertThat(eventIds()).contains("Q3:FOUNDED");
    }

    @Test
    void ownCoordinatesEventWithEndDateRange() {
        loadMain(0);
        var row = event("Q1:HAPPENED");
        assertThat(row.get("item_id")).isEqualTo("Q1");
        assertThat(row.get("kind")).isEqualTo("HAPPENED");
        assertThat(row.get("label")).isEqualTo("Great War");
        assertThat(row.get("importance")).isEqualTo(50);
        assertThat(row.get("year")).isEqualTo(1914L);
        assertThat(((Number) row.get("month")).intValue()).isEqualTo(7);
        assertThat(((Number) row.get("day")).intValue()).isEqualTo(28);
        assertThat(row.get("precision")).isEqualTo("DAY");
        assertThat(row.get("calendar")).isEqualTo("GREGORIAN");
        assertThat(row.get("end_year")).isEqualTo(1918L);
        assertThat(((Number) row.get("end_month")).intValue()).isEqualTo(11);
        assertThat(((Number) row.get("end_day")).intValue()).isEqualTo(11);
        assertThat(row.get("end_precision")).isEqualTo("DAY");
        assertThat(row.get("end_calendar")).isEqualTo("GREGORIAN");
        assertThat(row.get("earliest_year")).isEqualTo(1914L);
        assertThat(row.get("latest_year")).isEqualTo(1918L);
        assertThat(row.get("place_qid")).isNull();
        assertThat((Double) row.get("lon")).isCloseTo(2.25, within(EPS));
        assertThat((Double) row.get("lat")).isCloseTo(48.5, within(EPS));
        assertThat((Double) row.get("coord_precision_deg")).isCloseTo(0.0001, within(EPS));
        assertThat(row.get("srid")).isEqualTo(4326);
    }

    @Test
    void ownCoordinatesWithNullPrecisionStayNull() {
        loadMain(0);
        var row = event("Q3:FOUNDED");
        assertThat(row.get("place_qid")).isNull();
        assertThat((Double) row.get("lon")).isCloseTo(-20.25, within(EPS));
        assertThat((Double) row.get("lat")).isCloseTo(-10.5, within(EPS));
        assertThat(row.get("coord_precision_deg")).isNull();
        assertThat(row.get("earliest_year")).isEqualTo(1500L);
        assertThat(row.get("latest_year")).isEqualTo(1500L);
    }

    @Test
    void placeReferenceCopiesGeomAndPrecisionFromPlace() {
        loadMain(0);
        var row = event("Q2:BORN");
        assertThat(row.get("place_qid")).isEqualTo("Q90");
        assertThat((Double) row.get("lon")).isCloseTo(2.3522, within(EPS));
        assertThat((Double) row.get("lat")).isCloseTo(48.8566, within(EPS));
        assertThat((Double) row.get("coord_precision_deg")).isCloseTo(0.0001, within(EPS));
    }

    @Test
    void placeReferenceToPlaceWithNullPrecisionCopiesNull() {
        loadMain(0);
        var row = event("Q2:DIED");
        assertThat(row.get("place_qid")).isEqualTo("Q597");
        assertThat((Double) row.get("lon")).isCloseTo(12.5, within(EPS));
        assertThat((Double) row.get("lat")).isCloseTo(41.9, within(EPS));
        assertThat(row.get("coord_precision_deg")).isNull();
    }

    @Test
    void decadeDateSpansTheDecade() {
        loadMain(0);
        var row = event("Q2:BORN");
        assertThat(row.get("year")).isEqualTo(1885L);
        assertThat(((Number) row.get("month")).intValue()).isEqualTo(0);
        assertThat(((Number) row.get("day")).intValue()).isEqualTo(0);
        assertThat(row.get("precision")).isEqualTo("DECADE");
        assertThat(row.get("earliest_year")).isEqualTo(1880L);
        assertThat(row.get("latest_year")).isEqualTo(1889L);
        assertThat(row.get("end_year")).isNull();
        assertThat(row.get("end_month")).isNull();
        assertThat(row.get("end_day")).isNull();
        assertThat(row.get("end_precision")).isNull();
        assertThat(row.get("end_calendar")).isNull();
    }

    @Test
    void bcCenturyDateSpansTheCenturyWithJulianCalendar() {
        loadMain(0);
        var row = event("Q2:DIED");
        assertThat(row.get("year")).isEqualTo(-44L);
        assertThat(row.get("precision")).isEqualTo("CENTURY");
        assertThat(row.get("calendar")).isEqualTo("JULIAN");
        assertThat(row.get("earliest_year")).isEqualTo(-100L);
        assertThat(row.get("latest_year")).isEqualTo(-1L);
    }

    @Test
    void nullLabelIsStoredAsNull() {
        loadMain(0);
        assertThat(event("Q2:BORN").get("label")).isNull();
        assertThat(event("Q2:BORN").get("importance")).isEqualTo(20);
    }

    @Test
    void itemWithTwoEventsGetsOneRowPerKind() {
        loadMain(0);
        assertThat(jdbc.queryForList("SELECT id FROM events WHERE item_id = 'Q2'", String.class))
                .containsExactlyInAnyOrder("Q2:BORN", "Q2:DIED");
    }

    @Test
    void unresolvedPlaceReferencesAreDropped() {
        loadMain(0);
        assertThat(eventIds()).doesNotContain("Q4:OPENED", "Q3:ENDED");
    }

    @Test
    void onlyReferencedPlacesAreKeptAndDuplicateQidLoadsOnce() {
        loadMain(0);
        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class))
                .containsExactlyInAnyOrder("Q90", "Q597");
        var rome = jdbc.queryForMap(
                "SELECT ST_X(geom) AS lon, ST_Y(geom) AS lat, coord_precision_deg FROM places WHERE qid = 'Q597'");
        assertThat((Double) rome.get("lon")).isCloseTo(12.5, within(EPS));
        assertThat((Double) rome.get("lat")).isCloseTo(41.9, within(EPS));
        assertThat(rome.get("coord_precision_deg")).isNull();
        var paris = jdbc.queryForMap("SELECT coord_precision_deg FROM places WHERE qid = 'Q90'");
        assertThat((Double) paris.get("coord_precision_deg")).isCloseTo(0.0001, within(EPS));
    }

    @Test
    void secondLoadReplacesFirst() {
        loadMain(0);
        var result = loader.load(resource("items-second.jsonl"), resource("places-second.jsonl"), 0);
        assertThat(result).isEqualTo(new LoadResult(1, 0, 0, 1, 1, 0));
        assertThat(eventIds()).containsExactly("Q5:HAPPENED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM places", Integer.class)).isZero();
        var row = event("Q5:HAPPENED");
        assertThat((Double) row.get("coord_precision_deg")).isCloseTo(1.0, within(EPS));
    }

    @Test
    void malformedLineFailsAndKeepsPreviousContents() {
        loadMain(0);
        assertThatThrownBy(() -> loader.load(resource("items-malformed.jsonl"), resource("places-second.jsonl"), 0))
                .isInstanceOf(RuntimeException.class);
        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:HAPPENED", "Q2:BORN", "Q2:DIED", "Q3:FOUNDED");
        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class))
                .containsExactlyInAnyOrder("Q90", "Q597");
    }

    @Test
    void textRoundTripsExactlyThroughCopy() {
        loader.load(resource("items-text.jsonl"), resource("places-second.jsonl"), 0);
        assertThat(event("Q11:HAPPENED").get("label")).isEqualTo("Paris, France");
        assertThat(event("Q12:HAPPENED").get("label")).isEqualTo("The \"Big\" One");
        assertThat(event("Q13:HAPPENED").get("label")).isEqualTo("line1\nline2");
        assertThat(event("Q14:HAPPENED").get("label")).isEqualTo("back\\slash \\N");
        assertThat(event("Q15:HAPPENED").get("label")).isEqualTo("");
        assertThat(event("Q16:HAPPENED").get("label")).isNull();
    }

    @Test
    void wikiTitleIsStoredOnEveryEventOfTheItem() {
        loader.load(resource("items-wiki.jsonl"), resource("places-second.jsonl"), 0);
        assertThat(event("Q21:FOUNDED").get("wiki_title")).isEqualTo("Café \"Le Dôme\", Paris");
        assertThat(event("Q21:ENDED").get("wiki_title")).isEqualTo("Café \"Le Dôme\", Paris");
    }

    @Test
    void absentWikiTitleFieldLoadsAsNull() {
        loader.load(resource("items-wiki.jsonl"), resource("places-second.jsonl"), 0);
        assertThat(event("Q22:HAPPENED")).containsEntry("wiki_title", null);
    }

    @Test
    void jsonNullWikiTitleLoadsAsNull() {
        loader.load(resource("items-wiki.jsonl"), resource("places-second.jsonl"), 0);
        assertThat(event("Q23:HAPPENED")).containsEntry("wiki_title", null);
    }

    @Test
    void olderSampleWithoutWikiTitleStillLoads() {
        loadMain(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE wiki_title IS NOT NULL", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "items-missing-longitude.jsonl, places-second.jsonl",
        "items-missing-latitude.jsonl, places-second.jsonl",
        "items-missing-importance.jsonl, places-second.jsonl",
        "items-missing-year.jsonl, places-second.jsonl",
        "items-place-ref.jsonl, places-missing-latitude.jsonl",
    })
    void missingRequiredFieldFailsAndKeepsPreviousContents(String items, String places) {
        loadMain(0);
        assertThatThrownBy(() -> loader.load(resource(items), resource(places), 0))
                .isInstanceOf(RuntimeException.class);
        assertMainContentsUnchanged();
    }

    @Test
    void negativeMinImportanceIsRejectedAndKeepsPreviousContents() {
        loadMain(0);
        assertThatThrownBy(() -> loader.load(resource("items-second.jsonl"), resource("places-second.jsonl"), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertMainContentsUnchanged();
    }

    @Test
    void duplicatePlaceQidKeepsMostPreciseCoordinates() {
        loader.load(resource("items-place-ref.jsonl"), resource("places-duplicate-precision.jsonl"), 0);
        var place = jdbc.queryForMap(
                "SELECT ST_X(geom) AS lon, ST_Y(geom) AS lat, coord_precision_deg FROM places WHERE qid = 'Q800'");
        assertThat((Double) place.get("lon")).isCloseTo(11.0, within(EPS));
        assertThat((Double) place.get("lat")).isCloseTo(11.0, within(EPS));
        assertThat((Double) place.get("coord_precision_deg")).isCloseTo(0.001, within(EPS));
        var row = event("Q21:BORN");
        assertThat((Double) row.get("lon")).isCloseTo(11.0, within(EPS));
        assertThat((Double) row.get("lat")).isCloseTo(11.0, within(EPS));
        assertThat((Double) row.get("coord_precision_deg")).isCloseTo(0.001, within(EPS));
    }

    @Test
    void loadRebuildsTimelineCells() {
        var result = loadMain(0);
        for (int level = 0; level <= 2; level++) {
            assertThat(timelineSum(level)).as("level %d", level).isEqualTo(result.eventsLoaded());
        }
    }

    @Test
    void secondLoadReplacesTimelineCells() {
        loadMain(0);
        loader.load(resource("items-second.jsonl"), resource("places-second.jsonl"), 0);
        assertThat(timelineSum(0)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT kind FROM timeline_cells", String.class))
                .containsExactly("HAPPENED");
    }

    @Test
    void failedLoadKeepsPreviousTimelineCells() {
        loadMain(0);
        assertThatThrownBy(() -> loader.load(resource("items-malformed.jsonl"), resource("places-second.jsonl"), 0))
                .isInstanceOf(RuntimeException.class);
        assertThat(timelineSum(0)).isEqualTo(4);
    }

    private long timelineSum(int level) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(n), 0) FROM timeline_cells WHERE level = ?", Long.class, level);
    }

    private void assertMainContentsUnchanged() {
        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:HAPPENED", "Q2:BORN", "Q2:DIED", "Q3:FOUNDED");
        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class))
                .containsExactlyInAnyOrder("Q90", "Q597");
    }

    private LoadResult loadMain(int minImportance) {
        return loader.load(resource("items.jsonl"), resource("places.jsonl"), minImportance);
    }

    private List<String> eventIds() {
        return jdbc.queryForList("SELECT id FROM events", String.class);
    }

    private Map<String, Object> event(String id) {
        return jdbc.queryForMap("""
                SELECT *, ST_X(geom) AS lon, ST_Y(geom) AS lat, ST_SRID(geom) AS srid
                FROM events WHERE id = ?
                """, id);
    }

    private static Path resource(String name) {
        try {
            return Path.of(SampleLoaderTest.class.getResource("/load/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
