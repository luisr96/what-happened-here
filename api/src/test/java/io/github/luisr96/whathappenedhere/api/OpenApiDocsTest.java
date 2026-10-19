package io.github.luisr96.whathappenedhere.api;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Generated OpenAPI document at /v3/api-docs and the Swagger UI. Parameters are looked up with a
 * JsonPath filter, which yields a list, so matchers use {@code hasItem}.
 */
@AutoConfigureMockMvc
class OpenApiDocsTest extends PostgisIntegrationTest {

    @Autowired
    MockMvc mvc;

    private ResultActions docs() throws Exception {
        return mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private static String param(String path, String name, String field) {
        return "$.paths['" + path + "'].get.parameters[?(@.name == '" + name + "')]." + field;
    }

    private ResultActions expectDescription(String path, String name, Matcher<String> matcher) throws Exception {
        return docs().andExpect(jsonPath(param(path, name, "description"), hasItem(matcher)));
    }

    @Test
    void apiDocsAreServedAsJson() throws Exception {
        docs().andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void swaggerUiIsServedAsHtml() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void infoHasTitleAndVersion() throws Exception {
        docs().andExpect(jsonPath("$.info.title").value("What Happened Here API"))
                .andExpect(jsonPath("$.info.version", not(emptyOrNullString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/events", "/places/nearby", "/stats/timeline"})
    void endpointIsDocumentedAsGet(String path) throws Exception {
        docs().andExpect(jsonPath("$.paths['" + path + "'].get").exists());
    }

    @Test
    void eventsBboxDescribesOrderAndAntimeridianWithExample() throws Exception {
        expectDescription("/events", "bbox",
                allOf(containsString("minLon,minLat,maxLon,maxLat"), containsStringIgnoringCase("antimeridian")))
                .andExpect(jsonPath(param("/events", "bbox", "example"), hasSize(1)));
    }

    @ParameterizedTest
    @CsvSource({"/events,from", "/events,to", "/places/nearby,from", "/places/nearby,to"})
    void yearBoundsDescribeBcAndOverlapAndAreIntegers(String path, String name) throws Exception {
        expectDescription(path, name,
                allOf(containsStringIgnoringCase("negative"), containsStringIgnoringCase("overlap")))
                .andExpect(jsonPath(param(path, name, "schema.type"), hasItem("integer")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/events", "/places/nearby", "/stats/timeline"})
    void kindsNamesAllSixKindsCaseInsensitively(String path) throws Exception {
        expectDescription(path, "kinds", allOf(containsString("HAPPENED"), containsString("BORN"),
                containsString("DIED"), containsString("FOUNDED"), containsString("ENDED"),
                containsString("OPENED"), containsStringIgnoringCase("case-insensitive")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/events", "/places/nearby"})
    void limitDocumentsMaximumAndDefault(String path) throws Exception {
        expectDescription(path, "limit", containsString("2000"))
                .andExpect(jsonPath(param(path, "limit", "schema.default"), hasItem(500)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/events", "/places/nearby", "/stats/timeline"})
    void minImportanceDocumentsNonNegative(String path) throws Exception {
        String json = docs().andReturn().getResponse().getContentAsString();
        List<Object> minimum = JsonPath.read(json, param(path, "minImportance", "schema.minimum"));
        List<Object> description = JsonPath.read(json, param(path, "minImportance", "description"));
        boolean minZero = minimum.stream().anyMatch(m -> m instanceof Number n && n.doubleValue() == 0);
        boolean describes = description.stream().anyMatch(d -> d instanceof String s && s.contains(">= 0"));
        assertTrue(minZero || describes, "minImportance needs schema minimum 0 or '>= 0' in its description on " + path);
    }

    @Test
    void nearbyRadiusDescribesUnitAndMaximum() throws Exception {
        expectDescription("/places/nearby", "radius", allOf(containsStringIgnoringCase("metres"),
                anyOf(containsString("500000"), containsString("500,000"))));
    }

    @Test
    void nearbyLatAndLonDescribeRanges() throws Exception {
        expectDescription("/places/nearby", "lat", containsString("-90"));
        expectDescription("/places/nearby", "lon", containsString("-180"));
    }

    @Test
    void timelineBboxMentionsCellSnapping() throws Exception {
        expectDescription("/stats/timeline", "bbox", containsStringIgnoringCase("cell"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/events", "/places/nearby", "/stats/timeline"})
    void documentsOkAndProblemDetailBadRequest(String path) throws Exception {
        String responses = "$.paths['" + path + "'].get.responses";
        docs().andExpect(jsonPath(responses + "['200']").exists())
                .andExpect(jsonPath(responses + "['400'].content['application/problem+json'].schema['$ref']",
                        endsWith("/ProblemDetail")));
    }

    @Test
    void eventDetailDocumentsOkAndProblemDetailNotFound() throws Exception {
        String responses = "$.paths['/events/{id}'].get.responses";
        docs().andExpect(jsonPath("$.paths['/events/{id}'].get").exists())
                .andExpect(jsonPath(responses + "['200']").exists())
                .andExpect(jsonPath(responses + "['404'].content['application/problem+json'].schema['$ref']",
                        endsWith("/ProblemDetail")));
    }

    @Test
    void problemDetailSchemaHasRfc9457Fields() throws Exception {
        String props = "$.components.schemas.ProblemDetail.properties";
        docs().andExpect(jsonPath(props + ".status").exists())
                .andExpect(jsonPath(props + ".detail").exists())
                .andExpect(jsonPath(props + ".title").exists())
                .andExpect(jsonPath(props + ".type").exists());
    }

    @Test
    void eventsOkResponseReferencesEventsResponse() throws Exception {
        docs().andExpect(jsonPath("$.paths['/events'].get.responses['200'].content.*.schema['$ref']",
                hasItem(endsWith("/EventsResponse"))));
    }

    @Test
    void eventViewDocumentsApproximateAndEarliestYear() throws Exception {
        String props = "$.components.schemas.EventView.properties";
        docs().andExpect(jsonPath(props + ".approximate.description", not(emptyOrNullString())))
                .andExpect(jsonPath(props + ".earliestYear.description", not(emptyOrNullString())));
    }

    @ParameterizedTest
    @CsvSource({"/places/nearby,/EventsResponse", "/stats/timeline,/TimelineResponse"})
    void okResponseReferencesItsSchema(String path, String schemaSuffix) throws Exception {
        docs().andExpect(jsonPath("$.paths['" + path + "'].get.responses['200'].content.*.schema['$ref']",
                hasItem(endsWith(schemaSuffix))));
    }

    @Test
    void decadeCountDecadeDescribesBcFlooring() throws Exception {
        docs().andExpect(jsonPath("$.components.schemas.DecadeCount.properties.decade.description",
                allOf(not(emptyOrNullString()), containsString("-50"))));
    }

    @Test
    void timelineBboxSaysSmallBoxesAreExact() throws Exception {
        expectDescription("/stats/timeline", "bbox", containsStringIgnoringCase("narrower or shorter"));
    }

    @Test
    void timelineOperationMentionsEarliestYear() throws Exception {
        String json = docs().andReturn().getResponse().getContentAsString();
        java.util.Map<String, Object> operation = JsonPath.read(json, "$.paths['/stats/timeline'].get");
        Object summary = operation.get("summary");
        Object description = operation.get("description");
        boolean mentions = (summary instanceof String s && s.contains("earliestYear"))
                || (description instanceof String d && d.contains("earliestYear"));
        assertTrue(mentions, "/stats/timeline summary or description should mention earliestYear");
    }
}
