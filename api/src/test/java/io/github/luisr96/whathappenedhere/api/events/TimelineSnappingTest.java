package io.github.luisr96.whathappenedhere.api.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /stats/timeline over a seeded synthetic dataset: the endpoint's decades must equal a count
 * made directly over events in the snapped area (every cell of the chosen size touching the box).
 * The expected side picks the cell size and floors coordinates independently of the production code.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimelineSnappingTest extends PostgisIntegrationTest {

    private static final String[] KINDS = {"HAPPENED", "BORN", "DIED", "FOUNDED", "ENDED", "OPENED"};
    private static final BigDecimal[] SIZES = {new BigDecimal("10"), new BigDecimal("1"), new BigDecimal("0.1")};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TimelineCells cells;

    @BeforeAll
    void insertSyntheticEvents() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
        var random = new Random(20261018L);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            double lon;
            double lat;
            switch (i % 4) {
                // Clusters inside the test boxes so the fine levels see real data, not only the world.
                case 0 -> { lon = coord(random, 0, 40); lat = coord(random, 35, 60); }
                case 1 -> { lon = coord(random, 10, 15); lat = coord(random, 44, 47); }
                case 2 -> { lon = coord(random, 165, 195); lat = coord(random, -35, 35); }
                default -> { lon = coord(random, -180, 180); lat = coord(random, -90, 90); }
            }
            if (lon > 180) {
                lon -= 360;
            }
            long earliest = random.nextInt(4000) - 2000;
            String kind = KINDS[random.nextInt(KINDS.length)];
            String item = "Q" + i;
            rows.add(new Object[] {item + ":" + kind, item, kind, random.nextInt(301),
                    earliest, earliest, earliest, lon, lat});
        }
        // Exact cell borders, which double division would misplace.
        rows.add(new Object[] {"Q90001:BORN", "Q90001", "BORN", 5, -44L, -44L, -44L, 10.3, 44.1});
        rows.add(new Object[] {"Q90002:BORN", "Q90002", "BORN", 5, -44L, -44L, -44L, 180.0, 0.0});
        rows.add(new Object[] {"Q90003:BORN", "Q90003", "BORN", 5, -44L, -44L, -44L, -180.0, 0.0});
        rows.add(new Object[] {"Q90004:BORN", "Q90004", "BORN", 5, -44L, -44L, -44L, 0.0, 90.0});
        rows.add(new Object[] {"Q90005:BORN", "Q90005", "BORN", 5, -44L, -44L, -44L, 0.0, -90.0});
        jdbc.batchUpdate("""
                INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar, earliest_year, latest_year,
                    place_qid, geom, coord_precision_deg)
                VALUES (?, ?, ?, 'label', ?, ?, 0, 0, 'YEAR', 'GREGORIAN', NULL, NULL, NULL, NULL, NULL, ?, ?,
                    NULL, ST_SetSRID(ST_MakePoint(?, ?), 4326), 0.0001)
                """, rows);
        cells.rebuild();
    }

    /** Two decimals, so many points sit exactly on 0.1-, 1- and 10-degree borders. */
    private static double coord(Random random, double min, double max) {
        return Math.round((min + random.nextDouble() * (max - min)) * 100) / 100.0;
    }

    @ParameterizedTest(name = "{0} kinds={1} minImportance={2}")
    @CsvSource(nullValues = "-", value = {
        "'-180,-90,180,90', -, -",
        "'-180,-90,180,90', 'born,died', -",
        "'-180,-90,180,90', -, 100",
        "'-180,-90,180,90', 'FOUNDED', 250",
        "'0.5,35.2,40.3,60.1', -, -",
        "'0.5,35.2,40.3,60.1', 'HAPPENED,opened', 50",
        "'10.05,44.02,15.13,47.07', -, -",
        "'10.05,44.02,15.13,47.07', 'born', 0",
        "'10.05,44.02,15.13,47.07', -, 150",
        "'170,-30,-170,30', -, -",
        "'170,-30,-170,30', 'ENDED,DIED', 20",
        "'175.55,-1.05,-175.45,9.95', -, -",
        "'-20,-60,80,-40', -, -",
    })
    void snappedSumEqualsDirectCountOverSnappedArea(String bbox, String kinds, Integer minImportance)
            throws Exception {
        String query = "bbox=" + bbox
                + (kinds == null ? "" : "&kinds=" + kinds)
                + (minImportance == null ? "" : "&minImportance=" + minImportance);
        assertThat(endpoint(query)).isEqualTo(expectedSnapped(bbox, kinds, minImportance == null ? 0 : minImportance));
    }

    @Test
    void worldUsesTenDegreeCellsAndCoversEverything() throws Exception {
        long total = jdbc.queryForObject("SELECT count(*) FROM events", Long.class);
        assertThat(endpoint("bbox=-180,-90,180,90").values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(total);
    }

    @ParameterizedTest
    @CsvSource({
        "'10.05,44.02,10.55,47.07'",
        "'0.5,35.2,40.3,35.9'",
        "'179.6,-10,-179.7,10'",
    })
    void boxUnderOneDegreeEqualsExactIntersectsCount(String bbox) throws Exception {
        assertThat(endpoint("bbox=" + bbox)).isEqualTo(expectedExact(bbox));
    }

    @Test
    void choiceOfLevelMatters() throws Exception {
        // Guards the test itself: on this dataset the snapped and exact counts differ.
        String bbox = "10.05,44.02,15.13,47.07";
        assertThat(expectedSnapped(bbox, null, 0)).isNotEqualTo(expectedExact(bbox));
    }

    private Map<Long, Long> endpoint(String query) throws Exception {
        String body = mvc.perform(get("/stats/timeline?" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<Long, Long> result = new LinkedHashMap<>();
        for (JsonNode d : MAPPER.readTree(body).path("decades")) {
            result.put(d.path("decade").asLong(), d.path("count").asLong());
        }
        return result;
    }

    private Map<Long, Long> expectedSnapped(String bbox, String kinds, int minImportance) {
        double[] b = parse(bbox);
        boolean crosses = b[0] > b[2];
        double width = crosses ? (180 - b[0]) + (b[2] + 180) : b[2] - b[0];
        double height = b[3] - b[1];
        BigDecimal size = null;
        for (BigDecimal s : SIZES) {
            if (width / s.doubleValue() >= 10 && height / s.doubleValue() >= 10) {
                size = s;
                break;
            }
        }
        assertThat(size).as("box %s must be large enough to snap", bbox).isNotNull();
        String cx = "floor(ST_X(geom)::numeric / " + size + ")";
        String cy = "floor(ST_Y(geom)::numeric / " + size + ")";
        String xRange = crosses
                ? "(%s BETWEEN %d AND %d OR %s BETWEEN %d AND %d)".formatted(
                        cx, cell(b[0], size), cell(180, size), cx, cell(-180, size), cell(b[2], size))
                : "%s BETWEEN %d AND %d".formatted(cx, cell(b[0], size), cell(b[2], size));
        String where = xRange + " AND %s BETWEEN %d AND %d".formatted(cy, cell(b[1], size), cell(b[3], size));
        return perDecade(where, kinds, minImportance);
    }

    private Map<Long, Long> expectedExact(String bbox) {
        double[] b = parse(bbox);
        String env = "ST_Intersects(geom, ST_MakeEnvelope(%s, %s, %s, %s, 4326))";
        String where = b[0] > b[2]
                ? "(" + env.formatted(b[0], b[1], 180.0, b[3]) + " OR " + env.formatted(-180.0, b[1], b[2], b[3]) + ")"
                : env.formatted(b[0], b[1], b[2], b[3]);
        return perDecade(where, null, 0);
    }

    private Map<Long, Long> perDecade(String where, String kinds, int minImportance) {
        String kindFilter = kinds == null ? "" : " AND kind = ANY (string_to_array(upper(?), ','))";
        List<Object> args = new ArrayList<>();
        if (kinds != null) {
            args.add(kinds);
        }
        args.add(minImportance);
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT floor(earliest_year / 10.0)::bigint * 10 AS decade, count(*) AS n FROM events WHERE "
                + where + kindFilter + " AND importance >= ? GROUP BY decade ORDER BY decade",
                rs -> {
                    result.put(rs.getLong("decade"), rs.getLong("n"));
                }, args.toArray());
        return result;
    }

    /** Same rule as the spec: numeric floor, so 0.3 / 0.1 is 3, not 2. */
    private static long cell(double degrees, BigDecimal size) {
        return new BigDecimal(Double.toString(degrees)).divide(size, 0, RoundingMode.FLOOR).longValueExact();
    }

    private static double[] parse(String bbox) {
        String[] parts = bbox.split(",");
        double[] b = new double[4];
        for (int i = 0; i < 4; i++) {
            b[i] = Double.parseDouble(parts[i]);
        }
        return b;
    }
}
