package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.extractor.Event;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which events a request wants, shared by every endpoint. Invalid input throws
 * {@link InvalidQueryException}, which controllers turn into 400. All bounds are inclusive.
 *
 * @param area where to look
 * @param from earliest year wanted (negative for BC), or null for no lower bound
 * @param to latest year wanted, or null for no upper bound
 * @param kinds kinds to include; empty means all
 * @param minImportance at least 0
 */
public record EventFilter(Area area, Long from, Long to, Set<Event.Kind> kinds, int minImportance) {

    // Result limits are not part of the filter but are validated with the rest of the request.
    public static final int DEFAULT_LIMIT = 500;
    public static final int MAX_LIMIT = 2000;
    public static final int MAX_RADIUS_METRES = 500_000;

    /** A client error in the request, as opposed to an {@code IllegalArgumentException} bug. */
    public static class InvalidQueryException extends IllegalArgumentException {
        InvalidQueryException(String message) {
            super(message);
        }
    }

    /** Where to look: a map viewport or a circle around a point. */
    public sealed interface Area {}

    /**
     * A lon/lat rectangle; {@code minLon > maxLon} means it crosses the antimeridian.
     *
     * @param minLat south edge, at most {@code maxLat}
     */
    public record Box(double minLon, double minLat, double maxLon, double maxLat) implements Area {
        public Box {
            requireLon(minLon);
            requireLon(maxLon);
            requireLat(minLat);
            requireLat(maxLat);
            require(minLat <= maxLat, "minLat must not exceed maxLat");
        }

        /** True when the box wraps past 180°, e.g. 170,-10,-170,10. */
        public boolean crossesAntimeridian() {
            return minLon > maxLon;
        }

        /** Parses {@code minLon,minLat,maxLon,maxLat}. */
        public static Box parse(String bbox) {
            String[] parts = bbox.split(",", -1);
            require(parts.length == 4, "bbox must be minLon,minLat,maxLon,maxLat");
            try {
                double[] v = Arrays.stream(parts)
                        .mapToDouble(part -> Double.parseDouble(part.strip()))
                        .toArray();
                return new Box(v[0], v[1], v[2], v[3]);
            } catch (NumberFormatException e) {
                throw new InvalidQueryException("bbox values must be numbers");
            }
        }
    }

    /**
     * Everything within {@code radiusMetres} of a point, measured on the spheroid.
     *
     * @param radiusMetres 1 to {@link #MAX_RADIUS_METRES}
     */
    public record Circle(double lat, double lon, int radiusMetres) implements Area {
        public Circle {
            requireLat(lat);
            requireLon(lon);
            require(radiusMetres >= 1 && radiusMetres <= MAX_RADIUS_METRES,
                    "radius must be within [1, " + MAX_RADIUS_METRES + "] metres");
        }
    }

    public EventFilter {
        require(from == null || to == null || from <= to, "from must not exceed to");
        require(minImportance >= 0, "minImportance must be >= 0");
        kinds = Set.copyOf(kinds);
    }

    /** Parses a comma list of kind names, ignoring case; null or blank means all kinds. */
    public static Set<Event.Kind> parseKinds(String kinds) {
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

    /** Checks a result limit: 1 to {@link #MAX_LIMIT}. */
    public static int requireLimit(int limit) {
        require(limit >= 1 && limit <= MAX_LIMIT, "limit must be within [1, " + MAX_LIMIT + "]");
        return limit;
    }

    // NaN and infinities fail these comparisons too, so they are rejected.
    private static void requireLon(double lon) {
        require(lon >= -180 && lon <= 180, "longitude must be within [-180, 180]");
    }

    private static void requireLat(double lat) {
        require(lat >= -90 && lat <= 90, "latitude must be within [-90, 90]");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidQueryException(message);
        }
    }
}
