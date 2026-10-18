package io.github.luisr96.whathappenedhere.extractor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Facts about a whole item, read once per entity rather than per event. */
public final class ItemFacts {

    // Wikimedia project wikis whose sitelink keys end in "wiki" but aren't language Wikipedias.
    private static final Set<String> PROJECT_WIKIS = Set.of(
            "commonswiki", "specieswiki", "wikidatawiki", "metawiki", "mediawikiwiki",
            "sourceswiki", "outreachwiki", "incubatorwiki", "wikimaniawiki", "foundationwiki",
            "wikifunctionswiki", "abstractwiki");

    // Grows from the head-of-dump junk report; there is no single "fictional" flag on Wikidata.
    private static final Set<String> FICTIONAL_TYPES = Set.of(
            "Q15632617", // fictional human
            "Q95074",    // fictional character
            "Q15707521", // fictional battle
            "Q3658341",  // literary character
            "Q4271324",  // mythical character
            "Q13002315", // legendary figure
            "Q178885",   // deity
            "Q15773347"); // film character

    // Real or possibly real people keep their events despite a fictional type: King Arthur
    // (Q45792) is also a disputed human, Hagar (Q214617) also a biblical figure.
    private static final Set<String> HUMAN_TYPES = Set.of(
            "Q5",        // human
            "Q21070568", // human whose existence is disputed
            "Q20643955"); // human biblical figure

    private ItemFacts() {}

    /**
     * Number of language Wikipedias linking to the item: the importance used for ranking, and
     * 0 means the item fails the sitelink gate. Sister-project keys (enwikivoyage, enwikiquote,
     * ...) don't end in "wiki"; multilingual project wikis (sourceswiki, ...) are excluded
     * explicitly.
     */
    public static int importance(JsonNode sitelinks) {
        if (sitelinks == null || !sitelinks.isObject()) {
            return 0;
        }
        int count = 0;
        for (String site : sitelinks.propertyNames()) {
            if (isWikipediaSitelink(site)) {
                count++;
            }
        }
        return count;
    }

    /** True when a sitelink key counts towards {@link #importance}, e.g. "enwiki" or "zh_yuewiki". */
    public static boolean isWikipediaSitelink(String site) {
        return site.endsWith("wiki") && !PROJECT_WIKIS.contains(site);
    }

    /**
     * The item's display label: English, else the language-independent "mul" label, else the
     * label of the alphabetically first language code (so the choice is stable).
     *
     * @return the label, or null when the item has no non-empty label at all
     */
    public static String label(JsonNode labels) {
        if (labels == null || !labels.isObject()) {
            return null;
        }
        String preferred = labelIn(labels, "en");
        if (preferred == null) {
            preferred = labelIn(labels, "mul");
        }
        if (preferred != null) {
            return preferred;
        }
        return labels.propertyNames().stream()
                .sorted()
                .map(language -> labelIn(labels, language))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * The item's types (P31) in statement order without duplicates. Unlike event properties,
     * all preferred and normal values are kept: an item can be several things at once. Deprecated
     * and unranked statements are skipped, as everywhere.
     */
    public static List<String> instanceOf(JsonNode claims) {
        if (claims == null || !claims.isObject()) {
            return List.of();
        }
        Set<String> types = new LinkedHashSet<>();
        for (JsonNode statement : claims.path("P31")) {
            String rank = statement.path("rank").asString("");
            if (!(rank.equals("preferred") || rank.equals("normal"))
                    || !"value".equals(statement.path("mainsnak").path("snaktype").asString(""))) {
                continue;
            }
            String type = WikidataValues.itemId(
                    statement.path("mainsnak").path("datavalue").path("value"));
            if (type != null) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }

    /**
     * True when the item's types mark it as fiction, which excludes all its events: any
     * fictional type, unless the item is also typed as a (possibly) real human.
     */
    public static boolean isFictional(List<String> instanceOf) {
        return instanceOf.stream().noneMatch(HUMAN_TYPES::contains)
                && instanceOf.stream().anyMatch(FICTIONAL_TYPES::contains);
    }

    private static String labelIn(JsonNode labels, String language) {
        JsonNode value = labels.path(language).path("value");
        return value.isString() && !value.stringValue().isEmpty() ? value.stringValue() : null;
    }
}
