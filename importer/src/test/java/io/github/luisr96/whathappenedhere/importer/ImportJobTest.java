package io.github.luisr96.whathappenedhere.importer;

import static io.github.luisr96.whathappenedhere.importer.Entities.claim;
import static io.github.luisr96.whathappenedhere.importer.Entities.claims;
import static io.github.luisr96.whathappenedhere.importer.Entities.coords;
import static io.github.luisr96.whathappenedhere.importer.Entities.enwiki;
import static io.github.luisr96.whathappenedhere.importer.Entities.item;
import static io.github.luisr96.whathappenedhere.importer.Entities.ref;
import static io.github.luisr96.whathappenedhere.importer.Entities.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The fixture dump, in order: Q1 (FOUNDED 1900, own coordinates; unused as a place), Q2 (BORN
 * 1850 in Q90; DIED 1900 in Q404, which is not in the dump), Q90 (place only, no sitelink),
 * Q999 (place only, used by nothing), Q3 (HAPPENED 1500 with own coordinates), Q4 (sitelink but
 * nothing to extract), Q5 (BORN 1700 in Q91, a place that comes later in the dump), Q91 (place).
 * Resolved: events Q1:FOUNDED, Q2:BORN, Q3:HAPPENED, Q5:BORN; places Q90, Q91.
 *
 * <p>Chunk size 2: the restart test's failure after five entities lands after two committed chunks.
 * Four threads: every job here, including the restart and kill-recovery runs compared against an
 * uninterrupted one, processes the items of each chunk concurrently.
 */
