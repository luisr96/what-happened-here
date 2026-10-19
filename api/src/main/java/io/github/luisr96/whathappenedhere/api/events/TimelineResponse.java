package io.github.luisr96.whathappenedhere.api.events;

import java.util.List;

/** Body of {@code GET /stats/timeline}: the histogram behind the time slider. */
public record TimelineResponse(List<DecadeCount> decades) {

    /**
     * Events whose earliest year falls in one decade.
     *
     * @param decade first year of the decade, {@code floor(year / 10) * 10}: -50 covers -50..-41
     */
    public record DecadeCount(long decade, long count) {}
}
