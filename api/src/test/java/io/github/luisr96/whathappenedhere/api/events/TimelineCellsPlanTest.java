package io.github.luisr96.whathappenedhere.api.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Box;
import io.github.luisr96.whathappenedhere.extractor.Event;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The SQL that sums {@code timeline_cells} must bound cell_x and cell_y through the primary key
 * (integer bounds), not only level: numeric bounds cast the columns and turn them into a Filter,
 * which on production reads every level-1 row. The statement is captured from a spy on the
 * repository's {@link NamedParameterJdbcTemplate}, then EXPLAINed with the same parameters.
 */
class TimelineCellsPlanTest extends PostgisIntegrationTest {

    @MockitoSpyBean
    NamedParameterJdbcTemplate named;

    @Autowired
    EventRepository events;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void fillCells() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
        jdbc.update("DELETE FROM timeline_cells");
        jdbc.update("""
                INSERT INTO timeline_cells (level, cell_x, cell_y, decade, kind, importance, n)
                SELECT l, x, y, 1900, 'HAPPENED', 1, 1
                FROM generate_series(0, 2) l, generate_series(-180, 179) x, generate_series(-90, 89) y
                """);
        jdbc.execute("ANALYZE timeline_cells");
        Mockito.clearInvocations(named);
    }

    private record Statement(String sql, SqlParameterSource params) {}

    private Statement capturedCellsQuery() {
        events.timeline(new Box(-30, -20, 30, 40), EnumSet.allOf(Event.Kind.class), 0);
        for (Invocation call : Mockito.mockingDetails(named).getInvocations()) {
            Object[] args = call.getArguments();
            if (args.length >= 2 && args[0] instanceof String sql && sql.contains("timeline_cells")) {
                SqlParameterSource params = switch (args[1]) {
                    case SqlParameterSource source -> source;
                    case Map<?, ?> map -> new MapSqlParameterSource(castMap(map));
                    default -> throw new IllegalStateException("unexpected params " + args[1]);
                };
                return new Statement(sql, params);
            }
        }
        throw new AssertionError("timeline() ran no query on timeline_cells for a level-1 box");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> castMap(Map<?, ?> map) {
        return (Map<String, ?>) map;
    }

    /** Seq and bitmap scans are disabled so the plan shows which conditions the index can take. */
    private List<String> indexConditions(Statement statement) {
        List<String> plan = tx.execute(status -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            jdbc.execute("SET LOCAL enable_bitmapscan = off");
            return named.queryForList("EXPLAIN " + statement.sql(), statement.params(), String.class);
        });
        return plan.stream().filter(line -> line.contains("Index Cond")).toList();
    }

    @Test
    void indexConditionBoundsCellX() {
        assertThat(indexConditions(capturedCellsQuery()))
                .anySatisfy(line -> assertThat(line).contains("cell_x >=").doesNotContain("::numeric"));
    }

    @Test
    void indexConditionBoundsCellY() {
        assertThat(indexConditions(capturedCellsQuery()))
                .anySatisfy(line -> assertThat(line).contains("cell_y >=").doesNotContain("::numeric"));
    }
}
