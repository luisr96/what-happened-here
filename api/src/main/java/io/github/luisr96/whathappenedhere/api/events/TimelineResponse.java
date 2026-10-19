package io.github.luisr96.whathappenedhere.api.events;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Body of {@code GET /stats/timeline}: the histogram behind the time slider. */
public record TimelineResponse(List<DecadeCount> decades) {

    /** Events whose earliest year falls in one decade. */
    public record DecadeCount(
            @Schema(description = "First year of the decade, floor(year / 10) * 10: -50 covers "
                    + "-50..-41") long decade,
            @Schema(description = "Events in the decade") long count) {}
}
