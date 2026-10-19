package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.ApiDocs;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Box;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Circle;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.InvalidQueryException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read endpoints over events; see {@link EventFilter} for the shared parameters. */
@RestController
@ApiResponse(responseCode = "200", description = "OK")
@ApiResponse(responseCode = "400", description = "Invalid, missing or malformed parameter",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public class EventsController {

    private final EventRepository events;

    public EventsController(EventRepository events) {
        this.events = events;
    }

    /** The map view: events inside a viewport ({@code bbox=minLon,minLat,maxLon,maxLat}). */
    @GetMapping("/events")
    @Operation(summary = "Events in a viewport and time window, most important first")
    public EventsResponse events(
            @Parameter(description = ApiDocs.BBOX, example = ApiDocs.BBOX_EXAMPLE)
            @RequestParam String bbox,
            @Parameter(description = ApiDocs.FROM) @RequestParam(required = false) Long from,
            @Parameter(description = ApiDocs.TO) @RequestParam(required = false) Long to,
            @Parameter(description = ApiDocs.KINDS) @RequestParam(required = false) String kinds,
            @Parameter(description = ApiDocs.MIN_IMPORTANCE, schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int minImportance,
            @Parameter(description = ApiDocs.LIMIT, schema = @Schema(type = "integer",
                    defaultValue = "" + EventFilter.DEFAULT_LIMIT, minimum = "1",
                    maximum = "" + EventFilter.MAX_LIMIT))
            @RequestParam(defaultValue = "" + EventFilter.DEFAULT_LIMIT) int limit) {
        return events.find(new EventFilter(Box.parse(bbox), from, to,
                EventFilter.parseKinds(kinds), minImportance), EventFilter.requireLimit(limit));
    }

    /** "What happened here": events within {@code radius} metres, most important first. */
    @GetMapping("/places/nearby")
    @Operation(summary = "Events within a radius of a point, most important first")
    public EventsResponse nearby(
            @Parameter(description = "Latitude of the centre, -90..90", example = "48.8566")
            @RequestParam double lat,
            @Parameter(description = "Longitude of the centre, -180..180", example = "2.3522")
            @RequestParam double lon,
            @Parameter(description = ApiDocs.RADIUS, example = "5000",
                    schema = @Schema(minimum = "1", maximum = "" + EventFilter.MAX_RADIUS_METRES))
            @RequestParam int radius,
            @Parameter(description = ApiDocs.FROM) @RequestParam(required = false) Long from,
            @Parameter(description = ApiDocs.TO) @RequestParam(required = false) Long to,
            @Parameter(description = ApiDocs.KINDS) @RequestParam(required = false) String kinds,
            @Parameter(description = ApiDocs.MIN_IMPORTANCE, schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int minImportance,
            @Parameter(description = ApiDocs.LIMIT, schema = @Schema(type = "integer",
                    defaultValue = "" + EventFilter.DEFAULT_LIMIT, minimum = "1",
                    maximum = "" + EventFilter.MAX_LIMIT))
            @RequestParam(defaultValue = "" + EventFilter.DEFAULT_LIMIT) int limit) {
        return events.find(new EventFilter(new Circle(lat, lon, radius), from, to,
                EventFilter.parseKinds(kinds), minImportance), EventFilter.requireLimit(limit));
    }

    /** Counts per decade for the time slider's histogram; covers all time, so no from/to. */
    @GetMapping("/stats/timeline")
    @Operation(summary = "Event counts per decade in a viewport, for the time slider",
            description = "Each event counts once, in the decade of its earliestYear "
                    + "(floor(year / 10) * 10, so -44 falls in -50). Covers all time.")
    public TimelineResponse timeline(
            @Parameter(description = ApiDocs.TIMELINE_BBOX, example = ApiDocs.BBOX_EXAMPLE)
            @RequestParam String bbox,
            @Parameter(description = ApiDocs.KINDS) @RequestParam(required = false) String kinds,
            @Parameter(description = ApiDocs.MIN_IMPORTANCE, schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int minImportance) {
        return events.timeline(Box.parse(bbox), EventFilter.parseKinds(kinds), minImportance);
    }

    /** Same RFC 9457 shape as Spring's own 400s (spring.mvc.problemdetails.enabled). */
    @ExceptionHandler(InvalidQueryException.class)
    public ProblemDetail badRequest(InvalidQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
