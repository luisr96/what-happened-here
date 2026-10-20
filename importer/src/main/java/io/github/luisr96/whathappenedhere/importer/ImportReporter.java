package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate;
import io.github.luisr96.whathappenedhere.importer.ImportReport.KindStats;
import io.github.luisr96.whathappenedhere.importer.ImportReport.TypeCount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link ImportReport} from the database after resolving: staging says what the dump
 * gave, the served tables what was kept. Reading it back from the database keeps it right across
 * restarts, which an in-memory tally would not.
 */
@Component
public class ImportReporter {

    private static final int TOP_TYPES = 10;
    private static final List<String> TABLES = List.of("events", "places", "timeline_cells");

    private final JdbcTemplate jdbc;

    public ImportReporter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public ImportReport report(long entitiesRead) {
        long staged = count("SELECT count(*) FROM stage_events");
        long loaded = count("SELECT count(*) FROM events");
        Map<String, Long> tableBytes = new LinkedHashMap<>();
        for (String table : TABLES) {
            tableBytes.put(table, jdbc.queryForObject(
                    "SELECT pg_total_relation_size(?::regclass)", Long.class, table));
        }
        return new ImportReport(entitiesRead, staged, loaded, staged - loaded,
                count("SELECT count(DISTINCT qid) FROM stage_places"),
                count("SELECT count(*) FROM places"),
                kinds(), precisions(), millennia(), tableBytes,
                count("SELECT pg_database_size(current_database())"));
    }

    private Map<String, KindStats> kinds() {
        Map<String, Staged> stagedByKind = new HashMap<>();
        jdbc.query("""
                SELECT kind, count(*), count(*) FILTER (WHERE place_qid IS NULL),
                       count(*) FILTER (WHERE place_qid IS NOT NULL)
                FROM stage_events GROUP BY kind
                """, (RowCallbackHandler) rs -> stagedByKind.put(rs.getString(1),
                        new Staged(rs.getLong(2), rs.getLong(3), rs.getLong(4))));
        Map<String, Loaded> loadedByKind = new HashMap<>();
        jdbc.query("""
                SELECT kind, count(*), count(*) FILTER (WHERE place_qid IS NOT NULL)
                FROM events GROUP BY kind
                """, (RowCallbackHandler) rs -> loadedByKind.put(rs.getString(1), new Loaded(rs.getLong(2), rs.getLong(3))));
        Map<String, List<TypeCount>> types = new HashMap<>();
        jdbc.query("""
                SELECT kind, qid, n FROM (
                    SELECT e.kind, t.qid, count(*) AS n,
                           row_number() OVER (PARTITION BY e.kind ORDER BY count(*) DESC, t.qid) AS rank
                    FROM events e
                    JOIN stage_events s ON s.id = e.id
                    CROSS JOIN LATERAL unnest(s.instance_of) AS t(qid)
                    GROUP BY e.kind, t.qid
                ) ranked
                WHERE rank <= ?
                ORDER BY kind, rank
                """, (RowCallbackHandler) rs -> types.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new TypeCount(rs.getString(2), rs.getLong(3))), TOP_TYPES);

        Map<String, KindStats> kinds = new LinkedHashMap<>();
        for (Event.Kind kind : Event.Kind.values()) {
            Staged staged = stagedByKind.get(kind.name());
            if (staged == null) {
                continue;
            }
            Loaded loaded = loadedByKind.getOrDefault(kind.name(), new Loaded(0, 0));
            kinds.put(kind.name(), new KindStats(staged.total(), loaded.total(), staged.ownCoordinates(),
                    staged.placeReferences(), loaded.placeReferencesResolved(),
                    types.getOrDefault(kind.name(), List.of())));
        }
        return kinds;
    }

    private Map<String, Long> precisions() {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("SELECT precision, count(*) FROM events GROUP BY precision",
                (RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getLong(2)));
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (HistoricDate.Precision precision : List.of(HistoricDate.Precision.values()).reversed()) {
            Long n = counts.get(precision.name());
            if (n != null) {
                ordered.put(precision.name(), n);
            }
        }
        return ordered;
    }

    /**
     * Bucket 0 is years 1..1000, 1 is 1001..2000; bucket -1 is -1000..-1, -2 is -2000..-1001.
     * Years are in historical numbering (no year 0), so AD and BC are counted from 1 and -1.
     */
    private Map<String, Long> millennia() {
        Map<String, Long> millennia = new LinkedHashMap<>();
        jdbc.query("""
                SELECT bucket, count(*) FROM (
                    SELECT CASE WHEN earliest_year > 0 THEN (earliest_year - 1) / 1000
                                ELSE -((-earliest_year - 1) / 1000) - 1 END AS bucket
                    FROM events
                ) b
                GROUP BY bucket ORDER BY bucket
                """, rs -> {
            long bucket = rs.getLong(1);
            String key = bucket >= 0
                    ? (bucket * 1000 + 1) + ".." + (bucket + 1) * 1000
                    : (bucket * 1000) + ".." + ((bucket + 1) * 1000 - 1);
            millennia.put(key, rs.getLong(2));
        });
        return millennia;
    }

    private record Staged(long total, long ownCoordinates, long placeReferences) {}

    private record Loaded(long total, long placeReferencesResolved) {}

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
