package io.github.luisr96.whathappenedhere.extractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * A date as Wikidata states it, at the precision it actually carries.
 *
 * <p>Not a {@code java.time} type: historic dates need BC years, imprecise values and the
 * calendar they were written in. The year is kept exactly as written; {@link #earliestYear()}
 * and {@link #latestYear()} are the single place where an imprecise year is interpreted, and are
 * what filtering, sorting and bucketing should use.
 *
 * @param year  year in historical numbering: negative for BC, never 0 (-44 is 44 BC)
 * @param month 1-12, or 0 when below the precision
 * @param day   1-31, or 0 when below the precision
 * @param precision how much of the date is meaningful; {@code year} is kept as written even when
 *     the precision is coarser than a year
 * @param calendar the calendar the date is written in; never converted
 */
public record HistoricDate(long year, int month, int day, Precision precision, Calendar calendar) {

    /** How much of the date is meaningful, coarsest first. */
    public enum Precision { MILLENNIUM, CENTURY, DECADE, YEAR, MONTH, DAY }

    /** Calendar model the date is written in (Wikidata Q1985727 / Q1985786). */
    public enum Calendar { GREGORIAN, JULIAN }

    private static final Pattern TIME =
            Pattern.compile("([+-])(\\d+)-(\\d{2})-(\\d{2})T\\d{2}:\\d{2}:\\d{2}Z");
    private static final String GREGORIAN_URI = "http://www.wikidata.org/entity/Q1985727";
    private static final String JULIAN_URI = "http://www.wikidata.org/entity/Q1985786";

    /**
     * Parses the {@code value} object of a Wikidata time datavalue.
     *
     * <p>Sub-day precisions are truncated to the day. The {@code before}/{@code after}
     * uncertainty fields are ignored.
     *
     * @return the date, or null when it is unusable: missing or malformed fields, an unknown
     *     calendar, year 0, precision coarser than a millennium, or a value claiming more
     *     precision than it carries (e.g. {@code +1887-00-00} at day precision)
     */
    public static HistoricDate parse(JsonNode timeValue) {
        if (timeValue == null || !timeValue.isObject()) {
            return null;
        }
        Precision precision = precision(timeValue.path("precision"));
        Calendar calendar = calendar(timeValue.path("calendarmodel"));
        JsonNode time = timeValue.path("time");
        if (precision == null || calendar == null || !time.isString()) {
            return null;
        }
        Matcher m = TIME.matcher(time.stringValue());
        if (!m.matches()) {
            return null;
        }
        long year;
        try {
            year = Long.parseLong(m.group(2));
        } catch (NumberFormatException tooLong) {
            return null;
        }
        if (m.group(1).equals("-")) {
            year = -year;
        }
        int month = Integer.parseInt(m.group(3));
        int day = Integer.parseInt(m.group(4));
        if (year == 0 || month > 12 || day > 31) {
            return null;
        }
        if (precision.compareTo(Precision.MONTH) >= 0 && month == 0
                || precision == Precision.DAY && day == 0) {
            return null;
        }
        return new HistoricDate(
                year,
                precision.compareTo(Precision.MONTH) >= 0 ? month : 0,
                precision == Precision.DAY ? day : 0,
                precision,
                calendar);
    }

    /**
     * Inclusive start of the period the stated year falls in, by Wikidata's convention that a
     * decade/century/millennium is the one containing the year: century +1801 and +1900 both
     * give 1801..1900, decade -5 gives -9..-1. Equal to {@link #year()} at year precision or
     * finer. BC is negative and the result is never 0.
     */
    public long earliestYear() {
        return year > 0 ? firstOfPeriod() : -lastOfPeriod();
    }

    /** Inclusive end of the period; see {@link #earliestYear()}. */
    public long latestYear() {
        return year > 0 ? lastOfPeriod() : -firstOfPeriod();
    }

    // Both helpers work on the unsigned year; BC mirrors CE, so callers flip sign and ends.
    // Decades count from 0 (1880-1889), centuries and millennia from 1 (1801-1900).
    private long firstOfPeriod() {
        long size = periodSize();
        long magnitude = Math.abs(year);
        if (precision == Precision.DECADE) {
            return Math.max(magnitude / size * size, 1);
        }
        return (magnitude + size - 1) / size * size - size + 1;
    }

    private long lastOfPeriod() {
        long size = periodSize();
        long magnitude = Math.abs(year);
        if (precision == Precision.DECADE) {
            return magnitude / size * size + size - 1;
        }
        return (magnitude + size - 1) / size * size;
    }

    private long periodSize() {
        return switch (precision) {
            case MILLENNIUM -> 1000;
            case CENTURY -> 100;
            case DECADE -> 10;
            case YEAR, MONTH, DAY -> 1;
        };
    }

    private static Precision precision(JsonNode node) {
        if (!node.isInt()) {
            return null;
        }
        return switch (node.intValue()) {
            case 6 -> Precision.MILLENNIUM;
            case 7 -> Precision.CENTURY;
            case 8 -> Precision.DECADE;
            case 9 -> Precision.YEAR;
            case 10 -> Precision.MONTH;
            case 11, 12, 13, 14 -> Precision.DAY;
            default -> null;
        };
    }

    private static Calendar calendar(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        return switch (node.stringValue()) {
            case GREGORIAN_URI -> Calendar.GREGORIAN;
            case JULIAN_URI -> Calendar.JULIAN;
            default -> null;
        };
    }
}
