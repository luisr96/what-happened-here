package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.events.EventFilter.Box;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Circle;
import io.github.luisr96.whathappenedhere.api.events.EventsResponse.DateView;
import io.github.luisr96.whathappenedhere.api.events.EventsResponse.EventView;
import io.github.luisr96.whathappenedhere.api.events.TimelineResponse.DecadeCount;
import io.github.luisr96.whathappenedhere.extractor.Event;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads events for the map, the "what happened here" list and the timeline. */
@Repository
public class EventRepository {

    // CLAUDE.md: approximate = precision unknown or >= 0.1 degrees.
    private static final double APPROXIMATE_DEG = 0.1;
    private static final String EVENT_COLUMNS = "id, item_id, kind, label, importance, year,"
            + " month, day, precision, calendar, end_year, end_month, end_day, end_precision,"
            + " end_calendar, earliest_year, latest_year, coord_precision_deg, place_qid,"
            + " ST_X(geom) AS longitude, ST_Y(geom) AS latitude";

    /** One event with its item's English Wikipedia title (null without one). */
    public record EventWithTitle(EventView event, String wikiTitle) {}

    private final NamedParameterJdbcTemplate jdbc;

    public EventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Matching events, most important first; one extra row tells whether the list was cut. */
    public EventsResponse find(EventFilter filter, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit + 1);
        String sql = "SELECT " + EVENT_COLUMNS + " FROM events WHERE " + where(filter, params)
                + " ORDER BY importance DESC, id LIMIT :limit";
        List<EventView> events = jdbc.query(sql, params, (rs, row) -> event(rs));
        boolean truncated = events.size() > limit;
        return new EventsResponse(truncated ? events.subList(0, limit) : events, truncated);
    }

    /** The event with this id ({@code <QID>:<KIND>}), if there is one. */
    public Optional<EventWithTitle> findById(String id) {
        return jdbc.query("SELECT " + EVENT_COLUMNS + ", wiki_title FROM events WHERE id = :id",
                        new MapSqlParameterSource("id", id),
                        (rs, row) -> new EventWithTitle(event(rs), rs.getString("wiki_title")))
                .stream().findFirst();
    }

    /**
     * Events per decade of their earliest year, ascending, non-empty only. A box spanning enough
     * cells sums {@code timeline_cells} over every cell it touches, so it may count events up to
     * one cell outside its edges; a smaller box counts events exactly. Only boxes are supported.
     */
    public TimelineResponse timeline(Box box, Set<Event.Kind> kinds, int minImportance) {
        // The whole timeline is wanted, so no from/to: both paths must count the same events.
        EventFilter filter = new EventFilter(box, null, null, kinds, minImportance);
        double width = box.crossesAntimeridian()
                ? (180 - box.minLon()) + (box.maxLon() + 180)
                : box.maxLon() - box.minLon();
        int level = TimelineCells.levelFor(width, box.maxLat() - box.minLat());
        return level < 0 ? countEvents(filter) : sumCells(filter, box, level);
    }

    private TimelineResponse sumCells(EventFilter filter, Box box, int level) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("level", level)
                .addValue("size", BigDecimal.valueOf(TimelineCells.CELL_SIZES.get(level)))
                .addValue("minLon", BigDecimal.valueOf(box.minLon()))
                .addValue("maxLon", BigDecimal.valueOf(box.maxLon()))
                .addValue("minLat", BigDecimal.valueOf(box.minLat()))
                .addValue("maxLat", BigDecimal.valueOf(box.maxLat()))
                .addValue("minImportance", filter.minImportance());
        // Same numeric floor as sql/timeline_cells.sql, so box corners and points agree; cast
        // back to integer so the columns stay integer and the primary key can bound the cells.
        // A box crossing 180° is an OR on cell_x, which one index range can't take; only level
        // and cell_y bound it there. Fine while such boxes are rare.
        String cellX = box.crossesAntimeridian()
                ? "(cell_x >= floor(:minLon / :size)::integer OR cell_x <= floor(:maxLon / :size)::integer)"
                : "cell_x BETWEEN floor(:minLon / :size)::integer AND floor(:maxLon / :size)::integer";
        String sql = "SELECT decade, sum(n) AS n FROM timeline_cells WHERE level = :level AND "
                + cellX + " AND cell_y BETWEEN floor(:minLat / :size)::integer"
                + " AND floor(:maxLat / :size)::integer"
                + " AND importance >= :minImportance"
                + kindsCondition(filter, params)
                + " GROUP BY decade ORDER BY decade";
        return decades(sql, params);
    }

    private TimelineResponse countEvents(EventFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        // floor, not integer division: -44 belongs to the -50 decade, not -40.
        String sql = "SELECT floor(earliest_year / 10.0)::bigint * 10 AS decade, count(*) AS n"
                + " FROM events WHERE " + where(filter, params)
                + " GROUP BY decade ORDER BY decade";
        return decades(sql, params);
    }

    private TimelineResponse decades(String sql, MapSqlParameterSource params) {
        return new TimelineResponse(jdbc.query(sql, params,
                (rs, row) -> new DecadeCount(rs.getLong("decade"), rs.getLong("n"))));
    }

    private static String kindsCondition(EventFilter filter, MapSqlParameterSource params) {
        if (filter.kinds().isEmpty()) {
            return "";
        }
        params.addValue("kinds", filter.kinds().stream().map(Enum::name).toList());
        return " AND kind IN (:kinds)";
    }

    /** The filter as SQL, from fixed fragments; values go into {@code params}. */
    private static String where(EventFilter filter, MapSqlParameterSource params) {
        List<String> where = new ArrayList<>();
        where.add(switch (filter.area()) {
            case Box box -> box(box, params);
            case Circle circle -> circle(circle, params);
        });
        where.add("importance >= :minImportance");
        params.addValue("minImportance", filter.minImportance());
        if (filter.from() != null) {
            where.add("latest_year >= :from");
            params.addValue("from", filter.from());
        }
        if (filter.to() != null) {
            where.add("earliest_year <= :to");
            params.addValue("to", filter.to());
        }
        return String.join(" AND ", where) + kindsCondition(filter, params);
    }

    private static String box(Box box, MapSqlParameterSource params) {
        params.addValue("minLon", box.minLon()).addValue("minLat", box.minLat())
                .addValue("maxLon", box.maxLon()).addValue("maxLat", box.maxLat());
        // ST_Intersects, not bare &&: the index's float4 boxes round outward, so && alone can
        // return points a hair outside. A box crossing 180° is two boxes, one on each side.
        return box.crossesAntimeridian()
                ? "(ST_Intersects(geom, ST_MakeEnvelope(:minLon, :minLat, 180, :maxLat, 4326))"
                        + " OR ST_Intersects(geom,"
                        + " ST_MakeEnvelope(-180, :minLat, :maxLon, :maxLat, 4326)))"
                : "ST_Intersects(geom, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))";
    }

    private static String circle(Circle circle, MapSqlParameterSource params) {
        params.addValue("lat", circle.lat()).addValue("lon", circle.lon())
                .addValue("radius", circle.radiusMetres());
        // Same expression as events_geog_idx, so the index serves it; geography measures metres
        // on the spheroid and handles the antimeridian.
        return "ST_DWithin(geom::geography,"
                + " ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)";
    }

    private static EventView event(ResultSet rs) throws SQLException {
        Double precision = rs.getObject("coord_precision_deg", Double.class);
        DateView end = rs.getObject("end_year") == null ? null : new DateView(
                rs.getLong("end_year"), rs.getInt("end_month"), rs.getInt("end_day"),
                rs.getString("end_precision"), rs.getString("end_calendar"));
        return new EventView(rs.getString("id"), rs.getString("item_id"), rs.getString("kind"),
                rs.getString("label"), rs.getInt("importance"),
                new DateView(rs.getLong("year"), rs.getInt("month"), rs.getInt("day"),
                        rs.getString("precision"), rs.getString("calendar")),
                end, rs.getLong("earliest_year"), rs.getLong("latest_year"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), precision,
                precision == null || precision >= APPROXIMATE_DEG, rs.getString("place_qid"));
    }
}
