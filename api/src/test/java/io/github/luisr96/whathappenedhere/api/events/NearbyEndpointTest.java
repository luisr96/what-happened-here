package io.github.luisr96.whathappenedhere.api.events;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /places/nearby against rows inserted directly. Events are YEAR precision Gregorian with own
 * exact coordinates and no end date.
 */
@AutoConfigureMockMvc
class NearbyEndpointTest extends PostgisIntegrationTest {

    private static final String ORIGIN_10KM = "lat=0&lon=0&radius=10000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

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

    private ResultActions nearby(String query) throws Exception {
        return mvc.perform(get("/places/nearby?" + query));
    }

    private void expectIds(String query, String... ids) throws Exception {
        var result = nearby(query).andExpect(status().isOk());
        if (ids.length == 0) {
            result.andExpect(jsonPath("$.events", empty()));
        } else {
            result.andExpect(jsonPath("$.events[*].id", containsInAnyOrder(ids)));
        }
    }

    @Test
    void radiusIsGeodesicMetresAlongEquator() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 0.0089, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0.0091, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 0, 0);
        expectIds("lat=0&lon=0&radius=1000", "Q1:HAPPENED", "Q3:HAPPENED");
    }

    @Test
    void radiusIsGeodesicMetresAlongMeridian() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 0, 0.0089);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0, -0.0092);
        expectIds("lat=0&lon=0&radius=1000", "Q1:HAPPENED");
    }

    @Test
    void longitudeDegreesShrinkAwayFromEquator() throws Exception {
        // At 60N a degree of longitude is ~55.8 km: 0.0089 deg is ~500 m, 0.019 deg ~1060 m.
        // A planar degree-based radius would get both wrong.
        insert("Q1", "HAPPENED", 1, 1900, 1900, 10.0089, 60);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 10.019, 60);
        expectIds("lat=60&lon=10&radius=1000", "Q1:HAPPENED");
    }

    @Test
    void worksAcrossTheAntimeridian() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, -179.9995, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, -179.98, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 179.9985, 0);
        expectIds("lat=0&lon=179.999&radius=1000", "Q1:HAPPENED", "Q3:HAPPENED");
    }

    @Test
    void bcYearOverlap() throws Exception {
        insert("Q1", "HAPPENED", 1, -100, -1, 0, 0);
        expectIds(ORIGIN_10KM + "&from=-50&to=-40", "Q1:HAPPENED");
        expectIds(ORIGIN_10KM + "&from=0");
        expectIds(ORIGIN_10KM + "&to=-101");
        expectIds(ORIGIN_10KM + "&from=-1&to=-1", "Q1:HAPPENED");
    }

    @Test
    void openEndedFromOrTo() throws Exception {
        insert("Q1", "HAPPENED", 1, 1500, 1500, 0, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0, 0);
        expectIds(ORIGIN_10KM + "&from=1600", "Q2:HAPPENED");
        expectIds(ORIGIN_10KM + "&to=1600", "Q1:HAPPENED");
        expectIds(ORIGIN_10KM, "Q1:HAPPENED", "Q2:HAPPENED");
    }

    @Test
    void kindsFilterIsCaseInsensitive() throws Exception {
        insert("Q1", "BORN", 1, 1900, 1900, 0, 0);
        insert("Q1", "DIED", 1, 1950, 1950, 0, 0);
        insert("Q2", "FOUNDED", 1, 1900, 1900, 0, 0);
        expectIds(ORIGIN_10KM + "&kinds=born,Died", "Q1:BORN", "Q1:DIED");
    }

    @Test
    void minImportanceIsInclusive() throws Exception {
        insert("Q1", "HAPPENED", 9, 1900, 1900, 0, 0);
        insert("Q2", "HAPPENED", 10, 1900, 1900, 0, 0);
        expectIds(ORIGIN_10KM + "&minImportance=10", "Q2:HAPPENED");
    }

    @Test
    void orderedByImportanceThenIdNotByDistance() throws Exception {
        insert("Q3", "HAPPENED", 5, 1900, 1900, 0, 0);
        insert("Q1", "HAPPENED", 5, 1900, 1900, 0.001, 0);
        insert("Q2", "HAPPENED", 99, 1900, 1900, 0.05, 0);
        insert("Q1", "BORN", 1, 1900, 1900, 0, 0);
        nearby(ORIGIN_10KM).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].id",
                        contains("Q2:HAPPENED", "Q1:HAPPENED", "Q3:HAPPENED", "Q1:BORN")));
    }

    @Test
    void limitTruncatesKeepingMostImportant() throws Exception {
        insert("Q1", "HAPPENED", 3, 1900, 1900, 0, 0);
        insert("Q2", "HAPPENED", 2, 1900, 1900, 0, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 0, 0);
        nearby(ORIGIN_10KM + "&limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].id", contains("Q1:HAPPENED", "Q2:HAPPENED")))
                .andExpect(jsonPath("$.truncated").value(true));
        nearby(ORIGIN_10KM + "&limit=3").andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void defaultLimitIsFiveHundred() throws Exception {
        for (int i = 0; i < 501; i++) {
            insert("Q" + i, "HAPPENED", 1, 1900, 1900, 0, 0);
        }
        nearby(ORIGIN_10KM).andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(500)))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void emptyResultIsNotTruncated() throws Exception {
        nearby(ORIGIN_10KM).andExpect(status().isOk())
                .andExpect(jsonPath("$.events", empty()))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void eventHasSameShapeAsEventsEndpoint() throws Exception {
        insert("Q1", "HAPPENED", 7, 1900, 1900, 0.001, 0.002);
        nearby(ORIGIN_10KM).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].id").value("Q1:HAPPENED"))
                .andExpect(jsonPath("$.events[0].itemId").value("Q1"))
                .andExpect(jsonPath("$.events[0].kind").value("HAPPENED"))
                .andExpect(jsonPath("$.events[0].label").value("label Q1"))
                .andExpect(jsonPath("$.events[0].importance").value(7))
                .andExpect(jsonPath("$.events[0].when.year").value(1900))
                .andExpect(jsonPath("$.events[0].when.precision").value("YEAR"))
                .andExpect(jsonPath("$.events[0].when.calendar").value("GREGORIAN"))
                .andExpect(jsonPath("$.events[0].end").value(nullValue()))
                .andExpect(jsonPath("$.events[0].earliestYear").value(1900))
                .andExpect(jsonPath("$.events[0].latestYear").value(1900))
                .andExpect(jsonPath("$.events[0].longitude").value(0.001))
                .andExpect(jsonPath("$.events[0].latitude").value(0.002))
                .andExpect(jsonPath("$.events[0].coordPrecisionDeg").value(0.0001))
                .andExpect(jsonPath("$.events[0].approximate").value(false))
                .andExpect(jsonPath("$.events[0].placeQid").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "lon=0&radius=1000",
        "lat=0&radius=1000",
        "lat=0&lon=0",
        "lat=90.5&lon=0&radius=1000",
        "lat=-91&lon=0&radius=1000",
        "lat=0&lon=180.5&radius=1000",
        "lat=0&lon=-181&radius=1000",
        "lat=NaN&lon=0&radius=1000",
        "lat=0&lon=Infinity&radius=1000",
        "lat=x&lon=0&radius=1000",
        "lat=0&lon=0&radius=0",
        "lat=0&lon=0&radius=-5",
        "lat=0&lon=0&radius=500001",
        "lat=0&lon=0&radius=1.5",
        "lat=0&lon=0&radius=abc",
        "lat=0&lon=0&radius=1000&from=10&to=5",
        "lat=0&lon=0&radius=1000&from=abc",
        "lat=0&lon=0&radius=1000&kinds=BORN,WEDDING",
        "lat=0&lon=0&radius=1000&kinds=born,,died",
        "lat=0&lon=0&radius=1000&minImportance=-1",
        "lat=0&lon=0&radius=1000&limit=0",
        "lat=0&lon=0&radius=1000&limit=2001",
        "lat=0&lon=0&radius=1000&limit=x",
    })
    void badRequestsAreProblemDetails(String query) throws Exception {
        nearby(query).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "lat=90&lon=180&radius=1",
        "lat=-90&lon=-180&radius=500000",
        "lat=0&lon=0&radius=1000&limit=1",
        "lat=0&lon=0&radius=1000&limit=2000",
        "lat=0&lon=0&radius=1000&minImportance=0",
        "lat=0&lon=0&radius=1000&from=5&to=5",
    })
    void boundaryValuesAccepted(String query) throws Exception {
        nearby(query).andExpect(status().isOk());
    }
}
