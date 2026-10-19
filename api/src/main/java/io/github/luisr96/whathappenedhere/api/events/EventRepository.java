package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.events.EventsResponse.DateView;
import io.github.luisr96.whathappenedhere.api.events.EventsResponse.EventView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads events for the map. */
@Repository
public class EventRepository {

    // CLAUDE.md: approximate = precision unknown or >= 0.1 degrees.
    private static final double APPROXIMATE_DEG = 0.1;

    private final NamedParameterJdbcTemplate jdbc;

    public EventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Matching events, most important first; one extra row tells whether the list was cut. */
    public EventsResponse find(EventQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minLon", query.minLon()).addValue("minLat", query.minLat())
                .addValue("maxLon", query.maxLon()).addValue("maxLat", query.maxLat())
                .addValue("minImportance", query.minImportance())
                .addValue("limit", query.limit() + 1);
        List<String> where = new ArrayList<>();
        // ST_Intersects, not bare &&: the index's float4 boxes round outward, so && alone can
        // return points a hair outside. A box crossing 180° is two boxes, one on each side.
        where.add(query.crossesAntimeridian()
                ? "(ST_Intersects(geom, ST_MakeEnvelope(:minLon, :minLat, 180, :maxLat, 4326))"
                        + " OR ST_Intersects(geom,"
                        + " ST_MakeEnvelope(-180, :minLat, :maxLon, :maxLat, 4326)))"
                : "ST_Intersects(geom, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))");
        where.add("importance >= :minImportance");
        if (query.from() != null) {
            where.add("latest_year >= :from");
            params.addValue("from", query.from());
        }
        if (query.to() != null) {
            where.add("earliest_year <= :to");
            params.addValue("to", query.to());
        }
        if (!query.kinds().isEmpty()) {
            where.add("kind IN (:kinds)");
            params.addValue("kinds", query.kinds().stream().map(Enum::name).toList());
        }
        String sql = "SELECT id, item_id, kind, label, importance, year, month, day, precision,"
                + " calendar, end_year, end_month, end_day, end_precision, end_calendar,"
                + " earliest_year, latest_year, coord_precision_deg, place_qid,"
                + " ST_X(geom) AS longitude, ST_Y(geom) AS latitude FROM events WHERE "
                + String.join(" AND ", where) + " ORDER BY importance DESC, id LIMIT :limit";
        List<EventView> events = jdbc.query(sql, params, (rs, row) -> event(rs));
        boolean truncated = events.size() > query.limit();
        return new EventsResponse(truncated ? events.subList(0, query.limit()) : events, truncated);
    }

    private static EventView event(ResultSet rs) throws SQLException {
        Double precision = rs.getObject("coord_precision_deg", Double.class);
        DateView end = rs.getObject("end_year") == null ? null : new DateView(
                rs.getLong("end_year"),
                rs.getInt("end_month"), rs.getInt("end_day"), rs.getString("end_precision"),
                rs.getString("end_calendar"));
        return new EventView(rs.getString("id"), rs.getString("item_id"), rs.getString("kind"),
                rs.getString("label"), rs.getInt("importance"),
                new DateView(rs.getLong("year"), rs.getInt("month"), rs.getInt("day"),
                        rs.getString("precision"), rs.getString("calendar")),
                end, rs.getLong("earliest_year"), rs.getLong("latest_year"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), precision,
                precision == null || precision >= APPROXIMATE_DEG, rs.getString("place_qid"));
    }
}
