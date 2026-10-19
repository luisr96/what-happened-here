package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.events.EventQuery.InvalidQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The map view: events inside a viewport and time window. */
@RestController
public class EventsController {

    private final EventRepository events;

    public EventsController(EventRepository events) {
        this.events = events;
    }

    /** See {@link EventQuery} for the parameters; invalid ones give 400. */
    @GetMapping("/events")
    public EventsResponse events(@RequestParam String bbox,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false) String kinds,
            @RequestParam(defaultValue = "0") int minImportance,
            @RequestParam(defaultValue = "" + EventQuery.DEFAULT_LIMIT) int limit) {
        double[] box = EventQuery.parseBbox(bbox);
        return events.find(new EventQuery(box[0], box[1], box[2], box[3], from, to,
                EventQuery.parseKinds(kinds), minImportance, limit));
    }

    /** Same RFC 7807 shape as Spring's own 400s (spring.mvc.problemdetails.enabled). */
    @ExceptionHandler(InvalidQueryException.class)
    public ProblemDetail badRequest(InvalidQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
