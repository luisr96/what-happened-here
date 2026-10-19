package io.github.luisr96.whathappenedhere.api;

import io.github.luisr96.whathappenedhere.api.events.EventFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata, served at {@code /v3/api-docs} with a Swagger UI at
 * {@code /swagger-ui/index.html}. Parameter texts shared by several endpoints live here so they
 * can't drift apart.
 */
@Configuration
public class ApiDocs {

    public static final String BBOX = "Viewport as minLon,minLat,maxLon,maxLat in degrees "
            + "(lon -180..180, lat -90..90, edges included). minLon > maxLon means the box crosses "
            + "the antimeridian, e.g. 170,-10,-170,10.";
    public static final String BBOX_EXAMPLE = "-10,35,30,60";
    public static final String FROM = "Earliest year wanted; negative for BC (-1 is 1 BC). "
            + "An event matches when its year range [earliestYear, latestYear] overlaps from..to. "
            + "Omit for no lower bound.";
    public static final String TO = "Latest year wanted; negative for BC. An event matches when "
            + "its year range [earliestYear, latestYear] overlaps from..to. "
            + "Omit for no upper bound.";
    public static final String KINDS = "Comma list of kinds, case-insensitive: HAPPENED, BORN, "
            + "DIED, FOUNDED, ENDED, OPENED. Omit for all kinds.";
    public static final String MIN_IMPORTANCE = "Only items with at least this many Wikipedia "
            + "sitelinks; >= 0.";
    public static final String LIMIT = "Maximum events returned, 1.." + EventFilter.MAX_LIMIT
            + "; the most important come first and truncated says whether more matched.";
    public static final String RADIUS = "Radius in metres, 1.." + EventFilter.MAX_RADIUS_METRES
            + ", measured on the spheroid.";
    public static final String TIMELINE_BBOX = BBOX + " Large boxes are snapped outward to whole "
            + "grid cells (10, 1 or 0.1 degrees, at least 10 cells across), so counts may include "
            + "events up to one cell outside; boxes narrower or shorter than 1 degree are counted "
            + "exactly.";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("What Happened Here API")
                .version("0.1.0")
                .description("Events from Wikidata with a time and a place. Errors are RFC 9457 "
                        + "problem details (application/problem+json)."));
    }
}
