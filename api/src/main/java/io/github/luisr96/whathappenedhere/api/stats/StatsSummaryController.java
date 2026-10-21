package io.github.luisr96.whathappenedhere.api.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dataset in numbers. They only change with a re-import, after which the API is restarted, so
 * they are counted on the first request (not at startup, so a fresh database isn't counted empty)
 * and then served from memory: counting 5.7 million events on every page load would be wasted work.
 */
@RestController
@ApiResponse(responseCode = "200", description = "OK")
public class StatsSummaryController {

    private final StatsRepository stats;
    private final String dumpDate;
    private StatsSummary summary;

    public StatsSummaryController(StatsRepository stats,
            @Value("${app.data.dump-date:#{null}}") String dumpDate) {
        this.stats = stats;
        this.dumpDate = dumpDate;
    }

    @GetMapping("/stats/summary")
    @Operation(summary = "Whole-dataset numbers: events, places, events per kind, years covered",
            description = "Counted once per API start; the data only changes with a re-import.")
    public synchronized StatsSummary summary() {
        if (summary == null) {
            summary = stats.count(dumpDate);
        }
        return summary;
    }
}
