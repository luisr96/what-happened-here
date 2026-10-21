package io.github.luisr96.whathappenedhere.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import io.github.luisr96.whathappenedhere.extractor.Event;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /stats/summary. The summary is cached for the life of the application context, so every
 * test that reads numbers gets a fresh context (an empty cache) and fills the database before its
 * first request.
 */
class StatsSummaryEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    abstract static class Base extends PostgisIntegrationTest {

        @Autowired
        MockMvc mvc;

        @Autowired
        JdbcTemplate jdbc;

        @BeforeEach
        void clean() {
            jdbc.update("DELETE FROM timeline_cells");
            jdbc.update("DELETE FROM events");
            jdbc.update("DELETE FROM places");
        }

        void place(String qid) {
            jdbc.update("INSERT INTO places (qid, geom, coord_precision_deg) "
                    + "VALUES (?, ST_SetSRID(ST_MakePoint(1, 1), 4326), 0.0001)", qid);
        }

        void event(String item, String kind, long earliest, long latest, String placeQid) {
            jdbc.update("""
                    INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                        end_year, end_month, end_day, end_precision, end_calendar, earliest_year, latest_year,
                        place_qid, geom, coord_precision_deg)
                    VALUES (?, ?, ?, ?, 1, ?, 0, 0, 'YEAR', 'GREGORIAN', NULL, NULL, NULL, NULL, NULL, ?, ?,
                        ?, ST_SetSRID(ST_MakePoint(1, 1), 4326), 0.0001)
                    """,
                    item + ":" + kind, item, kind, "label " + item, earliest, earliest, latest, placeQid);
        }

        JsonNode summaryJson() throws Exception {
            String json = mvc.perform(get("/stats/summary")).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsString();
            return MAPPER.readTree(json);
        }
    }

    @Nested
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "app.data.dump-date=2026-10-12")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
    class WithDumpDate extends Base {

        @Test
        void countsEventsPlacesAndKinds() throws Exception {
            place("Q90");
            place("Q64");
            place("Q1490");
            event("Q1", "BORN", 1879, 1879, "Q90");
            event("Q1", "DIED", 1955, 1955, "Q64");
            event("Q2", "BORN", -100, -100, null);
            event("Q3", "HAPPENED", 1914, 1918, null);
            event("Q4", "FOUNDED", 1200, 1299, null);
            event("Q4", "ENDED", 1806, 1806, null);
            mvc.perform(get("/stats/summary")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.events").value(6))
                    .andExpect(jsonPath("$.places").value(3))
                    .andExpect(jsonPath("$.eventsByKind.HAPPENED").value(1))
                    .andExpect(jsonPath("$.eventsByKind.BORN").value(2))
                    .andExpect(jsonPath("$.eventsByKind.DIED").value(1))
                    .andExpect(jsonPath("$.eventsByKind.FOUNDED").value(1))
                    .andExpect(jsonPath("$.eventsByKind.ENDED").value(1))
                    .andExpect(jsonPath("$.eventsByKind.OPENED").value(0));
        }

        /** earliestYear is min(earliest_year) including BC; latestYear is max(latest_year). */
        @Test
        void yearRangeSpansBcToLatestYear() throws Exception {
            event("Q1", "HAPPENED", -44, -44, null);
            event("Q2", "FOUNDED", 1900, 1999, null);
            event("Q3", "BORN", 1950, 1950, null);
            JsonNode body = summaryJson();
            assertEquals(-44, body.get("earliestYear").asLong());
            // 1999 comes only from latest_year; max(earliest_year) would give 1950.
            assertEquals(1999, body.get("latestYear").asLong());
        }

        @Test
        void dumpDateComesFromConfiguration() throws Exception {
            assertEquals("2026-10-12", summaryJson().get("dumpDate").asString());
        }

        @Test
        void eventsByKindListsEveryKindInDeclarationOrder() throws Exception {
            event("Q1", "OPENED", 1889, 1889, null);
            JsonNode byKind = summaryJson().get("eventsByKind");
            List<String> keys = new ArrayList<>();
            byKind.propertyNames().forEach(keys::add);
            assertEquals(Arrays.stream(Event.Kind.values()).map(Enum::name).toList(), keys);
            assertEquals(1, byKind.get("OPENED").asLong());
            assertEquals(0, byKind.get("HAPPENED").asLong());
        }

        @Test
        void emptyDatabaseGivesZerosAndNullYears() throws Exception {
            JsonNode body = summaryJson();
            assertEquals(0, body.get("events").asLong());
            assertEquals(0, body.get("places").asLong());
            for (Event.Kind kind : Event.Kind.values()) {
                JsonNode count = body.get("eventsByKind").get(kind.name());
                assertTrue(count != null && count.isNumber() && count.asLong() == 0, kind + ": " + count);
            }
            assertTrue(body.has("earliestYear") && body.get("earliestYear").isNull(), "earliestYear null");
            assertTrue(body.has("latestYear") && body.get("latestYear").isNull(), "latestYear null");
        }

        @Test
        void secondRequestServesTheCachedNumbers() throws Exception {
            event("Q1", "BORN", 1900, 1900, null);
            assertEquals(1, summaryJson().get("events").asLong());
            place("Q90");
            event("Q2", "DIED", -500, 2000, "Q90");
            JsonNode second = summaryJson();
            assertEquals(1, second.get("events").asLong());
            assertEquals(0, second.get("places").asLong());
            assertEquals(0, second.get("eventsByKind").get("DIED").asLong());
            assertEquals(1900, second.get("earliestYear").asLong());
            assertEquals(1900, second.get("latestYear").asLong());
        }
    }

    @Nested
    @AutoConfigureMockMvc
    @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
    class WithoutDumpDate extends Base {

        @Test
        void dumpDateIsNullWhenUnset() throws Exception {
            JsonNode body = summaryJson();
            assertTrue(body.has("dumpDate") && body.get("dumpDate").isNull(), "dumpDate: " + body.get("dumpDate"));
        }
    }

    @Nested
    @AutoConfigureMockMvc
    class Docs extends PostgisIntegrationTest {

        @Autowired
        MockMvc mvc;

        @Test
        void documentedWithResponseSchemaProperties() throws Exception {
            String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode docs = MAPPER.readTree(json);
            JsonNode content = docs.path("paths").path("/stats/summary").path("get")
                    .path("responses").path("200").path("content");
            assertTrue(content.isObject() && !content.isEmpty(), "/stats/summary 200 response documented");
            String ref = content.iterator().next().path("schema").path("$ref").asString();
            String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
            JsonNode props = docs.path("components").path("schemas").path(schemaName).path("properties");
            for (String p : List.of("events", "places", "eventsByKind", "earliestYear", "latestYear", "dumpDate")) {
                assertTrue(props.has(p), "schema " + schemaName + " has property " + p);
            }
        }
    }
}
