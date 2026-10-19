package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.events.EventFilter.Box;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Circle;
import io.github.luisr96.whathappenedhere.api.events.EventsResponse.DateView;
import io.github.luisr96.whathappenedhere.api.events.EventsResponse.EventView;
import io.github.luisr96.whathappenedhere.api.events.TimelineResponse.DecadeCount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads events for the map, the "what happened here" list and the timeline. */
@Repository
public class EventRepository {

    // CLAUDE.md: approximate = precision unknown or >= 0.1 degrees.
    private static final double APPROXIMATE_DEG = 0.1;

    private final NamedParameterJdbcTemplate jdbc;

    public EventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Matching events, most important first; one extra row tells whether the list was cut. */
    public EventsResponse find(EventFilter filter, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit + 1);
        String sql = "SELECT id, item_id, kind, label, importance, year, month, day, precision,"
                + " calendar, end_year, end_month, end_day, end_precision, end_calendar,"
                + " earliest_year, latest_year, coord_precision_deg, place_qid,"
                + " ST_X(geom) AS longitude, ST_Y(geom) AS latitude FROM events WHERE "
                + where(filter, params) + " ORDER BY importance DESC, id LIMIT :limit";
        List<EventView> events = jdbc.query(sql, params, (rs, row) -> event(rs));
        boolean truncated = events.size() > limit;
        return new EventsResponse(truncated ? events.subList(0, limit) : events, truncated);
    }

    /**
     * Matching events counted per decade of their earliest year, ascending, non-empty only.
     * Counts every matching row (about 24 ms for 7,663 rows in the 100k sample), so a continent
     * box on the full dump will be slow; that needs a pre-aggregated table (grid cell, kind,
     * importance bucket, decade) or a default minImportance before step 6.
     */
    public TimelineResponse timeline(EventFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        // floor, not integer division: -44 belongs to the -50 decade, not -40.
        String sql = "SELECT floor(earliest_year / 10.0)::bigint * 10 AS decade, count(*) AS n"
                + " FROM events WHERE " + where(filter, params)
                + " GROUP BY decade ORDER BY decade";
        return new TimelineResponse(jdbc.query(sql, params,
                (rs, row) -> new DecadeCount(rs.getLong("decade"), rs.getLong("n"))));
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
        if (!filter.kinds().isEmpty()) {
            where.add("kind IN (:kinds)");
            params.addValue("kinds", filter.kinds().stream().map(Enum::name).toList());
        }
        return String.join(" AND ", where);
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
