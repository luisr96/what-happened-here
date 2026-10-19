package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.extractor.Event;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A validated {@code GET /events} request. Invalid input throws {@link InvalidQueryException},
 * which the controller turns into 400. All bounds are inclusive.
 *
 * @param minLon west edge; greater than {@code maxLon} when the box crosses the antimeridian
 * @param minLat south edge, at most {@code maxLat}
 * @param from earliest year wanted (negative for BC), or null for no lower bound
 * @param to latest year wanted, or null for no upper bound
 * @param kinds kinds to include; empty means all
 * @param minImportance at least 0
 * @param limit 1 to {@link #MAX_LIMIT}
 */
public record EventQuery(double minLon, double minLat, double maxLon, double maxLat,
        Long from, Long to, Set<Event.Kind> kinds, int minImportance, int limit) {

    public static final int DEFAULT_LIMIT = 500;
    public static final int MAX_LIMIT = 2000;

    /** A client error in the request, as opposed to an {@code IllegalArgumentException} bug. */
    public static class InvalidQueryException extends IllegalArgumentException {
        InvalidQueryException(String message) {
            super(message);
        }
    }

    public EventQuery {
        require(minLon >= -180 && minLon <= 180 && maxLon >= -180 && maxLon <= 180,
                "longitude must be within [-180, 180]");
        require(minLat >= -90 && minLat <= 90 && maxLat >= -90 && maxLat <= 90,
                "latitude must be within [-90, 90]");
        require(minLat <= maxLat, "minLat must not exceed maxLat");
        require(from == null || to == null || from <= to, "from must not exceed to");
        require(minImportance >= 0, "minImportance must be >= 0");
        require(limit >= 1 && limit <= MAX_LIMIT, "limit must be within [1, " + MAX_LIMIT + "]");
        kinds = Set.copyOf(kinds);
    }

    /** True when the box wraps past 180°, e.g. 170,-10,-170,10. */
    public boolean crossesAntimeridian() {
        return minLon > maxLon;
    }

    /** Parses {@code minLon,minLat,maxLon,maxLat}. */
    static double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",", -1);
        require(parts.length == 4, "bbox must be minLon,minLat,maxLon,maxLat");
        try {
            return Arrays.stream(parts)
                    .mapToDouble(part -> Double.parseDouble(part.strip()))
                    .toArray();
        } catch (NumberFormatException e) {
            throw new InvalidQueryException("bbox values must be numbers");
        }
    }

    /** Parses a comma list of kind names, ignoring case; null or blank means all kinds. */
    static Set<Event.Kind> parseKinds(String kinds) {
        if (kinds == null || kinds.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(kinds.split(","))
                    .map(kind -> Event.Kind.valueOf(kind.strip().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryException("kinds must be among " + List.of(Event.Kind.values()));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidQueryException(message);
        }
    }
}
