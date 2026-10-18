package io.github.luisr96.whathappenedhere.extractor;

import java.io.IOException;
import java.io.InputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Builds Wikidata time datavalue objects and reads saved entities for tests. */
final class TimeValues {

    static final String GREGORIAN = "http://www.wikidata.org/entity/Q1985727";
    static final String JULIAN = "http://www.wikidata.org/entity/Q1985786";

    static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private TimeValues() {
    }

    static ObjectNode time(String time, int precision) {
        return time(time, precision, GREGORIAN);
    }

    static ObjectNode time(String time, int precision, String calendarModel) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("time", time);
        node.put("timezone", 0);
        node.put("before", 0);
        node.put("after", 0);
        node.put("precision", precision);
        node.put("calendarmodel", calendarModel);
        return node;
    }

    static JsonNode entity(String id) {
        try (InputStream in = TimeValues.class.getResourceAsStream("/entities/" + id + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("No saved entity " + id);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The datavalue value of the first non-deprecated statement of the property. */
    static JsonNode firstTimeValue(String entityId, String propertyId) {
        for (JsonNode claim : entity(entityId).path("claims").path(propertyId)) {
            if (!"deprecated".equals(claim.path("rank").asString())) {
                return claim.path("mainsnak").path("datavalue").path("value");
            }
        }
        throw new IllegalArgumentException(entityId + " has no usable " + propertyId);
    }
}
