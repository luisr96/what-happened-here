package io.github.luisr96.whathappenedhere.api.wikipedia;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fetches article summaries from Wikipedia's REST API. Timeouts come from
 * {@code spring.http.clients.*}, so tests can bind a mock server to the builder.
 */
@Component
public class WikipediaClient {

    private static final String DEFAULT_CONTACT = "https://github.com/luisr96/what-happened-here";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Wikipedia failed or answered something unusable; {@link #status()} is 0 when not HTTP. */
    public static class WikipediaException extends RuntimeException {
        private final int status;

        public WikipediaException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final RestClient rest;
    private final String baseUrl;

    /**
     * @param contact how Wikimedia can reach the operator, sent in the User-Agent as their API
     *     etiquette asks; set {@code wikipedia.contact} (or {@code WIKIPEDIA_CONTACT}) per
     *     deployment
     */
    @Autowired
    public WikipediaClient(RestClient.Builder builder,
            @Value("${wikipedia.base-url:https://en.wikipedia.org/api/rest_v1}") String baseUrl,
            @Value("${wikipedia.contact:" + DEFAULT_CONTACT + "}") String contact) {
        this.rest = builder.defaultHeader(HttpHeaders.USER_AGENT,
                "what-happened-here/0.1 (" + contact + ")").build();
        this.baseUrl = baseUrl;
    }

    /** With the default contact. */
    public WikipediaClient(RestClient.Builder builder, String baseUrl) {
        this(builder, baseUrl, DEFAULT_CONTACT);
    }

    /**
     * The summary of the article titled {@code title}, or empty when Wikipedia has no such page.
     * Any other failure (error status, timeout, malformed body) throws.
     */
    public Optional<WikipediaSummary> fetch(String title) {
        // One path segment: "/" in "AC/DC" must become %2F, and spaces become underscores as in
        // Wikipedia's own URLs. URI.create keeps RestClient from encoding it a second time.
        URI uri = URI.create(baseUrl + "/page/summary/"
                + UriUtils.encodePathSegment(title.replace(' ', '_'), StandardCharsets.UTF_8));
        return rest.get().uri(uri).exchange((request, response) -> {
            if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            if (response.getStatusCode().isError()) {
                throw new WikipediaException("Wikipedia returned " + response.getStatusCode()
                        + " for " + title, response.getStatusCode().value());
            }
            try {
                return Optional.of(summary(MAPPER.readTree(response.getBody())));
            } catch (JacksonException e) {
                throw new WikipediaException("Unusable summary for " + title + ": " + e, 0);
            }
        });
    }

    private static WikipediaSummary summary(JsonNode page) {
        JsonNode thumbnail = page.path("thumbnail").path("source");
        return new WikipediaSummary(page.required("title").asString(),
                page.path("extract").asString(""),
                page.path("content_urls").path("desktop").required("page").asString(),
                thumbnail.isString() ? thumbnail.stringValue() : null);
    }
}
