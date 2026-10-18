package io.github.luisr96.whathappenedhere.api.load;

import io.github.luisr96.whathappenedhere.extractor.HistoricDate;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.copy.PGCopyOutputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads sampler output ({@code items.jsonl}, {@code places.jsonl}) into Postgres, replacing what
 * is there. Lines are streamed with COPY into staging tables, then {@code sql/resolve.sql} copies
 * place coordinates onto events and drops what can't be resolved. All in one transaction.
 */
@Component
public class SampleLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STAGE_SQL = sql("sql/stage.sql");
    private static final String RESOLVE_SQL = sql("sql/resolve.sql");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SampleLoader(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /**
     * Replaces events and places; items with importance under {@code minImportance} are skipped.
     * A malformed or unreadable file, or a line missing a required field, fails the whole load
     * and keeps the previous contents: the input is our own sampler's output, so that is a bug.
     */
    public LoadResult load(Path items, Path places, int minImportance) {
        if (minImportance < 0) {
            throw new IllegalArgumentException("minImportance must be >= 0: " + minImportance);
        }
        return transaction.execute(status -> {
            jdbc.execute(STAGE_SQL);
            EventCounts events = stageEvents(items, minImportance);
            long placesRead = stagePlaces(places);
            jdbc.execute(RESOLVE_SQL);
            long loaded = count("events");
            return new LoadResult(events.read(), events.belowCutoff(), events.staged() - loaded,
                    loaded, placesRead, count("places"));
        });
    }

    private record EventCounts(long read, long belowCutoff, long staged) {}

    private EventCounts stageEvents(Path items, int minImportance) {
        long[] read = {0}, belowCutoff = {0}, staged = {0};
        copy("stage_events", items, (item, out) -> {
            boolean kept = item.required("importance").asInt() >= minImportance;
            for (JsonNode event : item.path("events")) {
                read[0]++;
                if (kept) {
                    out.write(eventRow(item, event));
                    staged[0]++;
                } else {
                    belowCutoff[0]++;
                }
            }
        });
        return new EventCounts(read[0], belowCutoff[0], staged[0]);
    }

    private long stagePlaces(Path places) {
        long[] read = {0};
        copy("stage_places", places, (place, out) -> {
            read[0]++;
            out.write(csv(place.required("qid").asString(), place.required("longitude").asDouble(),
                    place.required("latitude").asDouble(), number(place.path("precision"))));
        });
        return read[0];
    }

    private static String eventRow(JsonNode item, JsonNode event) {
        String itemId = item.required("itemId").asString();
        String kind = event.required("kind").asString();
        HistoricDate when = date(event.required("when"));
        HistoricDate end = event.path("endDate").isObject() ? date(event.path("endDate")) : null;
        JsonNode where = event.required("where");
        boolean isRef = where.has("qid");
        return csv(itemId + ":" + kind, itemId, kind, text(item.path("label")),
                item.required("importance").asInt(),
                when.year(), when.month(), when.day(), when.precision(), when.calendar(),
                end == null ? null : end.year(), end == null ? null : end.month(),
                end == null ? null : end.day(), end == null ? null : end.precision(),
                end == null ? null : end.calendar(),
                when.earliestYear(), (end == null ? when : end).latestYear(),
                isRef ? where.path("qid").asString() : null,
                isRef ? null : where.required("longitude").asDouble(),
                isRef ? null : where.required("latitude").asDouble(),
                isRef ? null : number(where.path("precision")));
    }

    private static HistoricDate date(JsonNode node) {
        return new HistoricDate(node.required("year").asLong(), node.required("month").asInt(),
                node.required("day").asInt(),
                HistoricDate.Precision.valueOf(node.required("precision").asString()),
                HistoricDate.Calendar.valueOf(node.required("calendar").asString()));
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.stringValue() : null;
    }

    private static Double number(JsonNode node) {
        return node.isNumber() ? node.doubleValue() : null;
    }

    /** One CSV line for COPY: null is an unquoted empty field, every other value is quoted. */
    private static String csv(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            if (values[i] != null) {
                line.append('"').append(values[i].toString().replace("\"", "\"\"")).append('"');
            }
        }
        return line.append('\n').toString();
    }

    @FunctionalInterface
    private interface LineWriter {
        void write(JsonNode line, Writer out) throws IOException;
    }

    /** Streams {@code file}, one JSON object per line, into {@code table} without buffering it. */
    private void copy(String table, Path file, LineWriter writer) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                Writer out = new BufferedWriter(new OutputStreamWriter(new PGCopyOutputStream(
                        connection.unwrap(PGConnection.class),
                        "COPY " + table + " FROM STDIN (FORMAT csv)"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    writer.write(MAPPER.readTree(line), out);
                }
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("COPY into " + table + " failed", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static String sql(String resource) {
        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
