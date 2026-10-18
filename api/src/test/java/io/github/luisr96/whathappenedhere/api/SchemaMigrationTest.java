package io.github.luisr96.whathappenedhere.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaMigrationTest extends PostgisIntegrationTest {

    private static final String PARIS = "ST_GeomFromText('POINT(2.35 48.85)', 4326)";
    private static final String NO_END = "NULL, NULL, NULL, NULL, NULL";

    @Autowired
    JdbcTemplate jdbc;

    private record Column(String dataType, String udtName, boolean nullable) {}

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
    }

    @Test
    void flywayAppliedVersionOneSuccessfully() {
        var rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version = '1'");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("success")).isEqualTo(true);
    }

    @Test
    void postgisExtensionInstalled() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class)).isEqualTo(1);
    }

    @Test
    void placesColumns() {
        var cols = columns("places");
        assertThat(cols.keySet()).containsExactlyInAnyOrder("qid", "geom", "coord_precision_deg");
        assertThat(cols.get("qid")).isEqualTo(new Column("text", "text", false));
        assertThat(cols.get("geom").udtName()).isEqualTo("geometry");
        assertThat(cols.get("geom").nullable()).isFalse();
        assertThat(cols.get("coord_precision_deg")).isEqualTo(new Column("double precision", "float8", true));
    }

    @Test
    void placesGeomIsPoint4326() {
        assertThat(geometryColumn("places")).containsEntry("type", "POINT").containsEntry("srid", 4326);
    }

    @Test
    void eventsGeomIsPoint4326() {
        assertThat(geometryColumn("events")).containsEntry("type", "POINT").containsEntry("srid", 4326);
    }

    @Test
    void eventsColumns() {
        var cols = columns("events");
        assertThat(cols.keySet()).containsExactlyInAnyOrder(
                "id", "item_id", "kind", "label", "importance",
                "year", "month", "day", "precision", "calendar",
                "end_year", "end_month", "end_day", "end_precision", "end_calendar",
                "earliest_year", "latest_year", "place_qid", "geom", "coord_precision_deg");
        assertThat(cols.get("id")).isEqualTo(new Column("text", "text", false));
        assertThat(cols.get("item_id")).isEqualTo(new Column("text", "text", false));
        assertThat(cols.get("kind")).isEqualTo(new Column("text", "text", false));
        assertThat(cols.get("label")).isEqualTo(new Column("text", "text", true));
        assertThat(cols.get("importance")).isEqualTo(new Column("integer", "int4", false));
        assertThat(cols.get("year")).isEqualTo(new Column("bigint", "int8", false));
        assertThat(cols.get("month")).isEqualTo(new Column("smallint", "int2", false));
        assertThat(cols.get("day")).isEqualTo(new Column("smallint", "int2", false));
        assertThat(cols.get("precision")).isEqualTo(new Column("text", "text", false));
        assertThat(cols.get("calendar")).isEqualTo(new Column("text", "text", false));
        assertThat(cols.get("end_year")).isEqualTo(new Column("bigint", "int8", true));
        assertThat(cols.get("end_month")).isEqualTo(new Column("smallint", "int2", true));
        assertThat(cols.get("end_day")).isEqualTo(new Column("smallint", "int2", true));
        assertThat(cols.get("end_precision")).isEqualTo(new Column("text", "text", true));
        assertThat(cols.get("end_calendar")).isEqualTo(new Column("text", "text", true));
        assertThat(cols.get("earliest_year")).isEqualTo(new Column("bigint", "int8", false));
        assertThat(cols.get("latest_year")).isEqualTo(new Column("bigint", "int8", false));
        assertThat(cols.get("place_qid")).isEqualTo(new Column("text", "text", true));
        assertThat(cols.get("geom").udtName()).isEqualTo("geometry");
        assertThat(cols.get("geom").nullable()).isFalse();
        assertThat(cols.get("coord_precision_deg")).isEqualTo(new Column("double precision", "float8", true));
    }

    @Test
    void primaryKeys() {
        assertThat(primaryKey("places")).containsExactly("qid");
        assertThat(primaryKey("events")).containsExactly("id");
    }

    @Test
    void placeQidReferencesPlaces() {
        var refs = jdbc.queryForList("""
                SELECT ccu.table_name AS ref_table, ccu.column_name AS ref_column
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name = 'events'
                  AND kcu.column_name = 'place_qid'
                """);
        assertThat(refs).singleElement().satisfies(r -> {
            assertThat(r.get("ref_table")).isEqualTo("places");
            assertThat(r.get("ref_column")).isEqualTo("qid");
        });
    }

    @Test
    void insertsEventWithPlaceReferenceAndNoEndDate() {
        insertPlace("'Q90'", PARIS, "0.0001");
        insertEvent("Q254", "BORN", 0, "'DAY'", "'GREGORIAN'", NO_END, 1756, 1756, "'Q90'", PARIS);
        assertThat(jdbc.queryForObject("SELECT place_qid FROM events WHERE id = 'Q254:BORN'", String.class))
                .isEqualTo("Q90");
    }

    @Test
    void insertsEventWithOwnCoordinatesAndEndDate() {
        insertEvent("Q362", "HAPPENED", 120, "'DAY'", "'GREGORIAN'",
                "1945, 9, 2, 'DAY', 'GREGORIAN'", 1939, 1945, "NULL", PARIS);
        var row = jdbc.queryForMap("SELECT place_qid, end_year, latest_year FROM events WHERE id = 'Q362:HAPPENED'");
        assertThat(row.get("place_qid")).isNull();
        assertThat(row.get("end_year")).isEqualTo(1945L);
        assertThat(row.get("latest_year")).isEqualTo(1945L);
    }

    @Test
    void insertsBcEventWithCenturyPrecisionAndJulianCalendar() {
        // -753 at century precision is the 8th century BC: years -800 to -701.
        insert("'Q1:FOUNDED'", "'Q1'", "'FOUNDED'", 3, -753, 0, 0, "'CENTURY'", "'JULIAN'",
                NO_END, -800, -701, "NULL", PARIS);
        var row = jdbc.queryForMap("SELECT year, earliest_year, latest_year FROM events WHERE id = 'Q1:FOUNDED'");
        assertThat(row.get("year")).isEqualTo(-753L);
        assertThat(row.get("earliest_year")).isEqualTo(-800L);
        assertThat(row.get("latest_year")).isEqualTo(-701L);
    }

    @Test
    void insertsPlaceWithUnknownPrecision() {
        insertPlace("'Q597'", PARIS, "NULL");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM places WHERE qid = 'Q597' AND coord_precision_deg IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void acceptsBoundaryMonthAndDay() {
        insertDate(12, 31, "2, 12, 31, 'DAY', 'GREGORIAN'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsIdWhoseKindDiffersFromKindColumn() {
        assertRejected(() -> insert("'Q1:DIED'", "'Q1'", "'BORN'", 1, 1, 0, 0, "'YEAR'", "'GREGORIAN'",
                NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsIdWhoseItemDiffersFromItemIdColumn() {
        assertRejected(() -> insert("'Q2:BORN'", "'Q1'", "'BORN'", 1, 1, 0, 0, "'YEAR'", "'GREGORIAN'",
                NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsMonth13() {
        assertRejected(() -> insertDate(13, 1, NO_END));
    }

    @Test
    void rejectsNegativeMonth() {
        assertRejected(() -> insertDate(-1, 1, NO_END));
    }

    @Test
    void rejectsDay32() {
        assertRejected(() -> insertDate(1, 32, NO_END));
    }

    @Test
    void rejectsNegativeDay() {
        assertRejected(() -> insertDate(1, -1, NO_END));
    }

    @Test
    void rejectsEndMonth13() {
        assertRejected(() -> insertDate(1, 1, "2, 13, 1, 'DAY', 'GREGORIAN'"));
    }

    @Test
    void rejectsEndDay32() {
        assertRejected(() -> insertDate(1, 1, "2, 1, 32, 'DAY', 'GREGORIAN'"));
    }

    @Test
    void rejectsUnknownKind() {
        assertRejected(() -> insertEvent("Q1", "DISCOVERED", 1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsLowercaseKind() {
        assertRejected(() -> insertEvent("Q1", "born", 1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsUnknownPrecision() {
        assertRejected(() -> insertEvent("Q1", "BORN", 1, "'HOUR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsUnknownCalendar() {
        assertRejected(() -> insertEvent("Q1", "BORN", 1, "'YEAR'", "'HEBREW'", NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsUnknownEndPrecision() {
        assertRejected(() -> insertEvent("Q1", "HAPPENED", 1, "'YEAR'", "'GREGORIAN'",
                "2, 0, 0, 'HOUR', 'GREGORIAN'", 1, 2, "NULL", PARIS));
    }

    @Test
    void rejectsUnknownEndCalendar() {
        assertRejected(() -> insertEvent("Q1", "HAPPENED", 1, "'YEAR'", "'GREGORIAN'",
                "2, 0, 0, 'YEAR', 'HEBREW'", 1, 2, "NULL", PARIS));
    }

    @Test
    void rejectsNegativeImportance() {
        assertRejected(() -> insertEvent("Q1", "BORN", -1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void rejectsPartialEndDate() {
        assertRejected(() -> insertEvent("Q1", "HAPPENED", 1, "'YEAR'", "'GREGORIAN'",
                "2, NULL, NULL, NULL, NULL", 1, 2, "NULL", PARIS));
    }

    @Test
    void rejectsEndDateMissingOnlyCalendar() {
        assertRejected(() -> insertEvent("Q1", "HAPPENED", 1, "'YEAR'", "'GREGORIAN'",
                "2, 0, 0, 'YEAR', NULL", 1, 2, "NULL", PARIS));
    }

    @Test
    void rejectsLatestYearBeforeEarliestYear() {
        assertRejected(() -> insertEvent("Q1", "BORN", 1, "'YEAR'", "'GREGORIAN'", NO_END, 10, 9, "NULL", PARIS));
    }

    @Test
    void rejectsUnknownPlaceQid() {
        assertRejected(() -> insertEvent("Q1", "BORN", 1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "'Q404404'", PARIS));
    }

    @Test
    void rejectsEventWithoutGeom() {
        assertRejected(() -> insertEvent("Q1", "BORN", 1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", "NULL"));
    }

    @Test
    void rejectsPlaceWithoutGeom() {
        assertRejected(() -> jdbc.update("INSERT INTO places (qid, geom) VALUES ('Q1', NULL)"));
    }

    @Test
    void rejectsDuplicateEventId() {
        insertEvent("Q1", "BORN", 1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", PARIS);
        assertRejected(() -> insertEvent("Q1", "BORN", 1, "'YEAR'", "'GREGORIAN'", NO_END, 1, 1, "NULL", PARIS));
    }

    @Test
    void eventsGeomHasGistIndex() {
        assertThat(indexDefs("events")).anyMatch(d -> d.contains("USING gist (geom)"));
    }

    @Test
    void eventsGeographyCastHasGistIndex() {
        assertThat(indexDefs("events")).anyMatch(d -> d.contains("USING gist") && d.contains("geography"));
    }

    @Test
    void eventsYearRangeHasBtreeIndex() {
        assertThat(indexDefs("events")).anyMatch(d -> d.contains("USING btree (earliest_year, latest_year)"));
    }

    @Test
    void eventsImportanceHasDescendingBtreeIndex() {
        assertThat(indexDefs("events")).anyMatch(d -> d.contains("USING btree (importance DESC)"));
    }

    @Test
    void eventsItemIdHasBtreeIndex() {
        assertThat(indexDefs("events")).anyMatch(d -> d.contains("USING btree (item_id)"));
    }

    @Test
    void eventsPlaceQidHasBtreeIndex() {
        assertThat(indexDefs("events")).anyMatch(d -> d.contains("USING btree") && d.contains("(place_qid)"));
    }

    @Test
    void placesHasNoIndexBesidesPrimaryKey() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indrelid
                WHERE c.relname = 'places' AND NOT i.indisprimary
                """, Integer.class)).isZero();
    }

    private void insertPlace(String qid, String geom, String precisionDeg) {
        jdbc.update("INSERT INTO places (qid, geom, coord_precision_deg) VALUES (%s, %s, %s)"
                .formatted(qid, geom, precisionDeg));
    }

    /** A valid day-precision event on Q1 except possibly for the given month, day and end fields. */
    private void insertDate(int month, int day, String endFields) {
        insert("'Q1:HAPPENED'", "'Q1'", "'HAPPENED'", 1, 1, month, day, "'DAY'", "'GREGORIAN'",
                endFields, 1, 2, "NULL", PARIS);
    }

    /** An event with a consistent id ({@code itemId:kind}) and a year-only date. */
    private void insertEvent(String itemId, String kind, int importance, String precision, String calendar,
            String endFields, long earliest, long latest, String placeQid, String geom) {
        insert("'" + itemId + ":" + kind + "'", "'" + itemId + "'", "'" + kind + "'", importance,
                earliest, 0, 0, precision, calendar, endFields, earliest, latest, placeQid, geom);
    }

    /** Builds the insert from SQL literals so each test can put NULL or a bad value in any slot. */
    private void insert(String id, String itemId, String kind, int importance, long year, int month, int day,
            String precision, String calendar, String endFields, long earliest, long latest,
            String placeQid, String geom) {
        jdbc.update("""
                INSERT INTO events (id, item_id, kind, label, importance,
                    year, month, day, precision, calendar,
                    end_year, end_month, end_day, end_precision, end_calendar,
                    earliest_year, latest_year, place_qid, geom, coord_precision_deg)
                VALUES (%s, %s, %s, 'label', %d,
                    %d, %d, %d, %s, %s,
                    %s,
                    %d, %d, %s, %s, 0.0001)
                """.formatted(id, itemId, kind, importance, year, month, day, precision, calendar,
                endFields, earliest, latest, placeQid, geom));
    }

    private static void assertRejected(Runnable insert) {
        assertThatThrownBy(insert::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Map<String, Object> geometryColumn(String table) {
        return jdbc.queryForMap(
                "SELECT type, srid FROM geometry_columns WHERE f_table_name = ? AND f_geometry_column = 'geom'",
                table);
    }

    private Map<String, Column> columns(String table) {
        return jdbc.query("""
                SELECT column_name, data_type, udt_name, is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                """, (rs, i) -> Map.entry(rs.getString("column_name"), new Column(
                        rs.getString("data_type"), rs.getString("udt_name"),
                        "YES".equals(rs.getString("is_nullable")))),
                table).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<String> primaryKey(String table) {
        return jdbc.queryForList("""
                SELECT kcu.column_name FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_name = ?
                ORDER BY kcu.ordinal_position
                """, String.class, table);
    }

    private List<String> indexDefs(String table) {
        return jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?",
                String.class, table);
    }
}
