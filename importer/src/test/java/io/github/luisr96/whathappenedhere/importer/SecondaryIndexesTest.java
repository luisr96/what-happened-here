package io.github.luisr96.whathappenedhere.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs against a fresh database per test, created in {@link ImportJobTest}'s container and
 * migrated with the API's Flyway scripts, so dropping indexes can't disturb the import tests.
 */
class SecondaryIndexesTest {

    static final List<String> TABLES = List.of("events", "places", "timeline_cells");

    private static final String INDEXES = """
            SELECT i.indexname, i.indexdef, x.indisprimary
            FROM pg_indexes i
            JOIN pg_class c ON c.relname = i.indexname AND c.relnamespace = i.schemaname::regnamespace
            JOIN pg_index x ON x.indexrelid = c.oid
            WHERE i.schemaname = current_schema() AND i.tablename IN ('events', 'places', 'timeline_cells')
            """;

    record Index(String name, String definition) {
    }

    JdbcTemplate jdbc;
    TransactionTemplate tx;

    @BeforeEach
    void freshDatabase() {
        var admin = new JdbcTemplate(new SingleConnectionDataSource(
                ImportJobTest.POSTGIS.getJdbcUrl(), ImportJobTest.POSTGIS.getUsername(),
                ImportJobTest.POSTGIS.getPassword(), true));
        var name = "idx_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + name);
        var url = ImportJobTest.POSTGIS.getJdbcUrl().replaceFirst("/[^/?]+([?]|$)", "/" + name + "$1");
        var dataSource = new SingleConnectionDataSource(
                url, ImportJobTest.POSTGIS.getUsername(), ImportJobTest.POSTGIS.getPassword(), true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void dropAllReturnsExactlyTheNonPrimaryKeyIndexes() {
        var secondary = secondaryIndexes();
        assertThat(secondary).extracting(Index::name)
                .contains("events_geom_idx", "events_geog_idx", "events_years_idx",
                        "events_importance_idx", "events_item_id_idx", "events_place_qid_idx")
                .doesNotContain("events_pkey", "places_pkey", "timeline_cells_pkey");

        var dropped = new SecondaryIndexes(jdbc, TABLES).dropAll();

        assertThat(dropped).containsExactlyInAnyOrderElementsOf(
                secondary.stream().map(Index::definition).toList());
    }

    @Test
    void afterDropAllOnlyThePrimaryKeysRemain() {
        var primaryKeys = primaryKeys();
        assertThat(primaryKeys).extracting(Index::name)
                .containsExactlyInAnyOrder("events_pkey", "places_pkey", "timeline_cells_pkey");

        new SecondaryIndexes(jdbc, TABLES).dropAll();

        assertThat(allIndexes()).isEqualTo(primaryKeys);
    }

    @Test
    void recreateRestoresTheSameNamesAndDefinitions() {
        var before = allIndexes();
        var indexes = new SecondaryIndexes(jdbc, TABLES);

        indexes.recreate(indexes.dropAll());

        assertThat(allIndexes()).isEqualTo(before);
    }

    @Test
    void foreignKeyStillEnforcedAfterDropAll() {
        new SecondaryIndexes(jdbc, TABLES).dropAll();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO events (id, item_id, kind, importance, year, month, day, precision, calendar,
                                    earliest_year, latest_year, place_qid, geom)
                VALUES ('Q1:BORN', 'Q1', 'BORN', 1, 1900, 0, 0, 'YEAR', 'GREGORIAN', 1900, 1900, 'Q404',
                        ST_SetSRID(ST_MakePoint(0, 0), 4326))
                """)).hasMessageContaining("place_qid");
    }

    @Test
    void dropAllOnlyTouchesTheGivenTables() {
        var before = allIndexes();

        new SecondaryIndexes(jdbc, List.of("places")).dropAll();

        assertThat(allIndexes()).isEqualTo(before);
    }

    @Test
    void dropAllRolledBackLeavesEveryIndexInPlace() {
        var before = allIndexes();

        tx.executeWithoutResult(status -> {
            var dropped = new SecondaryIndexes(jdbc, TABLES).dropAll();
            assertThat(dropped).isNotEmpty();
            assertThat(allIndexes()).isEqualTo(primaryKeys());
            status.setRollbackOnly();
        });

        assertThat(allIndexes()).isEqualTo(before);
    }

    private Set<Index> allIndexes() {
        return jdbc.query(INDEXES, (rs, i) -> new Index(rs.getString(1), rs.getString(2)))
                .stream().collect(Collectors.toSet());
    }

    private Set<Index> primaryKeys() {
        return filtered(true);
    }

    private Set<Index> secondaryIndexes() {
        return filtered(false);
    }

    private Set<Index> filtered(boolean primary) {
        return jdbc.query(INDEXES + " AND x.indisprimary = ?",
                        (rs, i) -> new Index(rs.getString(1), rs.getString(2)), primary)
                .stream().collect(Collectors.toSet());
    }
}
