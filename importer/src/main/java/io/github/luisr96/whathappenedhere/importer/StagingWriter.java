package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.Event.PlaceRef;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.HistoricDate;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * COPYs a chunk into {@code stage_events} and {@code stage_places}. It uses the step's
 * transactional connection, so a chunk's rows commit together with the reader's checkpoint: a
 * restart never sees a chunk twice or half of one. Rows mirror the API's sample loader.
 */
public class StagingWriter implements ItemWriter<ImportRecord> {

    private final DataSource dataSource;

    public StagingWriter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void write(Chunk<? extends ImportRecord> chunk) throws SQLException, IOException {
        StringBuilder events = new StringBuilder();
        StringBuilder places = new StringBuilder();
        for (ImportRecord record : chunk) {
            if (record.item() != null) {
                for (Event event : record.item().events()) {
                    events.append(eventRow(record.item(), event));
                }
            }
            if (record.placeCoordinates() != null) {
                Coordinates c = record.placeCoordinates();
                places.append(csv(record.placeQid(), c.longitude(), c.latitude(), c.precisionDegrees()));
            }
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            var copy = connection.unwrap(PGConnection.class).getCopyAPI();
            if (!events.isEmpty()) {
                copy.copyIn("COPY stage_events FROM STDIN (FORMAT csv)", new StringReader(events.toString()));
            }
            if (!places.isEmpty()) {
                copy.copyIn("COPY stage_places FROM STDIN (FORMAT csv)", new StringReader(places.toString()));
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static String eventRow(ExtractedItem item, Event event) {
        HistoricDate when = event.when();
        HistoricDate end = event.endDate();
        boolean isRef = event.where() instanceof PlaceRef;
        Coordinates own = event.where() instanceof Coordinates c ? c : null;
        return csv(item.itemId() + ":" + event.kind(), item.itemId(), event.kind(), item.label(),
                item.wikiTitle(), item.importance(),
                when.year(), when.month(), when.day(), when.precision(), when.calendar(),
                end == null ? null : end.year(), end == null ? null : end.month(),
                end == null ? null : end.day(), end == null ? null : end.precision(),
                end == null ? null : end.calendar(),
                when.earliestYear(), (end == null ? when : end).latestYear(),
                isRef ? ((PlaceRef) event.where()).qid() : null,
                own == null ? null : own.longitude(), own == null ? null : own.latitude(),
                own == null ? null : own.precisionDegrees(),
                "{" + String.join(",", item.instanceOf()) + "}");
    }

    /**
     * One CSV line for COPY: null is an unquoted empty field, every other value is quoted. NUL
     * characters are dropped, since Postgres text can't hold them and one would fail the whole
     * chunk.
     */
    private static String csv(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            if (values[i] != null) {
                line.append('"')
                        .append(values[i].toString().replace("\0", "").replace("\"", "\"\""))
                        .append('"');
            }
        }
        return line.append('\n').toString();
    }
}
