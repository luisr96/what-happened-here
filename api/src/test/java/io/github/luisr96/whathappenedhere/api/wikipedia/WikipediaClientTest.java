package io.github.luisr96.whathappenedhere.api.wikipedia;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** WikipediaClient against a mocked REST server; no network, no Spring context. */
class WikipediaClientTest {

    private static final String BASE = "https://wiki.test/api/rest_v1";

    private MockRestServiceServer server;
    private WikipediaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WikipediaClient(builder, BASE);
    }

    /** A REST summary body; {@code thumbnail} null leaves the field out, as Wikipedia does. */
    private static String json(String title, String extract, String page, String thumbnail) {
        String thumb = thumbnail == null ? ""
                : "\"thumbnail\": {\"source\": \"" + thumbnail + "\", \"width\": 320, \"height\": 200},";
        return "{\"type\": \"standard\", \"title\": \"" + title + "\", " + thumb
                + " \"extract\": \"" + extract + "\", \"content_urls\": {\"desktop\": {\"page\": \"" + page
                + "\"}, \"mobile\": {\"page\": \"https://m.example/x\"}}}";
    }

    @Test
    void mapsFieldsIncludingThumbnail() {
        server.expect(requestTo(BASE + "/page/summary/Eiffel_Tower"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json("Eiffel Tower", "A lattice tower.",
                        "https://en.wikipedia.org/wiki/Eiffel_Tower", "https://upload.test/eiffel.jpg"),
                        MediaType.APPLICATION_JSON));

        WikipediaSummary summary = client.fetch("Eiffel Tower").orElseThrow();

        assertEquals("Eiffel Tower", summary.title());
        assertEquals("A lattice tower.", summary.extract());
        assertEquals("https://en.wikipedia.org/wiki/Eiffel_Tower", summary.url());
        assertEquals("https://upload.test/eiffel.jpg", summary.thumbnailUrl());
        server.verify();
    }

    @Test
    void missingThumbnailIsNull() {
        server.expect(requestTo(BASE + "/page/summary/Hagar"))
                .andRespond(withSuccess(json("Hagar", "Biblical figure.", "https://en.wikipedia.org/wiki/Hagar", null),
                        MediaType.APPLICATION_JSON));

        WikipediaSummary summary = client.fetch("Hagar").orElseThrow();

        assertNull(summary.thumbnailUrl());
        assertEquals("Biblical figure.", summary.extract());
    }

    @Test
    void sendsUserAgentNamingTheProject() {
        server.expect(requestTo(BASE + "/page/summary/Rome"))
                .andExpect(header(HttpHeaders.USER_AGENT, containsString("what-happened-here")))
                .andRespond(withSuccess(json("Rome", "Capital.", "https://en.wikipedia.org/wiki/Rome", null),
                        MediaType.APPLICATION_JSON));

        assertTrue(client.fetch("Rome").isPresent());
        server.verify();
    }

    @Test
    void defaultContactIsTheProjectRepository() {
        server.expect(requestTo(BASE + "/page/summary/Rome"))
                .andExpect(header(HttpHeaders.USER_AGENT,
                        containsString("(https://github.com/luisr96/what-happened-here)")))
                .andRespond(withSuccess(json("Rome", "Capital.", "https://en.wikipedia.org/wiki/Rome", null),
                        MediaType.APPLICATION_JSON));

        assertTrue(client.fetch("Rome").isPresent());
        server.verify();
    }

    @Test
    void configuredContactReplacesTheDefault() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer contactServer = MockRestServiceServer.bindTo(builder).build();
        WikipediaClient withContact = new WikipediaClient(builder, BASE, "ops@example.org");
        contactServer.expect(requestTo(BASE + "/page/summary/Rome"))
                .andExpect(header(HttpHeaders.USER_AGENT, "what-happened-here/0.1 (ops@example.org)"))
                .andRespond(withSuccess(json("Rome", "Capital.", "https://en.wikipedia.org/wiki/Rome", null),
                        MediaType.APPLICATION_JSON));

        assertTrue(withContact.fetch("Rome").isPresent());
        contactServer.verify();
    }

    @Test
    void encodesSpacesQuotesAndAccentsAsOnePathSegment() {
        // A comma is legal in a path segment, so either form of it is accepted.
        server.expect(request -> {
            String raw = request.getURI().getRawPath();
            String normalised = raw.replace("%2C", ",").replace("%2c", ",");
            assertEquals("/api/rest_v1/page/summary/Caf%C3%A9_%22Le_D%C3%B4me%22,_Paris", normalised,
                    "raw path was " + raw);
        }).andRespond(withSuccess(json("Caf\\u00e9 Le D\\u00f4me", "A caf\\u00e9.",
                "https://en.wikipedia.org/wiki/Caf%C3%A9", null), MediaType.APPLICATION_JSON));

        assertTrue(client.fetch("Café \"Le Dôme\", Paris").isPresent());
        server.verify();
    }

    @Test
    void encodesSlashSoTheTitleStaysOneSegment() {
        server.expect(requestTo(URI.create(BASE + "/page/summary/AC%2FDC")))
                .andRespond(withSuccess(json("AC/DC", "Band.", "https://en.wikipedia.org/wiki/AC/DC", null),
                        MediaType.APPLICATION_JSON));

        assertEquals("AC/DC", client.fetch("AC/DC").orElseThrow().title());
        server.verify();
    }

    @Test
    void notFoundIsEmpty() {
        server.expect(requestTo(BASE + "/page/summary/No_Such_Page"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"not_found\"}"));

        assertEquals(Optional.empty(), client.fetch("No Such Page"));
    }

    @Test
    void serverErrorThrows() {
        server.expect(requestTo(BASE + "/page/summary/Rome")).andRespond(withServerError());

        assertThrows(RuntimeException.class, () -> client.fetch("Rome"));
    }

    @Test
    void otherClientErrorThrowsRatherThanEmpty() {
        server.expect(requestTo(BASE + "/page/summary/Rome")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(RuntimeException.class, () -> client.fetch("Rome"));
    }

    @Test
    void malformedJsonThrows() {
        server.expect(requestTo(BASE + "/page/summary/Rome"))
                .andRespond(withSuccess("{\"title\": \"Rome\", \"extract\": ", MediaType.APPLICATION_JSON));

        assertThrows(RuntimeException.class, () -> client.fetch("Rome"));
    }

    @Test
    void serverErrorCarriesStatus() {
        server.expect(requestTo(BASE + "/page/summary/Rome")).andRespond(withServerError());

        var e = assertThrows(WikipediaClient.WikipediaException.class, () -> client.fetch("Rome"));
        assertEquals(500, e.status());
    }

    @Test
    void tooManyRequestsCarriesStatus() {
        server.expect(requestTo(BASE + "/page/summary/Rome")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        var e = assertThrows(WikipediaClient.WikipediaException.class, () -> client.fetch("Rome"));
        assertEquals(429, e.status());
    }

    @Test
    void malformedJsonHasStatusZero() {
        server.expect(requestTo(BASE + "/page/summary/Rome"))
                .andRespond(withSuccess("{\"title\": \"Rome\", \"extract\": ", MediaType.APPLICATION_JSON));

        var e = assertThrows(WikipediaClient.WikipediaException.class, () -> client.fetch("Rome"));
        assertEquals(0, e.status());
    }
}
