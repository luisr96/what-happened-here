package io.github.luisr96.whathappenedhere.extractor;

import java.util.List;

/**
 * Everything the extractor keeps from one Wikidata item. Item-level facts live here once rather
 * than on every event.
 *
 * @param itemId     the entity's QID; for a redirected QID this is the redirect target's id
 * @param label      display label (see {@link ItemFacts#label}), or null if the item has none
 * @param wikiTitle  English Wikipedia article title, or null when the item passed the sitelink
 *                   gate through other languages only (e.g. Q50829 Ivanovka)
 * @param importance number of language Wikipedias linking to the item; always at least 1
 * @param instanceOf the item's P31 types, for the junk report
 * @param events     at least one event, in {@link Event.Kind} order
 */
public record ExtractedItem(
        String itemId, String label, String wikiTitle, int importance, List<String> instanceOf,
        List<Event> events) {

    public ExtractedItem {
        instanceOf = List.copyOf(instanceOf);
        events = List.copyOf(events);
    }
}
