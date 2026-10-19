package io.github.luisr96.whathappenedhere.api.events;

import io.github.luisr96.whathappenedhere.api.events.EventsResponse.EventView;
import io.github.luisr96.whathappenedhere.api.wikipedia.WikipediaSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code GET /events/{id}}. */
public record EventDetails(
        @Schema(description = "The event, as in /events") EventView event,
        @Schema(description = "English Wikipedia summary of the event's item, or null when it has "
                + "no article or Wikipedia could not be reached") WikipediaSummary wikipedia) {}
