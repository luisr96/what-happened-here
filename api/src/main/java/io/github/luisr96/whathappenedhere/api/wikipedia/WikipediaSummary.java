package io.github.luisr96.whathappenedhere.api.wikipedia;

import io.swagger.v3.oas.annotations.media.Schema;

/** The lead of an English Wikipedia article, as the details panel shows it. */
public record WikipediaSummary(
        @Schema(description = "Article title as Wikipedia displays it") String title,
        @Schema(description = "First paragraph, plain text") String extract,
        @Schema(description = "Link to the article") String url,
        @Schema(description = "Lead image URL, or null when the article has none",
                nullable = true)
        String thumbnailUrl) {}
