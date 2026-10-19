package io.github.luisr96.whathappenedhere.api.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@link TimelineCells#rebuild()}: per-level cell keys and counts, from rows inserted directly. */
class TimelineCellsTest extends PostgisIntegrationTest {

    @Autowired
    TimelineCells cells;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
    }

    private void insert(String item, String kind, int importance, long earliest, double lon, double lat) {
        jdbc.update("""
                INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar, earliest_year, latest_year,
                    place_qid, geom, coord_precision_deg)
                VALUES (?, ?, ?, 'label', ?, ?, 0, 0, 'YEAR', 'GREGORIAN', NULL, NULL, NULL, NULL, NULL, ?, ?,
                    NULL, ST_SetSRID(ST_MakePoint(?, ?), 4326), 0.0001)
                """,
                item + ":" + kind, item, kind, importance, earliest, earliest, earliest, lon, lat);
    }

    private void insertBorderCases() {
        insert("Q1", "HAPPENED", 7, 1885, 0.3, 0.25);
        insert("Q2", "BORN", 3, -44, -0.05, -0.1);
        insert("Q3", "DIED", 0, -1, 180, 90);
        insert("Q4", "FOUNDED", 300, 9, -180, -90);
        insert("Q5", "ENDED", 1, 1950, 12.34, 41.9);
        insert("Q6", "OPENED", 1, 2000, -73.99, 40.75);
        insert("Q7", "HAPPENED", 7, 1889, 0.31, 0.29);
    }

    private Map<String, Object> cellOf(int level, String kind, int importance) {
        return jdbc.queryForMap("""
                SELECT cell_x, cell_y, decade, n FROM timeline_cells
                WHERE level = ? AND kind = ? AND importance = ?
                """, level, kind, importance);
    }

    private long sumN(int level) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(n), 0) FROM timeline_cells WHERE level = ?", Long.class, level);
    }

    @ParameterizedTest(name = "level {0}")
    @ValueSource(ints = {0, 1, 2})
    void eachLevelCountsEveryEventOnce(int level) {
        insertBorderCases();
        cells.rebuild();
        assertThat(sumN(level)).isEqualTo(7);
    }

    @Test
    void onlyLevelsZeroToTwoExist() {
        insertBorderCases();
        cells.rebuild();
        assertThat(jdbc.queryForList("SELECT DISTINCT level FROM timeline_cells ORDER BY level", Integer.class))
                .containsExactly(0, 1, 2);
    }

    @Test
    void sameKeyIsAggregatedIntoOneRow() {
        insertBorderCases();
        cells.rebuild();
        // Q1 and Q7 share kind, importance, decade 1880 and the 0.1-degree cell (3, 2).
        var row = cellOf(2, "HAPPENED", 7);
        assertThat(((Number) row.get("cell_x")).intValue()).isEqualTo(3);
        assertThat(((Number) row.get("cell_y")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("decade")).longValue()).isEqualTo(1880);
        assertThat(((Number) row.get("n")).intValue()).isEqualTo(2);
    }

    /** Numeric division: 0.3 / 0.1 is exactly 3 (in double it is 2.9999999999999996, floor 2). */
    @ParameterizedTest(name = "{1} at level {0} -> ({3}, {4}), decade {5}")
    @CsvSource({
        "0, HAPPENED, 7, 0, 0, 1880",
        "1, HAPPENED, 7, 0, 0, 1880",
        "2, HAPPENED, 7, 3, 2, 1880",
        "0, BORN, 3, -1, -1, -50",
        "1, BORN, 3, -1, -1, -50",
        "2, BORN, 3, -1, -1, -50",
        "0, DIED, 0, 18, 9, -10",
        "1, DIED, 0, 180, 90, -10",
        "2, DIED, 0, 1800, 900, -10",
        "0, FOUNDED, 300, -18, -9, 0",
        "1, FOUNDED, 300, -180, -90, 0",
        "2, FOUNDED, 300, -1800, -900, 0",
        "0, ENDED, 1, 1, 4, 1950",
        "1, ENDED, 1, 12, 41, 1950",
        "2, ENDED, 1, 123, 419, 1950",
        "0, OPENED, 1, -8, 4, 2000",
        "1, OPENED, 1, -74, 40, 2000",
        "2, OPENED, 1, -740, 407, 2000",
    })
    void cellKeyFloorsLonLatAndEarliestYear(int level, String kind, int importance,
            int cellX, int cellY, long decade) {
        insertBorderCases();
        cells.rebuild();
        var row = cellOf(level, kind, importance);
        assertThat(((Number) row.get("cell_x")).intValue()).isEqualTo(cellX);
        assertThat(((Number) row.get("cell_y")).intValue()).isEqualTo(cellY);
        assertThat(((Number) row.get("decade")).longValue()).isEqualTo(decade);
    }

    @Test
    void rebuildTwiceDoesNotDoubleCounts() {
        insertBorderCases();
        cells.rebuild();
        cells.rebuild();
        assertThat(sumN(0)).isEqualTo(7);
        assertThat(sumN(1)).isEqualTo(7);
        assertThat(sumN(2)).isEqualTo(7);
    }

    @Test
    void rebuildReflectsInsertsSinceLastRebuild() {
        insertBorderCases();
        cells.rebuild();
        insert("Q8", "BORN", 3, -44, -0.05, -0.1);
        cells.rebuild();
        assertThat(cellOf(2, "BORN", 3).get("n")).isEqualTo(2);
        assertThat(sumN(1)).isEqualTo(8);
    }

    @Test
    void rebuildAfterEventsDeletedEmptiesTable() {
        insertBorderCases();
        cells.rebuild();
        jdbc.update("DELETE FROM events");
        cells.rebuild();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timeline_cells", Integer.class)).isZero();
    }
}
