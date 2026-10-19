package io.github.luisr96.whathappenedhere.api.events;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The pre-aggregated counts behind {@code /stats/timeline}: one grid per level, cells of
 * {@link #CELL_SIZES} degrees.
 *
 * <p>Trade-off: with exact importance in the key, the table is about as large as events at
 * sample density (5.4 MB vs 5.7 MB for the 100k sample), mostly from the 0.1° level. Re-measure
 * on the full dump; if it's too big for the host, drop level 2 (boxes under 10° would then count
 * events directly) or bucket importance there.
 */
@Component
public class TimelineCells {

    /**
     * Cell size in degrees per level, coarsest first; the index is the stored level. Must match
     * the VALUES list in sql/timeline_cells.sql.
     */
    public static final List<Double> CELL_SIZES = List.of(10.0, 1.0, 0.1);

    /** A viewport must span at least this many cells each way to use a level. */
    public static final int MIN_CELLS_ACROSS = 10;

    private static final String REBUILD_SQL = sql("sql/timeline_cells.sql");

    private final JdbcTemplate jdbc;

    public TimelineCells(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces every cell with counts from the current events table. */
    public void rebuild() {
        jdbc.execute(REBUILD_SQL);
    }

    /**
     * The coarsest level whose cells fit {@link #MIN_CELLS_ACROSS} times into both sides of
     * the box, or -1 when none does and the box is small enough to count events directly.
     */
    static int levelFor(double width, double height) {
        for (int level = 0; level < CELL_SIZES.size(); level++) {
            double size = CELL_SIZES.get(level);
            if (width / size >= MIN_CELLS_ACROSS && height / size >= MIN_CELLS_ACROSS) {
                return level;
            }
        }
        return -1;
    }

    private static String sql(String resource) {
        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
