package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;

/**
 * What one dump entity contributes: its events ({@code item}, null if none) and, when it has
 * usable Earth coordinates, itself as a potential place for other items' events.
 */
public record ImportRecord(ExtractedItem item, String placeQid, Event.Coordinates placeCoordinates) {}
