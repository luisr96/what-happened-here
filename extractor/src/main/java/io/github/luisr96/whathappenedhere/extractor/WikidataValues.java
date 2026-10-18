package io.github.luisr96.whathappenedhere.extractor;

import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Readers for small Wikidata datavalue shapes shared by several facts. */
final class WikidataValues {

    private static final Pattern QID = Pattern.compile("Q[1-9]\\d*");

    private WikidataValues() {}

    /**
     * The id of an item from the {@code value} object of a wikibase-entityid datavalue.
     *
     * @return the QID, or null unless the value names an item with a well-formed {@code id}
     */
    static String itemId(JsonNode entityIdValue) {
        if (entityIdValue == null || !entityIdValue.isObject()) {
            return null;
        }
        JsonNode type = entityIdValue.path("entity-type");
        JsonNode id = entityIdValue.path("id");
        if (!type.isString() || !type.stringValue().equals("item")
                || !id.isString() || !isItemId(id.stringValue())) {
            return null;
        }
        return id.stringValue();
    }

    /** True for a well-formed item id such as "Q42" (no leading zeros). */
    static boolean isItemId(String id) {
        return QID.matcher(id).matches();
    }
}
