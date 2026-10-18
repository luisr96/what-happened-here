package io.github.luisr96.whathappenedhere.extractor;

import tools.jackson.databind.JsonNode;

/**
 * One kind of moment in an item's history, with when and where it happened.
 *
 * @param kind    what kind of moment this is
 * @param when    the date of the moment; for a range, its start
 * @param endDate end of a range (Happened from P580 to P582), or null for a single moment
 * @param where   own coordinates, or a reference to a place item resolved later
 */
public record Event(Kind kind, HistoricDate when, HistoricDate endDate, Place where) {

    /** The kinds of moment the extractor emits; see the When/Where table in CLAUDE.md. */
    public enum Kind { HAPPENED, BORN, DIED, FOUNDED, ENDED, OPENED }

    /** Where an event happened: either stated coordinates or a place item to look up. */
    public sealed interface Place permits Coordinates, PlaceRef {}

    /**
     * A point on Earth as Wikidata states it.
     *
     * @param latitude         degrees, -90..90
     * @param longitude        degrees, -180..180
     * @param precisionDegrees stated precision in degrees, or null when Wikidata gives none
     *                         (or a non-positive one); such points are kept, not dropped
     */
    public record Coordinates(double latitude, double longitude, Double precisionDegrees)
            implements Place {

        private static final String EARTH = "http://www.wikidata.org/entity/Q2";
        // ~11 km: coarser than a town, so the UI should show the point as approximate.
        private static final double APPROXIMATE_DEGREES = 0.1;

        /**
         * Parses the {@code value} object of a Wikidata globecoordinate datavalue.
         *
         * @return the point, or null when it is not on Earth (globe other than Q2) or latitude
         *     or longitude is missing, non-numeric or out of range (never normalised)
         */
        public static Coordinates parse(JsonNode globeCoordinateValue) {
            if (globeCoordinateValue == null || !globeCoordinateValue.isObject()) {
                return null;
            }
            JsonNode globe = globeCoordinateValue.path("globe");
            JsonNode lat = globeCoordinateValue.path("latitude");
            JsonNode lon = globeCoordinateValue.path("longitude");
            if (!globe.isString() || !globe.stringValue().equals(EARTH)
                    || !lat.isNumber() || !lon.isNumber()) {
                return null;
            }
            double latitude = lat.doubleValue();
            double longitude = lon.doubleValue();
            if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                return null;
            }
            JsonNode precision = globeCoordinateValue.path("precision");
            Double precisionDegrees = precision.isNumber() && precision.doubleValue() > 0
                    ? precision.doubleValue()
                    : null;
            return new Coordinates(latitude, longitude, precisionDegrees);
        }

        /** True when precision is unknown or at least {@link #APPROXIMATE_DEGREES} (e.g. Lisbon 1755 at 1°). */
        public boolean isApproximate() {
            return precisionDegrees == null || precisionDegrees >= APPROXIMATE_DEGREES;
        }
    }

    /**
     * A place given as another item (P276, P19, P20, P159, P131). Its coordinates come from the
     * places table later and are never copied into the event.
     *
     * @param qid the place item's id, e.g. "Q34713"
     */
    public record PlaceRef(String qid) implements Place {

        /**
         * Parses the {@code value} object of a wikibase-entityid datavalue.
         *
         * @return the reference, or null unless it names an item with a well-formed {@code id}
         */
        public static PlaceRef parse(JsonNode entityIdValue) {
            String qid = WikidataValues.itemId(entityIdValue);
            return qid == null ? null : new PlaceRef(qid);
        }
    }
}
