package io.github.luisr96.whathappenedhere.extractor;

import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.MOON;
import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.coordinate;
import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.firstValue;
import static io.github.luisr96.whathappenedhere.extractor.PlaceValues.item;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.Event.PlaceRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class PlaceTest {

    private static final double EPS = 1e-9;

    @Nested
    class CoordinatesParse {

        @Test
        void keepsLatitudeLongitudeAndPrecision() {
            Coordinates c = Coordinates.parse(coordinate(48.858296, 2.294479, 2.777777777778e-06));
            assertNotNull(c);
            assertEquals(48.858296, c.latitude(), EPS);
            assertEquals(2.294479, c.longitude(), EPS);
            assertEquals(2.777777777778e-06, c.precisionDegrees(), 1e-15);
        }

        @Test
        void acceptsIntegerLatitudeLongitudeAndPrecision() {
            ObjectNode node = coordinate(0, 0, 0.01);
            node.put("latitude", 36);
            node.put("longitude", -11);
            node.put("precision", 1);
            Coordinates c = Coordinates.parse(node);
            assertNotNull(c);
            assertEquals(36.0, c.latitude(), EPS);
            assertEquals(-11.0, c.longitude(), EPS);
            assertEquals(1.0, c.precisionDegrees(), EPS);
        }

        @Test
        void keepsSouthernAndWesternSigns() {
            Coordinates c = Coordinates.parse(coordinate(-33.8568, -151.2153, 0.0001));
            assertNotNull(c);
            assertEquals(-33.8568, c.latitude(), EPS);
            assertEquals(-151.2153, c.longitude(), EPS);
        }

        @Test
        void acceptsBoundaryValues() {
            assertNotNull(Coordinates.parse(coordinate(90, 180, 0.01)));
            assertNotNull(Coordinates.parse(coordinate(-90, -180, 0.01)));
        }

        @Test
        void ignoresAltitude() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("altitude", 1234.5);
            Coordinates c = Coordinates.parse(node);
            assertNotNull(c);
            assertEquals(10.0, c.latitude(), EPS);
        }

        @Test
        void moonGlobeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("globe", MOON);
            assertNull(Coordinates.parse(node));
        }

        @Test
        void missingGlobeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.remove("globe");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void nullGlobeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.putNull("globe");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void nonStringGlobeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("globe", 2);
            assertNull(Coordinates.parse(node));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Q2", "https://www.wikidata.org/entity/Q2",
                "http://www.wikidata.org/entity/Q22", "http://www.wikidata.org/entity/Q2 "})
        void globeMustBeExactlyEarthUri(String globe) {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("globe", globe);
            assertNull(Coordinates.parse(node));
        }

        @Test
        void missingPrecisionIsUnknownNotDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.remove("precision");
            Coordinates c = Coordinates.parse(node);
            assertNotNull(c);
            assertNull(c.precisionDegrees());
        }

        @Test
        void nullPrecisionIsUnknown() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.putNull("precision");
            Coordinates c = Coordinates.parse(node);
            assertNotNull(c);
            assertNull(c.precisionDegrees());
        }

        @Test
        void nonNumericPrecisionIsUnknown() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("precision", "0.01");
            Coordinates c = Coordinates.parse(node);
            assertNotNull(c);
            assertNull(c.precisionDegrees());
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, -1.0, -0.0001})
        void zeroOrNegativePrecisionIsUnknown(double precision) {
            Coordinates c = Coordinates.parse(coordinate(10, 20, precision));
            assertNotNull(c);
            assertNull(c.precisionDegrees());
        }

        @ParameterizedTest
        @ValueSource(doubles = {90.0001, -90.0001, 1000})
        void latitudeOutOfRangeIsDropped(double latitude) {
            assertNull(Coordinates.parse(coordinate(latitude, 20, 0.01)));
        }

        @ParameterizedTest
        @ValueSource(doubles = {180.0001, -180.0001, 360})
        void longitudeOutOfRangeIsDroppedNotNormalised(double longitude) {
            assertNull(Coordinates.parse(coordinate(10, longitude, 0.01)));
        }

        @Test
        void missingLatitudeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.remove("latitude");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void missingLongitudeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.remove("longitude");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void nullLatitudeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.putNull("latitude");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void nonNumericLatitudeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("latitude", "10");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void nonNumericLongitudeIsDropped() {
            ObjectNode node = coordinate(10, 20, 0.01);
            node.put("longitude", "20");
            assertNull(Coordinates.parse(node));
        }

        @Test
        void nullJavaReferenceIsDropped() {
            assertNull(Coordinates.parse(null));
        }

        @Test
        void missingNodeIsDropped() {
            assertNull(Coordinates.parse(MAPPER.missingNode()));
        }

        @Test
        void nonObjectNodesAreDropped() {
            assertNull(Coordinates.parse(MAPPER.nullNode()));
            assertNull(Coordinates.parse(MAPPER.createArrayNode()));
            assertNull(Coordinates.parse(MAPPER.getNodeFactory().stringNode("48.8,2.29")));
        }
    }

    @Nested
    class Approximate {

        @Test
        void unknownPrecisionIsApproximate() {
            assertTrue(new Coordinates(10, 20, null).isApproximate());
        }

        @Test
        void oneDegreeIsApproximate() {
            assertTrue(new Coordinates(36, -11, 1.0).isApproximate());
        }

        @Test
        void exactlyOneTenthDegreeIsApproximate() {
            assertTrue(new Coordinates(10, 20, 0.1).isApproximate());
        }

        @Test
        void justFinerThanOneTenthIsNotApproximate() {
            assertFalse(new Coordinates(10, 20, 0.0999).isApproximate());
        }

        @Test
        void arcSecondIsNotApproximate() {
            assertFalse(new Coordinates(10, 20, 2.777777777778e-06).isApproximate());
        }
    }

    @Nested
    class PlaceRefParse {

        @Test
        void itemIdBecomesQid() {
            PlaceRef ref = PlaceRef.parse(item("Q34713"));
            assertNotNull(ref);
            assertEquals("Q34713", ref.qid());
        }

        @Test
        void usesIdFieldNotNumericId() {
            ObjectNode node = item("Q34713");
            node.put("numeric-id", 1);
            assertEquals("Q34713", PlaceRef.parse(node).qid());
        }

        @Test
        void missingIdIsDroppedEvenWithNumericId() {
            ObjectNode node = item("Q34713");
            node.remove("id");
            assertNull(PlaceRef.parse(node));
        }

        @Test
        void propertyEntityIsDropped() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("entity-type", "property");
            node.put("numeric-id", 19);
            node.put("id", "P19");
            assertNull(PlaceRef.parse(node));
        }

        @Test
        void lexemeEntityIsDropped() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("entity-type", "lexeme");
            node.put("numeric-id", 7);
            node.put("id", "L7");
            assertNull(PlaceRef.parse(node));
        }

        @Test
        void propertyTypeWithItemLikeIdIsDropped() {
            ObjectNode node = item("Q19");
            node.put("entity-type", "property");
            assertNull(PlaceRef.parse(node));
        }

        @Test
        void missingEntityTypeIsDropped() {
            ObjectNode node = item("Q34713");
            node.remove("entity-type");
            assertNull(PlaceRef.parse(node));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "Q", "q34713", "Q34713a", "34713", " Q34713", "Q-1", "Q1.5", "P19"})
        void malformedIdIsDropped(String id) {
            ObjectNode node = item("Q1");
            node.put("id", id);
            assertNull(PlaceRef.parse(node));
        }

        @Test
        void nonStringIdIsDropped() {
            ObjectNode node = item("Q1");
            node.put("id", 34713);
            assertNull(PlaceRef.parse(node));
        }

        @Test
        void nullMissingAndNonObjectNodesAreDropped() {
            assertNull(PlaceRef.parse(null));
            assertNull(PlaceRef.parse(MAPPER.missingNode()));
            assertNull(PlaceRef.parse(MAPPER.nullNode()));
            assertNull(PlaceRef.parse(MAPPER.getNodeFactory().stringNode("Q34713")));
        }
    }

    @Nested
    class SavedEntities {

        @Test
        void lisbonEarthquakeIsOneDegreeApproximate() {
            Coordinates c = Coordinates.parse(firstValue("Q191055", "P625"));
            assertNotNull(c);
            assertEquals(36.0, c.latitude(), EPS);
            assertEquals(-11.0, c.longitude(), EPS);
            assertEquals(1.0, c.precisionDegrees(), EPS);
            assertTrue(c.isApproximate());
        }

        @Test
        void eiffelTowerIsExact() {
            Coordinates c = Coordinates.parse(firstValue("Q243", "P625"));
            assertNotNull(c);
            assertEquals(48.858296, c.latitude(), 1e-6);
            assertEquals(2.294479, c.longitude(), 1e-6);
            assertFalse(c.isApproximate());
        }

        @Test
        void titanicSinkingIsNotApproximate() {
            Coordinates c = Coordinates.parse(firstValue("Q2577588", "P625"));
            assertNotNull(c);
            assertEquals(0.012037448681459, c.precisionDegrees(), 1e-12);
            assertFalse(c.isApproximate());
        }

        @Test
        void tychoCraterOnMoonIsDropped() {
            assertNull(Coordinates.parse(firstValue("Q631696", "P625")));
        }

        @Test
        void syntheticOffEarthIsDropped() {
            assertNull(Coordinates.parse(firstValue("synthetic-off-earth", "P625")));
        }

        @Test
        void mozartBirthplaceIsSalzburg() {
            PlaceRef ref = PlaceRef.parse(firstValue("Q254", "P19"));
            assertNotNull(ref);
            assertEquals("Q34713", ref.qid());
        }
    }
}
