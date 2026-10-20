package io.github.luisr96.whathappenedhere.importer;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Drops and recreates the secondary indexes of some tables around a bulk insert: building an
 * index once over a full table is far cheaper than updating it row by row (the GiST ones above
 * all). Definitions come from the catalog at run time, so they always match the API's Flyway
 * migrations. Indexes backing constraints (primary keys, unique, exclusion) stay: dropping them
 * would drop or orphan the constraint, and they keep uniqueness and foreign keys checked during
 * the insert. Run both calls in one transaction: Postgres DDL is transactional, so a failure in
 * between brings the old indexes back.
 */
class SecondaryIndexes {

    private final JdbcTemplate jdbc;
    private final List<String> tables;

    SecondaryIndexes(JdbcTemplate jdbc, List<String> tables) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.tables = List.copyOf(tables);
    }

    /** Drops every index of the tables that backs no constraint; returns their CREATE INDEX statements. */
    List<String> dropAll() {
        List<IndexDef> indexes = jdbc.query("""
                SELECT current_schema(), c.relname, pg_get_indexdef(x.indexrelid)
                FROM pg_index x
                JOIN pg_class c ON c.oid = x.indexrelid
                JOIN pg_class t ON t.oid = x.indrelid
                WHERE t.relnamespace = current_schema()::regnamespace AND t.relname = ANY (?)
                  AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = x.indexrelid)
                ORDER BY c.relname
                """, (rs, row) -> new IndexDef(rs.getString(1), rs.getString(2), rs.getString(3)),
                (Object) tables.toArray(String[]::new));
        for (IndexDef index : indexes) {
            jdbc.execute("DROP INDEX " + quote(index.schema()) + "." + quote(index.name()));
        }
        return indexes.stream().map(IndexDef::definition).toList();
    }

    void recreate(List<String> definitions) {
        definitions.forEach(jdbc::execute);
    }

    private record IndexDef(String schema, String name, String definition) {}

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
