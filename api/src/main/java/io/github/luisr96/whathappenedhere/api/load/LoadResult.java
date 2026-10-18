package io.github.luisr96.whathappenedhere.api.load;

/**
 * Counts from one load, so a run can be checked against the head-of-dump report.
 *
 * @param eventsRead every event in the items file
 * @param belowCutoff events dropped because their item's importance is under the cutoff
 * @param unresolved events dropped because their place QID isn't in the places file
 * @param eventsLoaded rows now in {@code events}
 * @param placesRead lines in the places file, duplicates included
 * @param placesKept rows now in {@code places}: only places some loaded event uses
 */
public record LoadResult(long eventsRead, long belowCutoff, long unresolved, long eventsLoaded,
        long placesRead, long placesKept) {}
