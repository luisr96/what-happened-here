package io.github.luisr96.whathappenedhere.extractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;

/**
 * Picks the single best value of a property from an entity's {@code claims} object.
 *
 * <p>Preferred-rank values are used if any is usable, else normal-rank ones; deprecated values
 * never. {@code somevalue}/{@code novalue} snaks and values the parser rejects are skipped, so an
 * unusable best value falls through to the next usable one. Ties within a rank are broken per
 * value type, see {@link #bestDate} and {@link #bestPlace}.
 */
public final class Statements {

    private static final List<String> RANKS_IN_ORDER = List.of("preferred", "normal");
    private static final String END_TIME = "P582";

    private static final Comparator<Candidate<HistoricDate>> EARLIEST_DATE =
            Comparator.<Candidate<HistoricDate>>comparingLong(c -> c.value().earliestYear())
                    .thenComparingInt(c -> c.value().month())
                    .thenComparingInt(c -> c.value().day())
                    .thenComparingInt(Candidate::index);

    // A place that has an end time is a former one (e.g. a town that moved between provinces).
    private static final Comparator<Candidate<?>> CURRENT_THEN_FIRST =
            Comparator.<Candidate<?>, Boolean>comparing(Candidate::ended)
                    .thenComparingInt(Candidate::index);

    private Statements() {}

    /**
     * The best usable date of a time-valued property; ties go to the earliest date (by
     * {@link HistoricDate#earliestYear()}, then month, then day), e.g. Q243's two P1619 values.
     * Fields below a date's precision are 0, so a year-precision 1889 counts as earlier than
     * 1889-03-31.
     *
     * @return the date, or null when the property has no usable non-deprecated value
     */
    public static HistoricDate bestDate(JsonNode claims, String propertyId) {
        return bestDate(claims, propertyId, date -> true);
    }

    /** As {@link #bestDate(JsonNode, String)}, also treating dates that fail {@code usable} as unusable. */
    static HistoricDate bestDate(JsonNode claims, String propertyId, Predicate<HistoricDate> usable) {
        Function<JsonNode, HistoricDate> parser = value -> {
            HistoricDate date = HistoricDate.parse(value);
            return date != null && usable.test(date) ? date : null;
        };
        return best(claims, propertyId, parser, EARLIEST_DATE);
    }

    /**
     * True when the property has non-deprecated values and every one of them is on another
     * globe (e.g. Q631696, a lunar crater): a null {@link #bestPlace} alone can't tell that apart
     * from having no coordinates. A malformed value on Earth means the item isn't off Earth.
     */
    static boolean onlyOffEarthCoordinates(JsonNode claims, String propertyId) {
        if (claims == null || !claims.isObject()) {
            return false;
        }
        boolean any = false;
        for (JsonNode statement : claims.path(propertyId)) {
            if (!RANKS_IN_ORDER.contains(statement.path("rank").asString(""))
                    || !"value".equals(statement.path("mainsnak").path("snaktype").asString(""))) {
                continue;
            }
            if (!Event.Coordinates.isOffEarth(statement.path("mainsnak").path("datavalue").path("value"))) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /**
     * The best usable place of a property; ties go to current values (no P582 end time, or a
     * {@code novalue} one), then to the first in statement order.
     *
     * @param parser turns a datavalue's {@code value} object into a place, or null if unusable
     * @return the place, or null when the property has no usable non-deprecated value
     */
    public static <T> T bestPlace(JsonNode claims, String propertyId, Function<JsonNode, T> parser) {
        return best(claims, propertyId, parser, CURRENT_THEN_FIRST);
    }

    private static <T> T best(JsonNode claims, String propertyId, Function<JsonNode, T> parser,
            Comparator<? super Candidate<T>> tieBreak) {
        if (claims == null || !claims.isObject()) {
            return null;
        }
        JsonNode statements = claims.path(propertyId);
        if (!statements.isArray()) {
            return null;
        }
        for (String rank : RANKS_IN_ORDER) {
            List<Candidate<T>> usable = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) {
                JsonNode statement = statements.get(i);
                if (!rank.equals(statement.path("rank").asString(""))) {
                    continue;
                }
                JsonNode snak = statement.path("mainsnak");
                if (!"value".equals(snak.path("snaktype").asString(""))) {
                    continue;
                }
                JsonNode value = snak.path("datavalue").path("value");
                T parsed = value.isMissingNode() ? null : parser.apply(value);
                if (parsed != null) {
                    usable.add(new Candidate<>(parsed, i, hasEnded(statement)));
                }
            }
            if (!usable.isEmpty()) {
                return usable.stream().min(tieBreak).orElseThrow().value();
            }
        }
        return null;
    }

    // A novalue P582 states "no end"; a somevalue one says it ended at an unknown time.
    private static boolean hasEnded(JsonNode statement) {
        for (JsonNode endTime : statement.path("qualifiers").path(END_TIME)) {
            String snaktype = endTime.path("snaktype").asString("");
            if (snaktype.equals("value") || snaktype.equals("somevalue")) {
                return true;
            }
        }
        return false;
    }

    private record Candidate<T>(T value, int index, boolean ended) {}
}
