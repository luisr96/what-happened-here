package io.github.luisr96.whathappenedhere.api.stats;

import io.github.luisr96.whathappenedhere.extractor.Event;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Counts the whole dataset for {@link StatsSummary}. */
@Repository
public class StatsRepository {

    private final JdbcTemplate jdbc;

    public StatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One pass over events (per kind: count and year span) plus the places count, in one
     * read-only transaction so the numbers agree with each other.
     */
    @Transactional(readOnly = true)
    public StatsSummary count(String dumpDate) {
        Map<Event.Kind, Long> byKind = new EnumMap<>(Event.Kind.class);
        for (Event.Kind kind : Event.Kind.values()) {
            byKind.put(kind, 0L);
        }
        Long[] span = new Long[2];
        jdbc.query("SELECT kind, count(*), min(earliest_year), max(latest_year) FROM events GROUP BY kind",
                (RowCallbackHandler) rs -> {
                    byKind.put(Event.Kind.valueOf(rs.getString(1)), rs.getLong(2));
                    long earliest = rs.getLong(3);
                    long latest = rs.getLong(4);
                    span[0] = span[0] == null ? earliest : Math.min(span[0], earliest);
                    span[1] = span[1] == null ? latest : Math.max(span[1], latest);
                });
        long events = byKind.values().stream().mapToLong(Long::longValue).sum();
        long places = jdbc.queryForObject("SELECT count(*) FROM places", Long.class);
        return new StatsSummary(events, places, Collections.unmodifiableMap(byKind),
                span[0], span[1], dumpDate);
    }
}
