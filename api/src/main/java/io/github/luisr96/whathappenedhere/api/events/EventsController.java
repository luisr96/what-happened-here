package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.events.EventFilter.Box;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.Circle;
import io.github.luisr96.whathappenedhere.api.events.EventFilter.InvalidQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read endpoints over events; see {@link EventFilter} for the shared parameters. */
@RestController
public class EventsController {

    private final EventRepository events;

    public EventsController(EventRepository events) {
        this.events = events;
    }

    /** The map view: events inside a viewport ({@code bbox=minLon,minLat,maxLon,maxLat}). */
    @GetMapping("/events")
    public EventsResponse events(@RequestParam String bbox,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false) String kinds,
            @RequestParam(defaultValue = "0") int minImportance,
            @RequestParam(defaultValue = "" + EventFilter.DEFAULT_LIMIT) int limit) {
        return events.find(new EventFilter(Box.parse(bbox), from, to,
                EventFilter.parseKinds(kinds), minImportance), EventFilter.requireLimit(limit));
    }

    /** "What happened here": events within {@code radius} metres, most important first. */
    @GetMapping("/places/nearby")
    public EventsResponse nearby(@RequestParam double lat, @RequestParam double lon,
            @RequestParam int radius,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false) String kinds,
            @RequestParam(defaultValue = "0") int minImportance,
            @RequestParam(defaultValue = "" + EventFilter.DEFAULT_LIMIT) int limit) {
        return events.find(new EventFilter(new Circle(lat, lon, radius), from, to,
                EventFilter.parseKinds(kinds), minImportance), EventFilter.requireLimit(limit));
    }

    /** Counts per decade for the time slider's histogram; covers all time, so no from/to. */
    @GetMapping("/stats/timeline")
    public TimelineResponse timeline(@RequestParam String bbox,
            @RequestParam(required = false) String kinds,
            @RequestParam(defaultValue = "0") int minImportance) {
        return events.timeline(Box.parse(bbox), EventFilter.parseKinds(kinds), minImportance);
    }

    /** Same RFC 7807 shape as Spring's own 400s (spring.mvc.problemdetails.enabled). */
    @ExceptionHandler(InvalidQueryException.class)
    public ProblemDetail badRequest(InvalidQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
