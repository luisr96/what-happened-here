package io.github.luisr96.whathappenedhere.importer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one import produced (CLAUDE.md, Testing strategy item 3): enough to spot junk (odd P31
 * types under a kind), lost events (unresolved places) and the database size before loading
 * anything into the hosted database.
 *
 * @param entitiesRead dump entities read across every execution of the run, restarts included
 * @param kinds per kind with at least one staged event, in {@code Event.Kind} order
 * @param precisions loaded events per date precision, coarsest last; only non-zero
 * @param millennia loaded events per millennium of their earliest year, oldest first, keyed in
 *     historical numbering without a year 0 ("-1000..-1", "1..1000", "1001..2000")
 * @param tableBytes total size (with indexes) of events, places and timeline_cells
 */
public record ImportReport(
        long entitiesRead,
        long stagedEvents, long loadedEvents, long droppedEvents,
        long stagedPlaces, long loadedPlaces,
        Map<String, KindStats> kinds,
        Map<String, Long> precisions,
        Map<String, Long> millennia,
        Map<String, Long> tableBytes,
        long databaseBytes) {

    private static final double MB = 1024.0 * 1024.0;

    public ImportReport {
        kinds = Collections.unmodifiableMap(new LinkedHashMap<>(kinds));
        precisions = Collections.unmodifiableMap(new LinkedHashMap<>(precisions));
        millennia = Collections.unmodifiableMap(new LinkedHashMap<>(millennia));
        tableBytes = Collections.unmodifiableMap(new LinkedHashMap<>(tableBytes));
    }

    /**
     * Counts for one kind. Staged counts are before resolving; {@code placeReferencesResolved}
     * and {@code topTypes} are over loaded events only.
     *
     * @param topTypes the 10 most common P31 types of the loaded events, most common first
     */
    public record KindStats(long staged, long loaded, long ownCoordinates, long placeReferences,
            long placeReferencesResolved, List<TypeCount> topTypes) {

        public KindStats {
            topTypes = List.copyOf(topTypes);
        }

        /** Share of place references that resolved; 100 when there were none to resolve. */
        public double resolvedPercent() {
            return placeReferences == 0 ? 100.0 : 100.0 * placeReferencesResolved / placeReferences;
        }
    }

    /** @param events loaded events whose item has this P31 type */
    public record TypeCount(String qid, long events) {}

    public String toMarkdown() {
        StringBuilder md = new StringBuilder("# Import report\n\n");
        md.append(String.format("- Entities read: %,d%n", entitiesRead));
        md.append(String.format("- Events: %,d staged, %,d loaded, %,d dropped (place not found)%n",
                stagedEvents, loadedEvents, droppedEvents));
        md.append(String.format("- Places: %,d staged, %,d used by events%n", stagedPlaces, loadedPlaces));
        md.append(String.format("- Database: %s (", mb(databaseBytes)));
        md.append(String.join(", ", tableBytes.entrySet().stream()
                .map(e -> e.getKey() + " " + mb(e.getValue())).toList()));
        md.append(")\n\n## Events per kind\n\n");
        md.append("| Kind | Loaded | Staged | Own coordinates | Place references | Resolved |\n");
        md.append("|---|---:|---:|---:|---:|---:|\n");
        kinds.forEach((kind, s) -> md.append(String.format("| %s | %,d | %,d | %,d | %,d | %.1f%% |%n",
                kind, s.loaded(), s.staged(), s.ownCoordinates(), s.placeReferences(), s.resolvedPercent())));
        md.append("\n## Most common P31 types per kind (loaded events)\n\n");
        kinds.forEach((kind, s) -> {
            md.append("- ").append(kind).append(": ");
            md.append(s.topTypes().isEmpty() ? "none" : String.join(", ", s.topTypes().stream()
                    .map(t -> String.format("%s (%,d)", t.qid(), t.events())).toList()));
            md.append('\n');
        });
        md.append("\n## Date precision (loaded events)\n\n");
        precisions.forEach((precision, n) -> md.append(String.format("- %s: %,d%n", precision, n)));
        md.append("\n## Events per millennium (earliest year)\n\n");
        millennia.forEach((range, n) -> md.append(String.format("- %s: %,d%n", range, n)));
        return md.toString();
    }

    private static String mb(long bytes) {
        return Math.round(bytes / MB) + " MB";
    }
}
