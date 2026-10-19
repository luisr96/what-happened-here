package io.github.luisr96.whathappenedhere.api.events;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.luisr96.whathappenedhere.api.PostgisIntegrationTest;
import io.github.luisr96.whathappenedhere.api.wikipedia.WikipediaClient;
import io.github.luisr96.whathappenedhere.api.wikipedia.WikipediaSummary;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /events/{id}. The summary cache is a singleton bean that outlives each test, so every test
 * uses its own wiki titles to stay independent of the others.
 */
@AutoConfigureMockMvc
class EventDetailEndpointTest extends PostgisIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    WikipediaClient wikipedia;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM places");
        jdbc.update("INSERT INTO places (qid, geom, coord_precision_deg)"
                + " VALUES ('Q220', ST_SetSRID(ST_MakePoint(12.5, 41.9), 4326), 1.0)");
    }

    /** A YEAR-precision Julian event at Rome with 1-degree (approximate) coordinates. */
    private void insert(String item, String kind, long year, String wikiTitle) {
        jdbc.update("""
                INSERT INTO events (id, item_id, kind, label, importance, year, month, day, precision, calendar,
                    earliest_year, latest_year, place_qid, geom, coord_precision_deg, wiki_title)
                VALUES (?, ?, ?, ?, 42, ?, 0, 0, 'YEAR', 'JULIAN', ?, ?, 'Q220',
                    ST_SetSRID(ST_MakePoint(12.5, 41.9), 4326), 1.0, ?)
                """,
                item + ":" + kind, item, kind, "label " + item, year, year, year, wikiTitle);
    }

    @Test
    void eventWithTitleHasEventAndSummary() throws Exception {
        insert("Q1048", "DIED", -44, "Julius Caesar (detail test)");
        when(wikipedia.fetch("Julius Caesar (detail test)")).thenReturn(Optional.of(new WikipediaSummary(
                "Julius Caesar", "Roman general.", "https://en.wikipedia.org/wiki/Julius_Caesar",
                "https://upload.test/caesar.jpg")));

        mvc.perform(get("/events/Q1048:DIED"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.event.id").value("Q1048:DIED"))
                .andExpect(jsonPath("$.event.itemId").value("Q1048"))
                .andExpect(jsonPath("$.event.kind").value("DIED"))
                .andExpect(jsonPath("$.event.importance").value(42))
                .andExpect(jsonPath("$.event.earliestYear").value(-44))
                .andExpect(jsonPath("$.event.approximate").value(true))
                .andExpect(jsonPath("$.wikipedia.title").value("Julius Caesar"))
                .andExpect(jsonPath("$.wikipedia.extract").value("Roman general."))
                .andExpect(jsonPath("$.wikipedia.url").value("https://en.wikipedia.org/wiki/Julius_Caesar"))
                .andExpect(jsonPath("$.wikipedia.thumbnailUrl").value("https://upload.test/caesar.jpg"));
    }

    @Test
    void eventWithoutTitleHasNullSummaryAndSkipsClient() throws Exception {
        insert("Q2001", "HAPPENED", 1900, null);

        mvc.perform(get("/events/Q2001:HAPPENED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id").value("Q2001:HAPPENED"))
                .andExpect(jsonPath("$.wikipedia").value((Object) null));
        verify(wikipedia, never()).fetch(any());
    }

    @Test
    void clientFailureStillReturnsEvent() throws Exception {
        insert("Q2002", "FOUNDED", 1200, "Failing title (detail test)");
        when(wikipedia.fetch(anyString())).thenThrow(new IllegalStateException("HTTP 503"));

        mvc.perform(get("/events/Q2002:FOUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id").value("Q2002:FOUNDED"))
                .andExpect(jsonPath("$.wikipedia").value((Object) null));
    }

    @Test
    void unknownIdIsProblemNotFound() throws Exception {
        mvc.perform(get("/events/Q999999:BORN"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())));
        verify(wikipedia, never()).fetch(any());
    }

    @Test
    void idWithColonIsMatchedWhole() throws Exception {
        insert("Q254", "BORN", 1756, null);
        insert("Q254", "DIED", 1791, null);

        mvc.perform(get("/events/Q254:BORN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id").value("Q254:BORN"))
                .andExpect(jsonPath("$.event.kind").value("BORN"));
        mvc.perform(get("/events/Q254")).andExpect(status().isNotFound());
    }

    @Test
    void eventsOfSameItemShareOneFetch() throws Exception {
        insert("Q255", "BORN", 1770, "Shared title (detail test)");
        insert("Q255", "DIED", 1827, "Shared title (detail test)");
        when(wikipedia.fetch("Shared title (detail test)")).thenReturn(Optional.of(new WikipediaSummary(
                "Ludwig van Beethoven", "Composer.", "https://en.wikipedia.org/wiki/Ludwig_van_Beethoven", null)));

        mvc.perform(get("/events/Q255:BORN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wikipedia.title").value("Ludwig van Beethoven"))
                .andExpect(jsonPath("$.wikipedia.thumbnailUrl").value((Object) null));
        mvc.perform(get("/events/Q255:DIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wikipedia.title").value("Ludwig van Beethoven"));
        verify(wikipedia, times(1)).fetch("Shared title (detail test)");
    }
}
