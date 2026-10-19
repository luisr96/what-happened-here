package io.github.luisr96.whathappenedhere.api.events;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.emptyOrNullString;
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
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /events against rows inserted directly. Unless a test says otherwise, events are YEAR
 * precision Gregorian with own exact coordinates and no end date.
 */
@AutoConfigureMockMvc
class EventsEndpointTest extends PostgisIntegrationTest {

    private static final String WORLD = "bbox=-180,-90,180,90";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
    }

    /** End date of an event; null fields mean no end. */
    private record End(long year, int month, int day, String precision, String calendar) {
    }

    private void insert(String item, String kind, int importance, long earliest, long latest, double lon, double lat) {
        insertFull(item, kind, "label " + item, importance, earliest, 0, 0, "YEAR", "GREGORIAN",
                null, earliest, latest, null, lon, lat, 0.0001);
    }

    private void insertFull(String item, String kind, String label, int importance,
            long year, int month, int day, String precision, String calendar, End end,
            long earliest, long latest, String placeQid, double lon, double lat, Double coordPrecision) {
        jdbc.update("""
                INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar, earliest_year, latest_year,
                    place_qid, geom, coord_precision_deg)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                item + ":" + kind, item, kind, label, importance, year, month, day, precision, calendar,
                end == null ? null : end.year(), end == null ? null : end.month(),
                end == null ? null : end.day(), end == null ? null : end.precision(),
                end == null ? null : end.calendar(),
                earliest, latest, placeQid, lon, lat, coordPrecision);
    }

    private ResultActions events(String query) throws Exception {
        return mvc.perform(get("/events?" + query));
    }

    private void expectIds(String query, String... ids) throws Exception {
        var result = events(query).andExpect(status().isOk());
        if (ids.length == 0) {
            result.andExpect(jsonPath("$.events", empty()));
        } else {
            result.andExpect(jsonPath("$.events[*].id", containsInAnyOrder(ids)));
        }
    }

    @Test
    void bboxIncludesInsideAndEdgePointsAndExcludesOutside() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 5, 5);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 10, 10);
        insert("Q4", "HAPPENED", 1, 1900, 1900, 10, 0);
        insert("Q5", "HAPPENED", 1, 1900, 1900, 10.001, 5);
        insert("Q6", "HAPPENED", 1, 1900, 1900, 5, -0.001);
        expectIds("bbox=0,0,10,10", "Q1:HAPPENED", "Q2:HAPPENED", "Q3:HAPPENED", "Q4:HAPPENED");
    }

    @Test
    void bboxCrossingAntimeridian() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 175, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, -175, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 0, 0);
        insert("Q4", "HAPPENED", 1, 1900, 1900, 170, 10);
        insert("Q5", "HAPPENED", 1, 1900, 1900, 175, 20);
        expectIds("bbox=170,-10,-170,10", "Q1:HAPPENED", "Q2:HAPPENED", "Q4:HAPPENED");
    }

    @Test
    void bcRangeOverlap() throws Exception {
        insert("Q1", "HAPPENED", 1, -100, -1, 0, 0);
        expectIds(WORLD + "&from=-50&to=-40", "Q1:HAPPENED");
        expectIds(WORLD + "&from=0");
        expectIds(WORLD + "&to=-101");
        expectIds(WORLD + "&from=-1&to=-1", "Q1:HAPPENED");
        expectIds(WORLD + "&from=-100", "Q1:HAPPENED");
    }

    @Test
    void openEndedFromOrTo() throws Exception {
        insert("Q1", "HAPPENED", 1, 1500, 1500, 0, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 0, 0);
        expectIds(WORLD + "&from=1600", "Q2:HAPPENED");
        expectIds(WORLD + "&to=1600", "Q1:HAPPENED");
        expectIds(WORLD + "&from=1500", "Q1:HAPPENED", "Q2:HAPPENED");
        expectIds(WORLD + "&to=1900", "Q1:HAPPENED", "Q2:HAPPENED");
    }

    @Test
    void rangeEventMatchedByYearInsideIt() throws Exception {
        insert("Q1", "HAPPENED", 1, 1914, 1918, 0, 0);
        expectIds(WORLD + "&from=1916&to=1916", "Q1:HAPPENED");
        expectIds(WORLD + "&from=1910&to=1914", "Q1:HAPPENED");
        expectIds(WORLD + "&from=1919&to=1920");
    }

    @Test
    void kindsFilterIsCaseInsensitive() throws Exception {
        insert("Q1", "BORN", 1, 1900, 1900, 0, 0);
        insert("Q1", "DIED", 1, 1950, 1950, 0, 0);
        insert("Q2", "FOUNDED", 1, 1900, 1900, 0, 0);
        expectIds(WORLD + "&kinds=born,Died", "Q1:BORN", "Q1:DIED");
        expectIds(WORLD + "&kinds=FOUNDED", "Q2:FOUNDED");
        expectIds(WORLD, "Q1:BORN", "Q1:DIED", "Q2:FOUNDED");
    }

    @Test
    void minImportanceIsInclusive() throws Exception {
        insert("Q1", "HAPPENED", 9, 1900, 1900, 0, 0);
        insert("Q2", "HAPPENED", 10, 1900, 1900, 0, 0);
        insert("Q3", "HAPPENED", 0, 1900, 1900, 0, 0);
        expectIds(WORLD + "&minImportance=10", "Q2:HAPPENED");
        expectIds(WORLD, "Q1:HAPPENED", "Q2:HAPPENED", "Q3:HAPPENED");
    }

    @Test
    void orderedByImportanceThenId() throws Exception {
        insert("Q3", "HAPPENED", 5, 1900, 1900, 0, 0);
        insert("Q1", "HAPPENED", 5, 1900, 1900, 0, 0);
        insert("Q2", "HAPPENED", 99, 1900, 1900, 0, 0);
        insert("Q1", "BORN", 1, 1900, 1900, 0, 0);
        events(WORLD).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].id",
                        contains("Q2:HAPPENED", "Q1:HAPPENED", "Q3:HAPPENED", "Q1:BORN")));
    }

    @Test
    void limitTruncatesKeepingMostImportant() throws Exception {
        insert("Q1", "HAPPENED", 3, 1900, 1900, 0, 0);
        insert("Q2", "HAPPENED", 2, 1900, 1900, 0, 0);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 0, 0);
        events(WORLD + "&limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].id", contains("Q1:HAPPENED", "Q2:HAPPENED")))
                .andExpect(jsonPath("$.truncated").value(true));
        events(WORLD + "&limit=3").andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void emptyResultIsNotTruncated() throws Exception {
        events(WORLD).andExpect(status().isOk())
                .andExpect(jsonPath("$.events", empty()))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void fullShapeOfEventWithEndDate() throws Exception {
        insertFull("Q361", "HAPPENED", "World War I", 250, 1914, 7, 28, "DAY", "GREGORIAN",
                new End(1918, 11, 11, "DAY", "GREGORIAN"), 1914, 1918, null, 2.5, 48.75, 0.0001);
        events(WORLD).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].id").value("Q361:HAPPENED"))
                .andExpect(jsonPath("$.events[0].itemId").value("Q361"))
                .andExpect(jsonPath("$.events[0].kind").value("HAPPENED"))
                .andExpect(jsonPath("$.events[0].label").value("World War I"))
                .andExpect(jsonPath("$.events[0].importance").value(250))
                .andExpect(jsonPath("$.events[0].when.year").value(1914))
                .andExpect(jsonPath("$.events[0].when.month").value(7))
                .andExpect(jsonPath("$.events[0].when.day").value(28))
                .andExpect(jsonPath("$.events[0].when.precision").value("DAY"))
                .andExpect(jsonPath("$.events[0].when.calendar").value("GREGORIAN"))
                .andExpect(jsonPath("$.events[0].end.year").value(1918))
                .andExpect(jsonPath("$.events[0].end.month").value(11))
                .andExpect(jsonPath("$.events[0].end.day").value(11))
                .andExpect(jsonPath("$.events[0].end.precision").value("DAY"))
                .andExpect(jsonPath("$.events[0].end.calendar").value("GREGORIAN"))
                .andExpect(jsonPath("$.events[0].earliestYear").value(1914))
                .andExpect(jsonPath("$.events[0].latestYear").value(1918))
                .andExpect(jsonPath("$.events[0].longitude").value(2.5))
                .andExpect(jsonPath("$.events[0].latitude").value(48.75))
                .andExpect(jsonPath("$.events[0].coordPrecisionDeg").value(0.0001))
                .andExpect(jsonPath("$.events[0].approximate").value(false))
                .andExpect(jsonPath("$.events[0].placeQid").value(nullValue()));
    }

    @Test
    void fullShapeOfBcEventWithNullsAndPlaceReference() throws Exception {
        jdbc.update("INSERT INTO places (qid, geom, coord_precision_deg) "
                + "VALUES ('Q220', ST_SetSRID(ST_MakePoint(12.5, 41.9), 4326), NULL)");
        insertFull("Q1048", "DIED", null, 7, -44, 0, 0, "CENTURY", "JULIAN",
                null, -100, -1, "Q220", 12.5, 41.9, null);
        events(WORLD).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].label").value(nullValue()))
                .andExpect(jsonPath("$.events[0].when.year").value(-44))
                .andExpect(jsonPath("$.events[0].when.month").value(0))
                .andExpect(jsonPath("$.events[0].when.day").value(0))
                .andExpect(jsonPath("$.events[0].when.precision").value("CENTURY"))
                .andExpect(jsonPath("$.events[0].when.calendar").value("JULIAN"))
                .andExpect(jsonPath("$.events[0].end").value(nullValue()))
                .andExpect(jsonPath("$.events[0].earliestYear").value(-100))
                .andExpect(jsonPath("$.events[0].latestYear").value(-1))
                .andExpect(jsonPath("$.events[0].longitude").value(12.5))
                .andExpect(jsonPath("$.events[0].latitude").value(41.9))
                .andExpect(jsonPath("$.events[0].coordPrecisionDeg").value(nullValue()))
                .andExpect(jsonPath("$.events[0].approximate").value(true))
                .andExpect(jsonPath("$.events[0].placeQid").value("Q220"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 0.1})
    void approximateAtOrAboveOneTenthDegree(double precision) throws Exception {
        insertFull("Q1", "HAPPENED", "x", 1, 1755, 0, 0, "YEAR", "GREGORIAN",
                null, 1755, 1755, null, -9, 38, precision);
        events(WORLD).andExpect(status().isOk()).andExpect(jsonPath("$.events[0].approximate").value(true));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0001, 0.09})
    void preciseBelowOneTenthDegree(double precision) throws Exception {
        insertFull("Q1", "HAPPENED", "x", 1, 1755, 0, 0, "YEAR", "GREGORIAN",
                null, 1755, 1755, null, -9, 38, precision);
        events(WORLD).andExpect(status().isOk()).andExpect(jsonPath("$.events[0].approximate").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "bbox=",
        "bbox=1,2,3",
        "bbox=1,2,3,4,5",
        "bbox=a,0,10,10",
        "bbox=-181,0,10,10",
        "bbox=0,0,180.5,10",
        "bbox=0,-91,10,10",
        "bbox=0,0,10,91",
        "bbox=0,20,10,10",
        "bbox=-180,-90,180,90&from=10&to=5",
        "bbox=-180,-90,180,90&from=abc",
        "bbox=-180,-90,180,90&to=1.5",
        "bbox=-180,-90,180,90&kinds=BORN,WEDDING",
        "bbox=-180,-90,180,90&minImportance=-1",
        "bbox=-180,-90,180,90&limit=0",
        "bbox=-180,-90,180,90&limit=2001",
        "bbox=-180,-90,180,90&limit=x",
    })
    void badRequests(String query) throws Exception {
        events(query).andExpect(status().isBadRequest());
    }

    @Test
    void bboxIsExactAtTenMillionthOfADegree() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 10, 5);
        insert("Q2", "HAPPENED", 1, 1900, 1900, 10.0000001, 5);
        insert("Q3", "HAPPENED", 1, 1900, 1900, 5, 0);
        insert("Q4", "HAPPENED", 1, 1900, 1900, 5, -0.0000001);
        expectIds("bbox=0,0,10,10", "Q1:HAPPENED", "Q3:HAPPENED");
    }

    @Test
    void antimeridianBboxIncludesBothDateLineEdges() throws Exception {
        insert("Q1", "HAPPENED", 1, 1900, 1900, 180, 0);
        insert("Q2", "HAPPENED", 1, 1900, 1900, -180, 0);
        expectIds("bbox=170,-10,-170,10", "Q1:HAPPENED", "Q2:HAPPENED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "bbox=NaN,0,10,10",
        "bbox=0,NaN,10,10",
        "bbox=0,0,Infinity,10",
        "bbox=-Infinity,0,10,10",
        "bbox=0,0,10,Infinity",
    })
    void nonFiniteBboxIsRejected(String query) throws Exception {
        events(query).andExpect(status().isBadRequest());
    }

    @Test
    void emptyKindItemIsRejected() throws Exception {
        events(WORLD + "&kinds=born,,died").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "bbox=a,0,10,10",
        "bbox=0,-91,10,10",
        "bbox=0,0,10,91",
        "bbox=NaN,0,10,10",
        "bbox=-180,-90,180,90&from=abc",
        "bbox=-180,-90,180,90&limit=x",
        "bbox=-180,-90,180,90&from=10&to=5",
        "bbox=-180,-90,180,90&kinds=BORN,WEDDING",
        "bbox=-180,-90,180,90&kinds=born,,died",
        "bbox=-180,-90,180,90&limit=0",
    })
    void badRequestsAreProblemDetails(String query) throws Exception {
        events(query).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "bbox=-180,-90,180,90&limit=1",
        "bbox=-180,-90,180,90&limit=2000",
        "bbox=-180,-90,180,90&from=5&to=5",
        "bbox=-180,-90,180,90&minImportance=0",
        "bbox=0,10,10,10",
    })
    void boundaryValuesAccepted(String query) throws Exception {
        events(query).andExpect(status().isOk());
    }
}
