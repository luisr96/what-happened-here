package io.github.luisr96.whathappenedhere.extractor;

import static io.github.luisr96.whathappenedhere.extractor.TimeValues.MAPPER;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Builds Wikidata globecoordinate and entity-id datavalue objects and reads them from saved entities. */
final class PlaceValues {

    static final String EARTH = "http://www.wikidata.org/entity/Q2";
    static final String MOON = "http://www.wikidata.org/entity/Q405";

    private PlaceValues() {
    }

    static ObjectNode coordinate(double latitude, double longitude, double precision) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("latitude", latitude);
        node.put("longitude", longitude);
        node.putNull("altitude");
        node.put("precision", precision);
        node.put("globe", EARTH);
        return node;
    }

    static ObjectNode item(String id) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("entity-type", "item");
        node.put("numeric-id", Long.parseLong(id.substring(1)));
        node.put("id", id);
        return node;
    }

    /** The datavalue value of the first statement of the property, any rank. */
    static JsonNode firstValue(String entityId, String propertyId) {
        JsonNode claims = TimeValues.entity(entityId).path("claims").path(propertyId);
        if (claims.isEmpty()) {
            throw new IllegalArgumentException(entityId + " has no " + propertyId);
        }
        return claims.get(0).path("mainsnak").path("datavalue").path("value");
    }
}