@SpringBootTest(properties = {
    "import.chunk-size=2",
    "import.max-reconnects=1",
    "import.reconnect-delay=0s",
    "import.threads=4",
})
class ImportJobTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGIS.start();
    }

    private static final double EPS = 1e-9;

    static final List<String> FIXTURE = List.of(
            item("Q1", enwiki("Building"), claims(
                    claim("P571", time("P571", "+1900-01-01T00:00:00Z", 11)),
                    claim("P625", coords(48.5, 2.25, 0.0001)))),
            item("Q2", enwiki("Person"), claims(
                    claim("P569", time("P569", "+1850-03-04T00:00:00Z", 11)),
                    claim("P19", ref("P19", "Q90")),
                    claim("P570", time("P570", "+1900-05-06T00:00:00Z", 11)),
                    claim("P20", ref("P20", "Q404")))),
            item("Q90", "", claim("P625", coords(48.8566, 2.3522, 0.0001))),
            item("Q999", "", claim("P625", coords(1.0, 1.0, 1.0))),
            item("Q3", enwiki("Battle"), claims(
                    claim("P585", time("P585", "+1500-06-01T00:00:00Z", 11)),
                    claim("P625", coords(-10.5, -20.25, 0.01)))),
            item("Q4", enwiki("Concept"), ""),
            item("Q5", enwiki("Other person"), claims(
                    claim("P569", time("P569", "+1700-01-01T00:00:00Z", 9)),
                    claim("P19", ref("P19", "Q91")))),
            item("Q91", "", claim("P625", coords(41.9, 12.5, 0.001))));

    static final List<String> SECOND_FIXTURE = List.of(
            item("Q7", enwiki("Other battle"), claims(
                    claim("P585", time("P585", "+1066-10-14T00:00:00Z", 11)),
                    claim("P625", coords(50.9, 0.49, 0.001)))));

    /** Serves registered in-memory dumps; can be told to die after N entities on every open. */
    static final class FakeDumpOpener implements DumpOpener {

        private final Map<String, TestDumps.Dump> dumps = new ConcurrentHashMap<>();
        private volatile int failAfter = -1;

        String register(List<String> lines) {
            var source = "mem://" + UUID.randomUUID() + "/latest-all.json.gz";
            dumps.put(source, TestDumps.dump(lines));
            return source;
        }

        void registerAt(String source, List<String> lines) {
            dumps.put(source, TestDumps.dump(lines));
        }

        void failAfter(int entities) {
            failAfter = entities;
        }

        void heal() {
            failAfter = -1;
        }

        @Override
        public InputStream open(String source) throws IOException {
            var dump = dumps.get(source);
            if (dump == null) {
                throw new IOException("no such dump: " + source);
            }
            return failAfter >= 0 ? dump.failingAfter(failAfter) : dump.stream();
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FakeDumpOpener fakeDumpOpener() {
            return new FakeDumpOpener();
        }
    }

    @Autowired
    ImportService importService;

    @Autowired
    FakeDumpOpener opener;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void heal() {
        opener.heal();
    }

    @Test
    void runCompletes() {
        var execution = importService.run(opener.register(FIXTURE), 0);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void loadsExactlyTheResolvedEvents() {
        importService.run(opener.register(FIXTURE), 0);

        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:FOUNDED", "Q2:BORN", "Q3:HAPPENED", "Q5:BORN");
    }

    @Test
    void eventWithOwnCoordinatesKeepsThem() {
        importService.run(opener.register(FIXTURE), 0);

        var row = event("Q3:HAPPENED");
        assertThat(row.get("place_qid")).isNull();
        assertThat((Double) row.get("lon")).isCloseTo(-20.25, within(EPS));
        assertThat((Double) row.get("lat")).isCloseTo(-10.5, within(EPS));
        assertThat((Double) row.get("coord_precision_deg")).isCloseTo(0.01, within(EPS));
        assertThat(row.get("year")).isEqualTo(1500L);
        assertThat(row.get("precision")).isEqualTo("DAY");
        assertThat(row.get("label")).isEqualTo("Label Q3");
        assertThat(row.get("wiki_title")).isEqualTo("Battle");
        assertThat(row.get("importance")).isEqualTo(1);
    }

    @Test
    void eventWithPlaceReferenceGetsThePlacesCoordinates() {
        importService.run(opener.register(FIXTURE), 0);

        var born = event("Q2:BORN");
        assertThat(born.get("place_qid")).isEqualTo("Q90");
        assertThat((Double) born.get("lon")).isCloseTo(2.3522, within(EPS));
        assertThat((Double) born.get("lat")).isCloseTo(48.8566, within(EPS));
        assertThat((Double) born.get("coord_precision_deg")).isCloseTo(0.0001, within(EPS));
    }

    @Test
    void placeReferenceResolvesWhenThePlaceComesLaterInTheDump() {
        importService.run(opener.register(FIXTURE), 0);

        var born = event("Q5:BORN");
        assertThat(born.get("place_qid")).isEqualTo("Q91");
        assertThat((Double) born.get("lon")).isCloseTo(12.5, within(EPS));
        assertThat((Double) born.get("lat")).isCloseTo(41.9, within(EPS));
        assertThat(born.get("precision")).isEqualTo("YEAR");
    }

    @Test
    void eventWhosePlaceIsNotInTheDumpIsDropped() {
        importService.run(opener.register(FIXTURE), 0);

        assertThat(eventIds()).doesNotContain("Q2:DIED");
    }

    @Test
    void onlyPlacesUsedByEventsAreKept() {
        importService.run(opener.register(FIXTURE), 0);

        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class))
                .containsExactlyInAnyOrder("Q90", "Q91");
    }

    @Test
    void timelineCellsCountEveryEventAtEachLevel() {
        importService.run(opener.register(FIXTURE), 0);

        long events = jdbc.queryForObject("SELECT count(*) FROM events", Long.class);
        assertThat(events).isEqualTo(4);
        for (int level = 0; level <= 2; level++) {
            assertThat(timelineSum(level)).as("level %d", level).isEqualTo(events);
        }
    }

    @Test
    void limitLoadsOnlyEventsFromTheFirstEntities() {
        // Q1 and Q2 only: Q2:BORN's place Q90 is the third entity, so it is unresolved.
        var execution = importService.run(opener.register(FIXTURE), 2);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(eventIds()).containsExactly("Q1:FOUNDED");
        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class)).isEmpty();
        assertThat(timelineSum(0)).isEqualTo(1);
    }

    @Test
    void limitIncludingThePlaceResolvesIt() {
        importService.run(opener.register(FIXTURE), 3);

        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:FOUNDED", "Q2:BORN");
        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class)).containsExactly("Q90");
    }

    @Test
    void newInstanceReplacesPreviousContents() {
        importService.run(opener.register(FIXTURE), 0);
        var second = importService.run(opener.register(SECOND_FIXTURE), 0);

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(eventIds()).containsExactly("Q7:HAPPENED");
        assertThat(jdbc.queryForList("SELECT qid FROM places", String.class)).isEmpty();
        assertThat(timelineSum(0)).isEqualTo(1);
    }

    @Test
    void newInstanceWithADifferentLimitOnTheSameSourceReplacesRatherThanAppends() {
        var source = opener.register(FIXTURE);
        importService.run(source, 0);
        var limited = importService.run(source, 2);

        assertThat(limited.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(eventIds()).containsExactly("Q1:FOUNDED");
        assertThat(timelineSum(0)).isEqualTo(1);
    }

    @Test
    void rerunningACompletedImportStartsAFreshInstanceWithoutDuplicates() {
        var source = opener.register(FIXTURE);
        var first = importService.run(source, 0);
        var second = importService.run(source, 0);

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getJobInstanceId()).isNotEqualTo(first.getJobInstanceId());
        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:FOUNDED", "Q2:BORN", "Q3:HAPPENED", "Q5:BORN");
        assertThat(timelineSum(0)).isEqualTo(4);
    }

    @Test
    void failedImportRestartsFromTheLastChunkAndMatchesAnUninterruptedRun() {
        importService.run(opener.register(FIXTURE), 0);
        var expectedEvents = eventRows();
        var expectedPlaces = placeRows();
        var expectedCells = cellRows();

        // Same contents, different source: a separate job instance.
        var source = opener.register(FIXTURE);
        opener.failAfter(5);
        var failed = importService.run(source, 0);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);

        opener.heal();
        var restarted = importService.run(source, 0);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstanceId()).as("restart, not a new instance")
                .isEqualTo(failed.getJobInstanceId());
        assertThat(eventRows()).isEqualTo(expectedEvents);
        assertThat(placeRows()).isEqualTo(expectedPlaces);
        assertThat(cellRows()).isEqualTo(expectedCells);
    }

    @Test
    void restartWithALimitKeepsTheLimit() {
        var source = opener.register(FIXTURE);
        opener.failAfter(3);
        var failed = importService.run(source, 5);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);

        opener.heal();
        var restarted = importService.run(source, 5);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
        // First five entities: Q1, Q2, Q90, Q999, Q3.
        assertThat(eventIds()).containsExactlyInAnyOrder("Q1:FOUNDED", "Q2:BORN", "Q3:HAPPENED");
    }

    @Test
    void failedImportLeavesTheServedTablesUntouched() {
        importService.run(opener.register(SECOND_FIXTURE), 0);

        opener.failAfter(5);
        var failed = importService.run(opener.register(FIXTURE), 0);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(eventIds()).containsExactly("Q7:HAPPENED");
        assertThat(timelineSum(0)).isEqualTo(1);
    }

    @Test
    void newInstanceAfterAFailedOneDoesNotPickUpItsStagedRows() {
        opener.failAfter(5);
        var failed = importService.run(opener.register(FIXTURE), 0);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);

        opener.heal();
        var fresh = importService.run(opener.register(SECOND_FIXTURE), 0);

        assertThat(fresh.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(eventIds()).containsExactly("Q7:HAPPENED");
    }

    @Test
    void importKilledWithoutRecordingItsFailureIsResumedNotRestartedFromScratch() {
        importService.run(opener.register(FIXTURE), 0);
        var expectedEvents = eventRows();

        var source = opener.register(FIXTURE);
        opener.failAfter(5);
        var killed = importService.run(source, 0);
        // What a kill -9 leaves behind: the run still looks like it is running.
        jdbc.update("UPDATE batch_job_execution SET status = 'STARTED', exit_code = 'UNKNOWN', end_time = NULL"
                + " WHERE job_execution_id = ?", killed.getId());
        jdbc.update("UPDATE batch_step_execution SET status = 'STARTED', exit_code = 'UNKNOWN', end_time = NULL"
                + " WHERE job_execution_id = ? AND step_name = 'extract'", killed.getId());

        opener.heal();
        var resumed = importService.run(source, 0);

        assertThat(resumed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(resumed.getJobInstanceId()).isEqualTo(killed.getJobInstanceId());
        assertThat(eventRows()).isEqualTo(expectedEvents);
    }

    @Test
    void labelsWithCsvSpecialCharactersArriveIntact() {
        var tricky = """
                {"type":"item","id":"Q8","labels":{"en":{"language":"en",\
                "value":"Say \\"hi\\", then\\nleave \\\\ now\\u0000"}},\
                "sitelinks":{%s},"claims":{%s}}""".formatted(enwiki("Tricky, \\\"quoted\\\""), claims(
                claim("P585", time("P585", "+1066-10-14T00:00:00Z", 11)),
                claim("P625", coords(50.9, 0.49, 0.001))));

        var execution = importService.run(opener.register(List.of(tricky)), 0);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var row = event("Q8:HAPPENED");
        assertThat(row.get("label")).isEqualTo("Say \"hi\", then\nleave \\ now");
        assertThat(row.get("wiki_title")).isEqualTo("Tricky, \"quoted\"");
    }

    @Test
    void concurrentProcessingGivesTheSameRowsOnEveryRun() {
        var large = largeFixture();
        importService.run(opener.register(large), 0);
        var firstEvents = eventRows();
        var firstPlaces = placeRows();
        var firstCells = cellRows();

        importService.run(opener.register(large), 0);

        assertThat(eventRows()).isEqualTo(firstEvents);
        assertThat(placeRows()).isEqualTo(firstPlaces);
        assertThat(cellRows()).isEqualTo(firstCells);
    }

    @Test
    void concurrentProcessingLosesAndMixesUpNoEntity() {
        importService.run(opener.register(largeFixture()), 0);

        assertThat(eventIds()).hasSize(LARGE_BATTLES + LARGE_PEOPLE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM places", Long.class)).isEqualTo(LARGE_PEOPLE);
        for (int i = 1; i <= LARGE_BATTLES; i++) {
            var row = event("Q" + (1000 + i) + ":HAPPENED");
            assertThat(row.get("year")).as("Q%d", 1000 + i).isEqualTo((long) (1000 + i));
            assertThat((Double) row.get("lat")).as("Q%d", 1000 + i).isCloseTo(i * 0.5 - 30, within(EPS));
            assertThat(row.get("label")).isEqualTo("Label Q" + (1000 + i));
        }
        for (int i = 1; i <= LARGE_PEOPLE; i++) {
            var row = event("Q" + (2000 + i) + ":BORN");
            assertThat(row.get("place_qid")).isEqualTo("Q" + (3000 + i));
            assertThat((Double) row.get("lon")).as("Q%d", 2000 + i).isCloseTo(i * 0.25, within(EPS));
        }
        assertThat(timelineSum(0)).isEqualTo(LARGE_BATTLES + LARGE_PEOPLE);
    }

    @Test
    void successfulImportLeavesTheSameIndexes() {
        var before = indexRows();

        var execution = importService.run(opener.register(FIXTURE), 0);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(before).extracting(row -> row.get("indexname"))
                .contains("events_geom_idx", "events_place_qid_idx", "events_pkey");
        assertThat(indexRows()).isEqualTo(before);
    }

    @Test
    void failedImportLeavesTheSameIndexes() {
        importService.run(opener.register(SECOND_FIXTURE), 0);
        var before = indexRows();

        opener.failAfter(5);
        var failed = importService.run(opener.register(FIXTURE), 0);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(indexRows()).isEqualTo(before);
    }

    @Test
    void indexedQueriesFindTheImportedEvents() {
        importService.run(opener.register(FIXTURE), 0);

        assertThat(jdbc.queryForList(
                "SELECT id FROM events WHERE geom && ST_MakeEnvelope(-21, -11, -20, -10, 4326)", String.class))
                .containsExactly("Q3:HAPPENED");
        assertThat(jdbc.queryForList(
                "SELECT id FROM events WHERE place_qid = 'Q91'", String.class))
                .containsExactly("Q5:BORN");
    }

    @Test
    void importLeavesNoIndexInvalid() {
        importService.run(opener.register(FIXTURE), 0);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_index x JOIN pg_class t ON t.oid = x.indrelid
                WHERE t.relname IN ('events', 'places', 'timeline_cells') AND NOT (x.indisvalid AND x.indisready)
                """, Long.class)).isZero();
    }

    private static final int LARGE_BATTLES = 120;
    private static final int LARGE_PEOPLE = 40;

    /**
     * 200 entities so that chunks overlap on several threads: battles Q1001.. with their own
     * coordinates and year 1000+i, people Q2001.. born in Q3001.., and those places after them.
     */
    private static List<String> largeFixture() {
        var lines = new ArrayList<String>();
        for (int i = 1; i <= LARGE_BATTLES; i++) {
            lines.add(item("Q" + (1000 + i), enwiki("Battle " + i), claims(
                    claim("P585", time("P585", "+%04d-01-01T00:00:00Z".formatted(1000 + i), 9)),
                    claim("P625", coords(i * 0.5 - 30, i * 0.5, 0.001)))));
        }
        for (int i = 1; i <= LARGE_PEOPLE; i++) {
            lines.add(item("Q" + (2000 + i), enwiki("Person " + i), claims(
                    claim("P569", time("P569", "+%04d-01-01T00:00:00Z".formatted(1500 + i), 9)),
                    claim("P19", ref("P19", "Q" + (3000 + i))))));
        }
        for (int i = 1; i <= LARGE_PEOPLE; i++) {
            lines.add(item("Q" + (3000 + i), "", claim("P625", coords(i * 0.1, i * 0.25, 0.0001))));
        }
        return List.copyOf(lines);
    }

    /** Not via SecondaryIndexes: the catalog itself is the reference. */
    private List<Map<String, Object>> indexRows() {
        return jdbc.queryForList("""
                SELECT tablename, indexname, indexdef FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename IN ('events', 'places', 'timeline_cells')
                ORDER BY indexname
                """);
    }

    private List<String> eventIds() {
        return jdbc.queryForList("SELECT id FROM events", String.class);
    }

    private Map<String, Object> event(String id) {
        return jdbc.queryForMap("""
                SELECT *, ST_X(geom) AS lon, ST_Y(geom) AS lat FROM events WHERE id = ?
                """, id);
    }

    private List<Map<String, Object>> eventRows() {
        return jdbc.queryForList("""
                SELECT id, item_id, kind, label, wiki_title, importance, year, month, day, precision,
                       calendar, end_year, end_month, end_day, end_precision, end_calendar,
                       earliest_year, latest_year, place_qid, ST_AsText(geom) AS geom, coord_precision_deg
                FROM events ORDER BY id
                """);
    }

    private List<Map<String, Object>> placeRows() {
        return jdbc.queryForList(
                "SELECT qid, ST_AsText(geom) AS geom, coord_precision_deg FROM places ORDER BY qid");
    }

    private List<Map<String, Object>> cellRows() {
        return jdbc.queryForList(
                "SELECT * FROM timeline_cells ORDER BY level, cell_x, cell_y, decade, kind, importance");
    }

    private long timelineSum(int level) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(n), 0) FROM timeline_cells WHERE level = ?", Long.class, level);
    }
}
