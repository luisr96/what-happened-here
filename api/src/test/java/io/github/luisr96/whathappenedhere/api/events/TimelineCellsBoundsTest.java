package io.github.luisr96.whathappenedhere.api.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Box;
import io.github.luisr96.whathappenedhere.api.events.TimelineResponse.DecadeCount;
import io.github.luisr96.whathappenedhere.extractor.Event;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cell-range bounds of {@link EventRepository#timeline}: negative and exactly-on-edge boxes and
 * an antimeridian-crossing box give the same decade counts as counting the events in the box.
 * Each event has its own decade so a wrongly included or excluded cell shows up by decade.
 */
class TimelineCellsBoundsTest extends PostgisIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    TimelineCells cells;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
    }

    private void insert(String item, long earliest, double lon, double lat) {
        jdbc.update("""
                INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar, earliest_year, latest_year,
                    place_qid, geom, coord_precision_deg)
                VALUES (?, ?, 'HAPPENED', 'label', 1, ?, 0, 0, 'YEAR', 'GREGORIAN', NULL, NULL, NULL, NULL, NULL,
                    ?, ?, NULL, ST_SetSRID(ST_MakePoint(?, ?), 4326), 0.0001)
                """,
                item + ":HAPPENED", item, earliest, earliest, earliest, lon, lat);
    }

    private Map<Long, Long> timeline(double minLon, double minLat, double maxLon, double maxLat) {
        cells.rebuild();
        return events.timeline(new Box(minLon, minLat, maxLon, maxLat), EnumSet.allOf(Event.Kind.class), 0)
                .decades().stream()
                .collect(Collectors.toMap(DecadeCount::decade, DecadeCount::count));
    }

    @Test
    void levelOneBoxStartingAtNegativeWholeDegree() {
        insert("Q1", 1900, -10, 0);       // cell -10: inside
        insert("Q2", 1910, -10.5, 0);     // cell -11: outside
        insert("Q3", 1920, -9.5, -9.5);
        insert("Q4", 1930, 0, -10);       // cell_y -10: inside
        insert("Q5", 1940, 0, -10.01);    // cell_y -11: outside
        assertThat(timeline(-10, -10, 10, 10)).isEqualTo(Map.of(1900L, 1L, 1920L, 1L, 1930L, 1L));
    }

    @Test
    void levelTwoBoxStartingAtNegativeMultipleOfTenthDegree() {
        insert("Q1", 1900, -10, -4.5);    // cell -100: inside
        insert("Q2", 1910, -10.01, -4.5); // cell -101: outside
        insert("Q3", 1920, -9.95, -4.95);
        insert("Q4", 1930, -9.5, -5.01);  // cell_y -51: outside
        assertThat(timeline(-10, -5, -9, -4)).isEqualTo(Map.of(1900L, 1L, 1920L, 1L));
    }

    /** Floor, not truncation: -0.05 is in cell -1 and -1.05 in cell -11, outside a box from -1. */
    @Test
    void levelTwoNegativeFractionFloorsAwayFromZero() {
        insert("Q1", 1900, -0.05, 0.5);
        insert("Q2", 1910, -1.05, 0.5);
        insert("Q3", 1920, -0.95, 0.5);
        insert("Q4", 1930, -0.5, -0.05);
        assertThat(timeline(-1, -1, 0, 1)).isEqualTo(Map.of(1900L, 1L, 1920L, 1L, 1930L, 1L));
    }

    @Test
    void levelOneBoxCrossingAntimeridian() {
        insert("Q1", 1900, 175, 10);
        insert("Q2", 1910, -175, 10);
        insert("Q3", 1920, 0, 10);
        insert("Q4", 1930, 165, 10);
        insert("Q5", 1940, -165, 10);
        assertThat(timeline(170, 0, -170, 20)).isEqualTo(Map.of(1900L, 1L, 1910L, 1L));
    }
}
