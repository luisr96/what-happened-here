package io.github.luisr96.whathappenedhere.api.events;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /stats/timeline against rows inserted directly, with {@link TimelineCells} rebuilt before
 * each request. Boxes at least 1 degree in both directions read the pre-aggregated cells; smaller
 * boxes count events exactly.
 */
@AutoConfigureMockMvc
class TimelineEndpointTest extends PostgisIntegrationTest {

    private static final String WORLD = "bbox=-180,-90,180,90";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TimelineCells cells;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
    }

    private void insert(String item, String kind, int importance, long earliest, long latest, double lon, double lat) {
        jdbc.update("""
                INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar, earliest_year, latest_year,
                    place_qid, geom, coord_precision_deg)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, 'YEAR', 'GREGORIAN', NULL, NULL, NULL, NULL, NULL, ?, ?,
                    NULL, ST_SetSRID(ST_MakePoint(?, ?), 4326), 0.0001)
                """,
                item + ":" + kind, item, kind, "label " + item, importance, earliest,
                earliest, latest, lon, lat);
    }

    private ResultActions timeline(String query) throws Exception {
        cells.rebuild();
        return mvc.perform(get("/stats/timeline?" + query));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "1885, 1880", "1880, 1880", "1889, 1880",
        "-44, -50", "-1, -10", "-10, -10", "-11, -20",
        "1, 0", "9, 0", "0, 0",
    })
    void decadeIsFloorOfEarliestYear(long earliest, long decade) throws Exception {
        insert("Q1", "HAPPENED", 1, earliest, earliest + 50, 0, 0);
        timeline(WORLD).andExpect(status().isOk())
                .andExpect(jsonPath("$.decades", hasSize(1)))
                .andExpect(jsonPath("$.decades[0].decade").value(decade))
                .andExpect(jsonPath("$.decades[0].count").value(1));
    }

    @Test
    void countsPerDecadeAscendingOnlyNonEmpty() throws Exception {
        insert("Q1", "HAPPENED", 1, 1885, 1885, 0, 0);
        insert("Q2", "HAPPENED", 1, 1880, 1918, 0, 0);
        insert("Q3", "HAPPENED", 1, 1950, 1950, 0, 0);
        insert("Q4", "HAPPENED", 1, -44, -44, 0, 0);
        insert("Q5", "HAPPENED", 1, -1, -1, 0, 0);
        insert("Q6", "HAPPENED", 1, -10, -10, 0, 0);
        timeline(WORLD).andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].decade", contains(-50, -10, 1880, 1950)))
                .andExpect(jsonPath("$.decades[*].count", contains(1, 2, 2, 1)));
    }

    @Test
    void emptyWhenNothingMatches() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 50, 50);
        timeline("bbox=0,0,10,10").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades", empty()));
    }

    /** Under 1 degree wide: no cell size gives 10 cells across, so events are counted exactly. */
    @Test
    void smallBboxFilterIsExactAndInclusive() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 0.25, 0.25);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0.5, 0.5);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 0.5000001, 0.25);
        insert("Q4", "HAPPENED", 1, 1700, 1700, 0.55, 0.55);
        timeline("bbox=0,0,0.5,0.5").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].decade", contains(1900)))
                .andExpect(jsonPath("$.decades[*].count", contains(2)));
    }

    /** Wide but under 1 degree high still takes the exact path. */
    @Test
    void wideButShortBboxIsExact() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 50, 0.5);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 50, 0.95);
        timeline("bbox=0,0,100,0.9").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].count", contains(1)));
    }

    /** 10 x 10 degrees uses 1-degree cells, so a point in a cell touching the edge counts. */
    @Test
    void largeBboxCountsWholeTouchingCells() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 5, 5);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 10.5, 10.5);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 11, 5);
        insert("Q4", "HAPPENED", 1, 1900, 1900, -0.5, 5);
        timeline("bbox=0,0,10,10").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].count", contains(2)));
    }

    /**
     * 9.85 degrees wide is under 10 one-degree cells, so 0.1-degree cells 0..98 are used: 9.87
     * (cell 98, outside the exact box) counts, 9.95 (cell 99, but 1-degree cell 9) does not.
     */
    @Test
    void bboxJustUnderTenDegreesUsesTenthDegreeCells() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 9.87, 5);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 9.95, 5);
        timeline("bbox=0,0,9.85,9.9").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].count", contains(1)));
    }

    @Test
    void bboxCrossingAntimeridian() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 175, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, -175, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 180, 0);
        insert("Q4", "HAPPENED", 1, 1900, 1900, 0, 0);
        timeline("bbox=170,-10,-170,10").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].count", contains(3)));
    }

    @Test
    void kindsFilterIsCaseInsensitive() throws Exception {
        insert("Q1", "BORN", 1, 1900, 1900, 0, 0);
        insert("Q1", "DIED", 1, 1950, 1950, 0, 0);
        insert("Q2", "FOUNDED", 1, 1900, 1900, 0, 0);
        timeline(WORLD + "&kinds=born,Died").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].decade", contains(1900, 1950)))
                .andExpect(jsonPath("$.decades[*].count", contains(1, 1)));
    }

    @Test
    void minImportanceIsInclusive() throws Exception {
        insert("Q1", "HAPPENED", 9, 1900, 1900, 0, 0);
        insert("Q2", "HAPPENED", 10, 1900, 1900, 0, 0);
        insert("Q3", "HAPPENED", 11, 1900, 1900, 0, 0);
        timeline(WORLD + "&minImportance=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].count", contains(2)));
    }

    @Test
    void fromAndToAreIgnored() throws Exception {
        insert("Q1", "HAPPENED", 1, 1500, 1500, 0, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0, 0);
        timeline(WORLD + "&from=1800&to=1950").andExpect(status().isOk())
                .andExpect(jsonPath("$.decades[*].decade", contains(1500, 1900)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "bbox=",
        "bbox=1,2,3",
        "bbox=a,0,10,10",
        "bbox=-181,0,10,10",
        "bbox=0,-91,10,10",
        "bbox=0,20,10,10",
        "bbox=NaN,0,10,10",
        "bbox=-180,-90,180,90&kinds=BORN,WEDDING",
        "bbox=-180,-90,180,90&kinds=born,,died",
        "bbox=-180,-90,180,90&minImportance=-1",
        "bbox=-180,-90,180,90&minImportance=x",
    })
    void badRequestsAreProblemDetails(String query) throws Exception {
        timeline(query).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())));
    }
}
